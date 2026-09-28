package com.ssafy.dodam.domain.loan.service;

import com.ssafy.dodam.domain.loan.entity.Loan;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class LoanForecastCalculator {
    private final LoanRepaymentScheduleCalculator calculator;

    public LoanForecastCalculator(LoanRepaymentScheduleCalculator calculator) {
        this.calculator = calculator;
    }

    LoanForecast calculate(Loan loan, LocalDate asOfDate, int limit) {
        return calculate(loan, asOfDate, limit, null);
    }

    LoanForecast calculate(Loan loan, LocalDate asOfDate, int limit, LocalDate throughDate) {
        if (!loan.getStatus().isOutstanding() || Long.valueOf(0).equals(loan.getBalance())) {
            return new LoanForecast(loan, List.of(), false, List.of());
        }
        List<String> reasons = validate(loan, asOfDate);
        if (!reasons.isEmpty()) {
            return new LoanForecast(loan, List.of(), false, reasons);
        }
        try {
            if (throughDate != null) {
                LocalDate first = LoanRepaymentScheduleCalculator.resolveFirstRepaymentDate(
                        LoanRepaymentTerms.from(loan), asOfDate);
                // 조회 종료 이후 회차의 금액 오류가 현재 조회 구간까지 무효화하지 않도록 제한한다.
                int count = (int) ChronoUnit.MONTHS.between(YearMonth.from(first), YearMonth.from(throughDate)) + 1;
                limit = Math.min(limit, Math.max(0, count));
            }
            LoanRepaymentSchedule schedule = calculator.calculate(LoanRepaymentTerms.from(loan), asOfDate, limit);
            return new LoanForecast(loan, schedule.items(), schedule.truncated(), List.of());
        } catch (ArithmeticException exception) {
            // 검증된 양수 금리·회차 계산에서 발생하는 금액 범위 초과만 대출 단위로 격리한다.
            // 기간 합계는 이 경계 밖에서 정확하게 누적하므로 정상 회차를 버리지 않는다.
            return new LoanForecast(loan, List.of(), false, List.of("CALCULATION_AMOUNT_OUT_OF_RANGE"));
        }
    }

    private List<String> validate(Loan loan, LocalDate asOfDate) {
        List<String> reasons = new ArrayList<>();
        if (loan.getBalance() == null || loan.getBalance() < 0 || loan.getInterestRate() == null
                || loan.getInterestRate().signum() < 0 || loan.getRepaymentType() == null
                || (loan.getRepaymentDay() != null && (loan.getRepaymentDay() < 1 || loan.getRepaymentDay() > 31))) {
            reasons.add("INVALID_TERMS");
        }
        if (loan.getMaturityDate() == null) {
            reasons.add("MISSING_MATURITY_DATE");
        } else if (loan.getMaturityDate().isBefore(asOfDate)) {
            reasons.add("MATURED_WITH_BALANCE");
        }
        if (loan.getNextRepaymentDate() == null) {
            if (loan.getRepaymentDay() == null) {
                reasons.add("MISSING_REPAYMENT_DATE");
            }
        } else {
            if (loan.getNextRepaymentDate().isBefore(asOfDate)) {
                reasons.add("STALE_REPAYMENT_DATE");
            }
            if (loan.getMaturityDate() != null && loan.getNextRepaymentDate().isAfter(loan.getMaturityDate())) {
                reasons.add("INVALID_TERMS");
            }
        }
        return reasons.stream().distinct().sorted().toList();
    }
}
