package com.ssafy.dodam.domain.improvement.service;

import com.ssafy.dodam.domain.improvement.dto.ActionComparisonResponse.Period;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/**
 * 실행 전후 비교 기간 정책. "언제부터가 개선 이후인가"를 한 곳에서만 정한다.
 *
 * <p>기준은 <b>수락 시각이 속한 달</b>이다. 그 달은 중간에 행동이 바뀌어 전·후가 섞이므로 양쪽 모두에서 뺀다.
 *
 * <pre>
 *  ...  6월   7월   8월  │   9월   │  10월  11월  12월  ...
 *      └──── before ────┘ 수락한 달 └──── after ─────┘
 * </pre>
 *
 * <ul>
 *   <li>before = 수락 월 직전 3개 완료 월</li>
 *   <li>after  = 수락 다음 달부터 최대 3개 <b>완료</b> 월. 완료 월이 0개면 아직 측정할 수 없다</li>
 * </ul>
 *
 * <p>양쪽 개월 수가 다를 수 있으므로 금액은 합계가 아니라 <b>월평균</b>으로 비교한다. 진행 중인 달은
 * 어느 쪽에도 넣지 않는다 — 20일치 소비를 한 달치와 비교하면 반드시 "개선"으로 보인다.
 */
@Component
public class ImprovementMeasurementPolicy {

    /** 비교에 쓰는 최대 개월 수. 양쪽 모두 이 값을 넘지 않는다. */
    public static final int WINDOW_MONTHS = 3;

    /** 측정에 필요한 최소 완료 월. 1개월치 데이터가 쌓여야 실행 후를 말할 수 있다. */
    public static final int MINIMUM_AFTER_MONTHS = 1;

    public Window before(LocalDateTime plannedAt) {
        YearMonth accepted = YearMonth.from(plannedAt);
        YearMonth start = accepted.minusMonths(WINDOW_MONTHS);
        YearMonth end = accepted.minusMonths(1);
        return new Window(start.atDay(1), end.atEndOfMonth(), WINDOW_MONTHS);
    }

    /** 실행 후 관측이 시작되는 날. 이 날이 지나야 행동이 IN_PROGRESS가 된다. */
    public LocalDate afterStartDate(LocalDateTime plannedAt) {
        return YearMonth.from(plannedAt).plusMonths(1).atDay(1);
    }

    /**
     * 실행 후 완료 월 구간. 아직 완료된 달이 없으면 null이다.
     *
     * @param today 기준일. 이 날이 속한 달은 진행 중이므로 포함하지 않는다
     */
    public Window after(LocalDateTime plannedAt, LocalDate today) {
        YearMonth start = YearMonth.from(plannedAt).plusMonths(1);
        YearMonth lastCompleted = YearMonth.from(today).minusMonths(1);
        if (start.isAfter(lastCompleted)) {
            return null;
        }
        long available = ChronoUnit.MONTHS.between(start, lastCompleted) + 1;
        int months = (int) Math.min(available, WINDOW_MONTHS);
        if (months < MINIMUM_AFTER_MONTHS) {
            return null;
        }
        YearMonth end = start.plusMonths(months - 1L);
        return new Window(start.atDay(1), end.atEndOfMonth(), months);
    }

    /** 양 끝을 포함하는 집계 구간과 월평균의 분모가 되는 개월 수. */
    public record Window(LocalDate startDate, LocalDate endDate, int months) {

        public Period toPeriod() {
            return new Period(startDate, endDate);
        }
    }
}
