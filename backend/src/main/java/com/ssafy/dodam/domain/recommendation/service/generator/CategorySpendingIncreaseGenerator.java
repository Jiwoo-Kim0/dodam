package com.ssafy.dodam.domain.recommendation.service.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.recommendation.dto.CandidateGenerationResult.SkipReason;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationCandidate;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationInput;
import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import com.ssafy.dodam.domain.recommendation.service.RecommendationCandidateGenerator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * SPENDING/CATEGORY_SPENDING_INCREASE → SPENDING/REDUCE_CATEGORY_SPENDING(S15P21D105-627, 기준 변경 631). 늘어난 카테고리 소비를
 * 기준 기간 평균 수준으로 되돌리라는 권고다.
 *
 * <p>입력은 진단이 저장한 {@code currentValue}(categoryId·categoryCode·categoryName·monthlyAverage·increaseAmount·recentPeriod)와
 * {@code benchmarkValue.previousMonthlyAverage·baselinePeriod}다. {@code monthlyCostSaving = increaseAmount}(최근 기간 월평균 − 기준 기간
 * 월평균). 631부터 최근 기간은 지난달 1개월이므로 이 값은 "지난달이 직전 3개월 평균보다 더 쓴 금액"이다.
 *
 * <p>{@code annualCostSaving = monthly × 12}는 유지한다. 발견 화면 정렬(604)과 추천 요약 {@code discovery.expectedAnnualCostSaving}이
 * 이 값에 의존하기 때문이다. 다만 단월 급증에 ×12를 적용하는 것은 "그 증가가 1년 지속된다"는 가정이라, 근거 evidence에
 * {@code annualizationBasis = SINGLE_MONTH_INCREASE_X12}와 기간 길이를 남겨 FE가 "지속 시 연간" 표기를 붙일 수 있게 한다. 627 최초 구현
 * (버전 {@code 627.2} 이하)은 3개월 평균 증가에 ×12를 적용했고 evidence에 이 필드가 없다.
 *
 * <p>증가액이 0 이하면 후보를 만들지 않는다(진단 임계값상 나오지 않지만 방어한다). 후보 키는 카테고리 id라 같은 진단에서 멱등이다.
 * 근거 evidence의 {@code categoryCode}는 시뮬레이션 프리셋이 카테고리를 지정한 소비 감소 요청을 만들 때 읽는다.
 *
 * <p><b>목표 기여(696)</b>: 기획 원안의 발견 상세 문구("이 소비가 계속되면 '비상금' 목표가 약 N일 늦어질 수 있어요")를 만들 수 있도록
 * 늘어난 금액을 모으기 목표 저축에 돌렸을 때의 예상 달성일 변화를 계산한다. 진단 스냅샷 {@code goals[].forecast}(546)의 남은 금액·현재
 * 저축속도만 쓰고 현재 잔액으로 재계산하지 않는다({@link SavingGoalRiskGenerator}와 같은 식·같은 기준일). 대상 목표는
 * {@link GeneratorSupport#savingGoalForForecast}가 고른 하나이며 {@code Recommendation.goalId}에 저장돼 {@code goal-impact}가
 * {@code NO_GOAL}/{@code NOT_CALCULATED} 대신 날짜를 돌려준다. {@code currentForecastDate}는 현재 속도, {@code improvedForecastDate}는
 * 현재 속도 + 증가액, {@code advancedDays = current − improved}(개선일이 목표일 이내일 때만, 현재 속도가 0이면 null),
 * {@code contributionAmount = 증가액}.
 * 근거 {@code GOAL_FORECAST_ADVANCE}에 계산 입력을 남긴다. 스냅샷에 조건을 만족하는 목표가 없으면 날짜 효과는 비고 근거도 없다(627.3과 같음).
 */
@Component
@Order(40)
public class CategorySpendingIncreaseGenerator implements RecommendationCandidateGenerator {
    /**
     * 627.2 → 627.3: 진단 기준이 지난달 vs 직전 3개월 평균(631)으로 바뀌어 문구·evidence가 달라졌다.
     * 627.3 → 696.1: 모으기 목표 예상 달성일 변화(advancedDays)·대상 goalId·근거 GOAL_FORECAST_ADVANCE 추가.
     * 696.1 → 702.1: 개선 후에도 목표일을 넘으면 과도한 단축 일수를 노출하지 않는다.
     */
    public static final String VERSION = "702.1";
    public static final String DOMAIN = "SPENDING";
    public static final String TYPE = "REDUCE_CATEGORY_SPENDING";
    public static final String REASON_INCREASE = "CATEGORY_SPENDING_INCREASE";
    public static final String REASON_GOAL_FORECAST_ADVANCE = "GOAL_FORECAST_ADVANCE";
    public static final String CATEGORY_CODE_FIELD = "categoryCode";
    public static final String ANNUALIZATION_BASIS_FIELD = "annualizationBasis";
    public static final String SINGLE_MONTH_INCREASE_X12 = "SINGLE_MONTH_INCREASE_X12";
    public static final String RECENT_AVERAGE_INCREASE_X12 = "RECENT_AVERAGE_INCREASE_X12";
    public static final String GOAL_IMPACT_STATUS_FIELD = "goalImpactStatus";
    public static final String TARGET_DATE_MET = "TARGET_DATE_MET";
    public static final String TARGET_DATE_NOT_MET = "TARGET_DATE_NOT_MET";
    static final int MONTHS_PER_YEAR = 12;
    private final ObjectMapper json;

    public CategorySpendingIncreaseGenerator(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public boolean supports(RecommendationInput.Issue issue) {
        return "SPENDING".equals(issue.domain()) && "CATEGORY_SPENDING_INCREASE".equals(issue.type());
    }

    @Override
    public Outcome generate(RecommendationInput input, RecommendationInput.Issue issue) {
        JsonNode current = issue.currentValue();
        Long categoryId = GeneratorSupport.longOf(current, "categoryId");
        String categoryCode = GeneratorSupport.textOf(current, CATEGORY_CODE_FIELD);
        Long monthlyAverage = GeneratorSupport.longOf(current, "monthlyAverage");
        Long increase = GeneratorSupport.longOf(current, "increaseAmount");
        Long previousAverage = GeneratorSupport.longOf(issue.benchmarkValue(), "previousMonthlyAverage");
        if (categoryId == null || categoryCode == null || monthlyAverage == null || increase == null || previousAverage == null) {
            return Outcome.skipped(SkipReason.INSUFFICIENT_DATA);
        }
        if (increase <= 0) {
            return Outcome.of();
        }
        String categoryName = GeneratorSupport.textOf(current, "categoryName");
        String label = categoryName == null ? categoryCode : categoryName;
        int recentMonths = periodMonths(current.get("recentPeriod"), 1);
        int baselineMonths = periodMonths(issue.benchmarkValue().get("baselinePeriod"), 3);

        ObjectNode evidence = json.createObjectNode();
        evidence.put("categoryId", categoryId).put(CATEGORY_CODE_FIELD, categoryCode).put("monthlyAverage", monthlyAverage)
                .put("previousMonthlyAverage", previousAverage).put("increaseAmount", increase)
                .put("recentMonths", recentMonths).put("baselineMonths", baselineMonths)
                .put(ANNUALIZATION_BASIS_FIELD, recentMonths == 1 ? SINGLE_MONTH_INCREASE_X12 : RECENT_AVERAGE_INCREASE_X12);
        JsonNode rate = current.get("increaseRatePercent");
        if (rate != null && rate.isNumber()) {
            evidence.put("increaseRatePercent", rate.doubleValue());
        }
        JsonNode consecutive = current.get("consecutiveMonths");
        if (consecutive != null && consecutive.isNumber()) {
            evidence.put("consecutiveMonths", consecutive.intValue());
        }
        String recentLabel = recentMonths == 1 ? "지난달" : "최근 " + recentMonths + "개월 월평균";
        String baselineLabel = "직전 " + baselineMonths + "개월 월평균";
        List<RecommendationCandidate.Reason> reasons = new ArrayList<>();
        reasons.add(new RecommendationCandidate.Reason(REASON_INCREASE,
                recentLabel + " " + label + " 소비 " + GeneratorSupport.won(monthlyAverage) + "이 " + baselineLabel + " "
                        + GeneratorSupport.won(previousAverage) + "보다 " + GeneratorSupport.won(increase) + " 많아요.", evidence));

        Optional<GeneratorSupport.SnapshotSavingGoal> goal = GeneratorSupport.savingGoalForForecast(input);
        Recommendation.Effect effect;
        Long goalId = null;
        if (goal.isEmpty()) {
            effect = new Recommendation.Effect(increase, increase * MONTHS_PER_YEAR, null, null, null, null, null, null);
        } else {
            GeneratorSupport.SnapshotSavingGoal target = goal.get();
            goalId = target.goalId();
            LocalDate base = GeneratorSupport.baseDate(input);
            LocalDate currentDate = GeneratorSupport.expectedDate(target.remaining(), target.monthlyPace(), base);
            LocalDate improvedDate = GeneratorSupport.expectedDate(target.remaining(), target.monthlyPace() + increase, base);
            Integer advanced = GeneratorSupport.achievableAdvancedDays(currentDate, improvedDate, target.targetDate());
            boolean targetDateNotMet = target.targetDate() != null && improvedDate.isAfter(target.targetDate());
            String goalLabel = target.name() == null ? "목표" : "'" + target.name() + "'";
            ObjectNode forecastEvidence = json.createObjectNode();
            forecastEvidence.put("goalId", target.goalId()).put("goalName", target.name()).put("baseDate", base.toString())
                    .put("remainingAmount", target.remaining()).put("averageMonthlySaving", target.monthlyPace())
                    .put("improvedMonthlySaving", target.monthlyPace() + increase)
                    .put("currentForecastDate", currentDate == null ? null : currentDate.toString())
                    .put("improvedForecastDate", improvedDate.toString());
            if (target.targetDate() != null) {
                forecastEvidence.put("targetDate", target.targetDate().toString())
                        .put(GOAL_IMPACT_STATUS_FIELD, targetDateNotMet ? TARGET_DATE_NOT_MET : TARGET_DATE_MET);
            }
            if (advanced != null) {
                forecastEvidence.put("advancedDays", advanced);
            }
            reasons.add(new RecommendationCandidate.Reason(REASON_GOAL_FORECAST_ADVANCE,
                    targetDateNotMet
                            ? "이 금액을 " + goalLabel + " 저축에 더하면 예상 달성일은 " + improvedDate + "이지만 목표일 "
                                    + target.targetDate() + "보다 늦어요."
                            : currentDate == null
                            ? "지금 저축 속도로는 " + goalLabel + " 달성일을 계산할 수 없어요. 이 금액을 저축에 더하면 " + improvedDate
                                    + "에 달성할 수 있어요."
                            : "이 금액을 " + goalLabel + " 저축에 더하면 예상 달성일이 " + currentDate + "에서 " + improvedDate + "로 "
                                    + advanced + "일 앞당겨져요.",
                    forecastEvidence));
            effect = new Recommendation.Effect(increase, increase * MONTHS_PER_YEAR, null, null, currentDate, improvedDate, advanced,
                    increase);
        }
        return Outcome.of(new RecommendationCandidate(VERSION, TYPE + ":" + categoryId, issue.issueId(), DOMAIN, TYPE,
                label + " 지출 줄여보기",
                baselineLabel + " 수준으로 되돌리면 월 " + GeneratorSupport.won(increase) + "을 아낄 수 있어요.",
                reasons, effect, goalId));
    }

    /** 진단이 저장한 {@code recentPeriod}/{@code baselinePeriod}의 개월 수. 627 최초 구현 이전 형식이거나 없으면 기본값. */
    private static int periodMonths(JsonNode period, int fallback) {
        if (period == null || !period.isObject()) {
            return fallback;
        }
        JsonNode months = period.get("months");
        return months != null && months.isInt() && months.intValue() > 0 ? months.intValue() : fallback;
    }
}
