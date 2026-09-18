package com.ssafy.dodam.infrastructure.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * 고정 창(fixed window) 카운터. 사용자별 키 {@code {prefix}{userId}:{bucket}}를 INCR 하고 첫 증가에만 만료를 건다
 * (Lua 한 번이라 INCR와 EXPIRE 사이에 죽어도 만료 없는 키가 남지 않는다).
 *
 * <p>Redis가 응답하지 않으면 <b>인프로세스로 센다</b> — 캐시와 반대로 "모르면 허용"이 아니라 "모르면 이 서버 안에서라도
 * 센다"다. 외부 과금이 걸린 호출이므로 저장소 장애가 무제한 호출로 이어지면 안 된다(ai-coach.md §12).
 * 코치 3종({@link RedisCoachCallRateLimiter})과 채팅({@link RedisCoachChatRateLimiter})이 접두어만 달리 해서 같이 쓴다.
 */
public class RedisWindowCounter {

    private static final RedisScript<Long> INCREMENT_IN_WINDOW = new DefaultRedisScript<>("""
            local used = redis.call('INCR', KEYS[1])
            if used == 1 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return used
            """, Long.class);

    private static final Logger log = LoggerFactory.getLogger(RedisWindowCounter.class);

    private final StringRedisTemplate redis;
    private final String keyPrefix;
    private final InProcessCallCounter fallback = new InProcessCallCounter();

    public RedisWindowCounter(StringRedisTemplate redis, String keyPrefix) {
        this.redis = redis;
        this.keyPrefix = keyPrefix;
    }

    /** @return 이번 호출까지 포함해 창 안의 사용 횟수가 {@code permits} 이하면 true */
    public boolean tryAcquire(Long userId, int permits, Duration window) {
        long windowSeconds = Math.max(1, window.toSeconds());
        long bucket = Instant.now().getEpochSecond() / windowSeconds;
        String key = keyPrefix + userId + ":" + bucket;
        Long used;
        try {
            used = redis.execute(INCREMENT_IN_WINDOW, List.of(key), String.valueOf(windowSeconds));
        } catch (Exception exception) {
            log.warn("Rate limit store unavailable, counting in-process instead: prefix={} error={}", keyPrefix,
                    exception.getMessage());
            return fallback.tryAcquire(userId, bucket, permits);
        }
        if (used == null) {
            // 스크립트가 값을 돌려주지 않는 상황은 정상 경로에 없다. 셀 수 없었다는 뜻이므로 대체 경로로 센다.
            log.warn("Rate limit script returned no value, counting in-process instead: key={}", key);
            return fallback.tryAcquire(userId, bucket, permits);
        }
        return used <= permits;
    }
}
