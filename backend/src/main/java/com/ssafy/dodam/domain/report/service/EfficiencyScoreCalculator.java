package com.ssafy.dodam.domain.report.service;

import com.ssafy.dodam.domain.cashflow.entity.CashflowForecast;
import com.ssafy.dodam.domain.cashflow.entity.CashflowRisk;
import com.ssafy.dodam.domain.cashflow.entity.ForecastStatus;
import com.ssafy.dodam.domain.cashflow.repository.CashflowForecastRepository;
import com.ssafy.dodam.domain.cashflow.repository.CashflowRiskRepository;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisIssue;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisIssueRepository;
import com.ssafy.dodam.domain.report.dto.EfficiencyScoreResponse;
import com.ssafy.dodam.domain.report.dto.EfficiencyScoreResponse.Dimension;
import com.ssafy.dodam.domain.report.dto.MonthlyReportResponse.MonthAmounts;
import com.ssafy.dodam.domain.transaction.entity.SpendingType;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingAnalysisReader;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingReader;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 금융 효율 점수 입력을 모아 {@link EfficiencyScorePolicy}로 점수를 만든다(S15P21D105-635).
 *
 * <p>입력 출처는 전부 리포트가 이미 쓰는 저장값이다. 소비·저축·소득은 {@code ReportAmountReader}의 합계(리포트 합계와 같은
 * 숫자), 고정지출은 소비패턴의 {@code bySpendingType.FIXED}, 신용은 {@code user.credit_score}(680)를 우선하고 없으면
 * {@code user.credit_rating}(629)을 호환 입력으로 쓴다. 둘 다 현재 값이라 과거 달 리포트에도 조회 시점 값이 들어간다.
 * 현금흐름은 기준일이 그 달 말일 이전인 마지막 완료 예측의 위험, 비효율은 그 달 말일까지 완료된 가장 최근 진단의 문제
 * (GOAL 도메인 제외)다. 다시 예측하거나 진단하지 않는다.
 *
 * <p>예측·진단의 기준을 "그 달 안"이 아니라 "그 달 말일까지"로 두는 이유(694): 예측 기준일과 진단
 * 완료 시각은 항상 실행 시점이라 지난 달 것을 나중에 만들 수 없다. 그 달 안에 실행하지 않았다는 이유만으로 두 항목이
 * 영원히 계산 불가로 남는 대신, 그 달 말일 시점에 사용자가 알고 있던 마지막 상태를 쓴다. 그 달 이후의 실행은 여전히
 * 보지 않으므로 과거 리포트가 현재 상태로 바뀌지는 않는다.
 */
@Component
@Transactional(readOnly = true)
public class EfficiencyScoreCalculator {

    /** 목표 위험은 목표 화면이 보여 주고 607에서 발견 목록에서 제외했으므로 비효율 건수에도 넣지 않는다. */
    static final String EXCLUDED_ISSUE_DOMAIN = "GOAL";

    private final JdbcSpendingAnalysisReader spendingTypes;
    private final UserRepository users;
    private final CashflowForecastRepository forecasts;
    private final CashflowRiskRepository risks;
    private final DiagnosisIssueRepository issues;

    public EfficiencyScoreCalculator(JdbcSpendingAnalysisReader spendingTypes, UserRepository users,
            CashflowForecastRepository forecasts, CashflowRiskRepository risks, DiagnosisIssueRepository issues) {
        this.spendingTypes = spendingTypes;
        this.users = users;
        this.forecasts = forecasts;
        this.risks = risks;
        this.issues = issues;
    }

    /**
     * @param totals      그 달 합계(리포트 합계와 같은 값)
     * @param diagnosisId 그 달 말일까지 완료된 가장 최근 진단. 없으면 null
     */
    public EfficiencyScoreResponse calculate(Long userId, YearMonth month, MonthAmounts totals, Long diagnosisId) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        long fixed = spendingTypes.readMonthlyBySpendingType(userId, SpendingType.FIXED.name(), start, end).stream()
                .mapToLong(JdbcSpendingReader.MonthlySpendingRow::total).sum();
        User user = users.findById(userId).orElse(null);
        Integer creditScore = user == null ? null : user.getCreditScore();
        String creditRating = user == null ? null : user.getCreditRating();

        Optional<CashflowForecast> forecast = forecasts
                .findFirstByUserIdAndStatusAndBaseDateLessThanEqualOrderByBaseDateDescCompletedAtDescForecastIdDesc(
                        userId, ForecastStatus.COMPLETED, end);
        List<String> riskSeverities = forecast
                .map(f -> risks.findOwned(userId, f.getForecastId(), null, null).stream()
                        .map(CashflowRisk::getSeverity).map(Enum::name).toList())
                .orElse(List.of());

        List<String> issueSeverities = diagnosisId == null ? List.of()
                : issues.findAllOwned(userId, diagnosisId).stream()
                        .filter(issue -> !EXCLUDED_ISSUE_DOMAIN.equals(issue.getDomain()))
                        .map(DiagnosisIssue::getSeverity).map(Enum::name).toList();

        List<Dimension> dimensions = EfficiencyScorePolicy.ordered(
                EfficiencyScorePolicy.spending(totals.totalSpending(), totals.totalIncome()),
                EfficiencyScorePolicy.saving(totals.totalSaving(), totals.totalIncome()),
                EfficiencyScorePolicy.fixedExpense(fixed, totals.totalSpending()),
                EfficiencyScorePolicy.credit(creditScore, creditRating),
                EfficiencyScorePolicy.cashflow(forecast.isPresent(), riskSeverities),
                EfficiencyScorePolicy.inefficiency(diagnosisId != null, issueSeverities));
        return EfficiencyScorePolicy.overall(dimensions);
    }
}
