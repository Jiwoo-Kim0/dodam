package com.ssafy.dodam.domain.simulation.service.calculator;

import com.ssafy.dodam.domain.simulation.dto.SavingChangeInput;
import com.ssafy.dodam.domain.simulation.dto.ScenarioComputation;
import com.ssafy.dodam.domain.simulation.dto.ScenarioLedger;
import com.ssafy.dodam.domain.simulation.dto.SimulationBaseline;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Comparison;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.GoalImpact;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Item;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Snapshot;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import com.ssafy.dodam.domain.simulation.service.SimulationCalculator;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.springframework.stereotype.Component;

/**
 * 저축액 변경(POST /simulations/saving). {@code monthlySavingAmount}는 변경 후 월 저축액(절대값)이며 현재 속도보다 작아도 된다 —
 * 저축을 줄이면 달성이 늦어지는 것도 보여준다.
 *
 * <p>계좌를 지정했고 그 계좌가 어떤 모으기 목표에 연결돼 있으면 그 목표만 새 금액을 받고 나머지는 기존 속도를 유지한다.
 * 지정하지 않았거나 연결된 목표가 없으면 모든 모으기 목표에 남은 금액 비례로 배분한다.
 * 소득이 등록돼 있으면 새 월 저축액이 소득을 넘을 수 없다. 이자는 계산하지 않는다({@code INTEREST_NOT_APPLIED}).
 */
@Component
public class SavingChangeCalculator implements SimulationCalculator<SavingChangeInput> {
    static final String ACCOUNT_NOT_LINKED_TO_GOAL = "ACCOUNT_NOT_LINKED_TO_GOAL";

    @Override
    public SimulationType type() {
        return SimulationType.SAVING;
    }

    @Override
    public Class<SavingChangeInput> inputType() {
        return SavingChangeInput.class;
    }

    @Override
    public ScenarioComputation compute(SimulationBaseline baseline, SavingChangeInput input) {
        if (baseline.monthlyIncome() != null && input.monthlySavingAmount() > baseline.monthlyIncome()) {
            throw new BusinessException(ErrorCode.SIMULATION_SAVING_EXCEEDS_INCOME);
        }
        List<String> assumptions = new ArrayList<>(List.of(SimulationScenarios.INTEREST_NOT_APPLIED));
        Predicate<SimulationBaseline.SavingGoal> eligible = eligibleGoals(baseline, input);
        if (input.accountId() != null && !isLinked(baseline, input)) {
            assumptions.add(ACCOUNT_NOT_LINKED_TO_GOAL);
        }
        long currentSaving = baseline.averageMonthlySaving();
        Map<Long, Long> beforePaces = SimulationScenarios.currentPaces(baseline);
        Map<Long, Long> afterPaces = SimulationScenarios.replaceDistributed(baseline, eligible, input.monthlySavingAmount());
        // 대상 목표에 배분한 금액 + 대상이 아닌 목표의 기존 속도 = 변경 후 월 저축 합
        long afterSaving = afterPaces.values().stream().mapToLong(Long::longValue).sum();
        if (baseline.savingGoals().isEmpty()) {
            afterSaving = input.monthlySavingAmount();
        }
        Snapshot before = SimulationScenarios.snapshot(baseline, baseline.averageMonthlySpending(), currentSaving, beforePaces);
        Snapshot after = SimulationScenarios.snapshot(baseline, baseline.averageMonthlySpending(), afterSaving, afterPaces);
        long delta = afterSaving - currentSaving;

        List<Item> items = new ArrayList<>();
        items.add(Item.amount("MONTHLY_SAVING", "월 저축액", currentSaving, afterSaving));
        items.add(Item.amount("MONTHLY_AVAILABLE_FUNDS", "월 가용자금", before.monthlyAvailableFunds(), after.monthlyAvailableFunds()));
        items.addAll(SimulationScenarios.goalItems(before, after));
        List<Item> assets = List.of(Item.amount("SAVING_CUMULATIVE", input.durationMonths() + "개월 누적 저축액",
                currentSaving * input.durationMonths(), afterSaving * input.durationMonths()));
        List<GoalImpact> impacts = SimulationScenarios.goalImpacts(before, after);
        if (baseline.savingGoals().stream().filter(eligible).count() > 1) {
            assumptions.add(SimulationScenarios.SPLIT_BY_REMAINING);
        }
        String title = "월 저축액 " + SimulationScenarios.won(currentSaving) + " → " + SimulationScenarios.won(afterSaving);
        SimulationResult result = new SimulationResult(title, summary(delta, before, after), before, after,
                new Comparison(items, assets, List.of(), List.of()),
                SimulationScenarios.cashflow(before, after, input.durationMonths(), -delta), impacts, assumptions);
        return new ScenarioComputation(result, ScenarioLedger.noCost(baseline.baseDate().plusMonths(input.durationMonths())));
    }

    /** 지정 계좌가 어떤 모으기 목표에 연결돼 있는지. 복합 목표 투영도 같은 대상 선택 규칙을 쓴다. */
    static boolean isLinked(SimulationBaseline baseline, SavingChangeInput input) {
        return input.accountId() != null
                && baseline.savingGoals().stream().anyMatch(g -> g.linkedAccountIds().contains(input.accountId()));
    }

    /** 새 저축액을 받을 목표. 계좌가 연결된 목표가 있으면 그 목표만, 아니면 모든 모으기 목표. */
    static Predicate<SimulationBaseline.SavingGoal> eligibleGoals(SimulationBaseline baseline, SavingChangeInput input) {
        return isLinked(baseline, input) ? g -> g.linkedAccountIds().contains(input.accountId()) : g -> true;
    }

    private static String summary(long delta, Snapshot before, Snapshot after) {
        StringBuilder text = new StringBuilder("월 가용자금 ").append(delta > 0 ? "-" : "+")
                .append(SimulationScenarios.won(Math.abs(delta)));
        if (!before.goals().isEmpty()) {
            var b = before.goals().get(0);
            var a = after.goals().get(0);
            text.append(", ").append(b.name()).append(" 예상 달성일 ").append(SimulationScenarios.dateText(b.expectedAchievementDate()))
                    .append(" → ").append(SimulationScenarios.dateText(a.expectedAchievementDate()));
        }
        return text.toString();
    }
}
