package com.ssafy.dodam.domain.recommendation.service.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.diagnosis.entity.DetectionSource;
import com.ssafy.dodam.domain.recommendation.dto.CandidateGenerationResult.SkipReason;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationCandidate;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationInput;
import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import com.ssafy.dodam.domain.recommendation.service.RecommendationCandidateGenerator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Set;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 단독 군집 이탈 문제({@code DetectionSource.CLUSTER}, 641 {@code ClusterDeviationRule}) → {@code <문제 domain>/REVIEW_SPENDING_PATTERN}
 * "비슷한 유형과 소비 구조 비교해 보기"(S15P21D105-640 C-2, 기획안 5-2·5-7).
 *
 * <p>군집 이탈은 낭비 판정이 아니라 "비슷한 소비 유형 사용자군과 견줘 점검할 항목"이다. 그래서 <b>효과 금액을 추정하지 않는다</b>
 * ({@link Recommendation.Effect#NONE}) — 얼마를 줄여야 하는지는 군집 범위가 정해 주지 않고, 절감액을 만들어 내면 서버가 하지 않은
 * 판정이 된다. 발견 요약 합산에는 0으로 들어가며 FE는 "절감액 산정 전"으로 표시한다.
 *
 * <p>규칙 문제에 근거만 병합된 {@code RULE_AND_CLUSTER} 문제는 여기 오지 않는다 — 기존 생성기가 규칙 값으로 추천을 만들고, 군집 근거는
 * 우선순위 α({@code RecommendationPriorityPolicy})와 코치 설명에서만 쓰인다.
 *
 * <p>근거 {@code CLUSTER_RANGE_DEVIATION}의 evidence에 {@code feature·userValue·p5·p95·deviation·direction·clusterLabel·sampleSize·modelVersion}을
 * 남긴다. 시뮬레이션 프리셋은 {@code feature}로 고른다(소득 대비 소비율·재량 소비 비중만 소비 감소 시뮬레이션, 금액은 사용자 입력).
 */
@Component
@Order(60)
public class ClusterDeviationGenerator implements RecommendationCandidateGenerator {
    public static final String VERSION = "640.1";
    public static final String TYPE = "REVIEW_SPENDING_PATTERN";
    public static final String REASON_DEVIATION = "CLUSTER_RANGE_DEVIATION";
    public static final String FEATURE_FIELD = "feature";
    /** 소비 감소 시뮬레이션으로 이어질 수 있는 특징. 나머지(저축률·고정지출·변동성)는 프리셋이 없다. */
    public static final Set<String> SPENDING_SIMULATION_FEATURES = Set.of("spendingToIncomeRatio", "discretionaryShare");

    private final ObjectMapper json;

    public ClusterDeviationGenerator(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public boolean supports(RecommendationInput.Issue issue) {
        return issue.detectionSource() == DetectionSource.CLUSTER && issue.type() != null && issue.type().startsWith("CLUSTER_");
    }

    @Override
    public Outcome generate(RecommendationInput input, RecommendationInput.Issue issue) {
        JsonNode context = issue.clusterContext();
        if (context == null || context.isNull()) {
            return Outcome.skipped(SkipReason.NO_CLUSTER_CONTEXT);
        }
        String feature = GeneratorSupport.textOf(context, FEATURE_FIELD);
        String displayName = GeneratorSupport.textOf(context, "displayName");
        String clusterLabel = GeneratorSupport.textOf(context, "clusterLabel");
        Double userValue = doubleOf(context, "userValue");
        Double p5 = doubleOf(context, "p5");
        Double p95 = doubleOf(context, "p95");
        Double deviation = doubleOf(context, "deviation");
        Long sampleSize = GeneratorSupport.longOf(context, "sampleSize");
        if (feature == null || displayName == null || clusterLabel == null || userValue == null || p5 == null || p95 == null
                || deviation == null || sampleSize == null) {
            return Outcome.skipped(SkipReason.INSUFFICIENT_DATA);
        }
        boolean percent = !"spendingVolatility".equals(feature);
        String direction = GeneratorSupport.textOf(context, "direction");
        String comparison = "BELOW".equals(direction) ? "보다 낮아요" : "보다 높아요";

        ObjectNode evidence = json.createObjectNode();
        evidence.put(FEATURE_FIELD, feature).put("displayName", displayName).put("userValue", userValue).put("p5", p5).put("p95", p95)
                .put("deviation", deviation).put("direction", direction).put("clusterLabel", clusterLabel).put("sampleSize", sampleSize)
                .put("modelVersion", issue.modelVersion()).put("window", GeneratorSupport.textOf(context, "window"))
                .put("patternAnalysisId", GeneratorSupport.longOf(issue.evidence(), "patternAnalysisId"));
        String reason = displayName + " " + format(userValue, percent) + "이 비슷한 소비 유형(" + clusterLabel + ", "
                + String.format("%,d", sampleSize) + "명)의 기준 범위 " + format(p5, percent) + "~" + format(p95, percent) + comparison + ".";
        String description = SPENDING_SIMULATION_FEATURES.contains(feature)
                ? "비슷한 유형과 소비 구조를 비교해 보고, 줄일 수 있는 금액을 정하면 목표 영향을 시뮬레이션할 수 있어요."
                : "비슷한 유형과 " + displayName + "을 비교해 점검할 항목이 있어요. 낭비로 단정하지 않아요.";
        return Outcome.of(new RecommendationCandidate(VERSION, TYPE + ":" + feature, issue.issueId(), issue.domain(), TYPE,
                "비슷한 유형과 소비 구조 비교해 보기", description,
                List.of(new RecommendationCandidate.Reason(REASON_DEVIATION, reason, evidence)), Recommendation.Effect.NONE));
    }

    private static Double doubleOf(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || !value.isNumber() ? null : value.doubleValue();
    }

    private static String format(double value, boolean percent) {
        if (percent) {
            return BigDecimal.valueOf(value * 100).setScale(0, RoundingMode.HALF_UP).toPlainString() + "%";
        }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
