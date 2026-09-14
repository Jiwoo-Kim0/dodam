package com.ssafy.dodam.domain.goal.service;

import com.ssafy.dodam.domain.account.entity.Account;
import com.ssafy.dodam.domain.account.repository.AccountRepository;
import com.ssafy.dodam.domain.goal.dto.response.GoalForecastResponse;
import com.ssafy.dodam.domain.goal.dto.response.GoalProgressResponse;
import com.ssafy.dodam.domain.goal.dto.response.GoalRiskResponse;
import com.ssafy.dodam.domain.goal.dto.response.RequiredSavingResponse;
import com.ssafy.dodam.domain.goal.dto.response.SpendingGoalProgressResponse;
import com.ssafy.dodam.domain.goal.entity.Goal;
import com.ssafy.dodam.domain.goal.entity.GoalAccount;
import com.ssafy.dodam.domain.goal.entity.GoalHistoryEvent;
import com.ssafy.dodam.domain.goal.entity.GoalType;
import com.ssafy.dodam.domain.goal.entity.SavingGoal;
import com.ssafy.dodam.domain.goal.entity.SpendingGoal;
import com.ssafy.dodam.domain.goal.repository.GoalAccountRepository;
import com.ssafy.dodam.domain.goal.repository.GoalRepository;
import com.ssafy.dodam.domain.goal.repository.SavingGoalRepository;
import com.ssafy.dodam.domain.goal.repository.SpendingGoalRepository;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingAnalysisReader;
import com.ssafy.dodam.domain.user.repository.FinancialProfileRepository;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API 12 목표 계산(S15P21D105-510): 진행률, 필요 월 저축액, 예상 달성일, 소비목표 진행률, 위험상태.
 *
 * <p>기획안 「모으기 목표 달성 위험」·「줄이기 목표 초과 위험」의 파생 데이터를 그대로 구현했다.
 * <ul>
 *   <li>현재 확보 금액 = 연결 계좌 잔액 합. 조회 시점에 다시 계산하고 `goal.current_amount`도 갱신한다(동기화로 잔액이 바뀐 뒤에도 맞게).</li>
 *   <li>현재 저축속도(월평균 저축액) = 확보 금액 / 경과 개월(목표 생성일부터, 최소 1개월).</li>
 *   <li>필요 월 저축액 = 남은 금액 / 남은 개월(올림).</li>
 *   <li>예상 달성일 = 기준일 + (남은 금액 / 저축속도)개월(올림).</li>
 *   <li>줄이기 목표: 이번 달 소비(506 정의)를 월 예산과 비교하고 현재 소비속도로 월말 소비를 예측한다.</li>
 * </ul>
 */
@Service
@Transactional
public class GoalCalculationService {

    static final String SAVING_ONLY_MESSAGE = "모으기 목표에서만 조회할 수 있습니다.";
    static final String SPENDING_ONLY_MESSAGE = "줄이기 목표에서만 조회할 수 있습니다.";

    /** 진행률이 기대 진행률에서 이만큼 벗어나면 AHEAD / BEHIND로 본다. */
    static final double PACE_TOLERANCE_POINT = 5.0;
    /** 저축속도가 필요 월 저축액에 이 비율 이상 못 미치면 HIGH. */
    static final double HIGH_SHORTFALL_RATE = 0.3;
    static final double BEHIND_SCHEDULE_POINT = 10.0;
    static final double GOAL_WARNING_RATE_PERCENT = 80.0;

    /** 진단 결과와 함께 보존할 실제 위험 판정 상수. 계산 상수를 복제하지 않는다. */
    public RiskPolicy riskPolicySnapshot() {
        return new RiskPolicy(HIGH_SHORTFALL_RATE, BEHIND_SCHEDULE_POINT, 1);
    }

    public record RiskPolicy(double highShortfallRate, double behindSchedulePoint, int spendingBudgetMultiplier) { }

    private final GoalRepository goalRepository;
    private final SavingGoalRepository savingGoalRepository;
    private final SpendingGoalRepository spendingGoalRepository;
    private final GoalAccountRepository goalAccountRepository;
    private final AccountRepository accountRepository;
    private final FinancialProfileRepository financialProfileRepository;
    private final JdbcSpendingAnalysisReader spendingAnalysisReader;
    private final GoalHistoryRecorder goalHistoryRecorder;
    private final GoalClock goalClock;

