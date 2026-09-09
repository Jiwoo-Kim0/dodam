package com.ssafy.dodam.domain.loan.controller;

import com.ssafy.dodam.domain.loan.dto.response.LoanDetailResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanRepaymentResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanRepaymentScheduleResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanSummaryResponse;
import com.ssafy.dodam.domain.loan.service.LoanQueryService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.response.PageResponse;
import com.ssafy.dodam.global.security.AuthenticatedUserIdResolver;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
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
public class LoanQueryController {

    private static final String CODE_PATTERN = "^\s*$|[A-Za-z0-9_]+$";

    // 계좌번호는 숫자만 받는다. 은행별 자릿수 차이를 흡수하도록 범위를 넓게 두고
    // 실제 소유 여부는 HMAC 해시 조회로 판별한다.
    private static final String ACCOUNT_NO_PATTERN = "^[0-9]{10,20}$";

    private final LoanQueryService loanQueryService;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    public LoanQueryController(
            LoanQueryService loanQueryService,
            AuthenticatedUserIdResolver authenticatedUserIdResolver
    ) {
        this.loanQueryService = loanQueryService;
        this.authenticatedUserIdResolver = authenticatedUserIdResolver;
    }

    @GetMapping
    public ApiResponse<List<LoanResponse>> getLoans(
            @RequestParam(required = false) @Size(max = 30)
            @Pattern(regexp = CODE_PATTERN) String status,
            @RequestParam(required = false) @Size(max = 30)
            @Pattern(regexp = CODE_PATTERN) String loanType,
            Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(loanQueryService.getLoans(userId, status, loanType));
    }

    @GetMapping("/summary")
    public ApiResponse<LoanSummaryResponse> getSummary(Authentication authentication) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(loanQueryService.getSummary(userId));
    }

    @GetMapping("/{accountNo}")
    public ApiResponse<LoanDetailResponse> getLoan(
            @PathVariable @Pattern(regexp = ACCOUNT_NO_PATTERN) String accountNo,
            Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(loanQueryService.getLoan(userId, accountNo));
    }

    @GetMapping("/{accountNo}/repayments")
    public ApiResponse<PageResponse<LoanRepaymentResponse>> getRepayments(
            @PathVariable @Pattern(regexp = ACCOUNT_NO_PATTERN) String accountNo,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(loanQueryService.getRepayments(
                userId,
                accountNo,
                startDate,
                endDate,
                page,
                size
        ));
    }

    @GetMapping("/{accountNo}/repayment-schedule")
    public ApiResponse<LoanRepaymentScheduleResponse> getRepaymentSchedule(
            @PathVariable @Pattern(regexp = ACCOUNT_NO_PATTERN) String accountNo,
            Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(loanQueryService.getRepaymentSchedule(userId, accountNo));
    }
}
