package com.ssafy.dodam.domain.loan.controller;

import com.ssafy.dodam.domain.loan.dto.response.LoanCostComparisonResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanInterestForecastResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanInterestSummaryResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanMonthlyRepaymentsResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanRepaymentBurdenResponse;
import com.ssafy.dodam.domain.loan.service.LoanAnalysisService;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.security.AuthenticatedUserIdResolver;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/loans")
public class LoanAnalysisController {
    private final LoanAnalysisService service;
    private final AuthenticatedUserIdResolver userIdResolver;

    public LoanAnalysisController(LoanAnalysisService service, AuthenticatedUserIdResolver userIdResolver) {
        this.service = service;
        this.userIdResolver = userIdResolver;
    }

    @GetMapping("/monthly-repayments")
    public ApiResponse<LoanMonthlyRepaymentsResponse> monthlyRepayments(
            @RequestParam(required = false) String startMonth,
            @RequestParam(required = false) String endMonth,
            Authentication authentication
    ) {
        return ApiResponse.success(service.monthlyRepayments(userIdResolver.resolve(authentication), startMonth, endMonth));
    }

    @GetMapping("/repayment-burden")
    public ApiResponse<LoanRepaymentBurdenResponse> repaymentBurden(
            @RequestParam(required = false) String yearMonth, Authentication authentication
    ) {
        return ApiResponse.success(service.repaymentBurden(userIdResolver.resolve(authentication), yearMonth));
    }

    @GetMapping("/{accountNo}/interest-forecast")
    public ApiResponse<LoanInterestForecastResponse> interestForecast(
            @PathVariable @Pattern(regexp = "^[0-9]{10,20}$") String accountNo,
            Authentication authentication
    ) {
        return ApiResponse.success(service.interestForecast(userIdResolver.resolve(authentication), accountNo));
    }

    @GetMapping("/interest-summary")
    public ApiResponse<LoanInterestSummaryResponse> interestSummary(
            @RequestParam(defaultValue = "12") @Min(1) @Max(12) int months,
            @RequestParam Map<String, String> parameters, Authentication authentication
    ) {
        rejectBlankMonths(parameters);
        return ApiResponse.success(service.interestSummary(userIdResolver.resolve(authentication), months));
    }

    @GetMapping("/cost-comparison")
    public ApiResponse<LoanCostComparisonResponse> costComparison(
            @RequestParam(defaultValue = "12") @Min(1) @Max(12) int months,
            @RequestParam Map<String, String> parameters, Authentication authentication
    ) {
        rejectBlankMonths(parameters);
        return ApiResponse.success(service.costComparison(userIdResolver.resolve(authentication), months));
    }

    private void rejectBlankMonths(Map<String, String> parameters) {
        // Spring의 defaultValue가 명시한 빈 값까지 생략으로 바꾸는 것을 방지한다.
        if (parameters.containsKey("months") && parameters.get("months").isBlank()) {
            throw new InvalidRequestException("months는 빈 값일 수 없습니다.");
        }
    }
}