    public GoalCalculationService(
            GoalRepository goalRepository,
            SavingGoalRepository savingGoalRepository,
            SpendingGoalRepository spendingGoalRepository,
            GoalAccountRepository goalAccountRepository,
            AccountRepository accountRepository,
            FinancialProfileRepository financialProfileRepository,
            JdbcSpendingAnalysisReader spendingAnalysisReader,
            GoalHistoryRecorder goalHistoryRecorder,
            GoalClock goalClock
    ) {
        this.goalRepository = goalRepository;
        this.savingGoalRepository = savingGoalRepository;
        this.spendingGoalRepository = spendingGoalRepository;
        this.goalAccountRepository = goalAccountRepository;
        this.accountRepository = accountRepository;
        this.financialProfileRepository = financialProfileRepository;
        this.spendingAnalysisReader = spendingAnalysisReader;
        this.goalHistoryRecorder = goalHistoryRecorder;
        this.goalClock = goalClock;
    }

    // ---------- 진행률 ----------

    public GoalProgressResponse getProgress(Long userId, Long goalId) {
        SavingContext context = savingContext(userId, goalId, goalClock.today());
        return new GoalProgressResponse(
                context.goal.getId(),
                context.goal.getStatus().name(),
                context.target(),
                context.current,
                context.remaining(),
                context.progressRate(),
                context.startDate,
                context.targetDate(),
                context.today,
                context.totalDays(),
                context.daysElapsed(),
                context.daysRemaining(),
                context.expectedProgressRate(),
                round1(context.progressRate() - context.expectedProgressRate()),
                context.paceStatus(),
                context.linkedAccountCount
        );
    }

    // ---------- 필요 월 저축액 ----------

    public RequiredSavingResponse getRequiredSaving(Long userId, Long goalId) {
        SavingContext context = savingContext(userId, goalId, goalClock.today());
        long required = context.requiredMonthlySaving();
        long average = context.averageMonthlySaving();
        Double incomeRatio = financialProfileRepository.findById(userId)
                .map(profile -> profile.getMonthlyIncome())
                .filter(income -> income != null && income > 0)
                .map(income -> round1(required * 100.0 / income))
                .orElse(null);

        return new RequiredSavingResponse(
                context.goal.getId(),
                context.target(),
                context.current,
                context.remaining(),
                context.today,
                context.targetDate(),
                context.monthsRemaining(),
                context.daysRemaining(),
                required,
                context.requiredDailySaving(),
                context.monthsElapsed(),
                average,
                required - average,
                context.feasibility(),
                incomeRatio
        );
    }

    // ---------- 예상 달성일 ----------

    /**
     * @param baseDate 예측 기준일. null이면 오늘
     * @param months   예측 구간(개월). null이면 12, 1~120
     */
    public GoalForecastResponse getForecast(Long userId, Long goalId, LocalDate baseDate, Integer months) {
        LocalDate today = goalClock.today();
        SavingContext context = savingContext(userId, goalId, today);
        return forecast(context, baseDate == null ? today : baseDate, months == null ? 12 : months);
    }

    /**
     * 진단(546 goal-impact)용 예상 달성일. 접수 기준일을 오늘로 삼아 평가하며 목표 잔액·변화 이력을 수정하지 않는다.
     * 월별 projection은 진단 스냅샷에 보존할 대상이 아니라 비운다.
     */
    @Transactional(readOnly = true)
    public GoalForecastResponse getForecast(Long userId, Long goalId, LocalDate today) {
        return forecast(savingContext(userId, goalId, today, false), today, 0);
    }

    private GoalForecastResponse forecast(SavingContext context, LocalDate base, int horizon) {
        long pace = context.averageMonthlySaving();
        long remaining = context.remaining();
        Integer monthsToAchieve;
        LocalDate expected;
        if (remaining <= 0) {
            monthsToAchieve = 0;
            expected = base;
        } else if (pace <= 0) {
            monthsToAchieve = null;
            expected = null;
        } else {
            monthsToAchieve = (int) ceilDivide(remaining, pace);
            expected = base.plusMonths(monthsToAchieve);
        }

        List<GoalForecastResponse.Point> projection = new ArrayList<>();
        for (int i = 1; i <= horizon; i++) {
            long projected = Math.min(context.target(), context.current + pace * i);
            projection.add(new GoalForecastResponse.Point(
                    YearMonth.from(base.plusMonths(i)).toString(), projected, projected >= context.target()));
        }

        return new GoalForecastResponse(
                context.goal.getId(),
                context.target(),
                context.current,
                remaining,
                base,
                context.targetDate(),
                pace,
                monthsToAchieve,
                expected,
                expected == null ? null : ChronoUnit.DAYS.between(context.targetDate(), expected),
                expected != null && !expected.isAfter(context.targetDate()),
                projection
        );
    }

