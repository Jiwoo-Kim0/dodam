package com.ssafy.dodam.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.coach.service.CoachAnswerCache;
import com.ssafy.dodam.domain.coach.service.CoachProperties;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/**
 * 이 프로젝트에서 Redis를 실제로 쓰는 첫 코드다. 핵심 요구사항은 "캐시가 죽어도 코치는 산다"이므로
 * 실패 경로를 중심으로 본다.
 */
class RedisCoachAnswerCacheTest {

    private static final String KEY = "coach:1:FINANCIAL_SUMMARY:abcdef0123456789";

    private StringRedisTemplate redis;
    private ValueOperations<String, String> values;
    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        given(redis.opsForValue()).willReturn(values);
    }

    private RedisCoachAnswerCache cache(boolean enabled, Duration ttl) {
        return new RedisCoachAnswerCache(redis, json, new CoachProperties(enabled, ttl));
    }

    @Test
    void storesMessageWithTheConfiguredTtl() {
        RedisCoachAnswerCache cache = cache(true, Duration.ofMinutes(30));

        cache.put(KEY, new CoachAnswerCache.Entry("설명입니다.", "gpt-5.4-mini"));

        verify(values).set(eq(KEY), anyString(), eq(Duration.ofMinutes(30)));
    }

    @Test
    void readsBackWhatItStored() throws Exception {
        RedisCoachAnswerCache cache = cache(true, null);
        CoachAnswerCache.Entry stored = new CoachAnswerCache.Entry("설명입니다.", "gpt-5.4-mini");
        given(values.get(KEY)).willReturn(json.writeValueAsString(stored));

        assertThat(cache.find(KEY)).contains(stored);
    }

    @Test
    void missingEntryIsNotAnError() {
        RedisCoachAnswerCache cache = cache(true, null);
        given(values.get(KEY)).willReturn(null);

        assertThat(cache.find(KEY)).isEmpty();
    }

    @Test
    void redisFailureIsSwallowedOnBothPaths() {
        RedisCoachAnswerCache cache = cache(true, null);
        given(values.get(KEY)).willThrow(new QueryTimeoutException("redis down"));
        willThrow(new QueryTimeoutException("redis down")).given(values).set(anyString(), anyString(), any(Duration.class));

        // 캐시는 비용 절감 장치이지 기능이 아니다. 죽어도 코치는 계속 동작해야 한다.
        assertThat(cache.find(KEY)).isEmpty();
        cache.put(KEY, new CoachAnswerCache.Entry("설명입니다.", "gpt-5.4-mini"));
    }

    @Test
    void corruptedValueIsTreatedAsMiss() {
        RedisCoachAnswerCache cache = cache(true, null);
        given(values.get(KEY)).willReturn("not json");

        assertThat(cache.find(KEY)).isEmpty();
    }

    @Test
    void doesNotTouchRedisWhenDisabledOrKeyIsMissing() {
        cache(false, null).find(KEY);
        cache(false, null).put(KEY, new CoachAnswerCache.Entry("설명입니다.", "gpt-5.4-mini"));
        verifyNoInteractions(values);

        RedisCoachAnswerCache enabled = cache(true, null);
        assertThat(enabled.find(null)).isEmpty();
        enabled.put(null, new CoachAnswerCache.Entry("설명입니다.", "gpt-5.4-mini"));
        enabled.put(KEY, null);
        verify(values, never()).set(anyString(), anyString(), any(Duration.class));
    }
}
