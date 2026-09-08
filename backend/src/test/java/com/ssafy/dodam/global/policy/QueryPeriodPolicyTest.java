package com.ssafy.dodam.global.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class QueryPeriodPolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 8);

    private final QueryPeriodPolicy policy = new QueryPeriodPolicy();

    @Test
    void defaultsToTheLastThreeMonthsUntilToday() {
        QueryPeriod period = policy.resolve(null, null, TODAY);

        assertThat(period.startDate()).isEqualTo(LocalDate.of(2026, 6, 9));
        assertThat(period.endDate()).isEqualTo(TODAY);
    }

    @Test
    void keepsExplicitPeriodWithinTheMaximumWindow() {
        QueryPeriod period = policy.resolve(
                LocalDate.of(2025, 9, 9),
                LocalDate.of(2026, 9, 8),
                TODAY
        );

        assertThat(period.startDate()).isEqualTo(LocalDate.of(2025, 9, 9));
        assertThat(period.endDate()).isEqualTo(LocalDate.of(2026, 9, 8));
    }

    @Test
    void rejectsReversedFutureAndTooLongPeriods() {
        assertThatThrownBy(() -> policy.resolve(TODAY, TODAY.minusDays(1), TODAY))
                .isInstanceOf(InvalidRequestException.class);

        assertThatThrownBy(() -> policy.resolve(null, TODAY.plusDays(1), TODAY))
                .isInstanceOf(InvalidRequestException.class);

        assertThatThrownBy(() -> policy.resolve(LocalDate.of(2025, 9, 8), TODAY, TODAY))
                .isInstanceOf(InvalidRequestException.class);
    }
}
