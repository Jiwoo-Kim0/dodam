package com.ssafy.dodam.domain.notification.entity;

/**
 * 알림 원천 도메인(`notification.source_type`). `source_id`에는 그 도메인 PK를 문자열로 넣는다.
 *
 * <p>534(마이너스통장)의 `JdbcMinusAccountAlertReader`가 먼저 쓴 `MINUS_ACCOUNT` + `account_id` 규약을 그대로 채택했다.
 * 각 도메인은 이 enum의 이름으로 저장하고, 상세 화면 이동은 FE가 (sourceType, sourceId)로 라우팅한다.
 */
public enum NotificationSourceType {
    ACCOUNT,
    MINUS_ACCOUNT,
    CARD,
    INSTALLMENT,
    LOAN,
    DEPOSIT_SAVING,
    SUBSCRIPTION,
    GOAL,
    DIAGNOSIS,
    RECOMMENDATION,
    CASHFLOW_FORECAST,
    SIMULATION,
    PATTERN_ANALYSIS,
    REPORT
}
