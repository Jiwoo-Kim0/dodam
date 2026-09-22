package com.ssafy.dodam.domain.user.entity;

/**
 * 개인신용점수의 출처. SYNTHETIC은 도담 보유 데이터로 계산한 예상 점수이며 실제 CB 조회값이 아니다.
 */
public enum CreditScoreSource {
    SYNTHETIC,
    KCB,
    NICE
}
