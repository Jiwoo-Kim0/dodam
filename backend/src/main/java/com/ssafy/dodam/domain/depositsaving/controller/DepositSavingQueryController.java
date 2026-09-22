package com.ssafy.dodam.domain.depositsaving.controller;

import com.ssafy.dodam.domain.depositsaving.dto.response.DepositSavingMaturityForecastResponse;
import com.ssafy.dodam.domain.depositsaving.dto.response.DepositSavingMaturityResponse;
import com.ssafy.dodam.domain.depositsaving.dto.response.DepositSavingRateResponse;
import com.ssafy.dodam.domain.depositsaving.dto.response.DepositSavingResponse;
import com.ssafy.dodam.domain.depositsaving.dto.response.DepositSavingSummaryResponse;
import com.ssafy.dodam.domain.depositsaving.service.DepositSavingForecastService;
import com.ssafy.dodam.domain.depositsaving.service.DepositSavingQueryService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.response.PageResponse;
import com.ssafy.dodam.global.security.AuthenticatedUserIdResolver;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/deposit-savings")
public class DepositSavingQueryController {
    private final DepositSavingQueryService service;
    private final DepositSavingForecastService forecasts;
    private final AuthenticatedUserIdResolver userIds;

    public DepositSavingQueryController(
            DepositSavingQueryService service, DepositSavingForecastService forecasts,
            AuthenticatedUserIdResolver userIds
    ) {
        this.service = service;
        this.forecasts = forecasts;
        this.userIds = userIds;
    }

    @GetMapping
    public ApiResponse<PageResponse<DepositSavingResponse>> list(
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        return ApiResponse.success(service.list(userIds.resolve(authentication), type, status, page, size));
    }

    @GetMapping("/summary")
    public ApiResponse<DepositSavingSummaryResponse> summary(Authentication authentication) {
        return ApiResponse.success(service.summary(userIds.resolve(authentication)));
    }

    @GetMapping("/maturities")
    public ApiResponse<PageResponse<DepositSavingMaturityResponse>> maturities(
            @RequestParam(defaultValue = "30") @Min(1) @Max(365) int withinDays,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            Authentication authentication
    ) {
        return ApiResponse.success(service.maturities(userIds.resolve(authentication), withinDays, page, size));
    }

    @GetMapping("/rate")
    public ApiResponse<DepositSavingRateResponse> rate(
            @RequestParam(required = false) String yearMonth, Authentication authentication
    ) {
        return ApiResponse.success(service.rate(userIds.resolve(authentication), yearMonth));
    }

    /**
     * 678: 목록의 accountId로 바로 여는 만기 예상. 고정 경로(/summary 등)와 겹치지 않도록 숫자 ID 뒤에 접미사를 둔다.
     */
    @GetMapping("/{accountId}/maturity-forecast")
    public ApiResponse<DepositSavingMaturityForecastResponse> maturityForecast(
            @PathVariable @Positive Long accountId, Authentication authentication
    ) {
        return ApiResponse.success(forecasts.maturityForecast(userIds.resolve(authentication), accountId));
    }
}
