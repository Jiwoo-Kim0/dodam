package com.ssafy.dodam.domain.simulation.service.calculator;

import com.ssafy.dodam.domain.simulation.dto.CombinedInput;
import com.ssafy.dodam.domain.simulation.dto.SavingChangeInput;
import com.ssafy.dodam.domain.simulation.dto.ScenarioComputation;
import com.ssafy.dodam.domain.simulation.dto.ScenarioLedger;
import com.ssafy.dodam.domain.simulation.dto.SimulationBaseline;
import com.ssafy.dodam.domain.simulation.dto.SimulationInput;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Cashflow;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Comparison;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.CurrentMonth;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.GoalForecast;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.GoalImpact;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.GoalImpactDetail;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Item;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Month;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Snapshot;
import com.ssafy.dodam.domain.simulation.dto.SpendingReductionInput;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import com.ssafy.dodam.domain.simulation.service.SimulationAssumptions;
import com.ssafy.dodam.domain.simulation.service.SimulationCalculator;
import com.ssafy.dodam.domain.simulation.service.SimulationCalculatorRegistry;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * 복합(POST /simulations/combined). 자식을 같은 기준선에서 독립적으로 계산한 뒤 합성한다(586 §4).
 *
 * <ul>
 *   <li>현금흐름: 자식의 월 delta와 currentMonth.delta를 월별로 합한 뒤 누적을 처음부터 다시 계산한다. 표시 상한 120개월은
 *       자식과 같고, 자식의 전체 비용·종료일·truncated는 details.children에 보존한다.</li>
 *   <li>금융비용: 공통 평가기간 [기준일, min(자식 계산 종료일 최댓값, 기준일+120개월이 속한 달 말일)]에 납부일이 포함된
 *       이자+수수료를 자식별 전후로 합산한다({@code FINANCE_COST_EVALUATED}). 원금·상품 이자 수익은 넣지 않는다. 미완납 자식의
 *       이후 비용은 0으로 채우지 않고 {@code costComplete=false}로 표시한다.</li>
 *   <li>비교표: 단위·기간·의미가 같은 항목만 집계한다 — 기준일 최초 현금·부채·순자산 변화와 평가기간 비용. 유형별 나머지는
 *       details.children에 있다.</li>
 *   <li>목표: 소비·저축 자식만이면 {@link CombinedGoalProjection}, 아니면 {@code COMBINED_GOAL_EFFECT_NOT_COMPUTABLE}. 부모의
 *       after.goals·목표 비교 항목·goalImpacts는 모두 이 한 결과에서 조립하며 자식의 단독 날짜를 섞지 않는다.</li>
 * </ul>
 * 자식의 검증 오류(감소액 초과, 옵션 없음 등)는 그대로 전파되어 부모·자식 어느 행도 저장되지 않는다.
 */
@Component
public class CombinedCalculator implements SimulationCalculator<CombinedInput> {
    public static final String CALCULATION_POLICY = "INDEPENDENT_CHILDREN_NET_CASH_SUM";
    public static final String COST_BASIS_PAYMENT_DATE = "PAYMENT_DATE";
    public static final String GOAL_POLICY_DURATION_LIMITED = "DURATION_LIMITED_PROJECTION";
    public static final String GOAL_POLICY_NOT_COMPUTABLE = "NOT_COMPUTABLE";
    public static final String COST_REASON_HORIZON_LIMIT = "CHILD_COST_UNKNOWN_AFTER_COVERED_DATE";
    private static final int TITLE_MAX = 255;
    private static final int SUMMARY_MAX = 500;
    private static final String INITIAL_DEBT_KEY = "INITIAL_DEBT_CHANGE";
    private static final String FINANCE_COST_KEY = "FINANCE_COST_EVALUATED";

    private final SimulationCalculatorRegistry calculators;

    public CombinedCalculator(SimulationCalculatorRegistry calculators) {
        this.calculators = calculators;
    }

    @Override
    public SimulationType type() {
        return SimulationType.COMBINED;
    }

