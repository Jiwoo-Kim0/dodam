package com.ssafy.dodam.domain.loan.service;

import com.ssafy.dodam.domain.loan.entity.Loan;
import java.util.List;

record LoanForecast(Loan loan, List<LoanRepaymentScheduleItem> items, boolean truncated, List<String> reasons) {
    LoanForecast {
        items = List.copyOf(items);
        reasons = List.copyOf(reasons);
    }

    boolean available() {
        return reasons.isEmpty();
    }
}
