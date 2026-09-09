package com.ssafy.dodam.domain.minusaccount.controller;

import com.ssafy.dodam.domain.minusaccount.dto.response.MinusAccountAlertResponse;
import com.ssafy.dodam.domain.minusaccount.dto.response.MinusAccountDetailResponse;
import com.ssafy.dodam.domain.minusaccount.dto.response.MinusAccountInterestResponse;
import com.ssafy.dodam.domain.minusaccount.dto.response.MinusAccountResponse;
import com.ssafy.dodam.domain.minusaccount.dto.response.MinusAccountTransactionResponse;
import com.ssafy.dodam.domain.minusaccount.service.MinusAccountQueryService;
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
@RequestMapping("/api/v1/loans/minus-accounts")
public class MinusAccountQueryController {

    private static final String CODE_PATTERN = "^\s*$|[A-Za-z0-9_]+$";

    // Loan과 같은 기준이다. 계좌번호는 숫자만 받고 은행별 자릿수 차이를 흡수하도록 범위를 넓게 둔다.
    // 실제 소유 여부는 HMAC 해시 조회로 판별한다.
    private static final String ACCOUNT_NO_PATTERN = "^[0-9]{10,20}$";

    private final MinusAccountQueryService minusAccountQueryService;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    public MinusAccountQueryController(
            MinusAccountQueryService minusAccountQueryService,
            AuthenticatedUserIdResolver authenticatedUserIdResolver
    ) {
        this.minusAccountQueryService = minusAccountQueryService;
        this.authenticatedUserIdResolver = authenticatedUserIdResolver;
    }

    @GetMapping
    public ApiResponse<List<MinusAccountResponse>> getMinusAccounts(
            @RequestParam(required = false) @Size(max = 30)
            @Pattern(regexp = CODE_PATTERN) String status,
            Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(minusAccountQueryService.getMinusAccounts(userId, status));
    }

    @GetMapping("/{accountNo}")
    public ApiResponse<MinusAccountDetailResponse> getMinusAccount(
            @PathVariable @Pattern(regexp = ACCOUNT_NO_PATTERN) String accountNo,
            Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(minusAccountQueryService.getMinusAccount(userId, accountNo));
    }

    @GetMapping("/{accountNo}/interest")
    public ApiResponse<MinusAccountInterestResponse> getInterest(
            @PathVariable @Pattern(regexp = ACCOUNT_NO_PATTERN) String accountNo,
            Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(minusAccountQueryService.getInterest(userId, accountNo));
    }

    @GetMapping("/{accountNo}/transactions")
    public ApiResponse<PageResponse<MinusAccountTransactionResponse>> getTransactions(
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
        return ApiResponse.success(minusAccountQueryService.getTransactions(
                userId,
                accountNo,
                startDate,
                endDate,
                page,
                size
        ));
    }

    @GetMapping("/{accountNo}/alerts")
    public ApiResponse<PageResponse<MinusAccountAlertResponse>> getAlerts(
            @PathVariable @Pattern(regexp = ACCOUNT_NO_PATTERN) String accountNo,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(minusAccountQueryService.getAlerts(
                userId,
                accountNo,
                unreadOnly,
                page,
                size
        ));
    }
}