    // ---------- 소비목표 진행률 ----------

    public SpendingGoalProgressResponse getSpendingProgress(Long userId, Long goalId) {
        Goal goal = findOwned(userId, goalId);
        if (goal.getType() != GoalType.SPENDING) {
            throw new InvalidRequestException(SPENDING_ONLY_MESSAGE);
        }
        SpendingGoal spendingGoal = spendingGoalRepository.findById(goal.getId())
                .orElseThrow(() -> new ResourceNotFoundException(GoalService.GOAL_NOT_FOUND_MESSAGE));
        SpendingContext context = spendingContext(userId, goal, spendingGoal, goalClock.today());

        return new SpendingGoalProgressResponse(
                goal.getId(),
                goal.getStatus().name(),
                context.month.toString(),
                context.today,
                context.daysElapsed,
                context.daysInMonth,
                context.daysLeft(),
                context.budget,
                context.spent,
                context.budget - context.spent,
                context.usageRate(),
                context.dailyBudgetRemaining(),
                context.projected(),
                context.previousMonthSpending,
                Math.max(0L, context.previousMonthSpending - context.projected()),
                context.status()
        );
    }

    // ---------- 위험상태 ----------

    public GoalRiskResponse getRisk(Long userId, Long goalId) {
        return evaluateRisk(userId, goalId, goalClock.today(), true);
    }

    /** 진단은 접수 기준일로 평가하며 목표 잔액·변화 이력을 수정하지 않는다. */
    @Transactional(readOnly = true)
    public GoalRiskResponse getRisk(Long userId, Long goalId, LocalDate today) {
        return evaluateRisk(userId, goalId, today, false);
    }

    private GoalRiskResponse evaluateRisk(Long userId, Long goalId, LocalDate today, boolean updateAmount) {
        Goal goal = findOwned(userId, goalId);
        if (goal.getType() == GoalType.SAVING) {
            SavingContext context = savingContext(userId, goal.getId(), today, updateAmount);
            return savingRisk(context);
        }
        SpendingGoal spendingGoal = spendingGoalRepository.findById(goal.getId())
                .orElseThrow(() -> new ResourceNotFoundException(GoalService.GOAL_NOT_FOUND_MESSAGE));
        return spendingRisk(spendingContext(userId, goal, spendingGoal, today));
    }

    private GoalRiskResponse savingRisk(SavingContext context) {
        long required = context.requiredMonthlySaving();
        long average = context.averageMonthlySaving();
        GoalRiskResponse.Metrics metrics = GoalRiskResponse.Metrics.saving(
                required, average, context.progressRate(), context.expectedProgressRate());
        List<String> factors = new ArrayList<>();
        if (context.goal.getStatus().isTerminal() || context.remaining() <= 0) {
            return new GoalRiskResponse(context.goal.getId(), "SAVING", context.goal.getStatus().name(), "NONE", factors, metrics);
        }

        int level = 0; // 0 LOW, 1 MEDIUM, 2 HIGH
        if (context.targetDate().isBefore(context.today)) {
            factors.add("DEADLINE_PASSED");
            level = 2;
        }
        if (context.linkedAccountCount == 0) {
            factors.add("NO_LINKED_ACCOUNTS");
            level = Math.max(level, 1);
        }
        if (required > average) {
            factors.add("PACE_INSUFFICIENT");
            double shortfallRate = (required - average) / (double) required;
            level = Math.max(level, shortfallRate >= HIGH_SHORTFALL_RATE ? 2 : 1);
        }
        if (context.expectedProgressRate() - context.progressRate() > BEHIND_SCHEDULE_POINT) {
            factors.add("BEHIND_SCHEDULE");
            level = Math.max(level, 1);
        }
        return new GoalRiskResponse(context.goal.getId(), "SAVING", context.goal.getStatus().name(), levelName(level), factors, metrics);
    }

