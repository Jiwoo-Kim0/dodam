package com.ssafy.dodam.global.policy;

import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 단일 연월을 받는 집계 API의 형식과 기준월을 통일한다. */
@Component
public class YearMonthPolicy {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    public YearMonth resolveHistorical(String value) {
        YearMonth resolved = parseOrCurrent(value);
        if (resolved.isAfter(YearMonth.now(SERVICE_ZONE))) {
            throw new InvalidRequestException("조회 연월은 현재 월 이후일 수 없습니다.");
        }
        return resolved;
    }

    public YearMonth resolveBillingMonth(String value) {
        return parseOrCurrent(value);
    }

    private YearMonth parseOrCurrent(String value) {
        if (value == null) {
            return YearMonth.now(SERVICE_ZONE);
        }
        if (!YEAR_MONTH_PATTERN.matcher(value).matches()) {
            throw new InvalidRequestException("yearMonth는 YYYY-MM 형식의 유효한 연월이어야 합니다.");
        }
        return YearMonth.parse(value);
    }
}
