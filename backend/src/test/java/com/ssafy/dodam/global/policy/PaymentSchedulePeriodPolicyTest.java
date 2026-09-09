package com.ssafy.dodam.global.policy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PaymentSchedulePeriodPolicyTest {

    private final PaymentSchedulePeriodPolicy policy = new PaymentSchedulePeriodPolicy();
    private final LocalDate today = LocalDate.of(2026, 9, 8);

    @Test
    void resolvesDefaultAndSingleSidedPeriods() {
        assertThat(policy.resolve(null, null, today))
                .isEqualTo(new QueryPeriod(today, LocalDate.of(2026, 12, 7)));
        assertThat(policy.resolve(LocalDate.of(2026, 10, 1), null, today))
                .isEqualTo(new QueryPeriod(
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 12, 31)
                ));
        assertThat(policy.resolve(null, LocalDate.of(2026, 8, 31), today))
                .isEqualTo(new QueryPeriod(
                        LocalDate.of(2026, 6, 1),
                        LocalDate.of(2026, 8, 31)
                ));
    }

    @Test
    void allowsFutureDatesButRejectsInvalidOrTooLongPeriods() {
        assertThat(policy.resolve(
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 1, 31),
                today
        )).isEqualTo(new QueryPeriod(
                LocalDate.of(2027, 1, 1),
                LocalDate.of(2027, 1, 31)
        ));

        assertThatThrownBy(() -> policy.resolve(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 9, 30),
                today
        )).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> policy.resolve(
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2027, 1, 1),
                today
        )).isInstanceOf(InvalidRequestException.class);
    }
}