    @Override
    public Class<CombinedInput> inputType() {
        return CombinedInput.class;
    }

    @Override
    public ScenarioComputation compute(SimulationBaseline baseline, CombinedInput input) {
        // 기존 자식 계산에서 발생한 범위 초과도 복합 실행의 동일한 금액 오류로 변환한다.
        return SimulationScenarios.exact(() -> {
            List<ScenarioComputation> children = new ArrayList<>(input.children().size());
            for (SimulationInput child : input.children()) {
                children.add(calculators.compute(baseline, child));
            }
            requireTotalReductionWithinSpending(baseline, input);
            return build(baseline, input, children);
        });
    }

    /** 카테고리별 상한은 자식 계산기가 봤다. 서로 다른 카테고리의 합이 전체 평균 지출을 넘는 조합도 만들지 않는다. */
    private static void requireTotalReductionWithinSpending(SimulationBaseline baseline, CombinedInput input) {
        long total = 0;
        for (SimulationInput child : input.children()) {
            if (child instanceof SpendingReductionInput spending) {
                total = Math.addExact(total, spending.monthlyReduction());
            }
        }
        if (total > baseline.averageMonthlySpending()) {
            throw new BusinessException(ErrorCode.SIMULATION_REDUCTION_EXCEEDS_SPENDING);
        }
    }

