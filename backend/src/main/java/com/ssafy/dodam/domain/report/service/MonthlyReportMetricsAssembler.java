package com.ssafy.dodam.domain.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.cashflow.entity.CashflowForecast;
import com.ssafy.dodam.domain.cashflow.entity.CashflowRisk;
import com.ssafy.dodam.domain.cashflow.entity.ForecastStatus;
import com.ssafy.dodam.domain.cashflow.repository.CashflowForecastRepository;
import com.ssafy.dodam.domain.cashflow.repository.CashflowRiskRepository;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisIssue;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisIssueRepository;
import com.ssafy.dodam.domain.loan.service.LoanQueryService;
import com.ssafy.dodam.domain.report.dto.MonthlyReportMetricsResponse;
import com.ssafy.dodam.domain.report.dto.MonthlyReportMetricsResponse.KeyMetric;
import com.ssafy.dodam.domain.report.dto.MonthlyReportMetricsResponse.Pattern;
import com.ssafy.dodam.domain.report.dto.MonthlyReportResponse.MonthAmounts;
import com.ssafy.dodam.domain.transaction.entity.SpendingType;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingAnalysisReader;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 월간 리포트 "주요 지표"·"금융 생활 패턴"을 만든다(S15P21D105-697). 계약: {@code MonthlyReportMetricsResponse},
 * discovery-report-metrics-675.md §2.
 *
 * <p>입력은 효율 점수({@link EfficiencyScoreCalculator})와 같다 — 리포트 합계(저장값), 고정지출 {@code bySpendingType.FIXED}, 그 달 말일까지
 * 완료된 가장 최근 진단(없으면 생성 시점 최신, {@code MonthlyReport.diagnosisId})의 문제, 기준일이 그 달 말일 이전인 마지막 완료 현금흐름 예측
 * (없으면 생성 시점 최신)의 위험 — 694 v3 기준. 폴백이면 문구에 "가장 최근 예측 기준으로"를 붙인다. 두 카드의 숫자가
 * 육각형 점수의 근거 수치와 어긋나지 않게 하기 위해서다. 대출 잔액만 월별 이력이 없어 생성(보강) 시점 현재 값({@code asOf = CURRENT})이다.
 *
 * <p>패턴 문구는 서버 템플릿이다. FE가 조합하던 3장(저축률·소비 증가·현금흐름)을 그대로 옮겼고, 소비 증가는 API 6 소비 급증(N개월 평균 대
 * N개월 평균) 대신 <b>진단의 카테고리 소비 증가 판정</b>(지난달 vs 직전 3개월 평균, 631)을 쓴다 — 발견 화면과 같은 판정이어야 두 화면이
 * 서로 다른 말을 하지 않는다. 말일까지 진단이 없으면 "자료 없음"으로 두고 "늘어난 소비가 없다"고 말하지 않는다.
 */
@Component
@Transactional(readOnly = true)
public class MonthlyReportMetricsAssembler {

    public static final String VERSION = "report-metrics-v1";
    /** 저축률 양호 경계(FE 임시 구현의 20%를 그대로 옮긴 서비스 초기값). */
    static final double SAVING_RATE_GOOD = 0.2;
    static final String SPENDING_INCREASE_ISSUE_TYPE = "CATEGORY_SPENDING_INCREASE";

    static final String KEY_SAVING_RATE = "SAVING_RATE";
    static final String KEY_FIXED_EXPENSE_SHARE = "FIXED_EXPENSE_SHARE";
    static final String KEY_CARD_SPENDING = "CARD_SPENDING";
    static final String KEY_LOAN_BALANCE = "LOAN_BALANCE";
    static final String KEY_SPENDING_INCREASE = "SPENDING_INCREASE";
    static final String KEY_CASHFLOW_RISK = "CASHFLOW_RISK";

    static final String RATIO = "RATIO";
    static final String CURRENCY = "CURRENCY";
    static final String GOOD = "GOOD";
    static final String CHECK = "CHECK";
    static final String UNAVAILABLE = "UNAVAILABLE";
    static final String AS_OF_MONTH = "MONTH";
    static final String AS_OF_CURRENT = "CURRENT";

