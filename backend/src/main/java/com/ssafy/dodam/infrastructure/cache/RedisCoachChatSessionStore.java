package com.ssafy.dodam.infrastructure.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.coach.service.CoachChatProperties;
import com.ssafy.dodam.domain.coach.service.CoachChatSession;
import com.ssafy.dodam.domain.coach.service.CoachChatSessionStore;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 대화 세션의 Redis 구현(640 A-3). 키 {@code coach:chat:session:{userId}:{conversationId}}, 값은 세션 JSON,
 * 만료는 {@code app.coach.chat.session-ttl}(저장마다 다시 건다 — 마지막 턴 뒤 30분).
 *
 * <p>{@link RedisCoachAnswerCache}와 같은 태도로 <b>모든 예외를 삼킨다</b>. 세션은 대화를 이어 주는 편의이고 기능이
 * 아니다. 조회 실패는 새 대화, 저장 실패는 무시하며 둘 다 WARN만 남긴다. 사용자 문장이 들어 있으므로 값은 로그에 쓰지 않는다.
 */
@Component
public class RedisCoachChatSessionStore implements CoachChatSessionStore {

    static final String KEY_PREFIX = "coach:chat:session:";
    private static final Logger log = LoggerFactory.getLogger(RedisCoachChatSessionStore.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper json;
    private final CoachChatProperties properties;

    public RedisCoachChatSessionStore(StringRedisTemplate redis, ObjectMapper json, CoachChatProperties properties) {
        this.redis = redis;
        this.json = json;
        this.properties = properties;
    }

    static String key(Long userId, String conversationId) {
        return KEY_PREFIX + userId + ":" + conversationId;
    }

    @Override
    public Optional<CoachChatSession> find(Long userId, String conversationId) {
        if (userId == null || !CoachChatSession.isValidId(conversationId)) {
            return Optional.empty();
        }
        try {
            String stored = redis.opsForValue().get(key(userId, conversationId));
            return stored == null ? Optional.empty() : Optional.of(json.readValue(stored, CoachChatSession.class));
        } catch (Exception exception) {
            log.warn("Coach chat session lookup failed, starting a new conversation: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void save(Long userId, CoachChatSession session) {
        if (userId == null || session == null) {
            return;
        }
        try {
            redis.opsForValue().set(key(userId, session.conversationId()), json.writeValueAsString(session),
                    properties.sessionTtl());
        } catch (Exception exception) {
            log.warn("Coach chat session write failed, continuing: {}", exception.getMessage());
        }
    }
}
