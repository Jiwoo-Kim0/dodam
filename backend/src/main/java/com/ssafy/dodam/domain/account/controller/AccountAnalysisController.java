package com.ssafy.dodam.domain.account.controller;

import com.ssafy.dodam.domain.account.dto.response.AccountBalanceSummaryResponse;
import com.ssafy.dodam.domain.account.dto.response.AccountMonthlySummaryResponse;
import com.ssafy.dodam.domain.account.dto.response.AccountSummaryResponse;
import com.ssafy.dodam.domain.account.dto.response.AccountTrendResponse;
import com.ssafy.dodam.domain.account.dto.response.InactiveAccountsResponse;
import com.ssafy.dodam.domain.account.service.AccountAnalysisService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.security.AuthPrincipal;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 명세 5. Account 중 S15P21D105-503 범위(계좌 요약·현금성 자산 합계·휴면성 계좌·월별 입출금 요약·입출금 추이).
 *
 * <p>`/summary`, `/inactive`, `/balance-summary`는 {@link AccountController}의 `/{accountNo}`보다 구체적인 리터럴 경로라
 * Spring MVC가 먼저 매칭한다. 계좌번호 패턴이 숫자만 허용하므로 실제로 겹치지도 않는다.
 */
@Validated
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountAnalysisController {

    private static final String ACCOUNT_NO_PATTERN = "^[0-9]{10,20}$";
    /** 연월 파라미터는 서비스의 YearMonthPolicy가 형식·범위를 검증한다. 여기서는 길이만 막는다. */
    private static final String YEAR_MONTH_LOOSE_PATTERN = "^[0-9-]{0,7}$";

    private final AccountAnalysisService accountAnalysisService;

    public AccountAnalysisController(AccountAnalysisService accountAnalysisService) {
        this.accountAnalysisService = accountAnalysisService;
    }

    @GetMapping("/summary")
    public ApiResponse<AccountSummaryResponse> getSummary(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.success(accountAnalysisService.getSummary(principal.userId()));
    }

    @GetMapping("/balance-summary")
    public ApiResponse<AccountBalanceSummaryResponse> getBalanceSummary(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.success(accountAnalysisService.getBalanceSummary(principal.userId()));
    }

    /**
     * @param inactiveMonths 생략 시 {@value AccountAnalysisService#DEFAULT_INACTIVE_MONTHS}. 1~120
     * @param maxBalance     생략 시 잔액 상한 없음. 0 이상
     */
    @GetMapping("/inactive")
    public ApiResponse<InactiveAccountsResponse> getInactiveAccounts(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) @Min(1) @Max(120) Integer inactiveMonths,
            @RequestParam(required = false) @PositiveOrZero Long maxBalance
    ) {
        return ApiResponse.success(accountAnalysisService.getInactiveAccounts(principal.userId(), inactiveMonths, maxBalance));
    }

    @GetMapping("/{accountNo}/monthly-summary")
    public ApiResponse<AccountMonthlySummaryResponse> getMonthlySummary(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Pattern(regexp = ACCOUNT_NO_PATTERN) String accountNo,
            @RequestParam(required = false) @Pattern(regexp = YEAR_MONTH_LOOSE_PATTERN) String yearMonth
    ) {
        return ApiResponse.success(accountAnalysisService.getMonthlySummary(principal.userId(), accountNo, yearMonth));
    }

    @GetMapping("/{accountNo}/trend")
    public ApiResponse<AccountTrendResponse> getTrend(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Pattern(regexp = ACCOUNT_NO_PATTERN) String accountNo,
            @RequestParam(required = false) @Pattern(regexp = YEAR_MONTH_LOOSE_PATTERN) String startMonth,
            @RequestParam(required = false) @Pattern(regexp = YEAR_MONTH_LOOSE_PATTERN) String endMonth
    ) {
        return ApiResponse.success(accountAnalysisService.getTrend(principal.userId(), accountNo, startMonth, endMonth));
    }
}