    static final String REASON_INCOME_MISSING = "INCOME_MISSING";
    static final String REASON_NO_SPENDING = "NO_SPENDING";
    static final String REASON_NO_PREVIOUS_MONTH = "NO_PREVIOUS_MONTH";
    static final String REASON_HISTORY_NOT_STORED = "HISTORY_NOT_STORED";
    static final String REASON_DIAGNOSIS_NOT_FOUND = "DIAGNOSIS_NOT_FOUND";
    static final String REASON_FORECAST_NOT_FOUND = "FORECAST_NOT_FOUND";

    private final JdbcSpendingAnalysisReader spendingTypes;
    private final LoanQueryService loans;
    private final CashflowForecastRepository forecasts;
    private final CashflowRiskRepository risks;
    private final DiagnosisIssueRepository issues;
    private final ObjectMapper json;

    public MonthlyReportMetricsAssembler(JdbcSpendingAnalysisReader spendingTypes, LoanQueryService loans,
            CashflowForecastRepository forecasts, CashflowRiskRepository risks, DiagnosisIssueRepository issues, ObjectMapper json) {
        this.spendingTypes = spendingTypes;
        this.loans = loans;
        this.forecasts = forecasts;
        this.risks = risks;
        this.issues = issues;
        this.json = json;
    }

    /**
     * @param totals                  그 달 합계(리포트 합계와 같은 값)
     * @param previous                전월 합계. 비교 대상이 없으면(첫 거래 이전) null
     * @param cardSpending            그 달 카드 사용액
     * @param cardSpendingPrevious    전월 카드 사용액. 비교 불가면 null
     * @param cardSpendingDifference  카드 사용액 증감. 비교 불가면 null
     * @param diagnosisId             그 달 말일까지 완료된 가장 최근 진단(694). 없으면 null
     */
    public MonthlyReportMetricsResponse assemble(Long userId, YearMonth month, MonthAmounts totals, MonthAmounts previous,
            long cardSpending, Long cardSpendingPrevious, Long cardSpendingDifference, Long diagnosisId) {
        long fixed = fixedSpending(userId, month);
        Long previousFixed = previous == null ? null : fixedSpending(userId, month.minusMonths(1));

        List<KeyMetric> keyMetrics = List.of(
                ratioMetric(KEY_SAVING_RATE, "저축률", totals.totalSaving(), totals.totalIncome(), REASON_INCOME_MISSING,
                        previous == null ? null : previous.totalSaving(), previous == null ? null : previous.totalIncome()),
                ratioMetric(KEY_FIXED_EXPENSE_SHARE, "고정지출 비중", fixed, totals.totalSpending(), REASON_NO_SPENDING,
                        previousFixed, previous == null ? null : previous.totalSpending()),
                new KeyMetric(KEY_CARD_SPENDING, "카드 사용액", CURRENCY, cardSpending, cardSpendingPrevious, cardSpendingDifference,
                        null, cardSpendingPrevious == null ? REASON_NO_PREVIOUS_MONTH : null, AS_OF_MONTH),
                new KeyMetric(KEY_LOAN_BALANCE, "대출 잔액", CURRENCY, loans.getSummary(userId).totalBalance(), null, null,
                        null, REASON_HISTORY_NOT_STORED, AS_OF_CURRENT));

        List<Pattern> patterns = List.of(
                savingRatePattern(month, totals),
                spendingIncreasePattern(userId, month, diagnosisId),
                cashflowPattern(userId, month));
        return new MonthlyReportMetricsResponse(VERSION, keyMetrics, patterns);
    }

    // ---------- 주요 지표 ----------

    private long fixedSpending(Long userId, YearMonth month) {
        return spendingTypes.readMonthlyBySpendingType(userId, SpendingType.FIXED.name(), month.atDay(1), month.atEndOfMonth())
                .stream().mapToLong(JdbcSpendingReader.MonthlySpendingRow::total).sum();
    }

    /** 분모 0이면 값 없음(사유). 전월은 비교 대상이 없으면 NO_PREVIOUS_MONTH, 있는데 분모 0이면 같은 사유. */
    private static KeyMetric ratioMetric(String key, String label, long amount, long total, String zeroReason,
            Long previousAmount, Long previousTotal) {
        Double value = total > 0 ? ratio(amount, total) : null;
        String reason = total > 0 ? null : zeroReason;
        Double previousValue = null;
        String previousReason;
        if (previousTotal == null) {
            previousReason = REASON_NO_PREVIOUS_MONTH;
        } else if (previousTotal > 0) {
            previousValue = ratio(previousAmount == null ? 0L : previousAmount, previousTotal);
            previousReason = null;
        } else {
            previousReason = zeroReason;
        }
        Double difference = value == null || previousValue == null ? null
                : BigDecimal.valueOf(value).subtract(BigDecimal.valueOf(previousValue)).setScale(4, RoundingMode.HALF_UP).doubleValue();
        return new KeyMetric(key, label, RATIO, value, previousValue, difference, reason, previousReason, AS_OF_MONTH);
    }

