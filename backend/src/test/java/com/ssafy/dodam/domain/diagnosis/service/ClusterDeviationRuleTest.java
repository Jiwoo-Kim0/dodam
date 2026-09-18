package com.ssafy.dodam.domain.diagnosis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.diagnosis.entity.DetectionSource;
import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisIssue;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import com.ssafy.dodam.domain.diagnosis.service.ClusterDeviationRule.ClusterDeviationFeature;
import com.ssafy.dodam.domain.diagnosis.service.ClusterDeviationRule.Result;
import com.ssafy.dodam.domain.pattern.dto.response.ClusterProfileResponse;
import com.ssafy.dodam.domain.pattern.dto.response.ClusterProfileResponse.FeatureRange;
import com.ssafy.dodam.domain.pattern.service.PatternProperties;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 군집 이탈 규칙(S15P21D105-641)의 순수 판정. 저장된 프로필만 넣고 방향 필터·임계·심각도·노출 조건·병합을 확인한다.
 * 임계값을 바꾸면 여기와 {@code DiagnosisRuleVersion}을 함께 바꾼다.
 */
class ClusterDeviationRuleTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 9, 20);
    private static final LocalDateTime ANALYZED_AT = LocalDateTime.of(2026, 9, 18, 9, 12);
    private static final PatternProperties PROPERTIES = new PatternProperties(6, 3, 30, 10, 0.2, 4, null, null, "", true);

    private final ObjectMapper json = new ObjectMapper();
    private final ClusterDeviationRule rule = new ClusterDeviationRule(null, null, PROPERTIES, json);

    @Test
    void onlyCheckedDirectionsAboveThresholdBecomeCandidates() {
        ClusterProfileResponse profile = profile(1000, 0.58, 6, List.of(
                range("spendingToIncomeRatio", 1.05, 0.70, 0.94, 0.30, "ABOVE"),   // 후보 (MEDIUM)
                range("savingRate", 0.02, 0.05, 0.20, 0.20, "BELOW"),               // 임계 미달 → 제외
                range("discretionaryShare", 0.44, 0.12, 0.31, 0.68, "ABOVE"),      // 후보 (MEDIUM)
                range("fixedExpenseShare", 0.10, 0.20, 0.45, 0.40, "BELOW"),       // 방향 불일치(낮은 고정비는 문제 아님)
                range("spendingVolatility", 0.90, 0.05, 0.30, 1.20, "ABOVE")));    // 후보 (HIGH)
        Result result = ClusterDeviationRule.judge(7L, profile, 6, ANALYZED_AT, AS_OF, PROPERTIES);
        assertThat(result.available()).isTrue();
        assertThat(result.coverageStatus()).isEqualTo("EVALUATED");
        assertThat(result.items()).extracting(item -> item.feature().key())
                .containsExactly("spendingToIncomeRatio", "discretionaryShare", "spendingVolatility");
        assertThat(result.items()).extracting(item -> item.severity())
                .containsExactly(IssueSeverity.MEDIUM, IssueSeverity.MEDIUM, IssueSeverity.HIGH);
        assertThat(result.window()).isEqualTo("2026-03~2026-08");
        assertThat(result.modelVersion()).isEqualTo("spending-kmeans-v2");
    }

    @Test
    void savingRateBelowRangeIsAProblemButAboveIsNot() {
        ClusterProfileResponse low = profile(1000, 0.58, 6, List.of(range("savingRate", 0.01, 0.10, 0.30, 0.45, "BELOW")));
        ClusterProfileResponse high = profile(1000, 0.58, 6, List.of(range("savingRate", 0.60, 0.10, 0.30, 1.50, "ABOVE")));
        assertThat(ClusterDeviationRule.judge(1L, low, 6, ANALYZED_AT, AS_OF, PROPERTIES).items()).hasSize(1);
        assertThat(ClusterDeviationRule.judge(1L, high, 6, ANALYZED_AT, AS_OF, PROPERTIES).items()).isEmpty();
    }

    @Test
    void thresholdAndSeverityBoundariesAreInclusive() {
        ClusterProfileResponse profile = profile(1000, 0.58, 6, List.of(
                range("discretionaryShare", 0.40, 0.12, 0.31, 0.25, "ABOVE"),   // 정확히 임계 → 포함, MEDIUM
                range("spendingVolatility", 0.90, 0.05, 0.30, 1.00, "ABOVE"),   // 정확히 1.0 → HIGH
                range("fixedExpenseShare", 0.50, 0.20, 0.45, 0.2499, "ABOVE"))); // 임계 미달
        Result result = ClusterDeviationRule.judge(1L, profile, 6, ANALYZED_AT, AS_OF, PROPERTIES);
        assertThat(result.items()).extracting(item -> item.feature().key()).containsExactly("discretionaryShare", "spendingVolatility");
        assertThat(result.items().get(0).severity()).isEqualTo(IssueSeverity.MEDIUM);
        assertThat(result.items().get(1).severity()).isEqualTo(IssueSeverity.HIGH);
    }

    @Test
    void exposureConditionsAreReportedAsCoverageReasonsNotAsZeroIssues() {
        ClusterProfileResponse ok = profile(1000, 0.58, 6, List.of(range("discretionaryShare", 0.44, 0.12, 0.31, 0.68, "ABOVE")));
        assertThat(ClusterDeviationRule.judge(1L, ok, 6, AS_OF.minusDays(31).atStartOfDay(), AS_OF, PROPERTIES).coverageReason())
                .isEqualTo("STALE_PATTERN_ANALYSIS");
        assertThat(ClusterDeviationRule.judge(1L, ok, 6, AS_OF.minusDays(30).atStartOfDay(), AS_OF, PROPERTIES).available()).isTrue();
        assertThat(ClusterDeviationRule.judge(1L, profile(9, 0.58, 6, ok.features()), 6, ANALYZED_AT, AS_OF, PROPERTIES).coverageReason())
                .isEqualTo("SMALL_CLUSTER_SAMPLE");
        assertThat(ClusterDeviationRule.judge(1L, profile(1000, 0.19, 6, ok.features()), 6, ANALYZED_AT, AS_OF, PROPERTIES).coverageReason())
                .isEqualTo("LOW_CONFIDENCE");
        assertThat(ClusterDeviationRule.judge(1L, ok, 2, ANALYZED_AT, AS_OF, PROPERTIES).coverageReason())
                .isEqualTo("USER_DATA_TOO_SHORT");
        Result unavailable = ClusterDeviationRule.judge(1L, ok, 2, ANALYZED_AT, AS_OF, PROPERTIES);
        assertThat(unavailable.available()).isFalse();
        assertThat(unavailable.coverageStatus()).isEqualTo("UNAVAILABLE");
        assertThat(unavailable.items()).isEmpty();
    }

    @Test
    void discretionaryDeviationMergesOnlyIntoDiscretionaryCategoryIncreases() {
        Diagnosis diagnosis = Diagnosis.start(1L, null, ANALYZED_AT);
        DiagnosisIssue food = ruleIssue("SPENDING", "CATEGORY_SPENDING_INCREASE", "FOOD");
        DiagnosisIssue grocery = ruleIssue("SPENDING", "CATEGORY_SPENDING_INCREASE", "GROCERY");
        DiagnosisIssue loan = ruleIssue("LOAN", "HIGH_INTEREST_RATE", null);
        List<DiagnosisIssue> issues = new ArrayList<>(List.of(food, grocery, loan));

        Result result = ClusterDeviationRule.judge(7L, profile(1000, 0.58, 6, List.of(
                range("discretionaryShare", 0.44, 0.12, 0.31, 0.68, "ABOVE"))), 6, ANALYZED_AT, AS_OF, PROPERTIES);
        int created = rule.apply(diagnosis, result, issues);

        assertThat(created).isZero();
        assertThat(issues).hasSize(3);
        assertThat(food.getDetectionSource()).isEqualTo(DetectionSource.RULE_AND_CLUSTER);
        assertThat(food.getModelVersion()).isEqualTo("spending-kmeans-v2");
        assertThat(food.getClusterContext().get("feature").asText()).isEqualTo("discretionaryShare");
        assertThat(food.getClusterContext().get("clusterLabel").asText()).isEqualTo("소비 집중형");
        assertThat(food.getClusterContext().get("window").asText()).isEqualTo("2026-03~2026-08");
        assertThat(food.getClusterContext().get("analyzedAt").asText()).isEqualTo("2026-09-18T09:12");
        assertThat(food.getSeverity()).isEqualTo(IssueSeverity.LOW); // 규칙 심각도는 그대로
        assertThat(grocery.getDetectionSource()).isEqualTo(DetectionSource.RULE);
        assertThat(loan.getDetectionSource()).isEqualTo(DetectionSource.RULE);
    }

    @Test
    void withoutMergeTargetAStandaloneClusterIssueIsCreated() {
        Diagnosis diagnosis = Diagnosis.start(1L, null, ANALYZED_AT);
        List<DiagnosisIssue> issues = new ArrayList<>(List.of(ruleIssue("SPENDING", "CATEGORY_SPENDING_INCREASE", "GROCERY")));
        Result result = ClusterDeviationRule.judge(7L, profile(1000, 0.58, 6, List.of(
                range("discretionaryShare", 0.44, 0.12, 0.31, 0.68, "ABOVE"),
                range("spendingVolatility", 0.90, 0.05, 0.30, 1.20, "ABOVE"))), 6, ANALYZED_AT, AS_OF, PROPERTIES);
        int created = rule.apply(diagnosis, result, issues);

        assertThat(created).isEqualTo(2);
        assertThat(issues).hasSize(3);
        DiagnosisIssue discretionary = issues.get(1);
        assertThat(discretionary.getDetectionSource()).isEqualTo(DetectionSource.CLUSTER);
        assertThat(discretionary.getDomain()).isEqualTo("SPENDING");
        assertThat(discretionary.getType()).isEqualTo("CLUSTER_DISCRETIONARY_HIGH");
        assertThat(discretionary.getSeverity()).isEqualTo(IssueSeverity.MEDIUM);
        assertThat(discretionary.getTitle()).isEqualTo("비슷한 소비 유형과 견줘 점검할 항목이 있어요");
        assertThat(discretionary.getDescription()).isEqualTo("재량소비 비중이 44%로 비슷한 소비 유형(소비 집중형, 1,000명)의 기준 범위 12~31%보다 높아요.");
        assertThat(discretionary.getEvidence().get("ruleKey").asText()).isEqualTo("CLUSTER_discretionaryShare");
        assertThat(discretionary.getEvidence().get("targetId").asLong()).isEqualTo(7L);
        assertThat(discretionary.getBenchmarkValue().get("deviationThreshold").asDouble()).isEqualTo(0.25);
        DiagnosisIssue volatility = issues.get(2);
        assertThat(volatility.getDomain()).isEqualTo("CASHFLOW");
        assertThat(volatility.getType()).isEqualTo("CLUSTER_VOLATILITY_HIGH");
        assertThat(volatility.getSeverity()).isEqualTo(IssueSeverity.HIGH);
        assertThat(volatility.getDescription()).isEqualTo("소비 변동성이 0.90로 비슷한 소비 유형(소비 집중형, 1,000명)의 기준 범위 0.05~0.30보다 높아요.");
        assertThat(volatility.getClusterContext().get("deviationScore").asDouble()).isEqualTo(0.23);
    }

    @Test
    void clusterEvidenceCannotBeAttachedTwice() {
        DiagnosisIssue issue = ruleIssue("SPENDING", "CATEGORY_SPENDING_INCREASE", "FOOD");
        issue.attachClusterEvidence("spending-kmeans-v2", json.createObjectNode());
        assertThatThrownBy(() -> issue.attachClusterEvidence("spending-kmeans-v2", json.createObjectNode()))
                .isInstanceOf(IllegalStateException.class);
        assertThat(ClusterDeviationFeature.of("unknownFeature")).isNull();
    }

    private DiagnosisIssue ruleIssue(String domain, String type, String categoryCode) {
        var evidence = json.createObjectNode().put("ruleKey", type);
        if (categoryCode != null) {
            evidence.put("categoryCode", categoryCode);
        }
        return DiagnosisIssue.ofRule(1L, domain, type, IssueSeverity.LOW, "제목", "설명", null, null, evidence, ANALYZED_AT);
    }

    private static ClusterProfileResponse profile(int sampleSize, double confidence, int months, List<FeatureRange> features) {
        return new ClusterProfileResponse(1, "소비 집중형", "spending-kmeans-v2", sampleSize, 4000, confidence, months,
                "2026-03", "2026-08", 0.23, features, "ARTIFACT", "python-sklearn-1.9.0", null, null);
    }

    private static FeatureRange range(String feature, double userValue, double p5, double p95, double deviation, String direction) {
        String name = switch (feature) {
            case "spendingToIncomeRatio" -> "소득 대비 소비율";
            case "savingRate" -> "저축률";
            case "fixedExpenseShare" -> "고정지출 비중";
            case "discretionaryShare" -> "재량소비 비중";
            default -> "소비 변동성";
        };
        double mean = (p5 + p95) / 2;
        return new FeatureRange(feature, name, userValue, mean, p5, p95, p5, p95, mean, (p95 - p5) / 4, deviation, direction, 92.0, "TOP_10");
    }
}
