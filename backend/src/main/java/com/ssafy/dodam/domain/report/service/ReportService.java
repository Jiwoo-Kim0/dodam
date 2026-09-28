package com.ssafy.dodam.domain.report.service;

import com.ssafy.dodam.domain.report.dto.DailyReportResponse;
import com.ssafy.dodam.domain.report.dto.EfficiencyScoreResponse;
import com.ssafy.dodam.domain.report.dto.MonthlyGoalImpactResponse;
import com.ssafy.dodam.domain.report.dto.MonthlyImprovementsResponse;
import com.ssafy.dodam.domain.report.dto.MonthlyReportMetricsResponse;
import com.ssafy.dodam.domain.report.dto.MonthlyReportResponse;
import com.ssafy.dodam.domain.report.dto.MonthlyReportResponse.Financial;
import com.ssafy.dodam.domain.report.dto.MonthlySpendingPatternResponse;
import com.ssafy.dodam.domain.report.dto.ReportSummaryResponse;
import com.ssafy.dodam.domain.report.entity.DailyReport;
import com.ssafy.dodam.domain.report.entity.MonthlyReport;
import com.ssafy.dodam.domain.report.entity.Report;
import com.ssafy.dodam.domain.report.entity.ReportDataStatus;
import com.ssafy.dodam.domain.report.entity.ReportType;
import com.ssafy.dodam.domain.report.repository.DailyReportRepository;
import com.ssafy.dodam.domain.report.repository.MonthlyReportRepository;
import com.ssafy.dodam.domain.report.repository.ReportRepository;
import com.ssafy.dodam.domain.report.repository.ReportUserLock;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.policy.QueryPeriod;
import com.ssafy.dodam.global.policy.QueryPeriodPolicy;
import com.ssafy.dodam.global.response.PageResponse;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API 20 Report. 리포트는 배치가 아니라 <b>조회 시점에</b> 만들어진다(스케줄러가 없다).
 *
 * <h2>생성·캐시 정책(FE 협의안 4번 확정본)</h2>
 * <ul>
 *   <li>끝난 기간을 조회하면 저장된 리포트를 돌려주고, 없으면 만들어 저장한 뒤 돌려준다. 200이다.</li>
 *   <li>{@code READY}로 저장된 리포트는 <b>고정</b>이다. 같은 달을 다시 봐도 숫자와 {@code generatedAt}이 그대로다.</li>
 *   <li>{@code NO_TRANSACTIONS}·{@code INSUFFICIENT_DATA}는 다음 조회 때 다시 계산한다. 금융망 동기화가 늦게
 *       도착해 거래가 나중에 생기는 경우가 있어서다.</li>
 *   <li>그래서 404는 <b>리포트 상세(#5)</b>에만 남는다. 없는 리포트·타인 리포트를 구분하지 않는다.
 *       기간 자체가 잘못된 요청(오늘·미래·상한 초과)은 400이다.</li>
 * </ul>
 *
 * <p>하위 응답(소비패턴·목표변화·개선행동 요약)은 저장된 월간 리포트의 JSON을 읽는다. 같은 달을 두 경로로
 * 다시 집계하면 합계가 어긋나기 때문이다. 다음달 예정비용만 조회 시점 계산이다
 * ({@link UpcomingExpenseService}).
 */
@Service
public class ReportService {

    static final String NOT_FOUND_MESSAGE = "리포트를 찾을 수 없습니다.";
    static final String GOAL_NOT_FOUND_MESSAGE = "목표를 찾을 수 없습니다.";

    private final ReportRepository reports;
    private final DailyReportRepository dailyReports;
    private final MonthlyReportRepository monthlyReports;
    private final ReportUserLock users;
    private final DailyReportGenerator dailyGenerator;
    private final MonthlyReportGenerator monthlyGenerator;
    private final ReportPeriodPolicy periods;
    private final QueryPeriodPolicy dayPeriods;
    private final ReportJson json;
    private final ReportClock clock;

    public ReportService(ReportRepository reports, DailyReportRepository dailyReports,
            MonthlyReportRepository monthlyReports, ReportUserLock users, DailyReportGenerator dailyGenerator,
            MonthlyReportGenerator monthlyGenerator, ReportPeriodPolicy periods, QueryPeriodPolicy dayPeriods,
            ReportJson json, ReportClock clock) {
        this.reports = reports;
        this.dailyReports = dailyReports;
        this.monthlyReports = monthlyReports;
        this.users = users;
        this.dailyGenerator = dailyGenerator;
        this.monthlyGenerator = monthlyGenerator;
        this.periods = periods;
        this.dayPeriods = dayPeriods;
        this.json = json;
        this.clock = clock;
    }

    // ---------- #1 데일리 브리핑 ----------

    @Transactional
    public DailyReportResponse daily(Long userId, LocalDate date) {
        LocalDate resolved = periods.resolveDate(date, clock.today());
        Report report = ensureDaily(userId, resolved);
        return toResponse(report, dailyReports.findById(report.getReportId()).orElseThrow(
                () -> new ResourceNotFoundException(NOT_FOUND_MESSAGE)));
    }

    // ---------- #2 월간 리포트 ----------

    @Transactional
    public MonthlyReportResponse monthly(Long userId, String yearMonth) {
        YearMonth month = periods.resolveMonth(yearMonth, clock.today());
        Report report = ensureMonthly(userId, month);
        return toResponse(report, monthlyReport(report));
    }

    // ---------- #3 리포트 목록 ----------

    @Transactional(readOnly = true)
    public PageResponse<ReportSummaryResponse> list(Long userId, ReportType type, LocalDate fromDate,
            LocalDate toDate, int page, int size) {
        QueryPeriod period = dayPeriods.resolve(fromDate, toDate, clock.today());
        var found = reports.findOwned(userId, type, period.startDate(), period.endDate(),
                PageRequest.of(page, size));
        return PageResponse.from(found, ReportSummaryResponse::from);
    }

    // ---------- #5 리포트 상세 ----------

    /**
     * 저장된 리포트 1건. 타입에 따라 {@link DailyReportResponse} 또는 {@link MonthlyReportResponse}다.
     * 여기서는 생성하지 않는다 — 식별자로 부르는 조회라 만들 기간이 없다.
     */
    @Transactional(readOnly = true)
    public Object detail(Long userId, Long reportId) {
        Report report = reports.findByReportIdAndUserId(reportId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
        if (report.getType() == ReportType.DAILY) {
            return toResponse(report, dailyReports.findById(reportId)
                    .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE)));
        }
        return toResponse(report, monthlyReport(report));
    }

    // ---------- #4 · #6 · #8 저장된 월간 리포트에서 읽는 하위 응답 ----------

    @Transactional
    public MonthlySpendingPatternResponse spendingPattern(Long userId, String yearMonth) {
        MonthlyReport monthly = monthlyReport(ensureMonthly(userId, periods.resolveMonth(yearMonth, clock.today())));
        MonthlySpendingPatternResponse stored =
                json.fromJson(monthly.getSpendingSummary(), MonthlySpendingPatternResponse.class);
        return stored != null ? stored : new MonthlySpendingPatternResponse(monthly.getYearMonth(), 0L, 0L,
                MonthlySpendingPatternResponse.RATIO_BASE, List.of(), Map.of(), null, null);
    }

    @Transactional
    public MonthlyGoalImpactResponse goalImpact(Long userId, String yearMonth, Long goalId) {
        MonthlyReport monthly = monthlyReport(ensureMonthly(userId, periods.resolveMonth(yearMonth, clock.today())));
        MonthlyGoalImpactResponse stored = json.fromJson(monthly.getGoalSummary(), MonthlyGoalImpactResponse.class);
        List<MonthlyGoalImpactResponse.Goal> goals = stored == null ? List.of() : stored.goals();
        if (goalId == null) {
            return new MonthlyGoalImpactResponse(monthly.getYearMonth(), goals);
        }
        // 저장본에는 그 사용자의 모으기 목표만 들어 있다. 없으면 타인 목표·삭제된 목표·줄이기 목표이며 모두 404다.
        List<MonthlyGoalImpactResponse.Goal> filtered = goals.stream()
                .filter(goal -> goalId.equals(goal.goalId())).toList();
        if (filtered.isEmpty()) {
            throw new ResourceNotFoundException(GOAL_NOT_FOUND_MESSAGE);
        }
        return new MonthlyGoalImpactResponse(monthly.getYearMonth(), filtered);
    }

    @Transactional
    public MonthlyImprovementsResponse improvements(Long userId, String yearMonth) {
        MonthlyReport monthly = monthlyReport(ensureMonthly(userId, periods.resolveMonth(yearMonth, clock.today())));
        MonthlyImprovementsResponse stored =
                json.fromJson(monthly.getImprovementSummary(), MonthlyImprovementsResponse.class);
        return stored != null ? stored
                : new MonthlyImprovementsResponse(monthly.getYearMonth(), 0L, 0L, 0L, 0L, List.of());
    }

    // ---------- 생성 ----------

    private Report ensureDaily(Long userId, LocalDate date) {
        lock(userId);
        String period = date.toString();
        Report report = reports.findByUserIdAndTypeAndPeriod(userId, ReportType.DAILY, period).orElse(null);
        if (report == null) {
            DailyReportGenerator.Content content = dailyGenerator.generate(userId, date);
            report = reports.save(Report.of(userId, ReportType.DAILY, period, date, date,
                    "%s 데일리 브리핑".formatted(period), clock.now()));
            dailyReports.save(DailyReport.of(report.getReportId(), date, content.dataStatus(), content.summary(),
                    json.toJson(content.financial()), json.toJson(events(content))));
            return report;
        }
        DailyReport stored = dailyReports.findById(report.getReportId())
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
        if (stored.getDataStatus().isRegenerable()) {
            DailyReportGenerator.Content content = dailyGenerator.generate(userId, date);
            stored.refresh(content.dataStatus(), content.summary(), json.toJson(content.financial()),
                    json.toJson(events(content)));
        }
        return report;
    }

    private static DailyReportResponse.NotableEvents events(DailyReportGenerator.Content content) {
        return new DailyReportResponse.NotableEvents(content.notableEvents());
    }

    private Report ensureMonthly(Long userId, YearMonth month) {
        lock(userId);
        String period = month.toString();
        Report report = reports.findByUserIdAndTypeAndPeriod(userId, ReportType.MONTHLY, period).orElse(null);
        if (report == null) {
            var content = monthlyGenerator.generate(userId, month);
            report = reports.save(Report.of(userId, ReportType.MONTHLY, period, month.atDay(1), month.atEndOfMonth(),
                    "%s 월간 리포트".formatted(period), clock.now()));
            monthlyReports.save(MonthlyReport.of(report.getReportId(), period, toEntityContent(content)));
            return report;
        }
        MonthlyReport stored = monthlyReport(report);
        if (stored.getDataStatus().isRegenerable()) {
            stored.refresh(toEntityContent(monthlyGenerator.generate(userId, month)));
            return report;
        }
        supplementIfMissing(userId, month, stored);
        return report;
    }

    /**
     * 635·697 이전에 굳힌 READY 리포트에 카드 사용액·효율 점수·주요 지표/패턴이 없으면 그 값만 채운다. 합계·생성 시각은 그대로다.
     * 저장 형식을 읽지 못한 리포트({@code financial == null})는 건드리지 않는다 — {@code INSUFFICIENT_DATA}로 응답한다.
     */
    private void supplementIfMissing(Long userId, YearMonth month, MonthlyReport stored) {
        Financial financial = json.fromJson(stored.getFinancialSummary(), Financial.class);
        if (financial == null || (financial.hasCardSpending() && stored.hasEfficiencySummary() && stored.hasMetricsSummary())) {
            return;
        }
        MonthlyReportGenerator.Supplement supplement =
                monthlyGenerator.supplement(userId, month, financial, stored.getDiagnosisId());
        stored.supplement(supplement.financialSummary(), supplement.efficiencySummary(), supplement.metricsSummary());
    }

    private static MonthlyReport.Content toEntityContent(MonthlyReportGenerator.Content content) {
        return new MonthlyReport.Content(content.dataStatus(), content.diagnosisId(), content.overallStatus(),
                content.financialSummary(), content.spendingSummary(), content.goalSummary(),
                content.improvementSummary(), content.efficiencySummary(), content.metricsSummary());
    }

    private MonthlyReport monthlyReport(Report report) {
        return monthlyReports.findById(report.getReportId())
                .orElseThrow(() -> new ResourceNotFoundException(NOT_FOUND_MESSAGE));
    }

    /** 사용자 행을 잠가 "없으면 만든다"가 동시 요청에서 두 번 일어나지 않게 한다. 없는 사용자는 404다. */
    private void lock(Long userId) {
        users.lock(userId).orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
    }

    // ---------- 응답 변환 ----------

    private DailyReportResponse toResponse(Report report, DailyReport daily) {
        DailyReportResponse.Financial financial =
                json.fromJson(daily.getFinancialSummary(), DailyReportResponse.Financial.class);
        DailyReportResponse.NotableEvents events =
                json.fromJson(daily.getNotableEvents(), DailyReportResponse.NotableEvents.class);
        ReportDataStatus status = financial == null ? ReportDataStatus.INSUFFICIENT_DATA : daily.getDataStatus();
        return new DailyReportResponse(report.getReportId(), report.getType().name(), report.getPeriod(),
                report.getCreatedAt(), status.name(),
                financial == null ? null : financial.totalIncome(),
                financial == null ? null : financial.totalExpense(),
                financial == null ? null : financial.totalSpending(),
                financial == null ? null : financial.totalSaving(),
                financial == null ? null : financial.transactionCount(),
                financial == null ? List.of() : financial.topCategories(),
                financial == null ? null : financial.balanceChange(),
                events == null ? List.of() : events.items(),
                daily.getCoachMessage(),
                status.unavailableReason());
    }

    private MonthlyReportResponse toResponse(Report report, MonthlyReport monthly) {
        Financial financial = json.fromJson(monthly.getFinancialSummary(), Financial.class);
        ReportDataStatus status = financial == null ? ReportDataStatus.INSUFFICIENT_DATA : monthly.getDataStatus();
        return new MonthlyReportResponse(report.getReportId(), report.getType().name(), report.getPeriod(),
                report.getCreatedAt(), status.name(),
                financial == null ? null : financial.totalIncome(),
                financial == null ? null : financial.totalExpense(),
                financial == null ? null : financial.totalSpending(),
                financial == null ? null : financial.totalSaving(),
                financial == null ? null : financial.netCashflow(),
                financial == null ? null : financial.transactionCount(),
                financial == null ? null : financial.previousMonth(),
                financial == null ? null : financial.changeFromPreviousMonth(),
                financial == null ? null : financial.cardSpending(),
                financial == null ? null : financial.cardSpendingPreviousMonth(),
                financial == null ? null : financial.cardSpendingDifferenceFromPreviousMonth(),
                json.fromJson(monthly.getEfficiencySummary(), EfficiencyScoreResponse.class),
                json.fromJson(monthly.getMetricsSummary(), MonthlyReportMetricsResponse.class),
                monthly.getDiagnosisId(), monthly.getOverallStatus(), status.unavailableReason());
    }
}
