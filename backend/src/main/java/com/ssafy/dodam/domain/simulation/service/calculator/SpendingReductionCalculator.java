package com.ssafy.dodam.domain.simulation.service.calculator;

import com.ssafy.dodam.domain.simulation.dto.SimulationBaseline;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Comparison;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.GoalImpact;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Item;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Snapshot;
import com.ssafy.dodam.domain.simulation.dto.SpendingReductionInput;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import com.ssafy.dodam.domain.simulation.service.SimulationCalculator;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 소비 감소(POST /simulations/spending). 기획안 4-5 예시 "외식비 월 10만원 감소 → 목표 달성 55일 단축"에 해당한다.
 *
 * <p>두 가지를 구분해 보여준다.
 * <ul>
 *   <li>현금흐름: 감소액만큼 월 가용자금이 늘어난다(저축 약정은 바뀌지 않으므로 {@code after.monthlySaving}은 그대로).</li>
 *   <li>목표 영향: 감소액을 모으기 목표에 돌린다고 가정({@code REDUCTION_DIRECTED_TO_SAVING_GOALS})할 때의 예상 달성일.</li>
 * </ul>
 * 감소액은 기준 구간 월평균 지출(카테고리 지정 시 그 카테고리)을 넘을 수 없다 — 줄일 수 없는 금액을 줄인 결과는 만들지 않는다.
 */
@Component
public class SpendingReductionCalculator implements SimulationCalculator<SpendingReductionInput> {
    static final String REDUCTION_DIRECTED_TO_SAVING_GOALS = "REDUCTION_DIRECTED_TO_SAVING_GOALS";

    @Override
    public SimulationType type() {
        return SimulationType.SPENDING;
    }

    @Override
    public Class<SpendingReductionInput> inputType() {
        return SpendingReductionInput.class;
    }

    @Override
    public SimulationResult calculate(SimulationBaseline baseline, SpendingReductionInput input) {
        long scopeSpending = input.categoryCode() == null ? baseline.averageMonthlySpending()
                : baseline.spendingByCategory().getOrDefault(input.categoryCode(), 0L);
        if (input.monthlyReduction() > scopeSpending) {
            throw new BusinessException(ErrorCode.SIMULATION_REDUCTION_EXCEEDS_SPENDING);
        }
        long reduction = input.monthlyReduction();
        long currentSaving = baseline.averageMonthlySaving();

        Map<Long, Long> beforePaces = SimulationScenarios.currentPaces(baseline);
        Map<Long, Long> afterPaces = SimulationScenarios.addDistributed(baseline, g -> true, reduction);
        Snapshot before = SimulationScenarios.snapshot(baseline, baseline.averageMonthlySpending(), currentSaving, beforePaces);
        Snapshot after = SimulationScenarios.snapshot(baseline, baseline.averageMonthlySpending() - reduction, currentSaving, afterPaces);

        List<Item> items = new ArrayList<>();
        String scopeLabel = input.categoryCode() == null ? "월 지출" : input.categoryCode() + " 월 지출";
        items.add(Item.amount("SCOPE_MONTHLY_SPENDING", scopeLabel, scopeSpending, scopeSpending - reduction));
        items.add(Item.amount("MONTHLY_SPENDING", "월 지출 합계", before.monthlySpending(), after.monthlySpending()));
        items.add(Item.amount("MONTHLY_AVAILABLE_FUNDS", "월 가용자금", before.monthlyAvailableFunds(), after.monthlyAvailableFunds()));
        for (SimulationBaseline.SpendingGoal goal : baseline.spendingGoals()) {
            items.add(Item.amount("SPENDING_GOAL_" + goal.goalId() + "_PROJECTED_MONTH_END",
                    goal.name() + " 이번 달 예상 지출(예산 " + SimulationScenarios.won(goal.monthlyBudget()) + ")",
                    goal.projectedMonthEnd(), Math.max(0L, goal.projectedMonthEnd() - reduction)));
        }
        items.addAll(SimulationScenarios.goalItems(before, after));
        List<Item> assets = List.of(Item.amount("ADDITIONAL_SAVING_CUMULATIVE",
                input.durationMonths() + "개월 누적 추가 저축 가능액", 0L, reduction * input.durationMonths()));

        List<GoalImpact> impacts = SimulationScenarios.goalImpacts(before, after);
        List<String> assumptions = new ArrayList<>(List.of(REDUCTION_DIRECTED_TO_SAVING_GOALS, SimulationScenarios.INTEREST_NOT_APPLIED));
        if (baseline.savingGoals().size() > 1) {
            assumptions.add(SimulationScenarios.SPLIT_BY_REMAINING);
        }
        String title = scopeLabel + " " + SimulationScenarios.won(reduction) + " 감소";
        return new SimulationResult(title, summary(reduction, before, after), before, after,
                new Comparison(items, assets, List.of(), List.of()),
                SimulationScenarios.cashflow(before, after, input.durationMonths(), reduction), impacts, assumptions);
    }

    private static String summary(long reduction, Snapshot before, Snapshot after) {
        StringBuilder text = new StringBuilder("월 가용자금 +").append(SimulationScenarios.won(reduction));
        if (!before.goals().isEmpty()) {
            var b = before.goals().get(0);
            var a = after.goals().get(0);
            text.append(", ").append(b.name()).append(" 예상 달성일 ").append(SimulationScenarios.dateText(b.expectedAchievementDate()))
                    .append(" → ").append(SimulationScenarios.dateText(a.expectedAchievementDate()));
        }
        return text.toString();
    }
}
