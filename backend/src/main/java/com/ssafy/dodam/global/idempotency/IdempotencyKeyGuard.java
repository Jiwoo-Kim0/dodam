package com.ssafy.dodam.global.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * {@code Idempotency-Key} 헤더로 쓰기 요청의 재전송을 막는다.
 *
 * <p>규칙은 결제 API 관행(Stripe 등)을 따른다. 같은 사용자·같은 작업·같은 키의 요청은
 * <ul>
 *   <li>첫 요청이 성공했고 본문이 같으면 저장된 응답을 그대로 재생한다(처리는 한 번).</li>
 *   <li>본문이 다르면 {@code IDEMPOTENCY_KEY_REUSED}(422)로 거부한다 — 키를 잘못 재사용한 클라이언트 버그를 감추지 않는다.</li>
 *   <li>첫 요청이 아직 처리 중이면 {@code IDEMPOTENT_REQUEST_IN_PROGRESS}(409)다. 행 잠금으로 직렬화되는 동시 요청과 달리
 *       두 번 처리되지 않게 하려는 것이므로 기다리지 않고 바로 돌려보낸다.</li>
 *   <li>첫 요청이 실패(예외)하면 예약을 지워 같은 키로 다시 시도할 수 있게 한다.</li>
 * </ul>
 *
 * <p>저장소는 Redis다({@code SET NX}로 예약, TTL 뒤 소멸). 헤더가 없으면 보호 없이 처리한다 — 아직 이 API를 호출하는 화면이
 * 없어 하위 호환을 지키기 위한 것이며 FE 연동 시 항상 보내야 한다. Redis 장애는 코치 캐시와 같은 이유로 <b>열어 둔다</b>(보호 없이
 * 처리하고 WARN): 이 프로젝트에서 Redis 연결이 실제로 검증된 적이 없어 닫아 두면 상환 자체가 막힐 수 있다.
 * 원문 계좌번호는 키·저장값 어디에도 넣지 않고 요청 지문(SHA-256)에만 섞는다.
 */
@Component
public class IdempotencyKeyGuard {

    public static final String HEADER = "Idempotency-Key";
    static final String KEY_PREFIX = "idem:";
    static final String IN_PROGRESS = "IN_PROGRESS";
    private static final Pattern KEY_FORMAT = Pattern.compile("^[A-Za-z0-9_-]{1,128}$");
    private static final Logger log = LoggerFactory.getLogger(IdempotencyKeyGuard.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final Duration ttl;

    public IdempotencyKeyGuard(
            StringRedisTemplate redis, ObjectMapper json,
            @Value("${app.idempotency.ttl:PT24H}") Duration ttl
    ) {
        this.redis = redis;
        this.json = json;
        this.ttl = ttl;
    }

    /**
     * @param userId       요청 사용자. 키는 사용자별로 격리된다
     * @param operation    작업 이름(예: {@code minus-account-repayment}). 다른 작업의 같은 키와 섞이지 않게 한다
     * @param key          {@code Idempotency-Key} 헤더 값. null이면 보호 없이 {@code action}을 그대로 실행한다
     * @param fingerprint  본문 동일성 판단에 쓸 요청 표현. 민감정보를 넣어도 해시만 저장된다
     * @param responseType 재생 시 역직렬화할 응답 타입
     */
    public <T> T execute(Long userId, String operation, String key, Object fingerprint, Class<T> responseType,
            Supplier<T> action) {
        if (key == null) {
            return action.get();
        }
        if (!KEY_FORMAT.matcher(key).matches()) {
            throw new InvalidRequestException(HEADER + "는 영문·숫자·-·_ 1~128자여야 합니다.");
        }
        String redisKey = KEY_PREFIX + operation + ":" + userId + ":" + key;
        String hash = hash(fingerprint);

        Boolean reserved;
        try {
            reserved = redis.opsForValue().setIfAbsent(redisKey, IN_PROGRESS, ttl);
        } catch (Exception exception) {
            log.warn("Idempotency store unavailable, processing without protection: {}", exception.getMessage());
            return action.get();
        }
        if (Boolean.FALSE.equals(reserved)) {
            return replay(redisKey, hash, responseType);
        }

        T response;
        try {
            response = action.get();
        } catch (RuntimeException exception) {
            release(redisKey);
            throw exception;
        }
        store(redisKey, new Stored(hash, write(response)));
        return response;
    }

    private <T> T replay(String redisKey, String hash, Class<T> responseType) {
        String value;
        try {
            value = redis.opsForValue().get(redisKey);
        } catch (Exception exception) {
            log.warn("Idempotency store read failed after reservation: {}", exception.getMessage());
            throw new BusinessException(ErrorCode.IDEMPOTENT_REQUEST_IN_PROGRESS);
        }
        if (value == null || IN_PROGRESS.equals(value)) {
            throw new BusinessException(ErrorCode.IDEMPOTENT_REQUEST_IN_PROGRESS);
        }
        Stored stored = read(value, Stored.class);
        if (!stored.fingerprint().equals(hash)) {
            throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
        }
        return read(stored.response(), responseType);
    }

    private void store(String redisKey, Stored stored) {
        try {
            redis.opsForValue().set(redisKey, write(stored), ttl);
        } catch (Exception exception) {
            // 처리는 끝났으므로 응답은 돌려준다. 다음 재전송은 IN_PROGRESS 예약을 만나 409가 되며 TTL 뒤 풀린다.
            log.warn("Idempotency result write failed: {}", exception.getMessage());
        }
    }

    private void release(String redisKey) {
        try {
            redis.delete(redisKey);
        } catch (Exception exception) {
            log.warn("Idempotency reservation release failed: {}", exception.getMessage());
        }
    }

    private String hash(Object fingerprint) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(write(fingerprint).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("idempotency serialization failed", exception);
        }
    }

    private <T> T read(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (Exception exception) {
            throw new IllegalStateException("idempotency deserialization failed", exception);
        }
    }

    /** Redis에 두는 처리 결과. 응답은 JSON 문자열로 넣어 타입별 역직렬화를 호출자에게 맡긴다. */
    record Stored(String fingerprint, String response) {
    }
}
