package com.ssafy.dodam.domain.simulation.service;

import com.ssafy.dodam.domain.goal.service.GoalForecastFormula;
import com.ssafy.dodam.domain.simulation.dto.SimulationBaseline;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;

/**
 * 날짜별 절감액을 반영한 모으기 목표 예상 달성일(585 §10.5). {@link GoalForecastFormula}와 같은 달력 개월 축 위에서
 * 약정 상환일마다 발생하는 절감·증가분을 "그 날짜 이후 첫 투영 구간"에 얹는다.
 *
 * <p>투영일은 {@code t(k) = base.plusMonths(k)} (k ≥ 1, 최초 기준일 기준이라 말일이 복원된다). 구간 {@code (t(k−1), t(k)]}에
 * 실제로 발생한 절감액만 모아 {@code max(0, 기존 월 저축속도 + 구간 합)}을 그 구간의 저축액으로 본다. 음수여도 목표 원금을
 * 인출하지 않는다(0 하한). 남은 금액이 처음 0 이하가 되는 구간에서 달성일은 구간 일수에 비례한 일 단위 날짜다(658). 다만
 * 양수 절감액을 발생일보다 먼저 쓰지 않으므로, 그 날짜가 구간 안의 마지막 양수 사건일보다 앞서면 사건일로 늦춘다.
 * 마지막 사건을 포함한 구간 뒤에는 기존 속도로 같은 축에서 이어 계산한다({@code GoalForecastFormula.expectedDate(…, offset=k)}).
 *
 * <p>변화가 전혀 없으면 기준선 날짜를 그대로 돌려준다 — 같은 식이지만 "0 변화에서 before≠after"가 생길 여지를 없앤다.
 */
public final class GoalDateProjection {
    private GoalDateProjection() {
    }

    /**
     * @param remaining  기준일 남은 금액
     * @param pace       기존 월 저축속도
     * @param beforeDate 기준선 예상 달성일(변화가 없을 때 그대로 반환)
     * @param deltas     날짜별 이 목표에 배분된 변화액. 기준일 이후 날짜만
     * @return 예상 달성일. 속도 0이고 모든 예정 변화 반영 후에도 미달성이면 null
     */
    public static LocalDate project(long remaining, long pace, LocalDate base, LocalDate beforeDate, NavigableMap<LocalDate, Long> deltas) {
        if (remaining <= 0) {
            return base;
        }
        boolean unchanged = deltas.values().stream().allMatch(delta -> delta == 0L);
        if (unchanged) {
            return beforeDate;
        }
        LocalDate lastEvent = deltas.lastKey();
        long left = remaining;
        int k = 0;
        LocalDate previous = base;
        while (true) {
            k++;
            LocalDate projection = base.plusMonths(k);
            long delta = 0;
            for (long value : deltas.subMap(previous, false, projection, true).values()) {
                delta = Math.addExact(delta, value);
            }
            long contributed = Math.max(0L, Math.addExact(pace, delta));
            left -= contributed;
            if (left <= 0) {
                LocalDate achieved = GoalForecastFormula.dateWithin(previous, projection, left + contributed, contributed);
                LocalDate lastPositiveEvent = null;
                for (Map.Entry<LocalDate, Long> event : deltas.subMap(previous, false, projection, true).entrySet()) {
                    if (event.getValue() > 0) {
                        lastPositiveEvent = event.getKey();
                    }
                }
                return lastPositiveEvent != null && achieved.isBefore(lastPositiveEvent) ? lastPositiveEvent : achieved;
            }
            previous = projection;
            if (!projection.isBefore(lastEvent)) {
                break;
            }
        }
        if (pace <= 0) {
            return null;
        }
        return GoalForecastFormula.expectedDate(left, pace, base, k);
    }

    /**
     * 날짜별 총 변화액을 기준일 미달성 목표에 남은 금액 비례로 배분한다(goalId 오름차순, 마지막 목표가 나머지). 비중은 기준일
     * remaining으로 고정하며 도중에 달성한 목표의 몫을 재배분하지 않는다. 미달성 목표가 없으면 배분하지 않는다.
     *
     * @return goalId → (날짜 → 배분액). 배분 대상이 아닌(이미 달성한) 목표는 빈 map
     */
    public static Map<Long, NavigableMap<LocalDate, Long>> allocate(List<SimulationBaseline.SavingGoal> goals,
            NavigableMap<LocalDate, Long> totalDeltas) {
        List<SimulationBaseline.SavingGoal> targets = goals.stream()
                .filter(goal -> goal.remaining() > 0)
                .sorted(Comparator.comparing(SimulationBaseline.SavingGoal::goalId))
                .toList();
        Map<Long, NavigableMap<LocalDate, Long>> allocations = new LinkedHashMap<>();
        for (SimulationBaseline.SavingGoal goal : goals) {
            allocations.put(goal.goalId(), new TreeMap<>());
        }
        if (targets.isEmpty()) {
            return allocations;
        }
        long remainingSum = 0;
        for (SimulationBaseline.SavingGoal goal : targets) {
            remainingSum = Math.addExact(remainingSum, goal.remaining());
        }
        for (Map.Entry<LocalDate, Long> entry : totalDeltas.entrySet()) {
            long amount = entry.getValue();
            long allocated = 0;
            for (int i = 0; i < targets.size(); i++) {
                SimulationBaseline.SavingGoal goal = targets.get(i);
                long share = i == targets.size() - 1 ? amount - allocated
                        : Math.floorDiv(Math.multiplyExact(amount, goal.remaining()), remainingSum);
                allocated += share;
                allocations.get(goal.goalId()).put(entry.getKey(), share);
            }
        }
        return allocations;
    }

    /** 사건 날짜가 기준일 이후인지 확인하며 날짜별 합계를 만든다. */
    public static NavigableMap<LocalDate, Long> sumByDate(LocalDate base, List<DatedAmount> amounts) {
        NavigableMap<LocalDate, Long> result = new TreeMap<>();
        for (DatedAmount amount : amounts) {
            if (!amount.date().isAfter(base)) {
                throw new IllegalArgumentException("목표 투영 사건은 기준일 이후여야 합니다: " + amount.date());
            }
            result.merge(amount.date(), amount.amount(), Math::addExact);
        }
        return result;
    }

    public record DatedAmount(LocalDate date, long amount) {
    }
}
