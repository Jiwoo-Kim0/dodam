package com.ssafy.dodam.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.coach.service.CoachChatProperties;
import com.ssafy.dodam.domain.coach.service.CoachChatSession;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

/** 640 A-3: 세션 저장소는 사용자 ID로 키를 나누고, Redis가 죽어도 "새 대화"로 이어 간다. */
class RedisCoachChatSessionStoreTest {

    private static final Long USER = 7L;
    private static final String ID = "c_0123456789abcdef";

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

    private RedisCoachChatSessionStore store(Duration ttl) {
        return new RedisCoachChatSessionStore(redis, json,
                new CoachChatProperties(ttl, null, null, null, null));
    }

    @Test
    void storesUnderAUserScopedKeyWithTheSessionTtl() {
        CoachChatSession session = CoachChatSession.start(ID)
                .append(new CoachChatSession.Turn("카페를 월 5만원 줄이면?", List.of("단축(비상금): 121일")), 6);

        store(Duration.ofMinutes(30)).save(USER, session);

        verify(values).set(eq("coach:chat:session:7:" + ID), anyString(), eq(Duration.ofMinutes(30)));
    }

    @Test
    void readsBackWhatItStored() throws Exception {
        CoachChatSession session = CoachChatSession.start(ID)
                .append(new CoachChatSession.Turn("카페를 월 5만원 줄이면?", List.of("단축(비상금): 121일")), 6);
        given(values.get("coach:chat:session:7:" + ID)).willReturn(json.writeValueAsString(session));

        assertThat(store(null).find(USER, ID)).contains(session);
        // 다른 사용자는 같은 id로도 찾지 못한다(키에 사용자 ID가 들어간다)
        assertThat(store(null).find(8L, ID)).isEmpty();
    }

    @Test
    void ignoresMalformedIdentifiersWithoutTouchingRedis() {
        assertThat(store(null).find(USER, "../x")).isEmpty();
        assertThat(store(null).find(USER, null)).isEmpty();
        verifyNoInteractions(values);
    }

    @Test
    void treatsStoreFailuresAsANewConversation() {
        willThrow(new QueryTimeoutException("redis down")).given(values).get(anyString());
        willThrow(new QueryTimeoutException("redis down")).given(values).set(anyString(), anyString(), eq(Duration.ofMinutes(30)));

        RedisCoachChatSessionStore store = store(Duration.ofMinutes(30));
        assertThat(store.find(USER, ID)).isEmpty();
        store.save(USER, CoachChatSession.start(ID)); // 예외가 새지 않는다
    }
}
