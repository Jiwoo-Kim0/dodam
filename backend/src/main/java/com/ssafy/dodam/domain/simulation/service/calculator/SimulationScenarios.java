package com.ssafy.dodam.domain.simulation.service.calculator;

import com.ssafy.dodam.domain.simulation.dto.SimulationBaseline;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.GoalForecast;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.GoalImpact;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.GoalImpactDetail;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Item;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Month;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Snapshot;
import com.ssafy.dodam.domain.simulation.service.GoalAchievementProjection;
import com.ssafy.dodam.domain.simulation.service.SimulationAssumptions;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 계산기 공통 조립. 모으기 목표에 월 저축액을 배분하고 스냅샷·비교표·월별 변화표를 만든다.
 *
 * <p><b>배분 규칙</b>: 대상 목표가 여럿이면 남은 금액에 비례해 나눈다(마지막 목표가 나머지를 받아 합이 정확히 맞는다).
 * 남은 금액이 모두 0이면 배분하지 않는다. 가정 코드 {@code SPLIT_BY_REMAINING}으로 응답에 드러낸다.
 */
final class SimulationScenarios {
    static final String SPLIT_BY_REMAINING = "SPLIT_BY_REMAINING";
    static final String INTEREST_NOT_APPLIED = "INTEREST_NOT_APPLIED";

    private SimulationScenarios() {
    }

    /** 현재 속도 그대로. */
    static Map<Long, Long> currentPaces(SimulationBaseline baseline) {
        Map<Long, Long> paces = new LinkedHashMap<>();
        for (SimulationBaseline.SavingGoal goal : baseline.savingGoals()) {
            paces.put(goal.goalId(), goal.averageMonthlySaving());
        }
        return paces;
    }

    /** {@code eligible} 목표의 기존 속도에 {@code delta}를 남은 금액 비례로 얹는다. 소비 감소분을 목표에 돌릴 때. */
    static Map<Long, Long> addDistributed(SimulationBaseline baseline, Predicate<SimulationBaseline.SavingGoal> eligible, long delta) {
        Map<Long, Long> paces = currentPaces(baseline);
        distribute(baseline, eligible, delta).forEach((goalId, share) -> paces.merge(goalId, share, Long::sum));
        return paces;
    }

    /** {@code eligible} 목표의 속도를 {@code total}의 남은 금액 비례 배분값으로 바꾼다. 나머지 목표는 기존 속도. 저축액 절대값 변경 시. */
    static Map<Long, Long> replaceDistributed(SimulationBaseline baseline, Predicate<SimulationBaseline.SavingGoal> eligible, long total) {
        Map<Long, Long> paces = currentPaces(baseline);
        paces.putAll(distribute(baseline, eligible, total));
        return paces;
    }

    private static Map<Long, Long> distribute(SimulationBaseline baseline, Predicate<SimulationBaseline.SavingGoal> eligible, long amount) {
        Map<Long, Long> shares = new LinkedHashMap<>();
        List<SimulationBaseline.SavingGoal> targets = baseline.savingGoals().stream().filter(eligible).toList();
        long remainingSum = targets.stream().mapToLong(SimulationBaseline.SavingGoal::remaining).sum();
        if (targets.isEmpty()) {
            return shares;
        }
        if (remainingSum <= 0) {
            // 남은 금액이 모두 0(이미 달성)이면 비례 기준이 없으므로 균등 배분한다
            long each = amount / targets.size();
            for (int i = 0; i < targets.size(); i++) {
                shares.put(targets.get(i).goalId(), i == targets.size() - 1 ? amount - each * (targets.size() - 1) : each);
            }
            return shares;
        }
        long allocated = 0;
        for (int i = 0; i < targets.size(); i++) {
            SimulationBaseline.SavingGoal goal = targets.get(i);
            long share = i == targets.size() - 1 ? amount - allocated
                    : Math.floorDiv(Math.multiplyExact(amount, goal.remaining()), remainingSum);
            allocated += share;
            shares.put(goal.goalId(), share);
        }
        return shares;
    }

    static Snapshot snapshot(SimulationBaseline baseline, long monthlySpending, long monthlySaving, Map<Long, Long> paces) {
        List<GoalForecast> goals = new ArrayList<>();
        for (SimulationBaseline.SavingGoal goal : baseline.savingGoals()) {
            long pace = paces.getOrDefault(goal.goalId(), goal.averageMonthlySaving());
            goals.add(new GoalForecast(goal.goalId(), goal.name(), goal.remaining(), pace,
                    GoalAchievementProjection.expectedDate(goal.remaining(), pace, baseline.baseDate())));
        }
        Long available = baseline.monthlyIncome() == null ? null : baseline.monthlyIncome() - monthlySpending - monthlySaving;
        return new Snapshot(baseline.baseDate(), baseline.monthlyIncome(), monthlySpending, monthlySaving, available, goals);
    }

    static List<GoalImpact> goalImpacts(Snapshot before, Snapshot after) {
        List<GoalImpact> impacts = new ArrayList<>();
        for (int i = 0; i < before.goals().size(); i++) {
            GoalForecast b = before.goals().get(i);
            GoalForecast a = after.goals().get(i);
            impacts.add(new GoalImpact(b.goalId(), b.expectedAchievementDate(), a.expectedAchievementDate()));
        }
        return impacts;
    }

