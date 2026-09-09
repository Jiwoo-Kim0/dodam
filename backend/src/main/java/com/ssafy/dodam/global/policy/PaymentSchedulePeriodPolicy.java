package com.ssafy.dodam.global.policy;

import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/** 과거 거래와 달리 미래 결제일을 허용하는 결제예정 전용 기간 정책. */
@Component
public class PaymentSchedulePeriodPolicy {

    public static final int DEFAULT_PERIOD_MONTHS = 3;
    public static final int MAX_PERIOD_MONTHS = 12;

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    public QueryPeriod resolve(LocalDate startDate, LocalDate endDate) {
        return resolve(startDate, endDate, LocalDate.now(SERVICE_ZONE));
    }

    QueryPeriod resolve(LocalDate startDate, LocalDate endDate, LocalDate today) {
        LocalDate resolvedStart;
        LocalDate resolvedEnd;

        if (startDate == null && endDate == null) {
            resolvedStart = today;
            resolvedEnd = today.plusMonths(DEFAULT_PERIOD_MONTHS).minusDays(1);
        } else if (startDate == null) {
            resolvedEnd = endDate;
            resolvedStart = endDate.minusMonths(DEFAULT_PERIOD_MONTHS).plusDays(1);
        } else if (endDate == null) {
            resolvedStart = startDate;
            resolvedEnd = startDate.plusMonths(DEFAULT_PERIOD_MONTHS).minusDays(1);
        } else {
            resolvedStart = startDate;
            resolvedEnd = endDate;
        }

        if (resolvedStart.isAfter(resolvedEnd)) {
            throw new InvalidRequestException("시작일은 종료일보다 늦을 수 없습니다.");
        }
        if (resolvedStart.isBefore(resolvedEnd.minusMonths(MAX_PERIOD_MONTHS).plusDays(1))) {
            throw new InvalidRequestException(
                    "조회 기간은 최대 " + MAX_PERIOD_MONTHS + "개월까지 가능합니다."
            );
        }
        return new QueryPeriod(resolvedStart, resolvedEnd);
    }
}
