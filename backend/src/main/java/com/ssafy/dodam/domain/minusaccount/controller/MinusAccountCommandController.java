package com.ssafy.dodam.domain.minusaccount.controller;

import com.ssafy.dodam.domain.minusaccount.dto.request.MinusAccountInterestPaymentRequest;
import com.ssafy.dodam.domain.minusaccount.dto.request.MinusAccountRepaymentRequest;
import com.ssafy.dodam.domain.minusaccount.dto.response.MinusAccountInterestPaymentResponse;
import com.ssafy.dodam.domain.minusaccount.dto.response.MinusAccountRepaymentResponse;
import com.ssafy.dodam.domain.minusaccount.service.MinusAccountCommandService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.idempotency.IdempotencyKeyGuard;
import com.ssafy.dodam.global.security.AuthenticatedUserIdResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/loans/minus-accounts")
public class MinusAccountCommandController {

    private static final String ACCOUNT_NO_PATTERN = "^[0-9]{10,20}$";

    private static final String REPAYMENT_OPERATION = "minus-account-repayment";
    private static final String INTEREST_PAYMENT_OPERATION = "minus-account-interest-payment";

    private final MinusAccountCommandService minusAccountCommandService;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;
    private final IdempotencyKeyGuard idempotency;

    public MinusAccountCommandController(
            MinusAccountCommandService minusAccountCommandService,
            AuthenticatedUserIdResolver authenticatedUserIdResolver,
            IdempotencyKeyGuard idempotency
    ) {
        this.minusAccountCommandService = minusAccountCommandService;
        this.authenticatedUserIdResolver = authenticatedUserIdResolver;
        this.idempotency = idempotency;
    }

    @PostMapping("/{accountNo}/repayments")
    public ApiResponse<MinusAccountRepaymentResponse> repay(
            @PathVariable @Pattern(regexp = ACCOUNT_NO_PATTERN) String accountNo,
            @RequestHeader(name = IdempotencyKeyGuard.HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody MinusAccountRepaymentRequest request,
            Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        // 상환은 돈이 움직이는 쓰기라 재전송 시 두 번 처리되면 안 된다. 지문에 계좌번호를 섞되 해시만 저장된다.
        return ApiResponse.success(idempotency.execute(userId, REPAYMENT_OPERATION, idempotencyKey,
                List.of(accountNo, request), MinusAccountRepaymentResponse.class,
                () -> minusAccountCommandService.repay(userId, accountNo, request)));
    }

    @PostMapping("/{accountNo}/interest-payments")
    public ApiResponse<MinusAccountInterestPaymentResponse> payInterest(
            @PathVariable @Pattern(regexp = ACCOUNT_NO_PATTERN) String accountNo,
            @RequestHeader(name = IdempotencyKeyGuard.HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody MinusAccountInterestPaymentRequest request,
            Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(idempotency.execute(userId, INTEREST_PAYMENT_OPERATION, idempotencyKey,
                List.of(accountNo, request), MinusAccountInterestPaymentResponse.class,
                () -> minusAccountCommandService.payInterest(userId, accountNo, request)));
    }
}
