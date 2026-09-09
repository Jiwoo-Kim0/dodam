package com.ssafy.dodam.global.policy;

import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 현재 월 이후 남은 할부 일정에 사용하는 월 범위. 양 끝을 포함한다. */
@Component
public class FutureYearMonthPeriodPolicy {

    public static final int DEFAULT_MONTHS = 6;
    public static final int MAX_MONTHS = 12;
    private static final Pattern FORMAT = Pattern.compile("[0-9]{4}-(0[1-9]|1[0-2])");

    public YearMonthPeriod resolve(String startMonth, String endMonth, YearMonth currentMonth) {
        YearMonth start = startMonth == null ? currentMonth : parse(startMonth);
        YearMonth end = endMonth == null ? start.plusMonths(DEFAULT_MONTHS - 1) : parse(endMonth);
        return validate(start, end, currentMonth);
    }

    public YearMonthPeriod forecast(int months, YearMonth currentMonth) {
        if (months < 1 || months > MAX_MONTHS) {
            throw new InvalidRequestException("예측 개월 수는 1~12여야 합니다.");
        }
        return validate(currentMonth, currentMonth.plusMonths(months - 1L), currentMonth);
    }

    private YearMonthPeriod validate(YearMonth start, YearMonth end, YearMonth currentMonth) {
        if (start.getYear() < 1 || end.getYear() > 9999
                || start.isBefore(currentMonth) || start.isAfter(end)
                || ChronoUnit.MONTHS.between(start, end) + 1 > MAX_MONTHS) {
            throw new InvalidRequestException("현재 월 이후의 올바른 1~12개월 기간을 지정해야 합니다.");
        }
        return new YearMonthPeriod(start, end);
    }

    private YearMonth parse(String value) {
        if (!FORMAT.matcher(value).matches() || value.startsWith("0000")) {
            throw new InvalidRequestException("조회 연월은 YYYY-MM 형식의 유효한 연월이어야 합니다.");
        }
        return YearMonth.parse(value);
    }
}
