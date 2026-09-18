package com.ssafy.dodam.domain.recommendation.service.generator;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.diagnosis.entity.DetectionSource;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import com.ssafy.dodam.domain.recommendation.dto.CandidateGenerationResult.SkipReason;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationCandidate;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationInput;
import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import com.ssafy.dodam.domain.recommendation.service.RecommendationCandidateGenerator.Outcome;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 640 C-2: 단독 군집 이탈 문제 → REVIEW_SPENDING_PATTERN. 효과 금액을 만들지 않고 근거에 군집 값만 옮긴다. */
class ClusterDeviationGeneratorTest {

    private final ObjectMapper json = new ObjectMapper();
    private final ClusterDeviationGenerator generator = new ClusterDeviationGenerator(json);
    private final RecommendationInput input = new RecommendationInput(1L, 7L, "v7-cluster-hybrid", null,
            LocalDateTime.of(2026, 9, 18, 10, 0), null, List.of());

    @Test
    void supportsOnlyStandaloneClusterIssues() {
        assertThat(generator.supports(issue("SPENDING", "CLUSTER_DISCRETIONARY_HIGH", DetectionSource.CLUSTER, context("discretionaryShare")))).isTrue();
        assertThat(generator.supports(issue("SPENDING", "CATEGORY_SPENDING_INCREASE", DetectionSource.RULE_AND_CLUSTER, context("discretionaryShare")))).isFalse();
        assertThat(generator.supports(issue("SPENDING", "CATEGORY_SPENDING_INCREASE", DetectionSource.RULE, null))).isFalse();
    }

    @Test
    void discretionaryDeviationBecomesReviewCandidateWithoutEffect() {
        Outcome outcome = generator.generate(input, issue("SPENDING", "CLUSTER_DISCRETIONARY_HIGH", DetectionSource.CLUSTER, context("discretionaryShare")));
        assertThat(outcome.skipReason()).isNull();
        RecommendationCandidate candidate = outcome.candidates().get(0);
        assertThat(candidate.generatorVersion()).isEqualTo("640.1");
        assertThat(candidate.candidateKey()).isEqualTo("REVIEW_SPENDING_PATTERN:discretionaryShare");
        assertThat(candidate.sourceIssueId()).isEqualTo(31L);
        assertThat(candidate.domain()).isEqualTo("SPENDING");
        assertThat(candidate.type()).isEqualTo("REVIEW_SPENDING_PATTERN");
        assertThat(candidate.title()).isEqualTo("비슷한 유형과 소비 구조 비교해 보기");
        assertThat(candidate.description()).contains("시뮬레이션");
        assertThat(candidate.effect()).isEqualTo(Recommendation.Effect.NONE);
        RecommendationCandidate.Reason reason = candidate.reasons().get(0);
        assertThat(reason.code()).isEqualTo("CLUSTER_RANGE_DEVIATION");
        assertThat(reason.description()).isEqualTo("재량소비 비중 44%이 비슷한 소비 유형(소비 집중형, 1,000명)의 기준 범위 12%~31%보다 높아요.");
        assertThat(reason.evidence().get("feature").asText()).isEqualTo("discretionaryShare");
        assertThat(reason.evidence().get("modelVersion").asText()).isEqualTo("spending-kmeans-v2");
        assertThat(reason.evidence().get("sampleSize").asInt()).isEqualTo(1000);
        assertThat(reason.evidence().get("patternAnalysisId").asLong()).isEqualTo(99L);
    }

    @Test
    void nonSimulationFeaturesKeepIssueDomainAndSayNoJudgement() {
        ObjectNode volatility = context("spendingVolatility");
        volatility.put("displayName", "소비 변동성").put("userValue", 0.9).put("p5", 0.05).put("p95", 0.3);
        Outcome outcome = generator.generate(input, issue("CASHFLOW", "CLUSTER_VOLATILITY_HIGH", DetectionSource.CLUSTER, volatility));
        RecommendationCandidate candidate = outcome.candidates().get(0);
        assertThat(candidate.domain()).isEqualTo("CASHFLOW");
        assertThat(candidate.candidateKey()).isEqualTo("REVIEW_SPENDING_PATTERN:spendingVolatility");
        assertThat(candidate.description()).contains("낭비로 단정하지 않아요");
        assertThat(candidate.reasons().get(0).description()).isEqualTo("소비 변동성 0.90이 비슷한 소비 유형(소비 집중형, 1,000명)의 기준 범위 0.05~0.30보다 높아요.");

        ObjectNode saving = context("savingRate");
        saving.put("displayName", "저축률").put("userValue", 0.02).put("p5", 0.1).put("p95", 0.3).put("direction", "BELOW");
        Outcome low = generator.generate(input, issue("SAVING", "CLUSTER_SAVING_RATE_LOW", DetectionSource.CLUSTER, saving));
        assertThat(low.candidates().get(0).domain()).isEqualTo("SAVING");
        assertThat(low.candidates().get(0).reasons().get(0).description()).endsWith("10%~30%보다 낮아요.");
    }

    @Test
    void missingContextOrFieldsAreSkippedWithReasons() {
        assertThat(generator.generate(input, issue("SPENDING", "CLUSTER_DISCRETIONARY_HIGH", DetectionSource.CLUSTER, null)).skipReason())
                .isEqualTo(SkipReason.NO_CLUSTER_CONTEXT);
        ObjectNode partial = context("discretionaryShare");
        partial.remove("p95");
        assertThat(generator.generate(input, issue("SPENDING", "CLUSTER_DISCRETIONARY_HIGH", DetectionSource.CLUSTER, partial)).skipReason())
                .isEqualTo(SkipReason.INSUFFICIENT_DATA);
    }

    private ObjectNode context(String feature) {
        ObjectNode node = json.createObjectNode();
        node.put("modelVersion", "spending-kmeans-v2").put("clusterId", 1).put("clusterLabel", "소비 집중형").put("sampleSize", 1000)
                .put("confidence", 0.58).put("window", "2026-03~2026-08").put("analyzedAt", "2026-09-18T09:12")
                .put("feature", feature).put("displayName", "재량소비 비중").put("userValue", 0.44).put("p5", 0.12).put("p95", 0.31)
                .put("mean", 0.21).put("deviation", 0.68).put("direction", "ABOVE").put("deviationScore", 0.23);
        return node;
    }

    private RecommendationInput.Issue issue(String domain, String type, DetectionSource source, ObjectNode context) {
        ObjectNode evidence = json.createObjectNode().put("ruleKey", "CLUSTER_x").put("targetId", 99L).put("patternAnalysisId", 99L);
        return new RecommendationInput.Issue(31L, domain, type, IssueSeverity.MEDIUM, "제목", "설명", null, null, evidence, source,
                source == DetectionSource.RULE ? null : "spending-kmeans-v2", context);
    }
}
