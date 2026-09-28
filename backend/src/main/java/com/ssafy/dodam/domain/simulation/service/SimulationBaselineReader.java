package com.ssafy.dodam.domain.simulation.service;

import com.ssafy.dodam.domain.goal.entity.Goal;
import com.ssafy.dodam.domain.goal.entity.GoalStatus;
import com.ssafy.dodam.domain.goal.entity.GoalType;
import com.ssafy.dodam.domain.goal.entity.SavingGoal;
import com.ssafy.dodam.domain.goal.entity.SpendingGoal;
import com.ssafy.dodam.domain.goal.repository.GoalRepository;
import com.ssafy.dodam.domain.goal.repository.SavingGoalRepository;
import com.ssafy.dodam.domain.goal.repository.SpendingGoalRepository;
import com.ssafy.dodam.domain.goal.service.GoalAllocationReader;
import com.ssafy.dodam.domain.goal.service.GoalSavingPaceReader;
import com.ssafy.dodam.domain.simulation.dto.SimulationBaseline;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingAnalysisReader;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingReader;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingReader.CategorySpendingRow;
import com.ssafy.dodam.domain.transaction.service.SpendingQueryService;
import com.ssafy.dodam.domain.user.repository.FinancialProfileRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실행 시점의 사용자 현황을 읽기 전용으로 모은다. Goal·Spending API의 정의를 그대로 따르되 그 서비스들을 호출하지 않는다 —
 * Goal 계산 경로는 조회 중 목표 잔액을 갱신하고 이력을 남기므로 시뮬레이션이 부르면 안 된다.
 *
 * <p>소비 기준 구간은 기준일이 속한 달을 제외한 직전 3개 달력월이다(기획안 예시 "최근 3개월 평균"). 진행 중인 달을 넣으면
 * 월초에는 평균이 크게 낮아진다. 거래가 하나도 없으면 0과 {@code SPENDING_NO_TRANSACTIONS}를 남긴다.
 */
@Component
@Transactional(readOnly = true)
public class SimulationBaselineReader {
    static final int SPENDING_WINDOW_MONTHS = 3;
    private final FinancialProfileRepository profiles;
    private final JdbcSpendingReader spendingReader;
    private final JdbcSpendingAnalysisReader spendingAnalysisReader;
    private final GoalRepository goals;
    private final SavingGoalRepository savingGoals;
    private final SpendingGoalRepository spendingGoals;
    private final GoalAllocationReader allocationReader;
    private final GoalSavingPaceReader paceReader;

    public SimulationBaselineReader(FinancialProfileRepository profiles, JdbcSpendingReader spendingReader,
            JdbcSpendingAnalysisReader spendingAnalysisReader, GoalRepository goals, SavingGoalRepository savingGoals,
            SpendingGoalRepository spendingGoals, GoalAllocationReader allocationReader, GoalSavingPaceReader paceReader) {
        this.profiles = profiles;
        this.spendingReader = spendingReader;
        this.spendingAnalysisReader = spendingAnalysisReader;
        this.goals = goals;
        this.savingGoals = savingGoals;
        this.spendingGoals = spendingGoals;
        this.allocationReader = allocationReader;
        this.paceReader = paceReader;
    }

    public SimulationBaseline read(Long userId, LocalDate baseDate) {
        List<String> coverage = new ArrayList<>();
        Long income = profiles.findById(userId).map(p -> p.getMonthlyIncome()).orElse(null);
        if (income == null) {
            coverage.add(SimulationBaseline.INCOME_UNKNOWN);
        }

        YearMonth current = YearMonth.from(baseDate);
        LocalDate windowStart = current.minusMonths(SPENDING_WINDOW_MONTHS).atDay(1);
        LocalDate windowEnd = current.minusMonths(1).atEndOfMonth();
        Map<String, Long> byCategory = new HashMap<>();
        long total = 0;
        for (CategorySpendingRow row : spendingReader.readByMonthAndCategory(userId, windowStart, windowEnd)) {
            String code = row.categoryId() == null ? SpendingQueryService.UNCATEGORIZED_CODE : row.code();
            byCategory.merge(code, row.total(), Long::sum);
            total += row.total();
        }
        if (total == 0) {
            coverage.add(SimulationBaseline.SPENDING_NO_TRANSACTIONS);
        }
        byCategory.replaceAll((code, sum) -> sum / SPENDING_WINDOW_MONTHS);

        List<Goal> active = goals.findAllByUserIdAndDeletedAtIsNull(userId).stream()
                .filter(g -> g.getStatus() == GoalStatus.ACTIVE).toList();
        List<SimulationBaseline.SavingGoal> saving = readSavingGoals(userId, active, baseDate);
        if (saving.isEmpty()) {
            coverage.add(SimulationBaseline.NO_ACTIVE_SAVING_GOAL);
        }
        List<SimulationBaseline.SpendingGoal> spending = readSpendingGoals(userId, active, baseDate);

        return new SimulationBaseline(baseDate, income, total / SPENDING_WINDOW_MONTHS, byCategory, saving, spending, coverage);
    }

