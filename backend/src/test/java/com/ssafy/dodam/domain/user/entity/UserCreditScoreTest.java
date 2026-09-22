package com.ssafy.dodam.domain.user.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class UserCreditScoreTest {

    private static final LocalDateTime EVALUATED_AT = LocalDateTime.of(2026, 9, 27, 23, 59, 59);

    @Test
    void recordsSyntheticScoreWithRequiredModelMetadata() {
        User user = User.createLocalUser("score@dodam.invalid", "hash", "도담");

        user.updateCreditScore(768, CreditScoreSource.SYNTHETIC, " DODAM_CREDIT_V1 ", EVALUATED_AT);

        assertThat(user.getCreditScore()).isEqualTo(768);
        assertThat(user.getCreditScoreSource()).isEqualTo(CreditScoreSource.SYNTHETIC);
        assertThat(user.getCreditScoreModelVersion()).isEqualTo("DODAM_CREDIT_V1");
        assertThat(user.getCreditScoreUpdatedAt()).isEqualTo(EVALUATED_AT);
    }

    @Test
    void rejectsOutOfRangeScoreAndMissingSyntheticModelVersion() {
        User user = User.createLocalUser("score@dodam.invalid", "hash", "도담");

        assertThatIllegalArgumentException()
                .isThrownBy(() -> user.updateCreditScore(0, CreditScoreSource.SYNTHETIC,
                        "DODAM_CREDIT_V1", EVALUATED_AT));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> user.updateCreditScore(1001, CreditScoreSource.SYNTHETIC,
                        "DODAM_CREDIT_V1", EVALUATED_AT));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> user.updateCreditScore(768, CreditScoreSource.SYNTHETIC, " ", EVALUATED_AT));
    }
}
