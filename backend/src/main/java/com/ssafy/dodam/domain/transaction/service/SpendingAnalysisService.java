package com.ssafy.dodam.domain.transaction.service;

import com.ssafy.dodam.domain.transaction.dto.response.SavingRatioResponse;
import com.ssafy.dodam.domain.transaction.dto.response.SpendingAnomalyResponse;
import com.ssafy.dodam.domain.transaction.dto.response.SpendingGoalComparisonResponse;
import com.ssafy.dodam.domain.transaction.dto.response.SpendingTypeBreakdownResponse;
import com.ssafy.dodam.domain.transaction.entity.SpendingType;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingAnalysisReader;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingAnalysisReader.SpendingGoalRow;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingReader;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingReader.CategorySpendingRow;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingReader.MonthlySpendingRow;
import com.ssafy.dodam.domain.transaction.repository.TransactionCategoryRepository;
import com.ssafy.dodam.domain.user.repository.FinancialProfileRepository;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.policy.YearMonthPeriod;
import com.ssafy.dodam.global.policy.YearMonthPeriodPolicy;
import com.ssafy.dodam.global.policy.YearMonthPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API 6 소비 분석(S15P21D105-507): 소비·저축 비율, 소비 급증, 소비목표 비교, 고정·변동지출.
 * "소비" 정의는 506과 같다({@link JdbcSpendingReader#SPENDING_WHERE}).
 */
@Service
@Transactional(readOnly = true)
public class SpendingAnalysisService {

    /** 기획안 「변동지출 급증」의 기준 증가율. 예시(38% 증가)가 잡히고 소폭 변동은 걸러지는 값으로 잡았다. 팀 확정 전 기본값. */
    public static final double ANOMALY_THRESHOLD_RATE_PERCENT = 20.0;
    /** 소액 보정: 월평균 증가액이 이 금액 미만이면 급증으로 보지 않는다. */
    public static final long ANOMALY_MINIMUM_INCREASE = 10_000L;
    public static final double GOAL_WARNING_RATE_PERCENT = 80.0;

    static final String GOAL_NOT_FOUND_MESSAGE = "소비 목표를 찾을 수 없습니다.";

    private final JdbcSpendingReader spendingReader;
    private final JdbcSpendingAnalysisReader analysisReader;
    private final TransactionCategoryRepository transactionCategoryRepository;
    private final FinancialProfileRepository financialProfileRepository;
    private final YearMonthPolicy yearMonthPolicy;
    private final YearMonthPeriodPolicy yearMonthPeriodPolicy;
    private final TransactionClock transactionClock;

    public SpendingAnalysisService(
            JdbcSpendingReader spendingReader,
            JdbcSpendingAnalysisReader analysisReader,
            TransactionCategoryRepository transactionCategoryRepository,
            FinancialProfileRepository financialProfileRepository,
            YearMonthPolicy yearMonthPolicy,
            YearMonthPeriodPolicy yearMonthPeriodPolicy,
            TransactionClock transactionClock
    ) {
        this.spendingReader = spendingReader;
        this.analysisReader = analysisReader;
        this.transactionCategoryRepository = transactionCategoryRepository;
        this.financialProfileRepository = financialProfileRepository;
        this.yearMonthPolicy = yearMonthPolicy;
        this.yearMonthPeriodPolicy = yearMonthPeriodPolicy;
        this.transactionClock = transactionClock;
    }

    // ---------- 소비 및 저축 비율 ----------

    public SavingRatioResponse getSavingRatio(Long userId, String yearMonth) {
        YearMonth month = yearMonthPolicy.resolveHistorical(yearMonth, transactionClock.now().toLocalDate());
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        long spending = analysisReader.readSpendingTotal(userId, start, end);
        long savingTransfer = analysisReader.readSavingTransferTotal(userId, start, end);

        Long profileIncome = financialProfileRepository.findById(userId)
                .map(profile -> profile.getMonthlyIncome())
                .filter(value -> value != null && value > 0)
                .orElse(null);
        long income;
        String incomeSource;
        if (profileIncome != null) {
            income = profileIncome;
            incomeSource = "PROFILE";
        } else {
            income = analysisReader.readIncomeTotal(userId, start, end);
            incomeSource = income > 0 ? "TRANSACTIONS" : "NONE";
        }

        long budget = analysisReader.readSpendingGoals(userId).stream()
                .filter(goal -> "ACTIVE".equals(goal.status()))
                .mapToLong(SpendingGoalRow::monthlyBudget)
                .sum();
        Double spendingRatio = percent(spending, income);
        Double targetSpendingRatio = budget > 0 ? percent(budget, income) : null;

        return new SavingRatioResponse(
                month.toString(),
                income,
                incomeSource,
                spending,
                savingTransfer,
                income - spending,
                spendingRatio,
                percent(income - spending, income),
                targetSpendingRatio,
                targetSpendingRatio == null ? null : round1(100.0 - targetSpendingRatio),
                targetSpendingRatio == null || spendingRatio == null ? null : spendingRatio > targetSpendingRatio
        );
    }

    // ---------- 소비 급증 ----------

    /**
     * 요청 기간(기본 최근 3개월)을 "최근", 직전 같은 길이를 "이전"으로 두고 카테고리별 월평균을 비교한다.
     */
    public SpendingAnomalyResponse getAnomalies(Long userId, String startMonth, String endMonth, String category) {
        YearMonthPeriod recent = yearMonthPeriodPolicy.resolve(startMonth, endMonth);
        int months = (int) ChronoUnit.MONTHS.between(recent.startMonth(), recent.endMonth()) + 1;
        YearMonth baselineEnd = recent.startMonth().minusMonths(1);
        YearMonth baselineStart = baselineEnd.minusMonths(months - 1L);
        String categoryCode = resolveCategoryFilter(category);

        Map<String, CategoryAccumulator> byCategory = new LinkedHashMap<>();
        for (CategorySpendingRow row : spendingReader.readByMonthAndCategory(
                userId, baselineStart.atDay(1), recent.endMonth().atEndOfMonth())) {
            String code = codeOf(row);
            if (categoryCode != null && !categoryCode.equals(code)) {
                continue;
            }
            CategoryAccumulator accumulator = byCategory.computeIfAbsent(code,
                    key -> new CategoryAccumulator(row.categoryId(), code, nameOf(row)));
            if (row.yearMonth().isBefore(recent.startMonth())) {
                accumulator.baselineTotal += row.total();
            } else {
                accumulator.recentTotal += row.total();
            }
        }

        List<SpendingAnomalyResponse.Item> anomalies = new ArrayList<>();
        for (CategoryAccumulator accumulator : byCategory.values()) {
            long recentAverage = divide(accumulator.recentTotal, months);
            long baselineAverage = divide(accumulator.baselineTotal, months);
            long increase = recentAverage - baselineAverage;
            if (baselineAverage <= 0 || increase < ANOMALY_MINIMUM_INCREASE) {
                continue;
            }
            double rate = round1(increase * 100.0 / baselineAverage);
            if (rate <= ANOMALY_THRESHOLD_RATE_PERCENT) {
                continue;
            }
            anomalies.add(new SpendingAnomalyResponse.Item(
                    accumulator.categoryId, accumulator.code, accumulator.name,
                    recentAverage, baselineAverage, increase, rate));
        }
        anomalies.sort(Comparator.comparingDouble(SpendingAnomalyResponse.Item::increaseRatePercent).reversed()
                .thenComparing(SpendingAnomalyResponse.Item::code));

        return new SpendingAnomalyResponse(
                new SpendingAnomalyResponse.Period(recent.startMonth().toString(), recent.endMonth().toString(), months),
                new SpendingAnomalyResponse.Period(baselineStart.toString(), baselineEnd.toString(), months),
                ANOMALY_THRESHOLD_RATE_PERCENT,
                ANOMALY_MINIMUM_INCREASE,
                categoryCode,
                byCategory.size(),
                anomalies
        );
    }

    // ---------- 소비목표 비교 ----------

    /**
     * @param goalId 주면 그 소비목표 1건(상태 무관, 삭제된 것·타인 것은 404). 없으면 ACTIVE 소비목표 전부
     */
    public SpendingGoalComparisonResponse getGoalComparison(Long userId, Long goalId, String yearMonth) {
        LocalDate today = transactionClock.now().toLocalDate();
        YearMonth month = yearMonthPolicy.resolveHistorical(yearMonth, today);
        boolean currentMonth = month.equals(YearMonth.from(today));
        LocalDate asOf = currentMonth ? today : month.atEndOfMonth();
        int daysElapsed = asOf.getDayOfMonth();
        int daysInMonth = month.lengthOfMonth();

        List<SpendingGoalRow> goals = analysisReader.readSpendingGoals(userId);
        if (goalId != null) {
            goals = goals.stream().filter(goal -> goal.goalId().equals(goalId)).toList();
            if (goals.isEmpty()) {
                throw new ResourceNotFoundException(GOAL_NOT_FOUND_MESSAGE);
            }
        } else {
            goals = goals.stream().filter(goal -> "ACTIVE".equals(goal.status())).toList();
        }

        long spending = analysisReader.readSpendingTotal(userId, month.atDay(1), asOf);
        long projected = daysElapsed == 0 ? 0 : BigDecimal.valueOf(spending)
                .multiply(BigDecimal.valueOf(daysInMonth))
                .divide(BigDecimal.valueOf(daysElapsed), 0, RoundingMode.HALF_UP)
                .longValue();
        int daysLeft = currentMonth ? daysInMonth - daysElapsed : 0;

        List<SpendingGoalComparisonResponse.Goal> items = goals.stream()
                .map(goal -> {
                    long remaining = goal.monthlyBudget() - spending;
                    double usage = round1(spending * 100.0 / goal.monthlyBudget());
                    Long dailyBudget = !currentMonth || daysLeft <= 0
                            ? null
                            : Math.max(0L, divide(remaining, daysLeft));
                    return new SpendingGoalComparisonResponse.Goal(
                            goal.goalId(), goal.name(), goal.status(), goal.monthlyBudget(),
                            remaining, usage, projected, dailyBudget, goalStatus(usage));
                })
                .toList();
        return new SpendingGoalComparisonResponse(month.toString(), asOf, daysElapsed, daysInMonth, spending, items);
    }

    // ---------- 고정·변동지출 ----------

    public SpendingTypeBreakdownResponse getBreakdown(Long userId, SpendingType spendingType, String startMonth, String endMonth) {
        YearMonthPeriod period = yearMonthPeriodPolicy.resolve(startMonth, endMonth);
        LocalDate start = period.startMonth().atDay(1);
        LocalDate end = period.endMonth().atEndOfMonth();
        String type = spendingType.name();

        Map<YearMonth, MonthlySpendingRow> byMonth = analysisReader
                .readMonthlyBySpendingType(userId, type, start, end)
                .stream()
                .collect(Collectors.toMap(MonthlySpendingRow::yearMonth, Function.identity()));
        List<SpendingTypeBreakdownResponse.Month> months = new ArrayList<>();
        for (YearMonth month = period.startMonth(); !month.isAfter(period.endMonth()); month = month.plusMonths(1)) {
            MonthlySpendingRow row = byMonth.get(month);
            months.add(new SpendingTypeBreakdownResponse.Month(
                    month.toString(), row == null ? 0 : row.total(), row == null ? 0 : row.count()));
        }
        long total = months.stream().mapToLong(SpendingTypeBreakdownResponse.Month::amount).sum();
        long allSpending = analysisReader.readSpendingTotal(userId, start, end);

        long latest = months.get(months.size() - 1).amount();
        Long previousAverage = months.size() > 1
                ? divide(total - latest, months.size() - 1)
                : null;
        Double changeRate = previousAverage == null || previousAverage == 0
                ? null
                : round1((latest - previousAverage) * 100.0 / previousAverage);

        List<SpendingTypeBreakdownResponse.Category> categories = analysisReader
                .readCategoriesBySpendingType(userId, type, start, end)
                .stream()
                .map(row -> new SpendingTypeBreakdownResponse.Category(
                        row.categoryId(), codeOf(row), nameOf(row), row.total(), row.count(),
                        percentOrZero(row.total(), total)))
                .toList();

        return new SpendingTypeBreakdownResponse(
                type,
                period.startMonth().toString(),
                period.endMonth().toString(),
                total,
                divide(total, months.size()),
                percentOrZero(total, allSpending),
                latest,
                previousAverage,
                changeRate,
                months,
                categories
        );
    }

    // ---------- helpers ----------

    private String resolveCategoryFilter(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        String code = category.strip().toUpperCase(Locale.ROOT);
        if (SpendingQueryService.UNCATEGORIZED_CODE.equals(code)) {
            return code;
        }
        return transactionCategoryRepository.findByCode(code)
                .orElseThrow(() -> new InvalidRequestException(TransactionQueryService.CATEGORY_CODE_NOT_FOUND_MESSAGE))
                .getCode();
    }

    private static String goalStatus(double usageRatePercent) {
        if (usageRatePercent > 100.0) {
            return "EXCEEDED";
        }
        return usageRatePercent >= GOAL_WARNING_RATE_PERCENT ? "WARNING" : "ON_TRACK";
    }

    private static String codeOf(CategorySpendingRow row) {
        return row.categoryId() == null ? SpendingQueryService.UNCATEGORIZED_CODE : row.code();
    }

    private static String nameOf(CategorySpendingRow row) {
        return row.categoryId() == null ? SpendingQueryService.UNCATEGORIZED_NAME : row.name();
    }

    private static long divide(long amount, long divisor) {
        if (divisor <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(divisor), 0, RoundingMode.HALF_UP).longValue();
    }

    /** 분모가 0 이하면 null. */
    private static Double percent(long amount, long total) {
        if (total <= 0) {
            return null;
        }
        return round1(amount * 100.0 / total);
    }

    private static double percentOrZero(long amount, long total) {
        if (total <= 0 || amount <= 0) {
            return 0.0;
        }
        return round1(amount * 100.0 / total);
    }

    private static double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private static final class CategoryAccumulator {
        private final Long categoryId;
        private final String code;
        private final String name;
        private long recentTotal;
        private long baselineTotal;

        private CategoryAccumulator(Long categoryId, String code, String name) {
            this.categoryId = categoryId;
            this.code = code;
            this.name = name;
        }
    }
}
