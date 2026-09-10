package com.ssafy.dodam.domain.cashflow.controller;

import com.ssafy.dodam.domain.cashflow.dto.response.CashflowDailyBalanceListResponse;
import com.ssafy.dodam.domain.cashflow.dto.response.CashflowEventListResponse;
import com.ssafy.dodam.domain.cashflow.dto.response.CashflowForecastResponse;
import com.ssafy.dodam.domain.cashflow.service.CashflowQueryService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.security.AuthenticatedUserIdResolver;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/cashflow")
public class CashflowQueryController {

    /**
     * 이벤트 유형 필터의 형식.
     *
     * <p>값 집합을 고정하지 않는다. 539가 만드는 유형 외에 계좌 예정 거래는 어댑터 구현체가 자체
     * 유형을 지정하는 계약이라, 여기서 목록을 닫으면 어댑터가 붙는 순간 정상 값이 400이 된다.
     * 저장 컬럼 길이(50)와 대문자 코드 형식만 검증하고, 알 수 없는 값은 빈 결과로 응답한다.
     */
    private static final String EVENT_TYPE_PATTERN = "^[A-Z][A-Z0-9_]{0,49}$";

    private final CashflowQueryService cashflowQueryService;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    public CashflowQueryController(
            CashflowQueryService cashflowQueryService,
            AuthenticatedUserIdResolver authenticatedUserIdResolver
    ) {
        this.cashflowQueryService = cashflowQueryService;
        this.authenticatedUserIdResolver = authenticatedUserIdResolver;
    }

    @GetMapping("/forecasts/latest")
    public ApiResponse<CashflowForecastResponse> getLatestForecast(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate,
            Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(cashflowQueryService.getLatestForecast(userId, baseDate));
    }

    @GetMapping("/daily-balances")
    public ApiResponse<CashflowDailyBalanceListResponse> getDailyBalances(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(
                cashflowQueryService.getDailyBalances(userId, startDate, endDate));
    }

    @GetMapping("/events")
    public ApiResponse<CashflowEventListResponse> getEvents(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) @Pattern(regexp = EVENT_TYPE_PATTERN) String type,
            Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(
                cashflowQueryService.getEvents(userId, startDate, endDate, type));
    }
}
