package com.ssafy.dodam.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ssafy.dodam.domain.coach.service.CoachRateLimitProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * 외부 과금이 걸린 유일한 엔드포인트의 상한이다. 한도 계산 자체보다 <b>저장소가 죽었을 때 어떻게
 * 행동하는가</b>가 핵심이라, 그 경로를 중심으로 본다.
 */
class RedisCoachCallRateLimiterTest {

    private static final Long USER = 7L;

    private StringRedisTemplate redis;

    @BeforeEach
    void setup() {
        redis = mock(StringRedisTemplate.class);
    }

    private RedisCoachCallRateLimiter limiter(boolean enabled, int permits) {
        return new RedisCoachCallRateLimiter(redis,
                new CoachRateLimitProperties(enabled, permits, Duration.ofHours(1)));
    }

    @SuppressWarnings("unchecked")
    private void willCount(Long used) {
        given(redis.execute(any(RedisScript.class), anyList(), any())).willReturn(used);
    }

    @Test
    void allowsCallsUpToThePermitCount() {
        willCount(3L);
        assertThat(limiter(true, 3).tryAcquire(USER)).isTrue();
    }

    @Test
    void refusesTheCallAfterThePermitCountIsUsed() {
        willCount(4L);
        assertThat(limiter(true, 3).tryAcquire(USER)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void countsPerUserInsideAWindowKeyThatExpiresOnItsOwn() {
        willCount(1L);
        limiter(true, 3).tryAcquire(USER);

        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redis).execute(any(RedisScript.class), keys.capture(), any());
        assertThat(keys.getValue()).singleElement().asString()
                .startsWith(RedisCoachCallRateLimiter.KEY_PREFIX + USER + ":");
    }

    @Test
    void doesNotTouchRedisWhenTheLimitIsTurnedOff() {
        assertThat(limiter(false, 1).tryAcquire(USER)).isTrue();
        verifyNoInteractions(redis);
    }

    /**
     * 캐시와 정반대의 선택이다. Redis가 죽은 순간은 캐시도 함께 죽어 모든 호출이 과금되는 상황이라,
     * 여기서 열어 버리면 상한이 가장 필요한 때에 사라진다.
     */
    @Test
    @SuppressWarnings("unchecked")
    void keepsEnforcingTheLimitInProcessWhenRedisIsDown() {
        willThrow(new QueryTimeoutException("redis down"))
                .given(redis).execute(any(RedisScript.class), anyList(), any());

        RedisCoachCallRateLimiter limiter = limiter(true, 2);
        assertThat(limiter.tryAcquire(USER)).isTrue();
        assertThat(limiter.tryAcquire(USER)).isTrue();
        assertThat(limiter.tryAcquire(USER)).isFalse();
        // 예산은 사용자별이다. 한 사용자가 다 썼다고 다른 사용자가 막히면 안 된다.
        assertThat(limiter.tryAcquire(99L)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void countsInProcessWhenTheScriptReturnsNothing() {
        given(redis.execute(any(RedisScript.class), anyList(), any())).willReturn(null);

        RedisCoachCallRateLimiter limiter = limiter(true, 1);
        assertThat(limiter.tryAcquire(USER)).isTrue();
        assertThat(limiter.tryAcquire(USER)).isFalse();
    }
}
