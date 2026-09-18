package com.ssafy.dodam.infrastructure.ratelimit;

import com.ssafy.dodam.domain.coach.service.CoachCallRateLimiter;
import com.ssafy.dodam.domain.coach.service.CoachRateLimitProperties;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 사용자별 코치 호출 예산의 Redis 구현(ai-coach.md §12). 창·카운터·장애 시 인프로세스 대체는
 * {@link RedisWindowCounter}에 있고, 이 클래스는 3종 코치의 키 접두어와 한도({@code app.coach.rate-limit})를 댄다.
 *
 * <p>키는 {@code coach:rl:{userId}:{bucket}}. bucket은 창 길이로 나눈 epoch 초라 창이 바뀌면 키도 바뀌고
 * 옛 키는 만료로 사라진다.
 */
@Component
public class RedisCoachCallRateLimiter implements CoachCallRateLimiter {

    static final String KEY_PREFIX = "coach:rl:";

    private final RedisWindowCounter counter;
    private final CoachRateLimitProperties properties;

    public RedisCoachCallRateLimiter(StringRedisTemplate redis, CoachRateLimitProperties properties) {
        this.counter = new RedisWindowCounter(redis, KEY_PREFIX);
        this.properties = properties;
    }

    @Override
    public boolean tryAcquire(Long userId) {
        if (!properties.enabled() || userId == null) {
            return true;
        }
        return counter.tryAcquire(userId, properties.permits(), properties.window());
    }
}