    private List<SimulationBaseline.SavingGoal> readSavingGoals(Long userId, List<Goal> active, LocalDate baseDate) {
        List<Goal> savingType = active.stream().filter(g -> g.getType() == GoalType.SAVING).toList();
        if (savingType.isEmpty()) {
            return List.of();
        }
        List<Long> ids = savingType.stream().map(Goal::getId).toList();
        Map<Long, SavingGoal> details = savingGoals.findAllByGoalIdIn(ids).stream()
                .collect(Collectors.toMap(SavingGoal::getGoalId, Function.identity()));
        // Goal 도메인과 같은 정의(626 GoalAllocationReader): 모인 금액 = 예·적금 전액 + 입출금은 그 목표에 담은 금액.
        // 입출금 하나를 여러 목표에 나눠 담아도 같은 잔액이 두 목표에 중복 합산되지 않는다.
        Map<Long, GoalAllocationReader.GoalAllocation> allocations = allocationReader.readGoals(userId, ids);

        List<SimulationBaseline.SavingGoal> result = new ArrayList<>();
        for (Goal goal : savingType) {
            SavingGoal detail = details.get(goal.getId());
            if (detail == null) {
                continue;
            }
            GoalAllocationReader.GoalAllocation allocation = allocations.get(goal.getId());
            List<Long> linked = allocation == null ? List.of() : allocation.accountIds();
            // 속도 = Goal 도메인과 같은 정의(694 GoalSavingPaceReader: 연결 계좌의 최근 완료 월 순증가)
            long current = allocation == null ? 0L : allocation.securedAmount();
            long pace = paceReader.monthlyPace(userId, allocation, baseDate);
            long remaining = Math.max(0L, detail.getTargetAmount() - current);
            result.add(new SimulationBaseline.SavingGoal(goal.getId(), goal.getName(), detail.getTargetAmount(), current,
                    remaining, pace, detail.getTargetDate(), GoalAchievementProjection.expectedDate(remaining, pace, baseDate),
                    linked));
        }
        return result;
    }

    private List<SimulationBaseline.SpendingGoal> readSpendingGoals(Long userId, List<Goal> active, LocalDate baseDate) {
        List<Goal> spendingType = active.stream().filter(g -> g.getType() == GoalType.SPENDING).toList();
        if (spendingType.isEmpty()) {
            return List.of();
        }
        Map<Long, SpendingGoal> details = spendingGoals.findAllByGoalIdIn(spendingType.stream().map(Goal::getId).toList())
                .stream().collect(Collectors.toMap(SpendingGoal::getGoalId, Function.identity()));
        YearMonth month = YearMonth.from(baseDate);
        long spent = spendingAnalysisReader.readSpendingTotal(userId, month.atDay(1), baseDate);
        // Goal SpendingContext.projected()와 같은 식: spent × 월 일수 / 경과 일수, 반올림
        long projected = BigDecimal.valueOf(spent).multiply(BigDecimal.valueOf(month.lengthOfMonth()))
                .divide(BigDecimal.valueOf(baseDate.getDayOfMonth()), 0, RoundingMode.HALF_UP).longValue();
        List<SimulationBaseline.SpendingGoal> result = new ArrayList<>();
        for (Goal goal : spendingType) {
            SpendingGoal detail = details.get(goal.getId());
            if (detail != null) {
                result.add(new SimulationBaseline.SpendingGoal(goal.getId(), goal.getName(), detail.getMonthlyBudget(), spent, projected));
            }
        }
        return result;
    }
}