    static List<Item> goalItems(Snapshot before, Snapshot after) {
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < before.goals().size(); i++) {
            GoalForecast b = before.goals().get(i);
            GoalForecast a = after.goals().get(i);
            items.add(Item.amount("GOAL_" + b.goalId() + "_MONTHLY_SAVING", b.name() + " 월 저축", b.monthlySaving(), a.monthlySaving()));
            items.add(Item.date("GOAL_" + b.goalId() + "_EXPECTED_DATE", b.name() + " 예상 달성일",
                    b.expectedAchievementDate(), a.expectedAchievementDate()));
        }
        return items;
    }

    /** 기준일 다음 달부터 {@code months}개월, 매달 같은 delta. 예정 이벤트는 더하지 않는다. */
    static SimulationResult.Cashflow cashflow(Snapshot before, Snapshot after, int months, long monthlyDelta) {
        List<Month> series = new ArrayList<>();
        YearMonth start = YearMonth.from(before.baseDate());
        long cumulative = 0;
        for (int i = 1; i <= months; i++) {
            cumulative += monthlyDelta;
            series.add(new Month(start.plusMonths(i).toString(), monthlyDelta, cumulative));
        }
        return new SimulationResult.Cashflow(SimulationResult.Cashflow.MONTHLY_AVAILABLE_FUNDS,
                before.monthlyAvailableFunds(), after.monthlyAvailableFunds(), series);
    }

    /**
     * 585 유형의 스냅샷. 참고 기준선(소득·지출·저축 속도)은 전후가 같고 목표 예상일만 {@code afterDates}로 바꾼다.
     * 대출 납입액을 monthlySpending에 끼워 넣지 않는다 — 유형별 변화는 comparison·cashflow에 있다.
     */
    static Snapshot snapshotWithDates(SimulationBaseline baseline, Map<Long, LocalDate> afterDates) {
        List<GoalForecast> goals = new ArrayList<>();
        for (SimulationBaseline.SavingGoal goal : baseline.savingGoals()) {
            goals.add(new GoalForecast(goal.goalId(), goal.name(), goal.remaining(), goal.averageMonthlySaving(),
                    afterDates.get(goal.goalId())));
        }
        long saving = baseline.averageMonthlySaving();
        Long available = baseline.monthlyIncome() == null ? null
                : baseline.monthlyIncome() - baseline.averageMonthlySpending() - saving;
        return new Snapshot(baseline.baseDate(), baseline.monthlyIncome(), baseline.averageMonthlySpending(), saving, available, goals);
    }

    /** 기준선 그대로의 스냅샷(before). */
    static Snapshot baselineSnapshot(SimulationBaseline baseline) {
        Map<Long, LocalDate> dates = new LinkedHashMap<>();
        baseline.savingGoals().forEach(goal -> dates.put(goal.goalId(), goal.expectedAchievementDate()));
        return snapshotWithDates(baseline, dates);
    }

    /**
     * 자금 출처·목표 귀속을 알 수 없는 유형의 목표 영향: 모든 목표의 after는 null이고 사유를 남긴다. 목표가 없으면 빈 목록.
     * 이미 달성한 목표는 전후 모두 기준일이며 {@code ALREADY_ACHIEVED}다.
     */
    record NotComputableGoals(Snapshot after, List<GoalImpact> impacts, List<GoalImpactDetail> details) {
    }

    static NotComputableGoals notComputableGoals(SimulationBaseline baseline, String reason) {
        Map<Long, LocalDate> dates = new LinkedHashMap<>();
        List<GoalImpact> impacts = new ArrayList<>();
        List<GoalImpactDetail> details = new ArrayList<>();
        for (SimulationBaseline.SavingGoal goal : baseline.savingGoals()) {
            boolean achieved = goal.remaining() <= 0;
            LocalDate after = achieved ? baseline.baseDate() : null;
            dates.put(goal.goalId(), after);
            impacts.add(new GoalImpact(goal.goalId(), goal.expectedAchievementDate(), after));
            details.add(new GoalImpactDetail(goal.goalId(), achieved ? GoalImpactDetail.COMPUTED : GoalImpactDetail.NOT_COMPUTABLE,
                    achieved ? List.of(SimulationAssumptions.ALREADY_ACHIEVED) : List.of(reason)));
        }
        return new NotComputableGoals(snapshotWithDates(baseline, dates), impacts, details);
    }

    /** 금액 합계·차이가 long 범위를 넘으면 전체 실행을 거부한다. {@code Math.*Exact}·{@code longValueExact}의 예외를 한 코드로 모은다. */
    static <T> T exact(Supplier<T> calculation) {
        try {
            return calculation.get();
        } catch (ArithmeticException e) {
            throw new BusinessException(ErrorCode.SIMULATION_AMOUNT_OUT_OF_RANGE);
        }
    }

    static String dateText(LocalDate date) {
        return date == null ? "달성 불가" : date.toString();
    }

    static String won(long amount) {
        return String.format("%,d원", amount);
    }
}
