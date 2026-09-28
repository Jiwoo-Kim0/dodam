package com.ssafy.dodam.domain.cashflow.controller;

import com.ssafy.dodam.domain.cashflow.dto.response.CashflowAvailableTodayResponse;
import com.ssafy.dodam.domain.cashflow.dto.response.CashflowDailyBalanceListResponse;
import com.ssafy.dodam.domain.cashflow.dto.response.CashflowEventListResponse;
import com.ssafy.dodam.domain.cashflow.dto.response.CashflowForecastResponse;
import com.ssafy.dodam.domain.cashflow.dto.response.CashflowRiskListResponse;
import com.ssafy.dodam.domain.cashflow.entity.RiskSeverity;
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

    /**
     * 위험 유형 필터의 형식. 이벤트 유형과 같은 이유로 값 집합을 닫지 않는다.
     *
     * <p>현재 탐지되는 값은 `BALANCE_SHORTAGE` 하나지만(S15P21D105-542), 탐지 규칙이 늘어날 때마다
     * Controller를 고쳐야 하는 구조를 만들지 않는다. `cashflow_risk.type` 컬럼 길이(50)와 대문자 코드
     * 형식만 검증하고, 알 수 없는 값은 400이 아니라 빈 결과로 응답한다.
     */
    private static final String RISK_TYPE_PATTERN = "^[A-Z][A-Z0-9_]{0,49}$";

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

    /**
     * 위험 목록.
     *
     * <p>`severity`는 `type`과 달리 닫힌 코드 집합(ERD 주요 코드값 표)이라 enum으로 받는다. 잘못된
     * 값은 변환 단계에서 걸려 전역 처리기가 `INVALID_REQUEST`로 400을 낸다. 날짜 형식 오류와 같은
     * 경로이며, 필드 단위 `VALIDATION_ERROR`가 아니다.
     */
    @GetMapping("/risks")
    public ApiResponse<CashflowRiskListResponse> getRisks(
            @RequestParam(required = false) @Pattern(regexp = RISK_TYPE_PATTERN) String type,
            @RequestParam(required = false) RiskSeverity severity,
            Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(cashflowQueryService.getRisks(userId, type, severity));
    }

    @GetMapping("/available-today")
    public ApiResponse<CashflowAvailableTodayResponse> getAvailableToday(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate,
            Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        return ApiResponse.success(cashflowQueryService.getAvailableToday(userId, baseDate));
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
