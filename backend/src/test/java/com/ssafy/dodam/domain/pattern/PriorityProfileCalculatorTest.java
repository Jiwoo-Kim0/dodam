package com.ssafy.dodam.domain.pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.ssafy.dodam.domain.pattern.dto.response.PriorityProfileResponse;
import com.ssafy.dodam.domain.pattern.entity.LiquidityLevel;
import com.ssafy.dodam.domain.pattern.service.PriorityProfileCalculator;
import com.ssafy.dodam.domain.pattern.service.PriorityProfileCalculator.Inputs;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class PriorityProfileCalculatorTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 10, 0);
    private final PriorityProfileCalculator calculator = new PriorityProfileCalculator();

    @Test
    void 부채부담이_크면_위험_가중치가_오르고_부채_상환이_최우선이다() {
        PriorityProfileResponse profile = calculator.calculate("PATTERN_ANALYSIS", 7L, NOW,
                new Inputs(0.35, LiquidityLevel.MEDIUM, 0.2, 0.7, 0.2, 0.9, 0.4, 0.8, 0.3));

        assertThat(profile.profileType()).isEqualTo("DEBT_FOCUSED");
        assertThat(profile.focusAreas().get(0).area()).isEqualTo("DEBT_REPAYMENT");
        assertThat(sum(profile.weights())).isCloseTo(1.0, within(0.001));
        assertThat(profile.weights().financialRisk()).isGreaterThan(profile.weights().expectedEffect());
        assertThat(profile.clusterDeviationAlpha()).isEqualTo(0.16);
        assertThat(profile.analysisId()).isEqualTo(7L);
    }

    @Test
    void 재량소비가_크면_소비_집중_프로필이고_군집이_없으면_알파는_0이다() {
        PriorityProfileResponse profile = calculator.calculate("LIVE_CALCULATION", null, NOW,
                new Inputs(0.0, LiquidityLevel.HIGH, 0.5, 0.9, 0.25, 0.3, 0.3, null, null));

        assertThat(profile.profileType()).isEqualTo("SPENDING_FOCUSED");
        assertThat(profile.focusAreas().get(0).area()).isEqualTo("SPENDING_REDUCTION");
        assertThat(profile.clusterDeviationAlpha()).isZero();
        assertThat(profile.weights().expectedEffect()).isGreaterThan(profile.weights().financialRisk());
    }

    @Test
    void 기본_프로필은_BALANCED이고_가정에_데이터_없음을_남긴다() {
        PriorityProfileResponse profile = calculator.defaults(NOW);

        assertThat(profile.source()).isEqualTo("DEFAULT");
        assertThat(profile.profileType()).isEqualTo("BALANCED");
        assertThat(profile.clusterDeviationAlpha()).isZero();
        assertThat(sum(profile.weights())).isCloseTo(1.0, within(0.001));
        assertThat(profile.assumptions().get(0)).contains("기본 프로필");
        assertThat(profile.focusAreas()).hasSize(5);
    }

    private static double sum(PriorityProfileResponse.Weights w) {
        return w.financialRisk() + w.expectedEffect() + w.goalContribution() + w.urgency() + w.executionDifficulty();
    }
}
