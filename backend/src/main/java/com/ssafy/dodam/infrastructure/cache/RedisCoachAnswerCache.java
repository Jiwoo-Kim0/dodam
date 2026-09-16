package com.ssafy.dodam.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.coach.service.CoachAnswerCache;
import com.ssafy.dodam.domain.coach.service.CoachProperties;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 코치 문장 캐시의 Redis 구현.
 *
 * <p><b>이 프로젝트에서 Redis를 실제로 쓰는 첫 코드다.</b> 접속 설정은 처음부터 있었지만 Java 사용처가
 * 하나도 없었다(`docker-compose.yml`의 redis 블록 주석 참고). 즉 비밀번호·연결 설정이 실제로 맞는지가
 * 여기서 처음 드러난다 — H2로 도는 테스트는 이것을 잡지 못한다.
 *
 * <p>그래서 <b>모든 예외를 삼킨다.</b> 캐시는 외부 API 호출을 줄이는 장치이지 기능이 아니다. Redis가
 * 죽었을 때 코치까지 죽으면 캐시를 넣지 않느니만 못하다. 조회 실패는 캐시 미스와 같게 다루고, 저장
 * 실패는 무시한다. 둘 다 WARN으로 남겨 "캐시가 동작하지 않는다"는 사실 자체는 드러나게 한다.
 */
@Component
public class RedisCoachAnswerCache implements CoachAnswerCache {

    private static final Logger log = LoggerFactory.getLogger(RedisCoachAnswerCache.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final CoachProperties properties;

    public RedisCoachAnswerCache(StringRedisTemplate redis, ObjectMapper json, CoachProperties properties) {
        this.redis = redis;
        this.json = json;
        this.properties = properties;
    }

    @Override
    public Optional<Entry> find(String key) {
        if (!properties.cacheEnabled() || key == null) {
            return Optional.empty();
        }
        try {
            String stored = redis.opsForValue().get(key);
            return stored == null ? Optional.empty() : Optional.of(json.readValue(stored, Entry.class));
        } catch (Exception exception) {
            log.warn("Coach cache lookup failed, continuing without cache: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, Entry entry) {
        if (!properties.cacheEnabled() || key == null || entry == null) {
            return;
        }
        try {
            redis.opsForValue().set(key, json.writeValueAsString(entry), properties.cacheTtl());
        } catch (Exception exception) {
            log.warn("Coach cache write failed, continuing: {}", exception.getMessage());
        }
    }
}
