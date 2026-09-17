package com.ssafy.dodam.infrastructure.ratelimit;

import com.ssafy.dodam.domain.coach.service.CoachCallRateLimiter;
import com.ssafy.dodam.domain.coach.service.CoachRateLimitProperties;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

/**
 * 코치 호출 예산의 Redis 구현 — 사용자별 <b>고정 창 카운터</b>.
 *
 * <p>키는 {@code coach:rl:<userId>:<창 번호>}이고 창 번호는 epoch 초를 창 길이로 나눈 몫이다. 창이 바뀌면
 * 키 자체가 달라지므로 초기화 코드가 필요 없고, TTL이 지나면 사라진다.
 *
 * <p><b>왜 Lua인가</b>: {@code INCR} 다음에 {@code EXPIRE}를 따로 보내면 그 사이에 연결이 끊겼을 때
 * <b>TTL 없는 카운터</b>가 남는다. 그 사용자는 값이 한도를 넘은 채 영구히 묶인다(축출 정책이 지워주기를
 * 기다리는 수밖에 없다). 두 명령을 한 스크립트로 묶으면 그 창 자체가 없어진다.
 *
 * <p><b>왜 슬라이딩 윈도우가 아닌가</b>: 정렬 집합으로 하면 창 경계 문제는 사라지지만, 호출마다 원소를
 * 쌓고 지우는 비용과 코드가 늘어난다. 여기서 막으려는 것은 비용 폭주이지 정밀한 트래픽 정형이 아니다.
 * 경계에서 최악 2배(80건)까지 허용되더라도 상한이 상한으로 동작한다.
 *
 * <p><b>Redis가 죽으면 통과시키지 않는다.</b> 캐시와 정반대의 선택이다. 저장소가 죽은 순간은 캐시도 함께
 * 죽어 모든 호출이 과금되는 최악의 상황이라, 여기서 열어 두면 레이트 리밋이 가장 필요한 때에 없는 것과
 * 같아진다. 그렇다고 전부 막으면 코치가 통째로 템플릿으로 내려가므로, {@link InProcessCallCounter}로
 * 내려가 같은 한도를 계속 건다.
 */
@Component
public class RedisCoachCallRateLimiter implements CoachCallRateLimiter {

    static final String KEY_PREFIX = "coach:rl:";

    /** 첫 증가일 때만 TTL을 건다. 이미 세고 있는 창의 만료를 호출마다 미루면 창이 끝나지 않는다. */
    private static final RedisScript<Long> INCREMENT_IN_WINDOW = new DefaultRedisScript<>("""
            local used = redis.call('INCR', KEYS[1])
            if used == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return used
            """, Long.class);

    private static final Logger log = LoggerFactory.getLogger(RedisCoachCallRateLimiter.class);

    private final StringRedisTemplate redis;
    private final CoachRateLimitProperties properties;
    private final InProcessCallCounter fallback = new InProcessCallCounter();

    public RedisCoachCallRateLimiter(StringRedisTemplate redis, CoachRateLimitProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    @Override
    public boolean tryAcquire(Long userId) {
        if (!properties.enabled() || userId == null) {
            return true;
        }
        long windowSeconds = Math.max(1, properties.window().toSeconds());
        long bucket = Instant.now().getEpochSecond() / windowSeconds;
        String key = KEY_PREFIX + userId + ":" + bucket;

        Long used;
        try {
            used = redis.execute(INCREMENT_IN_WINDOW, List.of(key), String.valueOf(windowSeconds));
        } catch (Exception exception) {
            log.warn("Coach rate limit store unavailable, counting in-process instead: {}", exception.getMessage());
            return fallback.tryAcquire(userId, bucket, properties.permits());
        }
        if (used == null) {
            // 스크립트가 값을 돌려주지 않는 상황은 정상 경로에 없다. 셀 수 없었다는 뜻이므로 대체 경로로 센다.
            log.warn("Coach rate limit script returned no value, counting in-process instead: key={}", key);
            return fallback.tryAcquire(userId, bucket, properties.permits());
        }
        return used <= properties.permits();
    }
}
