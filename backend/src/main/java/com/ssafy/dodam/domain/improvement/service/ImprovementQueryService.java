package com.ssafy.dodam.domain.improvement.service;

import com.ssafy.dodam.domain.goal.entity.Goal;
import com.ssafy.dodam.domain.goal.entity.GoalStatus;
import com.ssafy.dodam.domain.goal.entity.GoalType;
import com.ssafy.dodam.domain.goal.repository.GoalRepository;
import com.ssafy.dodam.domain.goal.service.GoalCalculationService;
import com.ssafy.dodam.domain.improvement.dto.ActionComparisonResponse;
import com.ssafy.dodam.domain.improvement.dto.ActionComparisonResponse.Metric;
import com.ssafy.dodam.domain.improvement.dto.ActionComparisonResponse.Period;
import com.ssafy.dodam.domain.improvement.dto.AmountTotalResponse;
import com.ssafy.dodam.domain.improvement.dto.GoalImpactSummaryResponse;
import com.ssafy.dodam.domain.improvement.dto.ImprovementActionResponse;
import com.ssafy.dodam.domain.improvement.dto.ImprovementSnapshot;
import com.ssafy.dodam.domain.improvement.dto.ImprovementSummaryResponse;
import com.ssafy.dodam.domain.improvement.dto.MonthlyTrendResponse;
import com.ssafy.dodam.domain.improvement.entity.ImprovementAction;
import com.ssafy.dodam.domain.improvement.entity.ImprovementActionStatus;
import com.ssafy.dodam.domain.improvement.repository.ImprovementActionRepository;
import com.ssafy.dodam.domain.improvement.service.ImprovementMeasurementPolicy.Window;
import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import com.ssafy.dodam.domain.recommendation.repository.RecommendationRepository;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.policy.QueryPeriod;
import com.ssafy.dodam.global.policy.QueryPeriodPolicy;
import com.ssafy.dodam.global.policy.YearMonthPeriod;
import com.ssafy.dodam.global.policy.YearMonthPeriodPolicy;
import com.ssafy.dodam.global.response.PageResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API 19 조회 11종 중 재진단을 뺀 10종.
 *
 * <p>모든 경로가 읽기 전에 {@link ImprovementMeasurementService#refresh(Long)}를 부른다. 배치가 없으므로
 * 조회 시점이 측정 시점이다 — 그래서 이 클래스는 읽기 전용 트랜잭션이 아니다.
 *
 * <p>소유권: 모든 조회에 `userId` 조건이 들어가고, 타인 리소스와 없는 리소스를 구분하지 않고 404를 돌려준다.
 */
@Service
@Transactional
public class ImprovementQueryService {

    static final String NOT_FOUND_MESSAGE = "개선 행동을 찾을 수 없습니다.";
    /** 추이 기본 구간. 양 끝을 모두 생략했을 때만 쓴다(차트가 6개 점은 있어야 추세로 보인다). */
    static final int TREND_DEFAULT_MONTHS = 6;
    private static final int MONTHS_PER_YEAR = 12;

    private final ImprovementActionRepository actions;
    private final RecommendationRepository recommendations;
    private final ImprovementMeasurementService measurements;
    private final ImprovementMeasurementPolicy periods;
    private final ImprovementSnapshots codec;
    private final ImprovementClock clock;
    private final QueryPeriodPolicy dayPeriods;
    private final YearMonthPeriodPolicy monthPeriods;
    private final GoalRepository goals;
    private final GoalCalculationService goalCalculations;

    public ImprovementQueryService(ImprovementActionRepository actions, RecommendationRepository recommendations,
            ImprovementMeasurementService measurements, ImprovementMeasurementPolicy periods,
            ImprovementSnapshots codec, ImprovementClock clock, QueryPeriodPolicy dayPeriods,
            YearMonthPeriodPolicy monthPeriods, GoalRepository goals, GoalCalculationService goalCalculations) {
        this.actions = actions;
        this.recommendations = recommendations;
        this.measurements = measurements;
        this.periods = periods;
        this.codec = codec;
        this.clock = clock;
        this.dayPeriods = dayPeriods;
        this.monthPeriods = monthPeriods;
        this.goals = goals;
        this.goalCalculations = goalCalculations;
    }

    // ---------- #1 개선 결과 요약 ----------

    public ImprovementSummaryResponse summary(Long userId) {
        List<ImprovementAction> owned = refreshed(userId);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        // 4개 상태 키를 항상 모두 채운다. FE가 키 존재 여부를 분기하지 않도록.
        for (ImprovementActionStatus status : ImprovementActionStatus.values()) {
            byStatus.put(status.name(), 0L);
        }
        for (ImprovementAction action : owned) {
            byStatus.merge(action.getStatus().name(), 1L, Long::sum);
        }
        List<ImprovementAction> measured = owned.stream().filter(ImprovementAction::isMeasured).toList();
        return new ImprovementSummaryResponse(
                owned.size(),
                byStatus,
                measured.size(),
                sum(measured, ImprovementAction::getMonthlyCostSaving),
                sum(measured, ImprovementAction::getAnnualCostSaving),
                sum(measured, ImprovementAction::getDebtReduction),
                owned.stream().map(ImprovementAction::getPlannedAt).min(Comparator.naturalOrder()).orElse(null),
                measured.stream().map(ImprovementAction::getMeasuredAt).max(Comparator.naturalOrder()).orElse(null));
    }

    // ---------- #2 개선 행동 이력 ----------

    public PageResponse<ImprovementActionResponse> list(Long userId, ImprovementActionStatus status,
            LocalDate startDate, LocalDate endDate, int page, int size) {
        measurements.refresh(userId);
        QueryPeriod period = dayPeriods.resolve(startDate, endDate, clock.today());
        var found = actions.findOwned(userId, status, period.startDate().atStartOfDay(),
                period.endDate().plusDays(1).atStartOfDay(), PageRequest.of(page, size));
        Map<Long, Recommendation> byId = recommendationsOf(userId,
                found.getContent().stream().map(ImprovementAction::getRecommendationId).toList());
        return PageResponse.from(found, action -> ImprovementActionResponse.of(action, byId.get(action.getRecommendationId())));
    }

    // ---------- #3 개선 행동 상세 ----------

    public ImprovementActionResponse detail(Long userId, Long actionId) {
        measurements.refresh(userId);
        ImprovementAction action = owned(userId, actionId);
        return ImprovementActionResponse.of(action, recommendationOf(userId, action));
    }

    // ---------- #4 실행 전후 비교 ----------

    public ActionComparisonResponse comparison(Long userId, Long actionId) {
        measurements.refresh(userId);
        ImprovementAction action = owned(userId, actionId);
        String status = action.getStatus().name();
        Window beforeWindow = periods.before(action.getPlannedAt());
        Window afterWindow = periods.after(action.getPlannedAt(), clock.today());
        Period beforePeriod = beforeWindow.toPeriod();
        Period afterPeriod = afterWindow == null ? null : afterWindow.toPeriod();

        if (!action.getStatus().isMeasurable()) {
            return ActionComparisonResponse.unavailable(actionId, status, beforePeriod, afterPeriod,
                    "ACTION_NOT_MEASURABLE");
        }
        ImprovementSnapshot before = codec.fromJson(action.getBeforeSnapshot());
        if (before == null) {
            return ActionComparisonResponse.unavailable(actionId, status, beforePeriod, afterPeriod,
                    "BEFORE_SNAPSHOT_MISSING");
        }
        ImprovementSnapshot after = codec.fromJson(action.getAfterSnapshot());
        if (after == null) {
            return ActionComparisonResponse.unavailable(actionId, status, beforePeriod, afterPeriod,
                    "AFTER_PERIOD_NOT_READY");
        }
        List<Metric> metrics = new ArrayList<>();
        addMetric(metrics, "MONTHLY_SPENDING", "월 소비금액", before.monthlySpending(), after.monthlySpending(), false);
        addMetric(metrics, "MONTHLY_SAVING", "월 저축금액", before.monthlySaving(), after.monthlySaving(), true);
        addMetric(metrics, "DEBT_BALANCE", "부채 잔액", before.debtBalance(), after.debtBalance(), false);
        addMetric(metrics, "MONTHLY_INTEREST", "월 이자비용", before.monthlyInterest(), after.monthlyInterest(), false);
        if (metrics.isEmpty()) {
            return ActionComparisonResponse.unavailable(actionId, status, beforePeriod, afterPeriod, "INSUFFICIENT_DATA");
        }
        // 저장된 스냅샷의 기간을 그대로 쓴다. 조회 시점에 다시 계산하면 응답의 기간과 수치가 어긋난다.
        return new ActionComparisonResponse(actionId, status,
                new Period(before.startDate(), before.endDate()), new Period(after.startDate(), after.endDate()),
                metrics, null);
    }

    /**
     * @param increaseIsBetter 늘어야 개선인 지표인지. 소비·부채·이자는 줄어야 개선이라 부호만으로는 판단할 수 없다
     */
    private static void addMetric(List<Metric> metrics, String code, String displayName, Long before, Long after,
            boolean increaseIsBetter) {
        if (before == null || after == null) {
            return;
        }
        long delta = after - before;
        boolean improved = increaseIsBetter ? delta > 0 : delta < 0;
        metrics.add(new Metric(code, displayName, before, after, delta, improved));
    }

    // ---------- #5~#8 금액 집계 ----------

    public AmountTotalResponse savings(Long userId) {
        return amountTotal(userId, "SAVINGS", ImprovementAction::getMonthlyCostSaving, true);
    }

    public AmountTotalResponse debtReduction(Long userId) {
        return amountTotal(userId, "DEBT_REDUCTION", ImprovementAction::getDebtReduction, false);
    }

    public AmountTotalResponse spendingReduction(Long userId) {
        return amountTotal(userId, "SPENDING_REDUCTION", ImprovementAction::getSpendingReduction, true);
    }

    public AmountTotalResponse savingIncrease(Long userId) {
        return amountTotal(userId, "SAVING_INCREASE", ImprovementAction::getSavingIncrease, true);
    }

    /**
     * @param monthly 월 단위 지표인지. 월 단위면 {@code totalAmount}는 연환산이고, 잔액성 지표(부채)는
     *                월 개념이 없어 {@code monthlyAmount}가 null이며 {@code totalAmount}가 잔액 감소 합이다
     */
    private AmountTotalResponse amountTotal(Long userId, String metric,
            Function<ImprovementAction, Long> amount, boolean monthly) {
        List<ImprovementAction> measured = refreshed(userId).stream()
                .filter(ImprovementAction::isMeasured)
                .filter(action -> amount.apply(action) != null)
                .toList();
        long total = sum(measured, amount);
        List<LocalDate> measuredDates = measured.stream().map(action -> action.getMeasuredAt().toLocalDate()).toList();
        return new AmountTotalResponse(metric,
                monthly ? total * MONTHS_PER_YEAR : total,
                monthly ? total : null,
                AmountTotalResponse.CURRENCY,
                measuredDates.stream().min(Comparator.naturalOrder()).orElse(null),
                measuredDates.stream().max(Comparator.naturalOrder()).orElse(null),
                measured.size(),
                null);
    }

    // ---------- #9 목표달성일 개선 ----------

    public GoalImpactSummaryResponse goalImpact(Long userId) {
        List<ImprovementAction> owned = refreshed(userId).stream()
                .filter(action -> action.getStatus().isMeasurable()).toList();
        Map<Long, Recommendation> byId = recommendationsOf(userId,
                owned.stream().map(ImprovementAction::getRecommendationId).toList());
        Map<Long, List<ImprovementAction>> byGoal = new HashMap<>();
        for (ImprovementAction action : owned) {
            Recommendation recommendation = byId.get(action.getRecommendationId());
            if (recommendation == null || recommendation.getGoalId() == null) {
                continue;
            }
            byGoal.computeIfAbsent(recommendation.getGoalId(), key -> new ArrayList<>()).add(action);
        }
        LocalDate today = clock.today();
        List<GoalImpactSummaryResponse.Goal> results = new ArrayList<>();
        for (Goal goal : goals.findAllByUserIdAndDeletedAtIsNull(userId)) {
            if (goal.getType() != GoalType.SAVING || goal.getStatus() != GoalStatus.ACTIVE) {
                continue;
            }
            results.add(goalImpact(userId, goal, byGoal.getOrDefault(goal.getId(), List.of()), today));
        }
        results.sort(Comparator.comparing(GoalImpactSummaryResponse.Goal::goalId));
        return new GoalImpactSummaryResponse(results);
    }

    private GoalImpactSummaryResponse.Goal goalImpact(Long userId, Goal goal, List<ImprovementAction> linked,
            LocalDate today) {
        // 기준선은 그 목표에 연결된 가장 이른 행동의 수락 시점 예상일이다. 나중 행동의 기준선을 쓰면
        // 먼저 실행한 행동이 앞당긴 만큼이 기준선에 이미 반영돼 효과가 사라진다.
        Comparator<ImprovementAction> earliestFirst = Comparator
                .comparing(ImprovementAction::getPlannedAt)
                .thenComparing(ImprovementAction::getActionId);
        LocalDate baseline = linked.stream()
                .min(earliestFirst)
                .map(action -> codec.fromJson(action.getBeforeSnapshot()))
                .map(ImprovementSnapshot::goalForecastDate)
                .orElse(null);
        LocalDate current;
        try {
            current = goalCalculations.getForecast(userId, goal.getId(), today).expectedAchievementDate();
        } catch (RuntimeException failure) {
            // 목표가 조회 사이에 삭제됐거나 하위행이 없다. 다른 목표의 결과까지 버리지 않는다.
            current = null;
        }
        String reason = null;
        if (linked.isEmpty()) {
            reason = "NO_CONTRIBUTING_ACTION";
        } else if (baseline == null) {
            reason = "BASELINE_FORECAST_UNAVAILABLE";
        } else if (current == null) {
            reason = "CURRENT_FORECAST_UNAVAILABLE";
        }
        Long advancedDays = baseline == null || current == null ? null
                : java.time.temporal.ChronoUnit.DAYS.between(current, baseline);
        return new GoalImpactSummaryResponse.Goal(goal.getId(), goal.getName(), baseline, current, advancedDays,
                linked.size(), reason);
    }

    // ---------- #10 월별 개선추이 ----------

    public MonthlyTrendResponse monthlyTrend(Long userId, String startMonth, String endMonth) {
        YearMonthPeriod period = monthPeriods.resolve(startMonth, endMonth);
        if (startMonth == null && endMonth == null) {
            period = new YearMonthPeriod(period.endMonth().minusMonths(TREND_DEFAULT_MONTHS - 1L), period.endMonth());
        }
        List<ImprovementAction> owned = refreshed(userId);
        Map<YearMonth, long[]> buckets = emptyBuckets(period);
        for (ImprovementAction action : owned) {
            if (action.getCompletedAt() != null) {
                long[] bucket = buckets.get(YearMonth.from(action.getCompletedAt()));
                if (bucket != null) {
                    bucket[0]++;
                }
            }
            if (!action.isMeasured()) {
                continue;
            }
            long[] bucket = buckets.get(YearMonth.from(action.getMeasuredAt()));
            if (bucket == null) {
                continue;
            }
            bucket[1] += value(action.getMonthlyCostSaving());
            bucket[2] += value(action.getSpendingReduction());
            bucket[3] += value(action.getSavingIncrease());
            bucket[4] += value(action.getDebtReduction());
        }
        List<MonthlyTrendResponse.Month> months = new ArrayList<>();
        buckets.forEach((month, bucket) -> months.add(new MonthlyTrendResponse.Month(month.toString(),
                bucket[0], bucket[1], bucket[2], bucket[3], bucket[4])));
        months.sort(Comparator.comparing(MonthlyTrendResponse.Month::yearMonth));
        return new MonthlyTrendResponse(period.startMonth().toString(), period.endMonth().toString(), months);
    }

    /** 빈 달을 0으로 채운 연속 버킷. 차트가 구멍을 메우지 않아도 되도록 서버가 채운다. */
    private static Map<YearMonth, long[]> emptyBuckets(YearMonthPeriod period) {
        Map<YearMonth, long[]> buckets = new LinkedHashMap<>();
        for (YearMonth month = period.startMonth(); !month.isAfter(period.endMonth()); month = month.plusMonths(1)) {
            buckets.put(month, new long[5]);
        }
        return buckets;
    }

    // ---------- helpers ----------

    private List<ImprovementAction> refreshed(Long userId) {
        measurements.refresh(userId);
        return actions.findAllByUserId(userId);
    }

    private ImprovementAction owned(Long userId, Long actionId) {
        return actions.findByActionIdAndUserId(actionId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
    }

    private Recommendation recommendationOf(Long userId, ImprovementAction action) {
        return recommendations.findByRecommendationIdAndUserId(action.getRecommendationId(), userId).orElse(null);
    }

    private Map<Long, Recommendation> recommendationsOf(Long userId, List<Long> recommendationIds) {
        if (recommendationIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Recommendation> byId = new HashMap<>();
        recommendations.findAllByUserIdAndRecommendationIdIn(userId, recommendationIds)
                .forEach(recommendation -> byId.put(recommendation.getRecommendationId(), recommendation));
        return byId;
    }

    private static long sum(List<ImprovementAction> items, Function<ImprovementAction, Long> amount) {
        return items.stream().mapToLong(action -> value(amount.apply(action))).sum();
    }

    private static long value(Long amount) {
        return amount == null ? 0L : amount;
    }
}
