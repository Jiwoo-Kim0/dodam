package com.ssafy.dodam.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.dodam.domain.recommendation.service.RecommendationGenerationKey;
import org.junit.jupiter.api.Test;

class RecommendationGenerationKeyTest {

    @Test
    void sameInputsAlwaysProduceTheSameKey() {
        String a = RecommendationGenerationKey.of(1L, 10L, 100L, "card-v1", "CARD:7");
        String b = RecommendationGenerationKey.of(1L, 10L, 100L, "card-v1", "CARD:7");

        assertThat(a).isEqualTo(b).matches("[0-9a-f]{64}");
    }

    @Test
    void everyComponentChangesTheKey() {
        String base = RecommendationGenerationKey.of(1L, 10L, 100L, "card-v1", "CARD:7");

        assertThat(RecommendationGenerationKey.of(2L, 10L, 100L, "card-v1", "CARD:7")).isNotEqualTo(base);
        assertThat(RecommendationGenerationKey.of(1L, 11L, 100L, "card-v1", "CARD:7")).isNotEqualTo(base);
        assertThat(RecommendationGenerationKey.of(1L, 10L, 101L, "card-v1", "CARD:7")).isNotEqualTo(base);
        assertThat(RecommendationGenerationKey.of(1L, 10L, 100L, "card-v2", "CARD:7")).isNotEqualTo(base);
        assertThat(RecommendationGenerationKey.of(1L, 10L, 100L, "card-v1", "CARD:8")).isNotEqualTo(base);
    }

    /** 목표 없는 추천은 목표 있는 추천과 다른 키 공간이다. */
    @Test
    void nullGoalIsDistinctFromAnyGoal() {
        String withoutGoal = RecommendationGenerationKey.of(1L, 10L, null, "card-v1", "CARD:7");

        assertThat(withoutGoal).matches("[0-9a-f]{64}")
                .isNotEqualTo(RecommendationGenerationKey.of(1L, 10L, 0L, "card-v1", "CARD:7"));
    }

    /** 단순 이어붙이기였다면 ("ab","c")와 ("a","bc")가 같은 키가 된다. */
    @Test
    void concatenationBoundariesDoNotCollide() {
        assertThat(RecommendationGenerationKey.of(1L, 10L, null, "ab", "c"))
                .isNotEqualTo(RecommendationGenerationKey.of(1L, 10L, null, "a", "bc"));
    }

    @Test
    void rejectsBlankComponents() {
        assertThatThrownBy(() -> RecommendationGenerationKey.of(1L, 10L, null, " ", "CARD:7"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecommendationGenerationKey.of(1L, 10L, null, "card-v1", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecommendationGenerationKey.of(null, 10L, null, "card-v1", "CARD:7"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
