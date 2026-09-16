package com.ssafy.dodam.domain.coach.dto;

/** API 21의 3종. 응답의 {@code topic}이자 프롬프트 선택 키다. */
public enum CoachTopic {
    /** 금융현황 AI 요약 */
    FINANCIAL_SUMMARY,
    /** 비효율 원인 설명 */
    INEFFICIENCY_EXPLANATION,
    /** 추천 근거 설명 */
    RECOMMENDATION_EXPLANATION
}