    private ScenarioComputation build(SimulationBaseline baseline, CombinedInput input, List<ScenarioComputation> children) {
        LocalDate base = baseline.baseDate();
        Cashflow cashflow = composeCashflow(base, children);
        CostEvaluation cost = evaluateCost(base, input, children);
        boolean projectionIncomplete = children.stream().anyMatch(child -> child.ledger().fullTermEnd() == null);
        LocalDate fullTermEnd = projectionIncomplete ? null
                : children.stream().map(child -> child.ledger().fullTermEnd()).max(LocalDate::compareTo).orElse(base);
        LocalDate evaluatedThrough = children.stream().map(child -> child.ledger().costCoveredThrough()).max(LocalDate::compareTo).orElse(base);
        // 리볼빙 미완납이 있으면 종료일을 모른다(null). 표시기간 잘림(truncated)과 구분하기 위해 projectionIncomplete를 따로 둔다
        cashflow = new Cashflow(cashflow.basis(), cashflow.before(), cashflow.after(), cashflow.months(), cashflow.currentMonth(),
                cashflow.truncated(), cashflow.coveredThrough(), fullTermEnd);

        GoalOutcome goals = projectGoals(baseline, input, children);
        Snapshot before = SimulationScenarios.baselineSnapshot(baseline);

        long initialCash = 0;
        long initialNetAsset = 0;
        long initialDebt = 0;
        for (ScenarioComputation child : children) {
            initialCash = Math.addExact(initialCash, sectionAfter(child.result().comparison().assets(), "INITIAL_CASH_CHANGE"));
            initialNetAsset = Math.addExact(initialNetAsset, sectionAfter(child.result().comparison().assets(), "INITIAL_NET_ASSET_CHANGE"));
            initialDebt = Math.addExact(initialDebt, sectionAfter(child.result().comparison().debts(), INITIAL_DEBT_KEY));
        }
        List<Item> items = new ArrayList<>();
        items.add(Item.amount(FINANCE_COST_KEY, "평가기간 금융비용(이자·수수료)", cost.before(), cost.after()));
        items.add(Item.amount("INITIAL_CASH_CHANGE", "기준일 현금 변화 합계", 0L, initialCash));
        items.add(Item.amount(INITIAL_DEBT_KEY, "기준일 부채 변화 합계", 0L, initialDebt));
        items.add(Item.amount("INITIAL_NET_ASSET_CHANGE", "기준일 순자산 변화 합계", 0L, initialNetAsset));
        items.addAll(SimulationScenarios.goalItems(before, goals.after()));
        List<Item> assets = List.of(
                Item.amount("INITIAL_CASH_CHANGE", "기준일 현금 변화 합계", 0L, initialCash),
                Item.amount("INITIAL_NET_ASSET_CHANGE", "기준일 순자산 변화 합계", 0L, initialNetAsset));
        List<Item> debts = List.of(Item.amount(INITIAL_DEBT_KEY, "기준일 부채 변화 합계", 0L, initialDebt));
        List<Item> costs = List.of(Item.amount(FINANCE_COST_KEY, "평가기간 금융비용(이자·수수료)", cost.before(), cost.after()));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("calculationPolicy", CALCULATION_POLICY);
        details.put("calculationAsOfDate", base);
        details.put("scenarioCount", children.size());
        details.put("evaluationFromDate", base);
        details.put("evaluationThroughDate", cost.through());
        details.put("costBasis", COST_BASIS_PAYMENT_DATE);
        details.put("costComplete", cost.complete());
        details.put("projectionIncomplete", projectionIncomplete);
        details.put("cashflowEvaluatedThrough", evaluatedThrough);
        details.put("goalEffectPolicy", goals.policy());
        details.put("financeCost", cost.toMap());
        details.put("children", childDetails(input, children));
        List<Map<String, Object>> timing = new ArrayList<>();
        timing.add(LoanScenarios.timing("INITIAL_CASH_CHANGE", base, LoanScenarios.BASIS_INITIAL_STATE));
        timing.add(LoanScenarios.timing(INITIAL_DEBT_KEY, base, LoanScenarios.BASIS_INITIAL_STATE));
        timing.add(LoanScenarios.timing("INITIAL_NET_ASSET_CHANGE", base, LoanScenarios.BASIS_INITIAL_STATE));
        timing.add(LoanScenarios.periodTiming(FINANCE_COST_KEY, base, cost.through()));
        details.put("comparisonTiming", timing);

        List<String> assumptions = new ArrayList<>();
        assumptions.add(SimulationAssumptions.CHILD_SCENARIOS_INDEPENDENT);
        assumptions.add(SimulationAssumptions.FINANCE_COST_EVALUATED);
        if (!cost.complete()) {
            assumptions.add(SimulationAssumptions.FINANCE_COST_PARTIAL);
        }
        if (ScenarioCashflow.isTruncated(cashflow)) {
            assumptions.add(SimulationAssumptions.CASHFLOW_TABLE_TRUNCATED);
        }
        assumptions.addAll(goals.assumptions());
        // 자식 가정은 입력 순서와 무관하게 표시한다 — 순서를 바꾼 요청도 같은 가정 집합을 낸다
        Set<String> childAssumptions = new TreeSet<>();
        for (ScenarioComputation child : children) {
            childAssumptions.addAll(child.result().assumptions());
        }
        childAssumptions.removeAll(assumptions);
        assumptions.addAll(childAssumptions);

        SimulationResult result = new SimulationResult(title(children), summary(children, cost, goals, before), before, goals.after(),
                new Comparison(items, assets, debts, costs), cashflow, goals.impacts(), assumptions, details, goals.details());
        List<ScenarioLedger.Payment> beforePayments = new ArrayList<>();
        List<ScenarioLedger.Payment> afterPayments = new ArrayList<>();
        for (ScenarioComputation child : children) {
            beforePayments.addAll(child.ledger().beforePayments());
            afterPayments.addAll(child.ledger().afterPayments());
        }
        return new ScenarioComputation(result, new ScenarioLedger(beforePayments, afterPayments, fullTermEnd, cost.through(), cost.complete()));
    }

    // ---------- 현금흐름 ----------

