package com.ssafy.dodam.global.policy;

import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 청구서처럼 연월 범위를 받는 API의 공통 조회 기간 정책.
 */
@Component
public class YearMonthPeriodPolicy {

    public static final int DEFAULT_PERIOD_MONTHS = 3;
    public static final int MAX_PERIOD_MONTHS = 12;

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    public YearMonthPeriod resolve(String startMonth, String endMonth) {
        return resolve(startMonth, endMonth, YearMonth.now(SERVICE_ZONE));
    }

    YearMonthPeriod resolve(String startMonth, String endMonth, YearMonth currentMonth) {
        YearMonth parsedEnd = parse(endMonth, "endMonth");
        YearMonth resolvedEnd = parsedEnd == null ? currentMonth : parsedEnd;
        YearMonth parsedStart = parse(startMonth, "startMonth");
        YearMonth resolvedStart = parsedStart == null
                ? resolvedEnd.minusMonths(DEFAULT_PERIOD_MONTHS - 1L)
                : parsedStart;

        if (resolvedStart.isAfter(resolvedEnd)) {
            throw new InvalidRequestException("시작월은 종료월보다 늦을 수 없습니다.");
        }
        if (resolvedEnd.isAfter(currentMonth)) {
            throw new InvalidRequestException("종료월은 현재 월 이후일 수 없습니다.");
        }

        long includedMonths = ChronoUnit.MONTHS.between(resolvedStart, resolvedEnd) + 1;
        if (includedMonths > MAX_PERIOD_MONTHS) {
            throw new InvalidRequestException(
                    "조회 기간은 최대 " + MAX_PERIOD_MONTHS + "개월까지 가능합니다."
            );
        }

        return new YearMonthPeriod(resolvedStart, resolvedEnd);
    }

    private static YearMonth parse(String value, String parameterName) {
        if (value == null) {
            return null;
        }
        if (!YEAR_MONTH_PATTERN.matcher(value).matches()) {
            throw new InvalidRequestException(
                    parameterName + "는 YYYY-MM 형식의 유효한 연월이어야 합니다."
            );
        }
        return YearMonth.parse(value);
    }
}
