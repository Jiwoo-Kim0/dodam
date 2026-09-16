package com.ssafy.dodam.domain.simulation.service.calculator;

import com.ssafy.dodam.domain.simulation.dto.SavingChangeInput;
import com.ssafy.dodam.domain.simulation.dto.SimulationBaseline;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.GoalImpactDetail;
import com.ssafy.dodam.domain.simulation.dto.SpendingReductionInput;
import com.ssafy.dodam.domain.simulation.service.GoalDateProjection;
import com.ssafy.dodam.domain.simulation.service.SimulationAssumptions;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * 소비·저축 자식만으로 구성된 복합의 목표 달성일(586 §5). 기존 551 단독 계약(변경 속도를 표시기간과 무관하게 지속 적용)과 달리
 * 각 자식의 변경은 {@code k ≤ durationMonths}인 투영 구간에만 유효하고, 마지막 자식 종료 후에는 기준선 목표별 속도로 복귀한다
 * ({@code GOAL_EFFECT_LIMITED_TO_SCENARIO_DURATION}). 기존 단독 투영기는 바꾸지 않는다.
 *
 * <p>투영일 {@code t(k) = 기준일.plusMonths(k)}, k ≥ 1. 각 k에서
 * <ul>
 *   <li>SAVING이 유효하면 그 자식의 계좌별 대상 목표 선택·절대액 배분 규칙으로 목표별 속도를 교체하고, 같은 구간의 SPENDING
 *       절감액은 목표에 추가 배분하지 않는다({@code SAVING_AMOUNT_IS_FINAL_TARGET}).</li>
 *   <li>아니면 유효한 SPENDING 감소액을 합쳐 기준일 미달성 목표의 remaining 비중(goalId 오름차순, 마지막 목표 나머지)으로 한 번
 *       배분해 기준선 속도에 더한다. 이미 달성한 목표는 배분에서 제외하고 도중 달성한 목표 몫을 재배분하지 않는다.</li>
 * </ul>
 * 각 t(k)에 {@code max(0, 구간 저축액)}을 잔여액에서 빼고 처음 0 이하가 되는 t(k)가 달성일이다. 최대 유효기간 K 이후 미달성이면
 * 기준선 속도로 필요한 개월 수를 올림해 같은 축에서 이어 계산한다. 기준선 속도 0이면 null({@code SAVING_PACE_ZERO}).
 * 예: 잔여 100만원·속도 10만원·감소액 10만원 1개월 유효 → 첫 달 20만원, 이후 10만원씩 → 9개월째 달성(5개월이 아니다).
 */
final class CombinedGoalProjection {
    private CombinedGoalProjection() {
    }

    /**
     * @param afterDates      목표별 달성일(null = 계산 불가)
     * @param firstMonthPaces 첫 투영 구간(k = 1)의 목표별 저축 속도. 응답 {@code after.goals[].monthlySaving}에 쓴다
     * @param details         목표별 상태·사유
     * @param assumptions     부모 assumptions에 얹을 코드
     */
    record Outcome(Map<Long, LocalDate> afterDates, Map<Long, Long> firstMonthPaces, List<GoalImpactDetail> details,
            List<String> assumptions) {
    }