    private GoalRiskResponse spendingRisk(SpendingContext context) {
        GoalRiskResponse.Metrics metrics = GoalRiskResponse.Metrics.spending(
                context.budget, context.spent, context.projected(), context.usageRate());
        List<String> factors = new ArrayList<>();
        if (context.goal.getStatus().isTerminal()) {
            return new GoalRiskResponse(context.goal.getId(), "SPENDING", context.goal.getStatus().name(), "NONE", factors, metrics);
        }
        int level = 0;
        if (context.spent > context.budget) {
            factors.add("OVER_BUDGET");
            level = 2;
        } else if (context.projected() > context.budget) {
            factors.add("PACE_EXCEEDS_BUDGET");
            level = 1;
        }
        return new GoalRiskResponse(context.goal.getId(), "SPENDING", context.goal.getStatus().name(), levelName(level), factors, metrics);
    }

    // ---------- 컨텍스트 ----------

    private SavingContext savingContext(Long userId, Long goalId, LocalDate today) {
        return savingContext(userId, goalId, today, true);
    }

    private SavingContext savingContext(Long userId, Long goalId, LocalDate today, boolean updateAmount) {
        Goal goal = findOwned(userId, goalId);
        if (goal.getType() != GoalType.SAVING) {
            throw new InvalidRequestException(SAVING_ONLY_MESSAGE);
        }
        SavingGoal savingGoal = savingGoalRepository.findById(goal.getId())
                .orElseThrow(() -> new ResourceNotFoundException(GoalService.GOAL_NOT_FOUND_MESSAGE));

        List<GoalAccount> links = goalAccountRepository.findAllByGoalIdOrderByAccountIdAsc(goal.getId());
        long current = links.isEmpty()
                ? 0
                : accountRepository.findAllById(links.stream().map(GoalAccount::getAccountId).toList()).stream()
                        .mapToLong(Account::getBalance)
                        .sum();
        // 동기화로 계좌 잔액이 바뀐 뒤에도 저장된 모인 금액이 실제와 맞게 유지되도록 조회 시점에 갱신하고, 바뀌었으면 이력을 남긴다
        LocalDateTime now = goalClock.now();
        if (updateAmount && goal.updateCurrentAmount(Math.max(0L, current), now)) {
            goalHistoryRecorder.record(goal, savingGoal, GoalHistoryEvent.AMOUNT_UPDATED, now);
        }

        return new SavingContext(goal, savingGoal, Math.max(0L, current), links.size(), today);
    }

    private SpendingContext spendingContext(Long userId, Goal goal, SpendingGoal spendingGoal, LocalDate today) {
        YearMonth month = YearMonth.from(today);
        long spent = spendingAnalysisReader.readSpendingTotal(userId, month.atDay(1), today);
        YearMonth previous = month.minusMonths(1);
        long previousSpending = spendingAnalysisReader.readSpendingTotal(userId, previous.atDay(1), previous.atEndOfMonth());
        return new SpendingContext(goal, spendingGoal.getMonthlyBudget(), month, today, spent, previousSpending);
    }

    private Goal findOwned(Long userId, Long goalId) {
        return goalRepository.findByIdAndUserIdAndDeletedAtIsNull(goalId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(GoalService.GOAL_NOT_FOUND_MESSAGE));
    }

    // ---------- helpers ----------

    private static String levelName(int level) {
        return switch (level) {
            case 2 -> "HIGH";
            case 1 -> "MEDIUM";
            default -> "LOW";
        };
    }

