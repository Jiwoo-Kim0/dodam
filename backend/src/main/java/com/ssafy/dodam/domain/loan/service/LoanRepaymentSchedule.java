package com.ssafy.dodam.domain.loan.service;

import java.util.List;

/**
 * 계산된 상환일정. `truncated`는 남은 회차가 상한을 넘어 잘렸음을 뜻한다.
 */
public record LoanRepaymentSchedule(List<LoanRepaymentScheduleItem> items, boolean truncated) {

    public LoanRepaymentSchedule {
        items = List.copyOf(items);
    }

    public long totalPrincipal() {
        return items.stream().mapToLong(LoanRepaymentScheduleItem::principal).sum();
    }

    public long totalInterest() {
        return items.stream().mapToLong(LoanRepaymentScheduleItem::interest).sum();
    }

    /**
     * 다음 회차 상환액. 요약의 월 상환액 합계에 사용한다.
     */
    public long nextAmount() {
        return items.isEmpty() ? 0L : items.get(0).amount();
    }
}
