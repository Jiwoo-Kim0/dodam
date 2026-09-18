package com.ssafy.dodam.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.ssafy.dodam.domain.coach.service.CoachChatProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/** 640 A-3: 채팅 예산은 3종 코치와 키·한도가 다르다. 알고리즘은 RedisWindowCounter가 공유하므로 여기서는 분리만 본다. */
class RedisCoachChatRateLimiterTest {

    private static final Long USER = 42L;
    private StringRedisTemplate redis;

    @BeforeEach
    void setup() {
        redis = mock(StringRedisTemplate.class);
    }

    private RedisCoachChatRateLimiter limiter(boolean enabled, int permits) {
        return new RedisCoachChatRateLimiter(redis, new CoachChatProperties(null, null, null, null,
                new CoachChatProperties.RateLimit(enabled, permits, Duration.ofHours(1))));
    }

    @Test
    @SuppressWarnings("unchecked")
    void usesItsOwnKeyPrefixAndPermits() {
        given(redis.execute(any(RedisScript.class), anyList(), any())).willReturn(3L);
        assertThat(limiter(true, 3).tryAcquire(USER)).isTrue();

        ArgumentCaptor<List<String>> keys = ArgumentCaptor.forClass(List.class);
        verify(redis).execute(any(RedisScript.class), keys.capture(), any());
        assertThat(keys.getValue()).singleElement().asString().startsWith("coach:chat:rl:" + USER + ":");

        given(redis.execute(any(RedisScript.class), anyList(), any())).willReturn(4L);
        assertThat(limiter(true, 3).tryAcquire(USER)).isFalse();
    }

    @Test
    void doesNotTouchRedisWhenTurnedOff() {
        assertThat(limiter(false, 1).tryAcquire(USER)).isTrue();
        assertThat(limiter(true, 1).tryAcquire(null)).isTrue();
        verifyNoInteractions(redis);
    }

    @Test
    void defaultsAreTwentyCallsPerHour() {
        CoachChatProperties defaults = new CoachChatProperties(null, null, null, null, null);
        assertThat(defaults.rateLimit().permits()).isEqualTo(20);
        assertThat(defaults.rateLimit().window()).isEqualTo(Duration.ofHours(1));
        assertThat(defaults.sessionTtl()).isEqualTo(Duration.ofMinutes(30));
        assertThat(defaults.historyTurns()).isEqualTo(6);
        assertThat(defaults.maxTurnsPerSession()).isEqualTo(10);
        assertThat(defaults.maxMessageLength()).isEqualTo(300);
    }
}