    private static Cashflow composeCashflow(LocalDate base, List<ScenarioComputation> children) {
        YearMonth baseMonth = YearMonth.from(base);
        TreeMap<YearMonth, Long> byMonth = new TreeMap<>();
        long current = 0;
        boolean truncated = false;
        YearMonth end = baseMonth;
        for (ScenarioComputation child : children) {
            Cashflow flow = child.result().cashflow();
            if (flow.currentMonth() != null) {
                current = Math.addExact(current, flow.currentMonth().delta());
            }
            truncated |= ScenarioCashflow.isTruncated(flow);
            for (Month month : flow.months()) {
                YearMonth key = YearMonth.parse(month.yearMonth());
                byMonth.merge(key, month.delta(), Math::addExact);
                if (key.isAfter(end)) {
                    end = key;
                }
            }
        }
        int shown = (int) Math.min(ChronoUnit.MONTHS.between(baseMonth, end), SimulationInput.MAX_DURATION_MONTHS);
        List<Month> months = new ArrayList<>(shown);
        long cumulative = current;
        for (int i = 1; i <= shown; i++) {
            YearMonth month = baseMonth.plusMonths(i);
            long delta = byMonth.getOrDefault(month, 0L);
            cumulative = Math.addExact(cumulative, delta);
            months.add(new Month(month.toString(), delta, cumulative));
        }
        return new Cashflow(Cashflow.SCENARIO_NET_CASH_CHANGE, null, null, months, new CurrentMonth(baseMonth.toString(), current),
                truncated, baseMonth.plusMonths(shown).toString(), null);
    }

    // ---------- 금융비용 ----------

