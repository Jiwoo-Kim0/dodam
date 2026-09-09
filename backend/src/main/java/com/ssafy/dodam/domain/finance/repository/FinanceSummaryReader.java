package com.ssafy.dodam.domain.finance.repository;

import java.util.List;

/**
 * 금융 데이터 통합 요약을 위한 로컬 DB 집계 경계. 계좌·카드·대출 테이블은 각 도메인이 소유하므로
 * 엔티티에 의존하지 않고 ERD 컬럼명 기준 SQL로만 읽는다 (읽기 전용).
 */
public interface FinanceSummaryReader {

    /**
     * CLOSED가 아닌 계좌를 account_type별로 집계한다.
     */
    List<AccountTypeSummaryRow> readAccountsByType(Long userId);

    CardSummaryRow readCards(Long userId);

    LoanSummaryRow readLoans(Long userId);

    record AccountTypeSummaryRow(String accountType, long count, long totalBalance) {
    }

    /**
     * @param totalCreditLimit ACTIVE 카드 한도 합
     */
    record CardSummaryRow(long activeCount, long totalCount, long totalCreditLimit) {
    }

    /**
     * @param outstandingCount ACTIVE·OVERDUE 대출 건수
     * @param totalBalance     ACTIVE·OVERDUE 대출 잔액 합
     */
    record LoanSummaryRow(long outstandingCount, long overdueCount, long totalBalance) {
    }
}
