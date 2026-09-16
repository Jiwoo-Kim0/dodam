package com.ssafy.dodam.domain.report.controller;

import com.ssafy.dodam.domain.report.dto.DailyReportResponse;
import com.ssafy.dodam.domain.report.dto.MonthlyGoalImpactResponse;
import com.ssafy.dodam.domain.report.dto.MonthlyImprovementsResponse;
import com.ssafy.dodam.domain.report.dto.MonthlyReportResponse;
import com.ssafy.dodam.domain.report.dto.MonthlySpendingPatternResponse;
import com.ssafy.dodam.domain.report.dto.ReportSummaryResponse;
import com.ssafy.dodam.domain.report.dto.UpcomingExpensesResponse;
import com.ssafy.dodam.domain.report.entity.ReportType;
import com.ssafy.dodam.domain.report.service.ReportService;
import com.ssafy.dodam.domain.report.service.UpcomingExpenseService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.response.PageResponse;
import com.ssafy.dodam.global.security.AuthenticatedUserIdResolver;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 20 Report. 리터럴 경로(/daily, /monthly, /monthly/...)가 {@code /{reportId}}보다 먼저 매칭되고
 * path variable이 Long이라 문자열은 400이다.
 *
 * <p>{@code /daily}·{@code /monthly}는 저장된 리포트가 없으면 만들어서 돌려준다(200). 404는 상세 조회에만 남는다.
 * 기간 형식·범위 위반은 400이다.
 */
@Validated
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private final ReportService reports;
    private final UpcomingExpenseService upcomingExpenses;
    private final AuthenticatedUserIdResolver users;

    public ReportController(ReportService reports, UpcomingExpenseService upcomingExpenses,
            AuthenticatedUserIdResolver users) {
        this.reports = reports;
        this.upcomingExpenses = upcomingExpenses;
        this.users = users;
    }

    @GetMapping("/daily")
    public ApiResponse<DailyReportResponse> daily(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            Authentication authentication) {
        return ApiResponse.success(reports.daily(users.resolve(authentication), date));
    }

    @GetMapping("/monthly")
    public ApiResponse<MonthlyReportResponse> monthly(@RequestParam(required = false) String yearMonth,
            Authentication authentication) {
        return ApiResponse.success(reports.monthly(users.resolve(authentication), yearMonth));
    }

    @GetMapping
    public ApiResponse<PageResponse<ReportSummaryResponse>> list(
            @RequestParam(required = false) ReportType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, Authentication authentication) {
        return ApiResponse.success(reports.list(users.resolve(authentication), type, fromDate, toDate, page, size));
    }

    @GetMapping("/monthly/spending-pattern")
    public ApiResponse<MonthlySpendingPatternResponse> spendingPattern(
            @RequestParam(required = false) String yearMonth, Authentication authentication) {
        return ApiResponse.success(reports.spendingPattern(users.resolve(authentication), yearMonth));
    }

    @GetMapping("/monthly/goal-impact")
    public ApiResponse<MonthlyGoalImpactResponse> goalImpact(@RequestParam(required = false) String yearMonth,
            @RequestParam(required = false) @Positive Long goalId, Authentication authentication) {
        return ApiResponse.success(reports.goalImpact(users.resolve(authentication), yearMonth, goalId));
    }

    @GetMapping("/monthly/upcoming-expenses")
    public ApiResponse<UpcomingExpensesResponse> upcomingExpenses(@RequestParam(required = false) String yearMonth,
            Authentication authentication) {
        return ApiResponse.success(upcomingExpenses.upcoming(users.resolve(authentication), yearMonth));
    }

    @GetMapping("/monthly/improvements")
    public ApiResponse<MonthlyImprovementsResponse> improvements(@RequestParam(required = false) String yearMonth,
            Authentication authentication) {
        return ApiResponse.success(reports.improvements(users.resolve(authentication), yearMonth));
    }

    @GetMapping("/{reportId}")
    public ApiResponse<Object> detail(@PathVariable @Positive Long reportId, Authentication authentication) {
        return ApiResponse.success(reports.detail(users.resolve(authentication), reportId));
    }
}
