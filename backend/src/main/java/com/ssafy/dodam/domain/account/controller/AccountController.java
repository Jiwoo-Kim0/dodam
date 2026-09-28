package com.ssafy.dodam.domain.account.controller;

import com.ssafy.dodam.domain.account.dto.response.AccountBalanceResponse;
import com.ssafy.dodam.domain.account.dto.response.AccountResponse;
import com.ssafy.dodam.domain.account.dto.response.AccountTransactionResponse;
import com.ssafy.dodam.domain.account.dto.request.AccountTransferRequest;
import com.ssafy.dodam.domain.account.dto.response.AccountTransferResponse;
import com.ssafy.dodam.domain.account.service.AccountQueryService;
import com.ssafy.dodam.domain.account.service.AccountTransferService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.response.PageResponse;
import com.ssafy.dodam.global.security.AuthPrincipal;
import com.ssafy.dodam.global.security.AuthenticatedUserIdResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 명세 5. Account 중 S15P21D105-501(계좌 목록·상세·잔액), S15P21D105-502(계좌 거래내역·거래 단건) 범위.
 * 요약·월별 집계·휴면성·현금성 자산 합계는 다음 티켓에서 이어 붙인다.
 */
@Validated
@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    /** 코드 계열 파라미터. 빈 값은 "전체"로 서비스가 해석한다. */
    private static final String CODE_PATTERN = "^\\s*$|^[A-Za-z0-9_]+$";
    /** 계좌번호 원문. 금융망 계좌번호(16자리)와 seed 계좌번호를 모두 허용하고 구분자는 받지 않는다. */
    private static final String ACCOUNT_NO_PATTERN = "^[0-9]{10,20}$";
    /** 거래 ID: 금융망 transactionUniqueNo, seed(`TX-...`), 수동 등록 ID를 모두 허용한다. */
    private static final String TRANSACTION_ID_PATTERN = "^[A-Za-z0-9_-]+$";

    private final AccountQueryService accountQueryService;
    private final AccountTransferService accountTransferService;
    private final AuthenticatedUserIdResolver userIds;

    public AccountController(AccountQueryService accountQueryService, AccountTransferService accountTransferService,
            AuthenticatedUserIdResolver userIds) {
        this.accountQueryService = accountQueryService;
        this.accountTransferService = accountTransferService;
        this.userIds = userIds;
    }

    @PostMapping("/transfer")
    public ApiResponse<AccountTransferResponse> transfer(Authentication authentication,
            @Valid @RequestBody AccountTransferRequest request) {
        return ApiResponse.success(accountTransferService.transfer(userIds.resolve(authentication), request));
    }

    @GetMapping
    public ApiResponse<List<AccountResponse>> getAccounts(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) @Size(max = 20)
            @Pattern(regexp = CODE_PATTERN) String bankCode,
            @RequestParam(required = false) @Size(max = 30)
            @Pattern(regexp = CODE_PATTERN) String accountType
    ) {
        accountTransferService.reconcileIfPending(principal.userId());
        return ApiResponse.success(accountQueryService.getAccounts(principal.userId(), bankCode, accountType));
    }

    @GetMapping("/{accountNo}")
    public ApiResponse<AccountResponse> getAccount(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Pattern(regexp = ACCOUNT_NO_PATTERN) String accountNo
    ) {
        accountTransferService.reconcileIfPending(principal.userId());
        return ApiResponse.success(accountQueryService.getAccount(principal.userId(), accountNo));
    }

    @GetMapping("/{accountNo}/balance")
    public ApiResponse<AccountBalanceResponse> getBalance(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Pattern(regexp = ACCOUNT_NO_PATTERN) String accountNo
    ) {
        accountTransferService.reconcileIfPending(principal.userId());
        return ApiResponse.success(accountQueryService.getBalance(principal.userId(), accountNo));
    }

    // ---------- S15P21D105-502 계좌 거래내역 ----------

    @GetMapping("/{accountNo}/transactions")
    public ApiResponse<PageResponse<AccountTransactionResponse>> getTransactions(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Pattern(regexp = ACCOUNT_NO_PATTERN) String accountNo,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        accountTransferService.reconcileIfPending(principal.userId());
        return ApiResponse.success(accountQueryService.getTransactions(
                principal.userId(),
                accountNo,
                startDate,
                endDate,
                page,
                size
        ));
    }

    @GetMapping("/{accountNo}/transactions/{transactionId}")
    public ApiResponse<AccountTransactionResponse> getTransaction(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Pattern(regexp = ACCOUNT_NO_PATTERN) String accountNo,
            @PathVariable @Size(max = 100) @Pattern(regexp = TRANSACTION_ID_PATTERN) String transactionId
    ) {
        accountTransferService.reconcileIfPending(principal.userId());
        return ApiResponse.success(accountQueryService.getTransaction(principal.userId(), accountNo, transactionId));
    }
}
