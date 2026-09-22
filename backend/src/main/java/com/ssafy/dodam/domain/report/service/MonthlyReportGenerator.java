package com.ssafy.dodam.domain.report.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisRepository;
import com.ssafy.dodam.domain.improvement.service.ImprovementMeasurementService;
import com.ssafy.dodam.domain.report.dto.EfficiencyScoreResponse;
import com.ssafy.dodam.domain.report.dto.MonthlyReportResponse.Financial;
import com.ssafy.dodam.domain.report.dto.MonthlyReportResponse.MonthAmounts;
import com.ssafy.dodam.domain.report.entity.MonthlyReport;
import com.ssafy.dodam.domain.report.entity.Report;
import com.ssafy.dodam.domain.report.entity.ReportDataStatus;
import com.ssafy.dodam.domain.report.entity.ReportType;
import com.ssafy.dodam.domain.report.repository.JdbcReportReader;
import com.ssafy.dodam.domain.report.repository.MonthlyReportRepository;
import com.ssafy.dodam.domain.report.repository.ReportRepository;
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
 *
 * <p>(635) 카드 사용액과 금융 효율 점수도 같은 시점에 계산해 굳힌다. 효율 점수의 전월 차이는 전월 리포트가 저장돼
 * 있으면 그 점수를, 없으면 같은 산식으로 전월을 즉시 계산해 비교한다(저장하지는 않는다 — 전월 리포트는 그 달을 조회할 때
 * 만들어진다). 635 이전에 굳힌 리포트는 {@link #supplement}가 합계는 그대로 두고 두 값만 채운다.
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
    private final EfficiencyScoreCalculator efficiency;
    private final ReportRepository reportRows;
    private final MonthlyReportRepository monthlyRows;
    private final ReportJson json;

    public MonthlyReportGenerator(ReportAmountReader amounts, JdbcReportReader reports, DiagnosisRepository diagnoses,
            MonthlySpendingPatternAssembler patterns, MonthlyGoalImpactAssembler goalImpacts,
            MonthlyImprovementsAssembler improvements, ImprovementMeasurementService measurements,
            EfficiencyScoreCalculator efficiency, ReportRepository reportRows, MonthlyReportRepository monthlyRows,
            ReportJson json) {
        this.amounts = amounts;
        this.reports = reports;
        this.diagnoses = diagnoses;
        this.patterns = patterns;
        this.goalImpacts = goalImpacts;
        this.improvements = improvements;
        this.measurements = measurements;
        this.efficiency = efficiency;
        this.reportRows = reportRows;
        this.monthlyRows = monthlyRows;
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
        boolean comparable = comparable(userId, previousMonth);
        long transactionCount = reports.countTransactions(userId, start, end);
        CardSpending card = cardSpending(userId, month, comparable);

        Financial financial = new Financial(totals.totalIncome(), totals.totalExpense(), totals.totalSpending(),
                totals.totalSaving(), totals.totalIncome() - totals.totalExpense(), transactionCount,
                comparable ? previous : null, comparable ? totals.minus(previous) : null,
                card.current(), card.previous(), card.difference());

        Optional<Diagnosis> diagnosis = latestDiagnosis(userId, month);
        Long diagnosisId = diagnosis.map(Diagnosis::getDiagnosisId).orElse(null);
        EfficiencyScoreResponse score = efficiencyWithDifference(userId, month, totals, diagnosis.orElse(null), comparable);

        return new Content(
                transactionCount == 0 ? ReportDataStatus.NO_TRANSACTIONS : ReportDataStatus.READY,
                diagnosisId,
                diagnosis.map(Diagnosis::getOverallStatus).map(Enum::name).orElse(null),
                json.toJson(financial),
                json.toJson(patterns.assemble(userId, month, totals, previous)),
                json.toJson(goalImpacts.assemble(userId, month)),
                json.toJson(improvements.assemble(userId, month)),
                json.toJson(score));
    }

    /**
     * 635 이전에 굳힌 READY 리포트 보강. 저장된 합계는 그대로 두고 카드 사용액과 효율 점수만 계산한다. 효율 점수의
     * 소비·저축·소득은 저장된 합계를 쓰므로 리포트 본문과 어긋나지 않는다.
     */
    @Transactional
    public Supplement supplement(Long userId, YearMonth month, Financial stored, Long diagnosisId) {
        boolean comparable = stored.previousMonth() != null;
        CardSpending card = cardSpending(userId, month, comparable);
        MonthAmounts totals = new MonthAmounts(stored.totalIncome(), stored.totalExpense(), stored.totalSpending(),
                stored.totalSaving());
        Diagnosis diagnosis = diagnosisId == null ? null : diagnoses.findByDiagnosisIdAndUserId(diagnosisId, userId).orElse(null);
        EfficiencyScoreResponse score = efficiencyWithDifference(userId, month, totals, diagnosis, comparable);
        return new Supplement(json.toJson(stored.withCardSpending(card.current(), card.previous(), card.difference())),
                json.toJson(score));
    }

    private boolean comparable(Long userId, YearMonth previousMonth) {
        return reports.findFirstTransactionDate(userId)
                .filter(first -> !first.isAfter(previousMonth.atEndOfMonth()))
                .isPresent();
    }

    private CardSpending cardSpending(Long userId, YearMonth month, boolean comparable) {
        long current = reports.readCardSpending(userId, month.atDay(1), month.atEndOfMonth());
        if (!comparable) {
            return new CardSpending(current, null, null);
        }
        YearMonth previousMonth = month.minusMonths(1);
        long previous = reports.readCardSpending(userId, previousMonth.atDay(1), previousMonth.atEndOfMonth());
        return new CardSpending(current, previous, current - previous);
    }

    /**
     * 그 달 말일까지 완료된 가장 최근 진단, 없으면 생성 시점의 최신 완료 진단(694). 리포트의 {@code diagnosisId}·
     * {@code overallStatus}와 효율 점수 비효율 항목이 같은 진단을 본다. 폴백 여부는 {@code EfficiencyScoreCalculator}가
     * 완료 시각으로 판정한다.
     */
    private Optional<Diagnosis> latestDiagnosis(Long userId, YearMonth month) {
        return diagnoses.findFirstByUserIdAndCompletedAtLessThanEqualOrderByCompletedAtDescDiagnosisIdDesc(
                userId, month.atEndOfMonth().atTime(LocalTime.MAX))
                .or(() -> diagnoses.findFirstByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDescDiagnosisIdDesc(userId));
    }

    private EfficiencyScoreResponse efficiencyWithDifference(Long userId, YearMonth month, MonthAmounts totals,
            Diagnosis diagnosis, boolean comparable) {
        EfficiencyScoreResponse score = efficiency.calculate(userId, month, totals, diagnosis);
        if (!comparable || score.score() == null) {
            return score;
        }
        Integer previousScore = previousScore(userId, month.minusMonths(1));
        return previousScore == null ? score : score.withPreviousMonthDifference(score.score() - previousScore);
    }

    /**
     * 전월 점수. 전월 리포트가 저장돼 있고 같은 산식 버전이면 그 점수(사용자가 본 숫자와 같다), 아니면 즉시 계산한다.
     * 즉시 계산한 값은 저장하지 않는다.
     */
    private Integer previousScore(Long userId, YearMonth previousMonth) {
        Optional<EfficiencyScoreResponse> stored = reportRows
                .findByUserIdAndTypeAndPeriod(userId, ReportType.MONTHLY, previousMonth.toString())
                .map(Report::getReportId)
                .flatMap(monthlyRows::findById)
                .filter(MonthlyReport::hasEfficiencySummary)
                .map(monthly -> json.fromJson(monthly.getEfficiencySummary(), EfficiencyScoreResponse.class))
                .filter(score -> score != null && EfficiencyScorePolicy.VERSION.equals(score.calculationVersion()));
        if (stored.isPresent()) {
            return stored.get().score();
        }
        MonthAmounts totals = amounts.read(userId, previousMonth.atDay(1), previousMonth.atEndOfMonth());
        Diagnosis diagnosis = latestDiagnosis(userId, previousMonth).orElse(null);
        return efficiency.calculate(userId, previousMonth, totals, diagnosis).score();
    }

    private record CardSpending(long current, Long previous, Long difference) {
    }

    /** {@code MonthlyReport.Content}와 같은 모양이지만 엔티티에 의존하지 않도록 따로 둔다. */
    public record Content(
            ReportDataStatus dataStatus,
            Long diagnosisId,
            String overallStatus,
            JsonNode financialSummary,
            JsonNode spendingSummary,
            JsonNode goalSummary,
            JsonNode improvementSummary,
            JsonNode efficiencySummary
    ) {
    }

    /** 635 이전 리포트에 덧붙일 두 JSON. */
    public record Supplement(JsonNode financialSummary, JsonNode efficiencySummary) {
    }
}
