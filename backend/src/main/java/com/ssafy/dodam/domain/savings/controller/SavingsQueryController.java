package com.ssafy.dodam.domain.savings.controller;

import com.ssafy.dodam.domain.savings.dto.response.SavingsDetailResponse;
import com.ssafy.dodam.domain.savings.dto.response.SavingsForecastResponse;
import com.ssafy.dodam.domain.savings.dto.response.SavingsMonthlyAmountResponse;
import com.ssafy.dodam.domain.savings.dto.response.SavingsPaymentResponse;
import com.ssafy.dodam.domain.savings.dto.response.SavingsPaymentSchedulesResponse;
import com.ssafy.dodam.domain.savings.service.SavingsQueryService;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.response.PageResponse;
import com.ssafy.dodam.global.security.AuthenticatedUserIdResolver;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.Map;
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
@RequestMapping("/api/v1/savings")
public class SavingsQueryController {
    /**
     * 계좌번호는 숫자만 받는다. 은행별 자릿수 차이를 흡수하도록 범위를 넓게 두고 실제 소유 여부는
     * HMAC 해시 조회로 판별한다. 대출·마이너스통장·예금 컨트롤러와 같은 패턴이다.
     *
     * <p>고정 경로({@code /payment-schedules} 등)는 이 패턴과 겹치지 않고, 겹치더라도 Spring이 리터럴
     * 경로를 먼저 고른다.
     */
    static final String ACCOUNT_NO_PATTERN = "^[0-9]{10,20}$";

    private final SavingsQueryService service;
    private final AuthenticatedUserIdResolver userIdResolver;

    public SavingsQueryController(SavingsQueryService service, AuthenticatedUserIdResolver userIdResolver) {
        this.service = service;
        this.userIdResolver = userIdResolver;
    }

    @GetMapping("/{accountNo}")
    public ApiResponse<SavingsDetailResponse> detail(
            @PathVariable @Pattern(regexp = ACCOUNT_NO_PATTERN) String accountNo,
            Authentication authentication
    ) {
        return ApiResponse.success(service.detail(userIdResolver.resolve(authentication), accountNo));
    }

    @GetMapping("/{accountNo}/payments")
    public ApiResponse<PageResponse<SavingsPaymentResponse>> payments(
            @PathVariable @Pattern(regexp = ACCOUNT_NO_PATTERN) String accountNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam Map<String, String> parameters,
            Authentication authentication
    ) {
        rejectBlank(parameters, "page");
        rejectBlank(parameters, "size");
        return ApiResponse.success(service.payments(
                userIdResolver.resolve(authentication), accountNo, startDate, endDate, page, size));
    }

    @GetMapping("/payment-schedules")
    public ApiResponse<SavingsPaymentSchedulesResponse> paymentSchedules(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            Authentication authentication
    ) {
        return ApiResponse.success(service.paymentSchedules(
                userIdResolver.resolve(authentication), startDate, endDate));
    }

    @GetMapping("/monthly-amount")
    public ApiResponse<SavingsMonthlyAmountResponse> monthlyAmount(
            @RequestParam(required = false) String yearMonth, Authentication authentication
    ) {
        return ApiResponse.success(service.monthlyAmount(userIdResolver.resolve(authentication), yearMonth));
    }

    @GetMapping("/{accountNo}/early-termination")
    public ApiResponse<SavingsForecastResponse> earlyTermination(
            @PathVariable @Pattern(regexp = ACCOUNT_NO_PATTERN) String accountNo,
            Authentication authentication
    ) {
        return ApiResponse.success(
                service.forecast(userIdResolver.resolve(authentication), accountNo, true));
    }

    @GetMapping("/{accountNo}/maturity-forecast")
    public ApiResponse<SavingsForecastResponse> maturityForecast(
            @PathVariable @Pattern(regexp = ACCOUNT_NO_PATTERN) String accountNo,
            Authentication authentication
    ) {
        return ApiResponse.success(
                service.forecast(userIdResolver.resolve(authentication), accountNo, false));
    }

    /** Spring이 빈 문자열을 생략이나 null로 바꾸는 것을 막는다. 생략과 빈 값은 다른 입력이다. */
    private void rejectBlank(Map<String, String> parameters, String name) {
        if (parameters.containsKey(name) && parameters.get(name).isBlank()) {
            throw new InvalidRequestException(name + "은 빈 값일 수 없습니다.");
        }
    }
}
