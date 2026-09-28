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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * SPENDING/CATEGORY_SPENDING_INCREASE 규칙(S15P21D105-627, 기준 변경 S15P21D105-631): 카테고리별 <b>지난달</b> 소비가
 * <b>직전 3개월 월평균</b>보다 늘었는지 판정한다.
 *
 * <p>비교 기간(631, 규칙 버전 {@code v6-last-month-vs-3m}부터): 최근 기간 = 기준월 −1(마지막 완료 월, 1개월), 기준 기간 = 기준월 −4 ~ −2(3개월).
 * 예: 9월 진단 → 8월 vs 5~7월 평균. 기준일이 속한 달은 며칠치 거래만 있어 넣지 않는다. 627 최초 구현(v5까지)은 최근 3개월 평균 vs
 * 이전 3개월 평균이었다. 그 진단·추천은 규칙 버전이 다른 행으로 그대로 남는다.
 *
 * <p>임계값은 API 6 소비 급증({@link SpendingAnalysisService#getAnomalies})의 상수를 그대로 쓴다 — 증가액이 월
 * {@link SpendingAnalysisService#ANOMALY_MINIMUM_INCREASE}원 이상이고 증가율이 {@link SpendingAnalysisService#ANOMALY_THRESHOLD_RATE_PERCENT}%를
 * 초과하면 급증이다. 단, API 6은 N개월 평균 대 N개월 평균이라 비교 기간이 다르며, 단월 비교는 한 달 튄 값에 그대로 반응해
 * 탐지 건수가 늘 수 있다. 임계값 조정은 시연 데이터 실측 뒤에 정한다(remaining-work 627·631 절).
 *
 * <p>미분류 거래(category_id NULL)는 판정하지 않는다. 카테고리를 특정할 수 없어 추천·시뮬레이션 대상이 되지 못하기 때문이다.
 * 기준 기간 3개월 중 거래가 없는 달은 0원으로 평균에 들어간다(평균이 희석된다). 기준 평균이 0이면 판정 대상이 아니다.
 *
 * <p>{@code consecutiveMonths}는 기준 기간 첫 달부터 지난달까지 월별 금액을 최근 달부터 거슬러 올라가며 전달보다 큰 달이 이어진 수
 * (최대 {@code BASELINE_MONTHS})다. 지난달이 전달보다 줄었어도 3개월 평균보다 높으면 판정되며 그때 이 값은 0이다.
 * 심각도는 증가율 {@link #HIGH_SEVERITY_RATE_PERCENT}% 이상이면 HIGH, 그 외 MEDIUM. 심각도 경계는 서비스 초기값이며 팀에서 검증한 기준이 아니다.
 */
@Component
@Transactional(readOnly = true)
public class SpendingIncreaseRule {
    public static final String DOMAIN = "SPENDING";
    public static final String TYPE = "CATEGORY_SPENDING_INCREASE";
    /** 최근 기간 = 마지막 완료 월 1개월(631). */
    public static final int RECENT_MONTHS = 1;
    /** 기준 기간 = 최근 기간 직전 3개월. */
    public static final int BASELINE_MONTHS = 3;
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
        YearMonth recentStart = recentEnd.minusMonths(RECENT_MONTHS - 1L);
        YearMonth baselineEnd = recentStart.minusMonths(1);
        YearMonth baselineStart = baselineEnd.minusMonths(BASELINE_MONTHS - 1L);
        List<CategorySpendingRow> rows = spendingReader.readByMonthAndCategory(
                userId, baselineStart.atDay(1), recentEnd.atEndOfMonth());
        return evaluate(rows, baselineStart, recentStart, recentEnd);
    }

    /**
     * 순수 계산. 행은 카테고리·월 단위 합계이며 기간 밖의 행이 섞여 있어도 무시한다. 기간 길이는 인자로 정해지므로
     * (기준 {@code baselineStart}~{@code recentStart − 1}, 최근 {@code recentStart}~{@code recentEnd}) 테스트가 임의 길이를 넣을 수 있다.
     */
    static Result evaluate(List<CategorySpendingRow> rows, YearMonth baselineStart, YearMonth recentStart, YearMonth recentEnd) {
        YearMonth baselineEnd = recentStart.minusMonths(1);
        int recentMonths = monthsInclusive(recentStart, recentEnd);
        int baselineMonths = monthsInclusive(baselineStart, baselineEnd);
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
            long recentAverage = divide(recentTotal, recentMonths);
            long baselineAverage = divide(baselineTotal, baselineMonths);
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
        return new Result(new Period(recentStart.toString(), recentEnd.toString(), recentMonths),
                new Period(baselineStart.toString(), baselineEnd.toString(), baselineMonths),
                SpendingAnalysisService.ANOMALY_THRESHOLD_RATE_PERCENT, SpendingAnalysisService.ANOMALY_MINIMUM_INCREASE,
                evaluated, List.copyOf(items));
    }

    /**
     * 진단 문제로 변환한다. JSON 키 이름은 627(v5)과 같게 유지해 FE·추천 생성기가 그대로 읽는다 — {@code monthlyAverage}는 최근 기간
     * 월평균이며 631부터 최근 기간이 1개월이라 지난달 금액과 같다. 뜻을 드러내기 위해 {@code lastMonthAmount}를 같은 값으로 함께 싣는다.
     */
    public DiagnosisIssue toIssue(Diagnosis diagnosis, Result result, Item item) {
        ObjectNode current = json.createObjectNode();
        current.put("categoryId", item.categoryId()).put("categoryCode", item.categoryCode()).put("categoryName", item.categoryName())
                .put("monthlyAverage", item.monthlyAverage()).put("lastMonthAmount", item.monthlyAverage())
                .put("increaseAmount", item.increaseAmount())
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
        // 화면 한 줄에 들어가야 한다(FE 문구 정비, S15P21D105-604). 금액 두 개와 비율을 모두 넣으면
        // 두 줄로 넘어가고, 같은 수치가 추천 카드에서 다시 반복된다. 상세 수치는 currentValue·
        // benchmarkValue JSON에 그대로 남아 있으므로 잃는 정보는 없다.
        String description = recentLabel(result.recentPeriod()) + " 소비가 직전 " + result.baselinePeriod().months() + "개월 평균보다 "
                + item.increaseRatePercent() + "% 높아요.";
        return DiagnosisIssue.ofRule(diagnosis.getDiagnosisId(), DOMAIN, TYPE, item.severity(), title, description,
                current, benchmark, evidence, diagnosis.getCreatedAt());
    }

    /** 최근 기간이 1개월이면 "지난달", 아니면 "최근 N개월 평균". */
    static String recentLabel(Period recentPeriod) {
        return recentPeriod.months() == 1 ? "지난달" : "최근 " + recentPeriod.months() + "개월 평균";
    }

    /** 최근 달부터 거슬러 올라가며 전달보다 큰 달이 이어진 수. 첫 달은 비교할 전달이 없으므로 최대 size − 1이다. */
    static int consecutiveMonths(List<MonthAmount> monthly) {
        int count = 0;
        for (int i = monthly.size() - 1; i > 0 && monthly.get(i).amount() > monthly.get(i - 1).amount(); i--) {
            count++;
        }
        return count;
    }

    private static int monthsInclusive(YearMonth start, YearMonth end) {
        return (int) ChronoUnit.MONTHS.between(start, end) + 1;
    }

    private static long divide(long amount, long divisor) {
        return BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(divisor), 0, RoundingMode.HALF_UP).longValue();
    }

    private static double round1(double value) {
        return BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }

    public record Result(Period recentPeriod, Period baselinePeriod, double thresholdRatePercent, long minimumIncrease,
            int evaluatedCategories, List<Item> items) { }

    public record Period(String startMonth, String endMonth, int months) { }

    /**
     * @param monthlyAverage         최근 기간 월평균. 631부터 최근 기간이 1개월이라 지난달 금액과 같다
     * @param previousMonthlyAverage 기준 기간(직전 3개월) 월평균
     * @param consecutiveMonths      전달보다 늘어난 달이 지난달까지 이어진 수
     * @param monthly                기준 기간 첫 달부터 지난달까지의 월별 금액(거래 없는 달은 0)
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
