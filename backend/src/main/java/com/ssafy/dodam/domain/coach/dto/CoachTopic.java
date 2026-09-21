package com.ssafy.dodam.domain.coach.dto;

/** API 21의 3종. 응답의 {@code topic}이자 프롬프트 선택 키다. */
public enum CoachTopic {
    /** 금융현황 AI 요약 */
    FINANCIAL_SUMMARY,
    /** 비효율 원인 설명 */
    INEFFICIENCY_EXPLANATION,
    /** 추천 근거 설명 */
    RECOMMENDATION_EXPLANATION,

    /** 토리에게 묻기 — 대화형 시뮬레이션 에이전트(S15P21D105-640 A-3). {@code POST /coach/chat} 전용이며 캐시하지 않는다. */
    CHAT
}
