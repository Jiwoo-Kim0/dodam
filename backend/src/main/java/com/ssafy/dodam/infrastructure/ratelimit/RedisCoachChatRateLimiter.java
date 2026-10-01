package com.ssafy.dodam.infrastructure.ratelimit;

import com.ssafy.dodam.domain.coach.service.CoachChatProperties;
import com.ssafy.dodam.domain.coach.service.CoachChatRateLimiter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 채팅 예산의 Redis 구현(640 A-3). 알고리즘은 {@link RedisCoachCallRateLimiter}와 같고({@link RedisWindowCounter}),
 * 키 접두어와 한도({@code app.coach.chat.rate-limit})만 다르다.
 */
@Component
public class RedisCoachChatRateLimiter implements CoachChatRateLimiter {

    static final String KEY_PREFIX = "coach:chat:rl:";

    private final RedisWindowCounter counter;
    private final CoachChatProperties properties;

    public RedisCoachChatRateLimiter(StringRedisTemplate redis, CoachChatProperties properties) {
        this.counter = new RedisWindowCounter(redis, KEY_PREFIX);
        this.properties = properties;
    }

    @Override
    public boolean tryAcquire(Long userId) {
        CoachChatProperties.RateLimit limit = properties.rateLimit();
        if (!limit.enabled() || userId == null || limit.exemptUserIds().contains(userId)) {
            // 예외 사용자(시연 계정)는 Redis를 건드리지 않는다 — 세지 않으므로 창이 바뀌어도 상태가 없다
            return true;
        }
        return counter.tryAcquire(userId, limit.permits(), limit.window());
    }
}
