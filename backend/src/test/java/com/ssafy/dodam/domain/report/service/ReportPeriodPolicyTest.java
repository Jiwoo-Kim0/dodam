package com.ssafy.dodam.domain.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.time.LocalDate;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

/** 리포트는 끝난 기간만 만든다. 이 정책이 느슨해지면 같은 기간을 볼 때마다 숫자가 달라진다. */
class ReportPeriodPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 16);
    private final ReportPeriodPolicy policy = new ReportPeriodPolicy();

    @Test
    void defaultsToTheLastCompletedPeriod() {
        assertThat(policy.resolveMonth(null, TODAY)).isEqualTo(YearMonth.of(2026, 8));
        assertThat(policy.resolveDate(null, TODAY)).isEqualTo(LocalDate.of(2026, 9, 15));
    }

    @Test
    void rejectsPeriodsThatHaveNotFinished() {
        assertThatThrownBy(() -> policy.resolveMonth("2026-09", TODAY))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> policy.resolveMonth("2026-10", TODAY))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> policy.resolveDate(TODAY, TODAY))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsMalformedAndOutOfRangePeriods() {
        assertThatThrownBy(() -> policy.resolveMonth("2026-13", TODAY))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> policy.resolveMonth("202608", TODAY))
                .isInstanceOf(InvalidRequestException.class);
        // 상한을 넘는 과거는 거래가 비어 "소비 0원" 리포트가 된다. 빈 리포트를 만들지 않고 거부한다.
        assertThatThrownBy(() -> policy.resolveMonth("2024-08", TODAY))
                .isInstanceOf(InvalidRequestException.class);
        assertThat(policy.resolveMonth("2024-09", TODAY)).isEqualTo(YearMonth.of(2024, 9));
    }

    @Test
    void upcomingMonthDefaultsToNextMonthAndAllowsThePast() {
        assertThat(policy.resolveUpcomingMonth(null, TODAY)).isEqualTo(YearMonth.of(2026, 10));
        // 지난 월은 400이 아니다 — 지난 달 리포트 화면이 예정 비용 카드 때문에 통째로 실패하면 안 된다
        assertThat(policy.resolveUpcomingMonth("2026-08", TODAY)).isEqualTo(YearMonth.of(2026, 8));
        assertThatThrownBy(() -> policy.resolveUpcomingMonth("2027-10", TODAY))
                .isInstanceOf(InvalidRequestException.class);
    }
}
