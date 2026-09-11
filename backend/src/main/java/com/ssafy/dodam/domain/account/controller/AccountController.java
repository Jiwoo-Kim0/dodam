package com.ssafy.dodam.domain.account.controller;

import com.ssafy.dodam.domain.account.dto.response.AccountBalanceResponse;
import com.ssafy.dodam.domain.account.dto.response.AccountResponse;
import com.ssafy.dodam.domain.account.service.AccountQueryService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.security.AuthPrincipal;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 명세 5. Account 중 S15P21D105-501 범위(계좌 목록·상세·잔액).
 * 요약·거래내역·월별 집계·휴면성·현금성 자산 합계는 다음 티켓에서 이어 붙인다.
 */
@Validated
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    /** 코드 계열 파라미터. 빈 값은 "전체"로 서비스가 해석한다. */
    private static final String CODE_PATTERN = "^\\s*$|^[A-Za-z0-9_]+$";
    /** 계좌번호 원문. 금융망 계좌번호(16자리)와 seed 계좌번호를 모두 허용하고 구분자는 받지 않는다. */
    private static final String ACCOUNT_NO_PATTERN = "^[0-9]{10,20}$";

    private final AccountQueryService accountQueryService;

    public AccountController(AccountQueryService accountQueryService) {
        this.accountQueryService = accountQueryService;
    }

    @GetMapping
    public ApiResponse<List<AccountResponse>> getAccounts(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) @Size(max = 20)
            @Pattern(regexp = CODE_PATTERN) String bankCode,
            @RequestParam(required = false) @Size(max = 30)
            @Pattern(regexp = CODE_PATTERN) String accountType
    ) {
        return ApiResponse.success(accountQueryService.getAccounts(principal.userId(), bankCode, accountType));
    }

    @GetMapping("/{accountNo}")
    public ApiResponse<AccountResponse> getAccount(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Pattern(regexp = ACCOUNT_NO_PATTERN) String accountNo
    ) {
        return ApiResponse.success(accountQueryService.getAccount(principal.userId(), accountNo));
    }

    @GetMapping("/{accountNo}/balance")
    public ApiResponse<AccountBalanceResponse> getBalance(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Pattern(regexp = ACCOUNT_NO_PATTERN) String accountNo
    ) {
        return ApiResponse.success(accountQueryService.getBalance(principal.userId(), accountNo));
    }
}
