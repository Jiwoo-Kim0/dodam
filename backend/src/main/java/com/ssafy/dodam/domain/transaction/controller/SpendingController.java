package com.ssafy.dodam.domain.transaction.controller;

import com.ssafy.dodam.domain.transaction.dto.response.CategorySpendingResponse;
import com.ssafy.dodam.domain.transaction.dto.response.CategorySpendingTrendResponse;
import com.ssafy.dodam.domain.transaction.dto.response.DailySpendingResponse;
import com.ssafy.dodam.domain.transaction.dto.response.PaymentMethodSpendingResponse;
import com.ssafy.dodam.domain.transaction.dto.response.SpendingMonthlySummaryResponse;
import com.ssafy.dodam.domain.transaction.dto.response.SpendingProgressResponse;
import com.ssafy.dodam.domain.transaction.service.SpendingQueryService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.security.AuthPrincipal;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 명세 6. Spending 중 S15P21D105-506 범위(월 소비 요약·월 누적 소비·일별 소비·카테고리별 소비·카테고리 소비 추이·결제수단별 소비).
 * 소비·저축 비율, 소비 급증, 소비목표 비교, 고정·변동지출은 다음 티켓에서 이어 붙인다.
 */
@Validated
@RestController
@RequestMapping("/api/v1/spending")
public class SpendingController {

    /** 연월 파라미터는 서비스의 YearMonthPolicy가 형식·범위를 검증한다. 여기서는 길이만 막는다. */
    private static final String YEAR_MONTH_LOOSE_PATTERN = "^[0-9-]{0,7}$";
    private static final String CODE_PATTERN = "^\\s*$|^[A-Za-z0-9_]+$";

    private final SpendingQueryService spendingQueryService;

    public SpendingController(SpendingQueryService spendingQueryService) {
        this.spendingQueryService = spendingQueryService;
    }

    @GetMapping("/monthly-summary")
    public ApiResponse<SpendingMonthlySummaryResponse> getMonthlySummary(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) @Pattern(regexp = YEAR_MONTH_LOOSE_PATTERN) String yearMonth
    ) {
        return ApiResponse.success(spendingQueryService.getMonthlySummary(principal.userId(), yearMonth));
    }

    @GetMapping("/progress")
    public ApiResponse<SpendingProgressResponse> getProgress(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) @Pattern(regexp = YEAR_MONTH_LOOSE_PATTERN) String yearMonth
    ) {
        return ApiResponse.success(spendingQueryService.getProgress(principal.userId(), yearMonth));
    }

    @GetMapping("/daily")
    public ApiResponse<DailySpendingResponse> getDaily(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ApiResponse.success(spendingQueryService.getDaily(principal.userId(), startDate, endDate));
    }

    @GetMapping("/categories")
    public ApiResponse<CategorySpendingResponse> getByCategory(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) @Pattern(regexp = YEAR_MONTH_LOOSE_PATTERN) String yearMonth
    ) {
        return ApiResponse.success(spendingQueryService.getByCategory(principal.userId(), yearMonth));
    }

    @GetMapping("/categories/trend")
    public ApiResponse<CategorySpendingTrendResponse> getCategoryTrend(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) @Pattern(regexp = YEAR_MONTH_LOOSE_PATTERN) String startMonth,
            @RequestParam(required = false) @Pattern(regexp = YEAR_MONTH_LOOSE_PATTERN) String endMonth,
            @RequestParam(required = false) @Size(max = 50) @Pattern(regexp = CODE_PATTERN) String category
    ) {
        return ApiResponse.success(spendingQueryService.getCategoryTrend(principal.userId(), startMonth, endMonth, category));
    }

    @GetMapping("/payment-methods")
    public ApiResponse<PaymentMethodSpendingResponse> getByPaymentMethod(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) @Pattern(regexp = YEAR_MONTH_LOOSE_PATTERN) String yearMonth
    ) {
        return ApiResponse.success(spendingQueryService.getByPaymentMethod(principal.userId(), yearMonth));
    }
}
