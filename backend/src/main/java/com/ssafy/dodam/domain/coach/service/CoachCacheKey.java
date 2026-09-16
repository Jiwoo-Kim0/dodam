package com.ssafy.dodam.domain.coach.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.coach.dto.CoachFacts;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 캐시 키를 만든다: {@code coach:<userId>:<topic>:<facts 해시 16자>}.
 *
 * <p><b>핵심은 TTL이 아니라 {@link CoachFacts} 해시다.</b> 코치는 "저장된 사실 → 문장"이므로 입력이 같으면
 * 출력이 같아도 된다. 반대로 서버 계산값이 1바이트라도 달라지면 키가 달라져 자동으로 다시 생성된다 —
 * 재진단, 추천 상태 변경, 패턴 재분석이 모두 facts를 바꾸므로 별도의 무효화 코드가 필요 없다.
 *
 * <p><b>대상 id(diagnosisId 등)를 키에 넣지 않는 이유</b>: facts가 같으면 어느 대상이든 같은 문장이
 * 타당하다. id를 넣으면 키만 갈라질 뿐 내용은 같은 항목이 중복 저장된다. 반대로 같은 id라도 facts가
 * 바뀌면 이미 다른 키다.
 *
 * <p><b>{@code userId}를 넣는 이유</b>: facts에는 회원의 재무 수치가 들어 있다. 해시가 우연히 겹치거나
 * 두 사용자의 facts가 완전히 같은 경우에도 서로의 문장이 섞이지 않게 한다. 비용은 키 한 조각이다.
 */
@Component
public class CoachCacheKey {

    /** SHA-256 앞 8바이트. 사용자·topic 안에서 구분하는 용도라 충분하고, 키를 짧게 유지한다. */
    private static final int HASH_LENGTH = 16;

    private static final Logger log = LoggerFactory.getLogger(CoachCacheKey.class);

    private final ObjectMapper json;

    public CoachCacheKey(ObjectMapper json) {
        this.json = json;
    }

    /** @return 캐시 키. 만들 수 없으면 null이며 호출자는 캐시를 건너뛴다 */
    public String of(Long userId, CoachFacts facts) {
        if (userId == null || facts == null) {
            return null;
        }
        try {
            byte[] serialized = json.writeValueAsBytes(facts);
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(serialized);
            String hash = HexFormat.of().formatHex(digest).substring(0, HASH_LENGTH);
            return "coach:" + userId + ":" + facts.topic() + ":" + hash;
        } catch (NoSuchAlgorithmException | RuntimeException exception) {
            // 캐시 키를 못 만드는 것은 기능 실패가 아니다. 캐시 없이 정상 경로로 간다.
            log.warn("Coach cache key could not be built: {}", exception.getMessage());
            return null;
        } catch (Exception exception) {
            log.warn("Coach facts could not be serialized for caching: {}", exception.getMessage());
            return null;
        }
    }
}
