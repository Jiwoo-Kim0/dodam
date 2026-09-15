package com.ssafy.dodam.domain.notification.entity;

import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.util.Locale;

/**
 * 알림 유형. 기획안 14-6(결제 예정·상환 예정·연체 위험·추천 행동·개선 결과·월간 리포트)과 7-4(잔액 부족 사전 알림), 목표·브리핑을 더했다.
 *
 * <p>DB `notification.type`은 VARCHAR(50)이라 이 enum에 없는 값도 저장될 수 있다(다른 도메인이 SQL로 넣는 경우). 엔티티는 문자열로 들고
 * 이 enum은 생성 입력 검증과 목록 필터 검증에만 쓴다. 새 유형은 여기에 추가하고 notification.md 표를 함께 고친다.
 */
public enum NotificationType {
    /** 카드 결제·정기결제 결제 예정 */
    PAYMENT_DUE("결제 예정"),
    /** 대출·마이너스통장 상환(이자) 예정 */
    REPAYMENT_DUE("상환 예정"),
    /** 연체 위험(결제일 직전 잔액 부족 등) */
    OVERDUE_RISK("연체 위험"),
    /** 현금흐름 예측상 잔액 부족 위험 */
    CASHFLOW_RISK("잔액 부족 위험"),
    /** 목표 진행 상황(진행률 구간 도달, 지연 등) */
    GOAL_PROGRESS("목표 진행"),
    /** 목표 달성 */
    GOAL_ACHIEVED("목표 달성"),
    /** 개선안 추천 도착 */
    RECOMMENDATION("추천 행동"),
    /** 실행한 개선안의 결과 */
    IMPROVEMENT_RESULT("개선 결과"),
    /** 월간 리포트 생성 */
    MONTHLY_REPORT("월간 리포트"),
    /** 데일리 브리핑 */
    DAILY_BRIEFING("데일리 브리핑"),
    /** 서비스 공지·계정 관련 */
    SYSTEM("시스템");

    private final String displayName;

    NotificationType(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /**
     * 목록 필터 파라미터. null·빈 값은 "전체"(null), 대소문자는 구분하지 않는다.
     *
     * @throws InvalidRequestException 정의되지 않은 값
     */
    public static NotificationType parseFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("지원하지 않는 알림 유형입니다.");
        }
    }
}
