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
     * 사용자별 호출 예산({@code app.coach.rate-limit})을 넘겨 외부 호출을 하지 않았다. 코치는 이 서비스에서
     * 외부 과금이 걸린 유일한 경로라, 캐시로 줄지 않는 <b>새로운</b> 생성 요청에만 상한을 둔다. 창이 지나면
     * 저절로 풀리므로 사용자가 할 일은 없다.
     */
    RATE_LIMITED,
    /**
     * 프롬프트 직전 검사에서 민감정보 패턴이 발견돼 호출 자체를 하지 않았다. 정상 흐름에서는 발생하지
     * 않으며, 발생하면 조립기 쪽 회귀다(서버 로그에 ERROR로 남는다).
     */
    SENSITIVE_DATA_BLOCKED,

    /** 채팅(640 A-3): 2차 응답에 또 tool_calls가 왔다. 왕복은 1회만 허용하므로 템플릿으로 내려간다. */
    TOOL_LOOP_EXCEEDED,

    /** 채팅(640 A-3): 한 대화의 턴 수 상한에 닿았다. 새 대화를 시작해야 한다. */
    SESSION_TURN_LIMIT
}