    static long ceilDivide(long amount, long divisor) {
        if (divisor <= 0) {
            return amount;
        }
        return BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(divisor), 0, RoundingMode.CEILING).longValue();
    }

    static double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    /**
     * 모으기 목표 계산에 필요한 값 묶음. 기준일(today)에 대해 한 번만 계산한다.
     */
    private static final class SavingContext {
        private final Goal goal;
        private final SavingGoal savingGoal;
        private final long current;
        private final int linkedAccountCount;
        private final LocalDate today;
        private final LocalDate startDate;

        private SavingContext(Goal goal, SavingGoal savingGoal, long current, int linkedAccountCount, LocalDate today) {
            this.goal = goal;
            this.savingGoal = savingGoal;
            this.current = current;
            this.linkedAccountCount = linkedAccountCount;
            this.today = today;
            this.startDate = goal.getCreatedAt().toLocalDate();
        }

        long target() {
            return savingGoal.getTargetAmount();
        }

        LocalDate targetDate() {
            return savingGoal.getTargetDate();
        }

        long remaining() {
            return Math.max(0L, target() - current);
        }

        double progressRate() {
            return Math.min(100.0, round1(current * 100.0 / target()));
        }

        long totalDays() {
            return Math.max(1L, ChronoUnit.DAYS.between(startDate, targetDate()));
        }

        long daysElapsed() {
            return Math.max(0L, ChronoUnit.DAYS.between(startDate, today));
        }

        long daysRemaining() {
            return ChronoUnit.DAYS.between(today, targetDate());
        }

        double expectedProgressRate() {
            double rate = daysElapsed() * 100.0 / totalDays();
            return Math.max(0.0, Math.min(100.0, round1(rate)));
        }

        String paceStatus() {
            if (remaining() <= 0) {
                return "ACHIEVED";
            }
            double gap = progressRate() - expectedProgressRate();
            if (gap >= PACE_TOLERANCE_POINT) {
                return "AHEAD";
            }
            return gap <= -PACE_TOLERANCE_POINT ? "BEHIND" : "ON_TRACK";
        }

        /** 목표 생성일부터 기준일까지 경과 개월(최소 1). 첫 달에도 저축속도를 정의할 수 있게 한다. */
        int monthsElapsed() {
            return (int) Math.max(1L, ChronoUnit.MONTHS.between(startDate, today));
        }

        /** 기준일부터 목표일까지 남은 개월(올림). 지났으면 0. */
        int monthsRemaining() {
            if (!targetDate().isAfter(today)) {
                return 0;
            }
            long whole = ChronoUnit.MONTHS.between(today, targetDate());
            boolean partial = today.plusMonths(whole).isBefore(targetDate());
            return (int) (whole + (partial ? 1 : 0));
        }

        long averageMonthlySaving() {
            return current / monthsElapsed();
        }

        long requiredMonthlySaving() {
            int months = monthsRemaining();
            return months == 0 ? remaining() : ceilDivide(remaining(), months);
        }

        long requiredDailySaving() {
            long days = daysRemaining();
            return days <= 0 ? remaining() : ceilDivide(remaining(), days);
        }

        String feasibility() {
            if (remaining() <= 0) {
                return "ACHIEVED";
            }
            if (!targetDate().isAfter(today)) {
                return "DEADLINE_PASSED";
            }
            return averageMonthlySaving() >= requiredMonthlySaving() ? "SUFFICIENT" : "INSUFFICIENT";
        }
    }

    /**
     * 줄이기 목표 계산에 필요한 값 묶음.
     */
    private static final class SpendingContext {
        private final Goal goal;
        private final long budget;
        private final YearMonth month;
        private final LocalDate today;
        private final int daysElapsed;
        private final int daysInMonth;
        private final long spent;
        private final long previousMonthSpending;

        private SpendingContext(Goal goal, long budget, YearMonth month, LocalDate today, long spent, long previousMonthSpending) {
            this.goal = goal;
            this.budget = budget;
            this.month = month;
            this.today = today;
            this.daysElapsed = today.getDayOfMonth();
            this.daysInMonth = month.lengthOfMonth();
            this.spent = spent;
            this.previousMonthSpending = previousMonthSpending;
        }

        int daysLeft() {
            return daysInMonth - daysElapsed;
        }

        double usageRate() {
            return round1(spent * 100.0 / budget);
        }

        long projected() {
            return BigDecimal.valueOf(spent)
                    .multiply(BigDecimal.valueOf(daysInMonth))
                    .divide(BigDecimal.valueOf(daysElapsed), 0, RoundingMode.HALF_UP)
                    .longValue();
        }

        Long dailyBudgetRemaining() {
            if (daysLeft() <= 0) {
                return null;
            }
            return Math.max(0L, (budget - spent) / daysLeft());
        }

        String status() {
            double usage = usageRate();
            if (usage > 100.0) {
                return "EXCEEDED";
            }
            return usage >= GOAL_WARNING_RATE_PERCENT ? "WARNING" : "ON_TRACK";
        }
    }
}
