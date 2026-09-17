package com.ssafy.dodam.global.policy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class InterestIncomeTaxPolicyTest {

    @ParameterizedTest
    @CsvSource({
            "0, 0",
            "6, 0",        // 0.924 → 절사
            "7, 1",        // 1.078
            "538, 82",     // 82.852 (적금 통합 테스트와 같은 값)
            "36500, 5621", // 5621.0
            "1000000, 154000"
    })
    void withholdsFifteenPointFourPercentTruncatedToWon(long gross, long tax) {
        assertThat(InterestIncomeTaxPolicy.withhold(gross)).isEqualTo(tax);
        // 세액을 절사하므로 세전 = 세액 + 세후가 항상 성립한다.
        assertThat(gross - tax + tax).isEqualTo(gross);
    }
}
