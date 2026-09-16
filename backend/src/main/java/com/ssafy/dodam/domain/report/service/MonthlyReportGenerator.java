package com.ssafy.dodam.domain.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisRepository;
import com.ssafy.dodam.domain.improvement.service.ImprovementMeasurementService;
import com.ssafy.dodam.domain.report.dto.MonthlyReportResponse.Financial;
import com.ssafy.dodam.domain.report.dto.MonthlyReportResponse.MonthAmounts;
import com.ssafy.dodam.domain.report.entity.ReportDataStatus;
import com.ssafy.dodam.domain.report.repository.JdbcReportReader;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 월간 리포트 본문을 만든다. 하위 응답(소비패턴·목표변화·개선행동 요약)도 여기서 함께 계산해 같은 트랜잭션에
 * 저장한다 — 각 엔드포인트가 따로 집계하면 같은 달의 합계가 서로 어긋난다.
 *
 * <p>전월 비교는 "비교 대상이 존재하는가"와 "비교했더니 0원인가"를 구분한다. 사용자의 첫 거래일보다 앞선 달은
 * 비교 대상이 아예 없으므로 {@code previousMonth}와 {@code changeFromPreviousMonth}가 둘 다 null이다.
 * 이전 달이 존재하는데 거래가 없었다면 null이 아니라 0원이다.
 */
@Component
public class MonthlyReportGenerator {

    private final ReportAmountReader amounts;
    private final JdbcReportReader reports;
    private final DiagnosisRepository diagnoses;
    private final MonthlySpendingPatternAssembler patterns;
    private final MonthlyGoalImpactAssembler goalImpacts;
    private final MonthlyImprovementsAssembler improvements;
    private final ImprovementMeasurementService measurements;
    private final ReportJson json;

    public MonthlyReportGenerator(ReportAmountReader amounts, JdbcReportReader reports, DiagnosisRepository diagnoses,
            MonthlySpendingPatternAssembler patterns, MonthlyGoalImpactAssembler goalImpacts,
            MonthlyImprovementsAssembler improvements, ImprovementMeasurementService measurements, ReportJson json) {
        this.amounts = amounts;
        this.reports = reports;
        this.diagnoses = diagnoses;
        this.patterns = patterns;
        this.goalImpacts = goalImpacts;
        this.improvements = improvements;
        this.measurements = measurements;
        this.json = json;
    }

    @Transactional
    public Content generate(Long userId, YearMonth month) {
        // 개선행동 요약이 밀린 측정을 그대로 0으로 담지 않도록 먼저 따라잡는다(배치가 없어 조회 시점이 측정 시점이다).
        measurements.refresh(userId);

        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        MonthAmounts totals = amounts.read(userId, start, end);
        YearMonth previousMonth = month.minusMonths(1);
        MonthAmounts previous = amounts.read(userId, previousMonth.atDay(1), previousMonth.atEndOfMonth());
        boolean comparable = reports.findFirstTransactionDate(userId)
                .filter(first -> !first.isAfter(previousMonth.atEndOfMonth()))
                .isPresent();
        long transactionCount = reports.countTransactions(userId, start, end);

        Financial financial = new Financial(totals.totalIncome(), totals.totalExpense(), totals.totalSpending(),
                totals.totalSaving(), totals.totalIncome() - totals.totalExpense(), transactionCount,
                comparable ? previous : null, comparable ? totals.minus(previous) : null);

        Optional<Diagnosis> diagnosis = diagnoses
                .findFirstByUserIdAndCompletedAtBetweenOrderByCompletedAtDescDiagnosisIdDesc(
                        userId, start.atStartOfDay(), end.atTime(LocalTime.MAX));

        return new Content(
                transactionCount == 0 ? ReportDataStatus.NO_TRANSACTIONS : ReportDataStatus.READY,
                diagnosis.map(Diagnosis::getDiagnosisId).orElse(null),
                diagnosis.map(Diagnosis::getOverallStatus).map(Enum::name).orElse(null),
                json.toJson(financial),
                json.toJson(patterns.assemble(userId, month, totals, previous)),
                json.toJson(goalImpacts.assemble(userId, month)),
                json.toJson(improvements.assemble(userId, month)));
    }

    /** {@code MonthlyReport.Content}와 같은 모양이지만 엔티티에 의존하지 않도록 따로 둔다. */
    public record Content(
            ReportDataStatus dataStatus,
            Long diagnosisId,
            String overallStatus,
            JsonNode financialSummary,
            JsonNode spendingSummary,
            JsonNode goalSummary,
            JsonNode improvementSummary
    ) {
    }
}
