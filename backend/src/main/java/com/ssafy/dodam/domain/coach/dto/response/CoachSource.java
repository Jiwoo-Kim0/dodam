package com.ssafy.dodam.domain.coach.dto.response;

/** 응답 문장을 누가 썼는지. FE는 이 값으로 "AI 생성" 배지 노출 여부를 정한다. */
public enum CoachSource {
    /** 생성형 AI가 쓴 문장 */
    AI,
    /** 서버 템플릿이 쓴 문장. 기획안 11-3의 대체 방법이며 값·판정은 AI 경로와 동일하다 */
    TEMPLATE
}
