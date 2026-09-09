package com.ssafy.dodam.domain.loan.service;

import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.policy.FutureYearMonthPeriodPolicy;
import com.ssafy.dodam.global.policy.YearMonthPeriod;
import java.time.YearMonth;
import org.springframework.stereotype.Component;

@Component
public class LoanAnalysisPeriodPolicy {
    private final FutureYearMonthPeriodPolicy policy;

    public LoanAnalysisPeriodPolicy(FutureYearMonthPeriodPolicy policy) {
        this.policy = policy;
    }

    YearMonthPeriod resolve(String start, String end, YearMonth current) {
        YearMonthPeriod period = policy.resolve(start, end, current);
        if (period.endMonth().isAfter(current.plusMonths(11))) {
            throw new InvalidRequestException("예측 종료월은 현재 월부터 12개월 이내여야 합니다.");
        }
        return period;
    }

    YearMonthPeriod forecast(int months, YearMonth current) {
        return policy.forecast(months, current);
    }

    YearMonth single(String value, YearMonth current) {
        String month = value == null ? current.toString() : value;
        return resolve(month, month, current).startMonth();
    }
}