    private record ChildCost(int sequenceNo, SimulationType type, long before, long after, LocalDate coveredThrough, boolean complete) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("sequenceNo", sequenceNo);
            map.put("type", type.name());
            map.put("before", before);
            map.put("after", after);
            map.put("delta", after - before);
            map.put("costCoveredThrough", coveredThrough);
            map.put("costComplete", complete);
            map.put("reason", complete ? null : COST_REASON_HORIZON_LIMIT);
            return map;
        }
    }

    private record CostEvaluation(LocalDate through, long before, long after, boolean complete, List<ChildCost> children) {
        Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("before", before);
            map.put("after", after);
            map.put("delta", after - before);
            map.put("complete", complete);
            map.put("children", children.stream().map(ChildCost::toMap).toList());
            return map;
        }
    }

    /**
     * 평가 종료일 = min(자식 계산 종료일 최댓값, 기준일 + 120개월이 속한 달의 말일). 양 끝 포함, 납부일 기준.
     * 기간 밖 납부액을 일할 안분하거나 발생주의로 바꾸지 않는다.
     */
    private static CostEvaluation evaluateCost(LocalDate base, CombinedInput input, List<ScenarioComputation> children) {
        LocalDate horizon = YearMonth.from(base.plusMonths(SimulationInput.MAX_DURATION_MONTHS)).atEndOfMonth();
        LocalDate latest = children.stream().map(child -> child.ledger().costCoveredThrough()).max(LocalDate::compareTo).orElse(base);
        LocalDate through = latest.isBefore(horizon) ? latest : horizon;
        List<ChildCost> costs = new ArrayList<>();
        long before = 0;
        long after = 0;
        boolean complete = true;
        for (int i = 0; i < children.size(); i++) {
            ScenarioLedger ledger = children.get(i).ledger();
            long childBefore = costWithin(ledger.beforePayments(), base, through);
            long childAfter = costWithin(ledger.afterPayments(), base, through);
            boolean childComplete = ledger.costComplete() || !ledger.costCoveredThrough().isBefore(through);
            costs.add(new ChildCost(i + 1, input.children().get(i).type(), childBefore, childAfter, ledger.costCoveredThrough(),
                    childComplete));
            before = Math.addExact(before, childBefore);
            after = Math.addExact(after, childAfter);
            complete &= childComplete;
        }
        return new CostEvaluation(through, before, after, complete, costs);
    }

    private static long costWithin(List<ScenarioLedger.Payment> payments, LocalDate from, LocalDate through) {
        long total = 0;
        for (ScenarioLedger.Payment payment : payments) {
            if (!payment.date().isBefore(from) && !payment.date().isAfter(through)) {
                total = Math.addExact(total, payment.cost());
            }
        }
        return total;
    }

    // ---------- 목표 ----------

    private record GoalOutcome(Snapshot after, List<GoalImpact> impacts, List<GoalImpactDetail> details, List<String> assumptions,
            String policy) {
    }

    private static GoalOutcome projectGoals(SimulationBaseline baseline, CombinedInput input, List<ScenarioComputation> children) {
        List<SpendingReductionInput> spendings = new ArrayList<>();
        SavingChangeInput saving = null;
        boolean onlySpendingSaving = true;
        for (SimulationInput child : input.children()) {
            if (child instanceof SpendingReductionInput spending) {
                spendings.add(spending);
            } else if (child instanceof SavingChangeInput change) {
                saving = change;
            } else {
                onlySpendingSaving = false;
            }
        }
        Map<Long, LocalDate> dates = new LinkedHashMap<>();
        Map<Long, Long> paces = new LinkedHashMap<>();
        List<GoalImpactDetail> details;
        List<String> assumptions;
        String policy;
        if (onlySpendingSaving) {
            CombinedGoalProjection.Outcome outcome = CombinedGoalProjection.project(baseline, spendings, saving);
            dates.putAll(outcome.afterDates());
            paces.putAll(outcome.firstMonthPaces());
            details = outcome.details();
            assumptions = outcome.assumptions();
            policy = GOAL_POLICY_DURATION_LIMITED;
        } else {
            details = new ArrayList<>();
            for (SimulationBaseline.SavingGoal goal : baseline.savingGoals()) {
                boolean achieved = goal.remaining() <= 0;
                dates.put(goal.goalId(), achieved ? baseline.baseDate() : null);
                paces.put(goal.goalId(), goal.averageMonthlySaving());
                List<String> reasons = new ArrayList<>();
                if (achieved) {
                    reasons.add(SimulationAssumptions.ALREADY_ACHIEVED);
                } else {
                    reasons.add(SimulationAssumptions.COMBINED_GOAL_EFFECT_NOT_COMPUTABLE);
                    reasons.addAll(childReasons(children, goal.goalId()));
                }
                details.add(new GoalImpactDetail(goal.goalId(), achieved ? GoalImpactDetail.COMPUTED : GoalImpactDetail.NOT_COMPUTABLE, reasons));
            }
            assumptions = baseline.savingGoals().isEmpty() ? List.of() : List.of(SimulationAssumptions.COMBINED_GOAL_EFFECT_NOT_COMPUTABLE);
            policy = GOAL_POLICY_NOT_COMPUTABLE;
        }
        List<GoalForecast> forecasts = new ArrayList<>();
        List<GoalImpact> impacts = new ArrayList<>();
        for (SimulationBaseline.SavingGoal goal : baseline.savingGoals()) {
            LocalDate after = dates.get(goal.goalId());
            forecasts.add(new GoalForecast(goal.goalId(), goal.name(), goal.remaining(),
                    paces.getOrDefault(goal.goalId(), goal.averageMonthlySaving()), after));
            impacts.add(new GoalImpact(goal.goalId(), goal.expectedAchievementDate(), after));
        }
        Snapshot baselineSnapshot = SimulationScenarios.baselineSnapshot(baseline);
        Snapshot after = new Snapshot(baseline.baseDate(), baselineSnapshot.monthlyIncome(), baselineSnapshot.monthlySpending(),
                baselineSnapshot.monthlySaving(), baselineSnapshot.monthlyAvailableFunds(), forecasts);
        return new GoalOutcome(after, impacts, details, assumptions, policy);
    }

    /** 자식이 단독 계산에서 남긴 계산 불가 사유(자금 출처·귀속 불명 등). 중복 없이 자식 순서대로. */
    private static List<String> childReasons(List<ScenarioComputation> children, Long goalId) {
        Set<String> reasons = new LinkedHashSet<>();
        for (ScenarioComputation child : children) {
            List<GoalImpactDetail> details = child.result().goalImpactDetails();
            if (details == null) {
                continue;
            }
            for (GoalImpactDetail detail : details) {
                if (detail.goalId().equals(goalId) && GoalImpactDetail.NOT_COMPUTABLE.equals(detail.status())) {
                    reasons.addAll(detail.reasons());
                }
            }
        }
        return new ArrayList<>(reasons);
    }

    // ---------- details.children ----------

    private static List<Map<String, Object>> childDetails(CombinedInput input, List<ScenarioComputation> children) {
        List<Map<String, Object>> list = new ArrayList<>(children.size());
        for (int i = 0; i < children.size(); i++) {
            SimulationResult result = children.get(i).result();
            ScenarioLedger ledger = children.get(i).ledger();
            Map<String, Object> child = new LinkedHashMap<>();
            child.put("sequenceNo", i + 1);
            child.put("type", input.children().get(i).type().name());
            child.put("title", result.title());
            child.put("summary", result.summary());
            child.put("fullTermEndDate", ledger.fullTermEnd());
            child.put("costCoveredThrough", ledger.costCoveredThrough());
            child.put("costComplete", ledger.costComplete());
            child.put("assumptions", result.assumptions());
            child.put("comparison", result.comparison());
            child.put("cashflow", result.cashflow());
            child.put("goalImpacts", childGoalImpacts(result));
            child.put("details", result.details());
            list.add(child);
        }
        return list;
    }

    /** 자식의 단독 목표 영향. 부모 합성 날짜와 다를 수 있으며 참고용이다. */
    private static List<Map<String, Object>> childGoalImpacts(SimulationResult result) {
        Map<Long, GoalImpactDetail> details = new LinkedHashMap<>();
        if (result.goalImpactDetails() != null) {
            for (GoalImpactDetail detail : result.goalImpactDetails()) {
                details.put(detail.goalId(), detail);
            }
        }
        List<Map<String, Object>> impacts = new ArrayList<>();
        for (GoalImpact impact : result.goalImpacts()) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("goalId", impact.goalId());
            map.put("beforeForecastDate", impact.before());
            map.put("afterForecastDate", impact.after());
            GoalImpactDetail detail = details.get(impact.goalId());
            if (detail != null) {
                map.put("status", detail.status());
                map.put("reasons", detail.reasons());
            }
            impacts.add(map);
        }
        return impacts;
    }

    // ---------- 기타 ----------

    private static long sectionAfter(List<Item> items, String key) {
        for (Item item : items) {
            if (key.equals(item.key()) && item.after() instanceof Long value) {
                return value;
            }
        }
        return 0L;
    }

    private static String title(List<ScenarioComputation> children) {
        StringBuilder text = new StringBuilder("복합 시나리오 " + children.size() + "건: ");
        for (int i = 0; i < children.size(); i++) {
            if (i > 0) {
                text.append(" + ");
            }
            text.append(children.get(i).result().title());
        }
        return truncate(text.toString(), TITLE_MAX);
    }

    private static String summary(List<ScenarioComputation> children, CostEvaluation cost, GoalOutcome goals, Snapshot before) {
        StringBuilder text = new StringBuilder(children.size() + "개 시나리오 독립 합성 · " + cost.through() + "까지 금융비용 "
                + SimulationScenarios.won(cost.before()) + " → " + SimulationScenarios.won(cost.after())
                + " (" + (cost.after() - cost.before() >= 0 ? "+" : "-") + SimulationScenarios.won(Math.abs(cost.after() - cost.before())) + ")");
        if (!cost.complete()) {
            text.append(", 미완납 자식 비용 미상");
        }
        if (!before.goals().isEmpty()) {
            GoalForecast b = before.goals().get(0);
            GoalForecast a = goals.after().goals().get(0);
            if (GOAL_POLICY_NOT_COMPUTABLE.equals(goals.policy()) && b.remaining() > 0) {
                text.append(", 목표 영향 계산 불가");
            } else {
                text.append(", ").append(b.name()).append(" 예상 달성일 ").append(SimulationScenarios.dateText(b.expectedAchievementDate()))
                        .append(" → ").append(SimulationScenarios.dateText(a.expectedAchievementDate()));
            }
        }
        return truncate(text.toString(), SUMMARY_MAX);
    }

    private static String truncate(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "…";
    }
}
