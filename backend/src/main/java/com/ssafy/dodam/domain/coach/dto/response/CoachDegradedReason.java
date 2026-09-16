package com.ssafy.dodam.domain.coach.dto.response;

/**
 * 템플릿으로 내려간 사유. {@code source=AI}면 null이다.
 *
 * <p>사용자에게 보이는 문구가 아니라 FE·운영이 원인을 구분하기 위한 코드다. 어떤 값이든 응답은
 * 200이고 내용(수치·판정)은 동일하다 — 달라지는 것은 문장을 누가 썼는지뿐이다.
 */
public enum CoachDegradedReason {
    /** {@code app.ai.enabled=false} 또는 API 키 미설정. 로컬·테스트 기본값이다 */
    AI_DISABLED,
    /** 타임아웃·연결 실패·비정상 응답 코드(401/429/5xx)·응답 해석 불가 */
    AI_UNAVAILABLE,
    /** 호출은 성공했으나 본문이 비어 있었다 */
    AI_EMPTY_RESPONSE,
    /**
     * 모델 문장에 서버가 준 적 없는 숫자가 있었다. 명세 21의 "AI 자체 계산 금지"를 응답 단계에서 강제한다
     * ({@code CoachNumberGuard}).
     */
    AI_UNVERIFIED_NUMBER,
    /**
     * 프롬프트 직전 검사에서 민감정보 패턴이 발견돼 호출 자체를 하지 않았다. 정상 흐름에서는 발생하지
     * 않으며, 발생하면 조립기 쪽 회귀다(서버 로그에 ERROR로 남는다).
     */
    SENSITIVE_DATA_BLOCKED
}