    /**
     * @param spendings SPENDING 자식(카테고리·전체 혼합 없음은 검증 완료)
     * @param saving    SAVING 자식. 없으면 null(복합에 SAVING은 최대 1개)
     */
    static Outcome project(SimulationBaseline baseline, List<SpendingReductionInput> spendings, SavingChangeInput saving) {
        LocalDate base = baseline.baseDate();
        int horizon = 0;
        for (SpendingReductionInput spending : spendings) {
            horizon = Math.max(horizon, spending.durationMonths());
        }
        if (saving != null) {
            horizon = Math.max(horizon, saving.durationMonths());
        }
        Predicate<SimulationBaseline.SavingGoal> savingEligible = saving == null ? g -> false
                : SavingChangeCalculator.eligibleGoals(baseline, saving);
        Map<Long, Long> savingPaces = saving == null ? Map.of()
                : SimulationScenarios.replaceDistributed(baseline, savingEligible, saving.monthlySavingAmount());

        // SAVING이 유효하지 않은 구간의 SPENDING 감소액 합을 날짜별로 모아 기준일 비중으로 배분한다
        NavigableMap<LocalDate, Long> reductionByDate = new TreeMap<>();
        boolean[] savingActive = new boolean[horizon + 1];
        for (int k = 1; k <= horizon; k++) {
            savingActive[k] = saving != null && k <= saving.durationMonths();
            if (savingActive[k]) {
                continue;
            }
            long reduction = 0;
            for (SpendingReductionInput spending : spendings) {
                if (k <= spending.durationMonths()) {
                    reduction = Math.addExact(reduction, spending.monthlyReduction());
                }
            }
            reductionByDate.put(base.plusMonths(k), reduction);
        }
        Map<Long, NavigableMap<LocalDate, Long>> shares = GoalDateProjection.allocate(baseline.savingGoals(), reductionByDate);

        Map<Long, LocalDate> afterDates = new LinkedHashMap<>();
        Map<Long, Long> firstPaces = new LinkedHashMap<>();
        List<GoalImpactDetail> details = new ArrayList<>();
        boolean bothPresent = !spendings.isEmpty() && saving != null;
        for (SimulationBaseline.SavingGoal goal : baseline.savingGoals()) {
            long basePace = goal.averageMonthlySaving();
            if (goal.remaining() <= 0) {
                afterDates.put(goal.goalId(), base);
                firstPaces.put(goal.goalId(), paceAt(1, goal, basePace, savingActive, savingPaces, shares, base, horizon));
                details.add(new GoalImpactDetail(goal.goalId(), GoalImpactDetail.COMPUTED, List.of(SimulationAssumptions.ALREADY_ACHIEVED)));
                continue;
            }
            long left = goal.remaining();
            LocalDate achieved = null;
            for (int k = 1; k <= horizon; k++) {
                long pace = paceAt(k, goal, basePace, savingActive, savingPaces, shares, base, horizon);
                if (k == 1) {
                    firstPaces.put(goal.goalId(), pace);
                }
                left -= Math.max(0L, pace);
                if (left <= 0) {
                    achieved = base.plusMonths(k);
                    break;
                }
            }
            if (achieved == null && basePace > 0) {
                achieved = base.plusMonths(horizon + ceilDivide(left, basePace));
            }
            afterDates.put(goal.goalId(), achieved);
            List<String> reasons = new ArrayList<>();
            reasons.add(SimulationAssumptions.GOAL_EFFECT_LIMITED_TO_SCENARIO_DURATION);
            if (!spendings.isEmpty()) {
                reasons.add(SpendingReductionCalculator.REDUCTION_DIRECTED_TO_SAVING_GOALS);
            }
            if (bothPresent) {
                reasons.add(SimulationAssumptions.SAVING_AMOUNT_IS_FINAL_TARGET);
            }
            String status = GoalImpactDetail.COMPUTED;
            if (achieved == null) {
                status = GoalImpactDetail.NOT_COMPUTABLE;
                reasons.add(SimulationAssumptions.SAVING_PACE_ZERO);
            }
            details.add(new GoalImpactDetail(goal.goalId(), status, reasons));
        }

        List<String> assumptions = new ArrayList<>();
        assumptions.add(SimulationAssumptions.GOAL_EFFECT_LIMITED_TO_SCENARIO_DURATION);
        if (!spendings.isEmpty()) {
            assumptions.add(SpendingReductionCalculator.REDUCTION_DIRECTED_TO_SAVING_GOALS);
        }
        if (bothPresent) {
            assumptions.add(SimulationAssumptions.SAVING_AMOUNT_IS_FINAL_TARGET);
        }
        String targetMiss = saving == null ? null : SavingChangeCalculator.targetMissAssumption(baseline, saving);
        if (targetMiss != null) {
            assumptions.add(targetMiss);
        }
        if (baseline.savingGoals().stream().filter(goal -> goal.remaining() > 0).count() > 1) {
            assumptions.add(SimulationScenarios.SPLIT_BY_REMAINING);
        }
        return new Outcome(afterDates, firstPaces, details, assumptions);
    }

    /** k번째 투영 구간의 목표 속도. SAVING 유효 구간은 교체 속도, 아니면 기준선 속도 + 배분된 절감액. 유효기간 밖은 기준선 속도. */
    private static long paceAt(int k, SimulationBaseline.SavingGoal goal, long basePace, boolean[] savingActive,
            Map<Long, Long> savingPaces, Map<Long, NavigableMap<LocalDate, Long>> shares, LocalDate base, int horizon) {
        if (k > horizon) {
            return basePace;
        }
        if (savingActive[k]) {
            return savingPaces.getOrDefault(goal.goalId(), basePace);
        }
        long share = shares.getOrDefault(goal.goalId(), new TreeMap<>()).getOrDefault(base.plusMonths(k), 0L);
        return Math.addExact(basePace, share);
    }

    private static long ceilDivide(long amount, long divisor) {
        return (amount + divisor - 1) / divisor;
    }
}
