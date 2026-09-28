package com.ssafy.dodam.domain.report.service;

import com.ssafy.dodam.domain.cashflow.entity.CashflowForecast;
import com.ssafy.dodam.domain.cashflow.entity.ForecastStatus;
import com.ssafy.dodam.domain.cashflow.repository.CashflowForecastRepository;
import com.ssafy.dodam.domain.cashflow.repository.CashflowRiskRepository;
import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisIssueRepository;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisRepository;
import com.ssafy.dodam.domain.loan.repository.LoanRepaymentReader;
import com.ssafy.dodam.domain.report.dto.EfficiencyScoreResponse;
import com.ssafy.dodam.domain.report.dto.EfficiencyScoreResponse.Dimension;
import com.ssafy.dodam.domain.report.dto.MonthlyReportResponse.MonthAmounts;
import com.ssafy.dodam.domain.transaction.entity.SpendingType;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingAnalysisReader;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingReader;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalTime;
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
 * 부채 상환은 그 달 {@code loan_repayment}의 PAID 상환액 합계다(712, v4).
 *
 * <p>현금흐름·비효율(v5에서 복원)은 694 v3 기준을 따른다. 현금흐름은 기준일이 그 달 말일 이전인 마지막 완료 예측의 위험, 비효율은
 * 그 달 말일까지 완료된 최신 진단의 문제(GOAL 제외)이며 둘 다 없으면 생성 시점 최신으로 폴백한다. 리포트 주요 지표
 * ({@link MonthlyReportMetricsAssembler})·{@code MonthlyReport.diagnosisId}와 같은 예측·진단을 본다. 사용자가 예측·발견 분석을
 * 실행하기 전이면 계산 불가이며, 그렇게 굳은 점수는 실행 뒤 조회에서 다시 계산된다({@link EfficiencyScorePolicy#needsRecalculation}).
 */
@Component
@Transactional(readOnly = true)
public class EfficiencyScoreCalculator {

    /** 목표 위험은 목표 화면이 보여 주므로 발견(비효율) 대상에서 뺀다(607). */
    static final String GOAL_DOMAIN = "GOAL";

    private final JdbcSpendingAnalysisReader spendingTypes;
    private final UserRepository users;
    private final LoanRepaymentReader loanRepayments;
    private final CashflowForecastRepository forecasts;
    private final CashflowRiskRepository risks;
    private final DiagnosisRepository diagnoses;
    private final DiagnosisIssueRepository issues;

    public EfficiencyScoreCalculator(JdbcSpendingAnalysisReader spendingTypes, UserRepository users,
            LoanRepaymentReader loanRepayments, CashflowForecastRepository forecasts, CashflowRiskRepository risks,
            DiagnosisRepository diagnoses, DiagnosisIssueRepository issues) {
        this.spendingTypes = spendingTypes;
        this.users = users;
        this.loanRepayments = loanRepayments;
        this.forecasts = forecasts;
        this.risks = risks;
        this.diagnoses = diagnoses;
        this.issues = issues;
    }

    /**
     * @param totals 그 달 합계(리포트 합계와 같은 값)
     */
    public EfficiencyScoreResponse calculate(Long userId, YearMonth month, MonthAmounts totals) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        long fixed = spendingTypes.readMonthlyBySpendingType(userId, SpendingType.FIXED.name(), start, end).stream()
                .mapToLong(JdbcSpendingReader.MonthlySpendingRow::total).sum();
        User user = users.findById(userId).orElse(null);
        Integer creditScore = user == null ? null : user.getCreditScore();
        String creditRating = user == null ? null : user.getCreditRating();
        long paidRepayment = loanRepayments.sumPaidAmount(userId, start, end);

        List<Dimension> dimensions = EfficiencyScorePolicy.ordered(
                EfficiencyScorePolicy.spending(totals.totalSpending(), totals.totalIncome()),
                EfficiencyScorePolicy.saving(totals.totalSaving(), totals.totalIncome()),
                EfficiencyScorePolicy.fixedExpense(fixed, totals.totalSpending()),
                EfficiencyScorePolicy.credit(creditScore, creditRating),
                cashflow(userId, end),
                inefficiency(userId, end),
                EfficiencyScorePolicy.debtRepayment(paidRepayment, totals.totalIncome()));
        return EfficiencyScorePolicy.overall(dimensions);
    }

    private Dimension cashflow(Long userId, LocalDate end) {
        Optional<CashflowForecast> forecast = forecasts
                .findFirstByUserIdAndStatusAndBaseDateLessThanEqualOrderByBaseDateDescCompletedAtDescForecastIdDesc(
                        userId, ForecastStatus.COMPLETED, end)
                .or(() -> forecasts.findFirstByUserIdAndStatusOrderByCompletedAtDescForecastIdDesc(userId, ForecastStatus.COMPLETED));
        if (forecast.isEmpty()) {
            return EfficiencyScorePolicy.cashflow(null, false);
        }
        CashflowForecast f = forecast.get();
        List<String> severities = risks.findOwned(userId, f.getForecastId(), null, null).stream()
                .map(risk -> risk.getSeverity().name())
                .toList();
        return EfficiencyScorePolicy.cashflow(severities, f.getBaseDate().isAfter(end));
    }

    private Dimension inefficiency(Long userId, LocalDate end) {
        Optional<Diagnosis> inMonth = diagnoses.findFirstByUserIdAndCompletedAtLessThanEqualOrderByCompletedAtDescDiagnosisIdDesc(
                userId, end.atTime(LocalTime.MAX));
        Optional<Diagnosis> diagnosis = inMonth
                .or(() -> diagnoses.findFirstByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDescDiagnosisIdDesc(userId));
        if (diagnosis.isEmpty()) {
            return EfficiencyScorePolicy.inefficiency(null, false);
        }
        List<String> severities = issues.findAllOwned(userId, diagnosis.get().getDiagnosisId()).stream()
                .filter(issue -> !GOAL_DOMAIN.equals(issue.getDomain()))
                .map(issue -> issue.getSeverity().name())
                .toList();
        return EfficiencyScorePolicy.inefficiency(severities, inMonth.isEmpty());
    }
}
