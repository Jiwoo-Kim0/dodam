package com.ssafy.dodam.domain.pattern.service;

import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * 분석 기간. 기준일이 속한 달을 빼고 **완료된 달** `months`개다.
 *
 * <p>예: 기준일 2026-09-14, 6개월 → 2026-03 ~ 2026-08. 진행 중인 달을 넣으면 월 합계가 날짜에 따라 달라져 규칙성·변동성
 * 지표가 흔들리고, 사용자 간 비교(군집)도 같은 달 수로 맞출 수 없다. 시연 seed(2026-03-01 ~ 2026-08-31)도 이 정의로 6개월이 된다.
 *
 * @param start  첫 달
 * @param end    마지막 달(기준일의 전 달)
 */
public record PatternWindow(YearMonth start, YearMonth end) {

    public static PatternWindow endingBefore(LocalDate referenceDate, int months) {
        if (months < 1) {
            throw new IllegalArgumentException("분석 기간은 1개월 이상이어야 합니다.");
        }
        YearMonth end = YearMonth.from(referenceDate).minusMonths(1);
        return new PatternWindow(end.minusMonths(months - 1L), end);
    }

    /** 조회 API의 `months` 파라미터. 비어 있으면 기본값, 1~12 밖이면 400. */
    public static int resolveMonths(Integer requested, int defaultMonths) {
        if (requested == null) {
            return defaultMonths;
        }
        if (requested < 1 || requested > PatternProperties.MAX_LOOKBACK_MONTHS) {
            throw new InvalidRequestException("months는 1~12 사이여야 합니다.");
        }
        return requested;
    }

    public LocalDate startDate() {
        return start.atDay(1);
    }

    public LocalDate endDate() {
        return end.atEndOfMonth();
    }

    public int months() {
        return (int) (start.until(end, java.time.temporal.ChronoUnit.MONTHS) + 1);
    }

    /** 기간 안 모든 달(오래된 순). 거래가 없는 달도 포함한다. */
    public List<YearMonth> yearMonths() {
        List<YearMonth> result = new ArrayList<>();
        for (YearMonth cursor = start; !cursor.isAfter(end); cursor = cursor.plusMonths(1)) {
            result.add(cursor);
        }
        return result;
    }

    public boolean contains(YearMonth yearMonth) {
        return !yearMonth.isBefore(start) && !yearMonth.isAfter(end);
    }
}
