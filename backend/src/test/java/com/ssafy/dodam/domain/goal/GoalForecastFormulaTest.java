package com.ssafy.dodam.domain.goal;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.dodam.domain.goal.service.GoalForecastFormula;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 658: 예상 달성일의 단일 식. 정수 개월은 달력 축, 나머지는 그 달 일수 비례 올림. */
class GoalForecastFormulaTest {
    private static final LocalDate SEP_21 = LocalDate.of(2026, 9, 21);

    @Test
    void boundariesMatchTheOldRule() {
        assertThat(GoalForecastFormula.expectedDate(0, 100_000, SEP_21)).isEqualTo(SEP_21);
        assertThat(GoalForecastFormula.expectedDate(-1, 0, SEP_21)).isEqualTo(SEP_21);
        assertThat(GoalForecastFormula.expectedDate(1, 0, SEP_21)).isNull();
        assertThat(GoalForecastFormula.expectedDate(1_000_000, 100_000, SEP_21)).isEqualTo(SEP_21.plusMonths(10));
        assertThat(GoalForecastFormula.monthsToAchieve(1_000_001, 100_000)).isEqualTo(11);
        assertThat(GoalForecastFormula.monthsToAchieve(0, 100_000)).isZero();
    }

    @Test
    void remainderIsProratedOverTheNextCalendarMonth() {
        // U0003 시연 계정: 남은 31,300,000·속도 3,333,333 → 9개월(2027-06-21) + 1,300,003/3,333,333 × 30일 = 11.7 → 12일 → 2027-07-03
        assertThat(GoalForecastFormula.expectedDate(31_300_000, 3_333_333, SEP_21)).isEqualTo(LocalDate.of(2027, 7, 3));
        // 카페 5만원 줄이면 속도 3,383,333 → 9개월 + 850,003/3,383,333 × 30일 = 7.5 → 8일 → 2027-06-29 (4일 단축). 예전 식은 둘 다 2027-07-21
        assertThat(GoalForecastFormula.expectedDate(31_300_000, 3_383_333, SEP_21)).isEqualTo(LocalDate.of(2027, 6, 29));
        // 나머지가 속도와 같아지는 일은 없다(정수 나눗셈). 나머지 1원은 1일
        assertThat(GoalForecastFormula.expectedDate(1_000_001, 100_000, SEP_21)).isEqualTo(SEP_21.plusMonths(10).plusDays(1));
    }

    @Test
    void axisKeepsMonthEndsAndOffsetContinuesTheSameAxis() {
        LocalDate jan31 = LocalDate.of(2026, 1, 31);
        // 1/31 축: 2/28, 3/31. 1.5개월 → 2/28 + 31일 × 0.5 = 16일 → 3/16
        assertThat(GoalForecastFormula.expectedDate(15_000, 10_000, jan31)).isEqualTo(LocalDate.of(2026, 3, 16));
        // offset 1: 2/28에서 시작해도 축은 1/31 기준이라 4개월 뒤는 6/30(2/28 + 4개월 = 6/28이 아니다)
        assertThat(GoalForecastFormula.expectedDate(40_000, 10_000, jan31, 1)).isEqualTo(LocalDate.of(2026, 6, 30));
        assertThat(GoalForecastFormula.expectedDate(0, 10_000, jan31, 3)).isEqualTo(LocalDate.of(2026, 4, 30));
    }

    @Test
    void dateWithinProratesAndClampsToTheInterval() {
        LocalDate from = LocalDate.of(2026, 2, 1);
        LocalDate to = LocalDate.of(2026, 3, 1);
        assertThat(GoalForecastFormula.dateWithin(from, to, 0, 10_000)).isEqualTo(from);
        assertThat(GoalForecastFormula.dateWithin(from, to, 10_000, 10_000)).isEqualTo(to);
        assertThat(GoalForecastFormula.dateWithin(from, to, 12_000, 10_000)).isEqualTo(to);
        assertThat(GoalForecastFormula.dateWithin(from, to, 5_000, 0)).isEqualTo(to);
        // 8,000/12,000 × 28일 = 18.67 → 19일 → 2/20
        assertThat(GoalForecastFormula.dateWithin(from, to, 8_000, 12_000)).isEqualTo(LocalDate.of(2026, 2, 20));
    }
}