    // ---------- 금융 생활 패턴 ----------

    private Pattern savingRatePattern(YearMonth month, MonthAmounts totals) {
        if (totals.totalIncome() <= 0) {
            return new Pattern(KEY_SAVING_RATE, UNAVAILABLE, "저축률을 계산할 소득 정보가 없어요",
                    "이 달 수입 거래가 없어 저축률을 계산하지 못했어요.", month.toString(), REASON_INCOME_MISSING, null);
        }
        double rate = ratio(totals.totalSaving(), totals.totalIncome());
        ObjectNode evidence = json.createObjectNode();
        evidence.put("income", totals.totalIncome()).put("saving", totals.totalSaving()).put("rate", rate)
                .put("goodThreshold", SAVING_RATE_GOOD);
        return new Pattern(KEY_SAVING_RATE, rate >= SAVING_RATE_GOOD ? GOOD : CHECK,
                "이번 달 저축률은 " + percent(rate) + "%예요",
                "수입 " + won(totals.totalIncome()) + " 중 " + won(totals.totalSaving()) + "을 저축했어요.",
                month.toString(), null, evidence);
    }

    /**
     * 그 달 완료 진단의 카테고리 소비 증가 문제. 여럿이면 증가액이 가장 큰 카테고리 하나를 문구에 쓰고 건수를 evidence에 남긴다.
     * 진단이 없으면 UNAVAILABLE — "늘어난 소비가 없다"는 판정이 아니다.
     */
    private Pattern spendingIncreasePattern(Long userId, YearMonth month, Long diagnosisId) {
        if (diagnosisId == null) {
            return new Pattern(KEY_SPENDING_INCREASE, UNAVAILABLE, "소비 변화를 진단한 기록이 없어요",
                    "완료한 진단이 없어 소비 증가를 판정하지 못했어요.", month.toString(), REASON_DIAGNOSIS_NOT_FOUND, null);
        }
        List<DiagnosisIssue> increases = issues.findAllOwned(userId, diagnosisId).stream()
                .filter(issue -> SPENDING_INCREASE_ISSUE_TYPE.equals(issue.getType()))
                .sorted(Comparator.comparingLong((DiagnosisIssue issue) -> longOf(issue.getCurrentValue(), "increaseAmount")).reversed()
                        .thenComparing(DiagnosisIssue::getIssueId))
                .toList();
        if (increases.isEmpty()) {
            return new Pattern(KEY_SPENDING_INCREASE, GOOD, "급격하게 늘어난 소비가 없어요",
                    "진단 기준으로 직전 3개월 평균보다 크게 늘어난 카테고리가 없었어요.", month.toString(), null, null);
        }
        DiagnosisIssue top = increases.get(0);
        JsonNode current = top.getCurrentValue();
        JsonNode benchmark = top.getBenchmarkValue();
        String category = textOf(current, "categoryName", textOf(current, "categoryCode", "일부"));
        long increase = longOf(current, "increaseAmount");
        int baselineMonths = intOf(benchmark == null ? null : benchmark.get("baselinePeriod"), "months", 3);
        ObjectNode evidence = json.createObjectNode();
        evidence.put("diagnosisId", diagnosisId).put("issueId", top.getIssueId()).put("issueCount", increases.size())
                .put("categoryCode", textOf(current, "categoryCode", null)).put("categoryName", textOf(current, "categoryName", null))
                .put("increaseAmount", increase).put("baselineMonths", baselineMonths);
        JsonNode rate = current == null ? null : current.get("increaseRatePercent");
        if (rate != null && rate.isNumber()) {
            evidence.put("increaseRatePercent", rate.doubleValue());
        }
        return new Pattern(KEY_SPENDING_INCREASE, CHECK, category + " 소비가 늘었어요",
                "직전 " + baselineMonths + "개월 평균보다 월 " + won(increase) + " 늘었어요"
                        + (increases.size() > 1 ? " (늘어난 카테고리 " + increases.size() + "개 중 최대)." : "."),
                period(current, benchmark, month), null, evidence);
    }

