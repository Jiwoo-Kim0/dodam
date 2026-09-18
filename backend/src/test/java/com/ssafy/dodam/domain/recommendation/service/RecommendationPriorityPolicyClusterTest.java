package com.ssafy.dodam.domain.recommendation.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.diagnosis.entity.DetectionSource;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationCandidate;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationInput;
import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 640 C-2: 군집 이탈 가중치 α. 순위를 뒤집는 힘이 아니라 동점 해소 수준(최대 10, 대개 5 안팎)임을 고정한다. */
class RecommendationPriorityPolicyClusterTest {

    private static final LocalDate BASE = LocalDate.of(2026, 9, 18);
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void clusterPointsAreAlphaTimesDeviationScoreCappedAtTen() {
        assertThat(RecommendationPriorityPolicy.clusterPoints(null)).isZero();
        assertThat(RecommendationPriorityPolicy.clusterPoints(issue(1L, DetectionSource.RULE, null))).isZero();
        assertThat(RecommendationPriorityPolicy.clusterPoints(issue(1L, DetectionSource.CLUSTER, context(0.0)))).isZero();
        assertThat(RecommendationPriorityPolicy.clusterPoints(issue(1L, DetectionSource.RULE_AND_CLUSTER, context(0.23)))).isEqualTo(2);
        assertThat(RecommendationPriorityPolicy.clusterPoints(issue(1L, DetectionSource.CLUSTER, context(0.46)))).isEqualTo(5);
        assertThat(RecommendationPriorityPolicy.clusterPoints(issue(1L, DetectionSource.CLUSTER, context(3.0)))).isEqualTo(10);
        ObjectNode withoutScore = context(0.5);
        withoutScore.remove("deviationScore");
        assertThat(RecommendationPriorityPolicy.clusterPoints(issue(1L, DetectionSource.CLUSTER, withoutScore))).isZero();
    }

    @Test
    void mergedClusterEvidenceBreaksTiesButDoesNotOutrankHigherSeverity() {
        // 같은 MEDIUM·같은 효과의 규칙 문제 둘: 군집 근거가 붙은 쪽(+5)이 앞선다
        RecommendationInput.Issue plain = issue(10L, DetectionSource.RULE, null);
        RecommendationInput.Issue merged = issue(11L, DetectionSource.RULE_AND_CLUSTER, context(0.46));
        // HIGH 문제(30점 + 효과 1점)는 군집 근거 없는데도 MEDIUM+군집(20+5+5)보다 앞선다 — α는 심각도를 뒤집지 못한다
        RecommendationInput.Issue high = new RecommendationInput.Issue(12L, "LOAN", "HIGH_INTEREST_RATE", IssueSeverity.HIGH, "t", "d",
                null, null, null, DetectionSource.RULE, null, null);
        RecommendationInput input = new RecommendationInput(1L, 7L, "v7-cluster-hybrid", null, LocalDateTime.of(2026, 9, 18, 10, 0), null,
                List.of(plain, merged, high));
        List<RecommendationCandidate> candidates = List.of(
                candidate("REDUCE_CATEGORY_SPENDING:1", 10L, "SPENDING"),
                candidate("REDUCE_CATEGORY_SPENDING:2", 11L, "SPENDING"),
                new RecommendationCandidate("t", "REFINANCE_LOAN:5", 12L, "LOAN", "REFINANCE_LOAN", "제목", "설명", List.of(),
                        new Recommendation.Effect(100_000L, 1_200_000L, null, null, null, null, null, null)));

        List<RecommendationPriorityPolicy.Scored> ranked = RecommendationPriorityPolicy.rank(input, candidates, BASE);

        assertThat(ranked).extracting(s -> s.candidate().candidateKey())
                .containsExactly("REFINANCE_LOAN:5", "REDUCE_CATEGORY_SPENDING:2", "REDUCE_CATEGORY_SPENDING:1");
        assertThat(ranked.get(0).score()).isEqualTo(31);       // HIGH 30 + 효과 10만 원 1점, LOAN 도메인
        assertThat(ranked.get(1).score()).isEqualTo(20 + 5 + 5); // MEDIUM 20 + SPENDING 5 + 군집 5
        assertThat(ranked.get(2).score()).isEqualTo(20 + 5);
    }

    private ObjectNode context(double deviationScore) {
        return json.createObjectNode().put("modelVersion", "spending-kmeans-v2").put("feature", "discretionaryShare")
                .put("deviationScore", deviationScore);
    }

    private RecommendationInput.Issue issue(long id, DetectionSource source, ObjectNode context) {
        return new RecommendationInput.Issue(id, "SPENDING", "CATEGORY_SPENDING_INCREASE", IssueSeverity.MEDIUM, "t", "d", null, null, null,
                source, source == DetectionSource.RULE ? null : "spending-kmeans-v2", context);
    }

    private static RecommendationCandidate candidate(String key, long issueId, String domain) {
        return new RecommendationCandidate("t", key, issueId, domain, key.substring(0, key.indexOf(':')), "제목", "설명", List.of(),
                Recommendation.Effect.NONE);
    }
}
