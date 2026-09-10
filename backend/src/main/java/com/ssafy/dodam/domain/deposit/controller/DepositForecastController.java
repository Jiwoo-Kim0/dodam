package com.ssafy.dodam.domain.deposit.controller;

import com.ssafy.dodam.domain.deposit.dto.response.DepositEarlyTerminationResponse;
import com.ssafy.dodam.domain.deposit.dto.response.DepositMaturityForecastResponse;
import com.ssafy.dodam.domain.deposit.service.DepositForecastService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.security.AuthenticatedUserIdResolver;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/deposits")
public class DepositForecastController {
    private final DepositForecastService service;
    private final AuthenticatedUserIdResolver userIdResolver;

    public DepositForecastController(
            DepositForecastService service, AuthenticatedUserIdResolver userIdResolver
    ) {
        this.service = service;
        this.userIdResolver = userIdResolver;
    }

    @GetMapping("/{accountNo}/early-termination")
    public ApiResponse<DepositEarlyTerminationResponse> earlyTermination(
            @PathVariable @Pattern(regexp = DepositQueryController.ACCOUNT_NO_PATTERN) String accountNo,
            Authentication authentication
    ) {
        return ApiResponse.success(
                service.earlyTermination(userIdResolver.resolve(authentication), accountNo));
    }

    @GetMapping("/{accountNo}/maturity-forecast")
    public ApiResponse<DepositMaturityForecastResponse> maturityForecast(
            @PathVariable @Pattern(regexp = DepositQueryController.ACCOUNT_NO_PATTERN) String accountNo,
            Authentication authentication
    ) {
        return ApiResponse.success(
                service.maturityForecast(userIdResolver.resolve(authentication), accountNo));
    }
}