    private Pattern cashflowPattern(Long userId, YearMonth month) {
        // 694와 같은 기준: 기준일이 그 달 말일 이전인 마지막 완료 예측(효율 점수 CASHFLOW 항목과 같은 예측).
        // 694 v3와 같은 기준: 말일 이전 마지막 완료 예측, 없으면 생성 시점 최신 완료 예측으로 폴백(근거에 "가장 최근 예측 기준"을 남긴다).
        Optional<CashflowForecast> forecast = forecasts
                .findFirstByUserIdAndStatusAndBaseDateLessThanEqualOrderByBaseDateDescCompletedAtDescForecastIdDesc(
                        userId, ForecastStatus.COMPLETED, month.atEndOfMonth())
                .or(() -> forecasts.findFirstByUserIdAndStatusOrderByCompletedAtDescForecastIdDesc(userId, ForecastStatus.COMPLETED));
        if (forecast.isEmpty()) {
            return new Pattern(KEY_CASHFLOW_RISK, UNAVAILABLE, "현금흐름 예측 데이터가 없어요",
                    "완료한 현금흐름 예측이 없어 예정 잔액 위험을 확인하지 못했어요.", month.toString(), REASON_FORECAST_NOT_FOUND, null);
        }
        CashflowForecast f = forecast.get();
        boolean afterMonthEnd = f.getBaseDate().isAfter(month.atEndOfMonth());
        String prefix = afterMonthEnd ? "가장 최근 예측 기준으로 " : "";
        String period = f.getBaseDate() + "~" + f.getBaseDate().plusDays(f.getHorizonDays() == null ? 0 : f.getHorizonDays());
        List<CashflowRisk> found = risks.findOwned(userId, f.getForecastId(), null, null).stream()
                .sorted(Comparator.comparing(CashflowRisk::getRiskDate).thenComparing(CashflowRisk::getRiskId))
                .toList();
        if (found.isEmpty()) {
            return new Pattern(KEY_CASHFLOW_RISK, GOOD, "예정된 현금흐름 위험이 없어요",
                    prefix + "예측 기간 안에 잔액 부족 위험이 발견되지 않았어요.", period, null, null);
        }
        CashflowRisk first = found.get(0);
        ObjectNode evidence = json.createObjectNode();
        evidence.put("forecastId", f.getForecastId()).put("riskCount", found.size()).put("riskId", first.getRiskId())
                .put("riskDate", first.getRiskDate().toString()).put("type", first.getType()).put("severity", first.getSeverity().name());
        if (first.getExpectedBalance() != null) {
            evidence.put("expectedBalance", first.getExpectedBalance());
        }
        evidence.put("afterMonthEnd", afterMonthEnd);
        return new Pattern(KEY_CASHFLOW_RISK, CHECK, "예상 잔액을 확인해 주세요", prefix + first.getMessage(), period, null, evidence);
    }

    // ---------- 도우미 ----------

    private static String period(JsonNode current, JsonNode benchmark, YearMonth fallback) {
        JsonNode recent = current == null ? null : current.get("recentPeriod");
        JsonNode baseline = benchmark == null ? null : benchmark.get("baselinePeriod");
        String recentText = recent == null ? null : range(recent);
        String baselineText = baseline == null ? null : range(baseline);
        if (recentText == null) {
            return fallback.toString();
        }
        return baselineText == null ? recentText : recentText + " vs " + baselineText;
    }

    private static String range(JsonNode period) {
        String start = textOf(period, "startMonth", null);
        String end = textOf(period, "endMonth", null);
        if (start == null || end == null) {
            return null;
        }
        return start.equals(end) ? start : start + "~" + end;
    }

    static double ratio(long amount, long total) {
        return BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP).doubleValue();
    }

    private static String percent(double ratio) {
        return BigDecimal.valueOf(ratio * 100).setScale(0, RoundingMode.HALF_UP).toPlainString();
    }

    private static String won(long amount) {
        return String.format("%,d원", amount);
    }

    private static long longOf(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isNumber() ? value.asLong() : 0L;
    }

    private static int intOf(JsonNode node, String field, int fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isInt() && value.intValue() > 0 ? value.intValue() : fallback;
    }

    private static String textOf(JsonNode node, String field, String fallback) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && !value.isNull() ? value.asText() : fallback;
    }
}
