package com.ssafy.dodam.domain.coach.service;

import java.time.Duration;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 토리에게 묻기(채팅, S15P21D105-640 A-3) 설정. {@code app.coach.chat}.
 *
 * <p>3종 코치와 값을 따로 두는 이유: 채팅은 자유 입력 한 턴에 외부 호출이 최대 2회 나가고(1차 도구 선택, 2차 문장),
 * 사용자가 연달아 묻는다. 같은 예산을 쓰면 채팅 몇 번에 진단 화면의 코치 문장이 템플릿으로 떨어진다.
 *
 * @param sessionTtl         마지막 턴 뒤 대화를 기억하는 시간. 기본 30분
 * @param historyTurns       모델에 재생하는 최근 턴 수. 기본 6
 * @param maxTurnsPerSession 한 대화(conversationId)에서 받는 턴 상한. 기본 10 — 넘으면 새 대화를 시작하게 한다
 * @param maxMessageLength   사용자 문장 길이 상한(자). 기본 300
 * @param rateLimit          채팅 전용 호출 예산. 기본 20건/시간(자유 입력 10턴 분량). {@code exempt-user-ids}에 적은
 *                           사용자는 예산을 세지 않는다(시연 계정용. 운영 사용자에게 쓰지 않는다)
 */
@ConfigurationProperties(prefix = "app.coach.chat")
public record CoachChatProperties(Duration sessionTtl, Integer historyTurns, Integer maxTurnsPerSession,
        Integer maxMessageLength, RateLimit rateLimit) {

    public CoachChatProperties {
        sessionTtl = sessionTtl == null || sessionTtl.isZero() || sessionTtl.isNegative() ? Duration.ofMinutes(30) : sessionTtl;
        historyTurns = historyTurns == null || historyTurns <= 0 ? 6 : historyTurns;
        maxTurnsPerSession = maxTurnsPerSession == null || maxTurnsPerSession <= 0 ? 10 : maxTurnsPerSession;
        maxMessageLength = maxMessageLength == null || maxMessageLength <= 0 ? 300 : maxMessageLength;
        rateLimit = rateLimit == null ? new RateLimit(null, null, null, null) : rateLimit;
    }

    public record RateLimit(Boolean enabled, Integer permits, Duration window, Set<Long> exemptUserIds) {
        public RateLimit {
            enabled = enabled == null || enabled;
            permits = permits == null || permits <= 0 ? 20 : permits;
            window = window == null || window.isZero() || window.isNegative() ? Duration.ofHours(1) : window;
            exemptUserIds = exemptUserIds == null ? Set.of() : Set.copyOf(exemptUserIds);
        }
    }
}
