package com.ssafy.dodam.domain.diagnosis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisIssue;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingReader;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingReader.CategorySpendingRow;
import com.ssafy.dodam.domain.transaction.service.SpendingAnalysisService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * SPENDING/CATEGORY_SPENDING_INCREASE 규칙(S15P21D105-627): 카테고리별 소비가 이전 기간보다 늘었는지 판정한다.
 *
 * <p>판정식은 API 6 소비 급증({@link SpendingAnalysisService#getAnomalies})과 같다 — 최근 N개월 카테고리 월평균이 이전 N개월
 * 월평균보다 {@link SpendingAnalysisService#ANOMALY_THRESHOLD_RATE_PERCENT}% 초과·월
 * {@link SpendingAnalysisService#ANOMALY_MINIMUM_INCREASE}원 이상 높으면 급증이다. 임계값은 그 서비스의 상수를 그대로 쓴다.
 *
 * <p>API 6과 다른 점은 <b>진행 중인 달을 넣지 않는다</b>는 것이다. 기준일이 속한 달은 며칠치 거래만 있어 월평균을 끌어내리므로
 * 최근 기간 = 기준월 −3 ~ −1, 이전 기간 = 기준월 −6 ~ −4(N = 3)로 잡는다. 같은 달에 API 6 기본 조회와 결과가 다를 수 있다.
 *
 * <p>미분류 거래(category_id NULL)는 판정하지 않는다. 카테고리를 특정할 수 없어 추천·시뮬레이션 대상이 되지 못하기 때문이다.
 *
 * <p>{@code consecutiveMonths}는 6개월 월별 금액을 최근 달부터 거슬러 올라가며 전달보다 큰 달이 이어진 수(최대 5)다. 거래가 없는
 * 달은 0원으로 본다. 심각도는 증가율 {@link #HIGH_SEVERITY_RATE_PERCENT}% 이상이면 HIGH, 그 외 MEDIUM이다.
 * 심각도 경계는 서비스 초기값이며 팀에서 검증한 기준이 아니다.
 */
@Component
@Transactional(readOnly = true)
public class SpendingIncreaseRule {
    public static final String DOMAIN = "SPENDING";
    public static final String TYPE = "CATEGORY_SPENDING_INCREASE";
    public static final int PERIOD_MONTHS = 3;
    public static final double HIGH_SEVERITY_RATE_PERCENT = 50.0;
    static final String COVERAGE_NO_BASELINE = "NO_BASELINE_SPENDING";

    private final JdbcSpendingReader spendingReader;
    private final ObjectMapper json;

    public SpendingIncreaseRule(JdbcSpendingReader spendingReader, ObjectMapper json) {
        this.spendingReader = spendingReader;
        this.json = json;
    }

    public Result evaluate(Long userId, LocalDate baseDate) {
        YearMonth recentEnd = YearMonth.from(baseDate).minusMonths(1);
        YearMonth recentStart = recentEnd.minusMonths(PERIOD_MONTHS - 1L);
        YearMonth baselineEnd = recentStart.minusMonths(1);
        YearMonth baselineStart = baselineEnd.minusMonths(PERIOD_MONTHS - 1L);
        List<CategorySpendingRow> rows = spendingReader.readByMonthAndCategory(
                userId, baselineStart.atDay(1), recentEnd.atEndOfMonth());
        return evaluate(rows, baselineStart, recentStart, recentEnd);
    }

    /** 순수 계산. 행은 카테고리·월 단위 합계이며 기간 밖의 행이 섞여 있어도 무시한다. */
    static Result evaluate(List<CategorySpendingRow> rows, YearMonth baselineStart, YearMonth recentStart, YearMonth recentEnd) {
        YearMonth baselineEnd = recentStart.minusMonths(1);
        Map<Long, Accumulator> byCategory = new LinkedHashMap<>();
        for (CategorySpendingRow row : rows) {
            if (row.categoryId() == null || row.yearMonth().isBefore(baselineStart) || row.yearMonth().isAfter(recentEnd)) {
                continue;
            }
            byCategory.computeIfAbsent(row.categoryId(), id -> new Accumulator(row.categoryId(), row.code(), row.name()))
                    .monthly.merge(row.yearMonth(), row.total(), Long::sum);
        }
        List<Item> items = new ArrayList<>();
        int evaluated = 0;
        for (Accumulator accumulator : byCategory.values()) {
            long recentTotal = 0;
            long baselineTotal = 0;
            List<MonthAmount> monthly = new ArrayList<>();
            for (YearMonth month = baselineStart; !month.isAfter(recentEnd); month = month.plusMonths(1)) {
                long amount = accumulator.monthly.getOrDefault(month, 0L);
                monthly.add(new MonthAmount(month.toString(), amount));
                if (month.isBefore(recentStart)) {
                    baselineTotal += amount;
                } else {
                    recentTotal += amount;
                }
            }
            long recentAverage = divide(recentTotal, PERIOD_MONTHS);
            long baselineAverage = divide(baselineTotal, PERIOD_MONTHS);
            if (baselineAverage <= 0) {
                continue;
            }
            evaluated++;
            long increase = recentAverage - baselineAverage;
            if (increase < SpendingAnalysisService.ANOMALY_MINIMUM_INCREASE) {
                continue;
            }
            double rate = round1(increase * 100.0 / baselineAverage);
            if (rate <= SpendingAnalysisService.ANOMALY_THRESHOLD_RATE_PERCENT) {
                continue;
            }
            items.add(new Item(accumulator.categoryId, accumulator.code, accumulator.name, recentAverage, baselineAverage,
                    increase, rate, consecutiveMonths(monthly), List.copyOf(monthly),
                    rate >= HIGH_SEVERITY_RATE_PERCENT ? IssueSeverity.HIGH : IssueSeverity.MEDIUM));
        }
        items.sort(Comparator.comparingDouble(Item::increaseRatePercent).reversed().thenComparing(Item::categoryId));
        return new Result(new Period(recentStart.toString(), recentEnd.toString(), PERIOD_MONTHS),
                new Period(baselineStart.toString(), baselineEnd.toString(), PERIOD_MONTHS),
                SpendingAnalysisService.ANOMALY_THRESHOLD_RATE_PERCENT, SpendingAnalysisService.ANOMALY_MINIMUM_INCREASE,
                evaluated, List.copyOf(items));
    }

    public DiagnosisIssue toIssue(Diagnosis diagnosis, Result result, Item item) {
        ObjectNode current = json.createObjectNode();
        current.put("categoryId", item.categoryId()).put("categoryCode", item.categoryCode()).put("categoryName", item.categoryName())
                .put("monthlyAverage", item.monthlyAverage()).put("increaseAmount", item.increaseAmount())
                .put("increaseRatePercent", item.increaseRatePercent()).put("consecutiveMonths", item.consecutiveMonths());
        current.set("recentPeriod", json.valueToTree(result.recentPeriod()));
        ObjectNode benchmark = json.createObjectNode();
        benchmark.put("previousMonthlyAverage", item.previousMonthlyAverage())
                .put("thresholdRatePercent", result.thresholdRatePercent()).put("minimumIncrease", result.minimumIncrease())
                .put("highSeverityRatePercent", HIGH_SEVERITY_RATE_PERCENT);
        benchmark.set("baselinePeriod", json.valueToTree(result.baselinePeriod()));
        ObjectNode evidence = json.createObjectNode();
        evidence.put("ruleKey", TYPE).put("targetId", item.categoryId()).put("categoryCode", item.categoryCode());
        evidence.set("monthly", json.valueToTree(item.monthly()));
        String title = item.consecutiveMonths() >= 2
                ? item.categoryName() + " 소비가 " + item.consecutiveMonths() + "개월째 늘고 있어요"
                : item.categoryName() + " 소비가 늘었어요";
        String description = "최근 " + result.recentPeriod().months() + "개월 월평균 " + won(item.monthlyAverage()) + "으로 이전 "
                + result.baselinePeriod().months() + "개월 평균 " + won(item.previousMonthlyAverage()) + "보다 "
                + item.increaseRatePercent() + "%(" + won(item.increaseAmount()) + ") 높아요.";
        return DiagnosisIssue.ofRule(diagnosis.getDiagnosisId(), DOMAIN, TYPE, item.severity(), title, description,
                current, benchmark, evidence, diagnosis.getCreatedAt());
    }

    /** 최근 달부터 거슬러 올라가며 전달보다 큰 달이 이어진 수. 첫 달은 비교할 전달이 없으므로 최대 size − 1이다. */
    static int consecutiveMonths(List<MonthAmount> monthly) {
        int count = 0;
        for (int i = monthly.size() - 1; i > 0 && monthly.get(i).amount() > monthly.get(i - 1).amount(); i--) {
            count++;
        }
        return count;
    }

    private static long divide(long amount, long divisor) {
        return BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(divisor), 0, RoundingMode.HALF_UP).longValue();
    }

    private static double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    private static String won(long amount) {
        return String.format("%,d원", amount);
    }

    public record Result(Period recentPeriod, Period baselinePeriod, double thresholdRatePercent, long minimumIncrease,
            int evaluatedCategories, List<Item> items) { }

    public record Period(String startMonth, String endMonth, int months) { }

    /**
     * @param monthlyAverage         최근 기간 월평균
     * @param previousMonthlyAverage 이전 기간 월평균
     * @param consecutiveMonths      전달보다 늘어난 달이 최근 달까지 이어진 수
     * @param monthly                이전 기간 첫 달부터 최근 기간 마지막 달까지의 월별 금액(거래 없는 달은 0)
     */
    public record Item(Long categoryId, String categoryCode, String categoryName, long monthlyAverage, long previousMonthlyAverage,
            long increaseAmount, double increaseRatePercent, int consecutiveMonths, List<MonthAmount> monthly, IssueSeverity severity) { }

    public record MonthAmount(String month, long amount) { }

    private static final class Accumulator {
        private final Long categoryId;
        private final String code;
        private final String name;
        private final Map<YearMonth, Long> monthly = new LinkedHashMap<>();

        private Accumulator(Long categoryId, String code, String name) {
            this.categoryId = categoryId;
            this.code = code;
            this.name = name;
        }
    }
}
