package com.ssafy.dodam.domain.savings.controller;

import com.ssafy.dodam.domain.savings.dto.response.SavingsCashflowResponse;
import com.ssafy.dodam.domain.savings.dto.response.SavingsRateComparisonResponse;
import com.ssafy.dodam.domain.savings.service.SavingsCashflowService;
import com.ssafy.dodam.domain.savings.service.SavingsRateComparisonService;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.response.ApiResponse;
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

/** 적금 금리비교와 현금흐름 분석. 단순 조회는 {@link SavingsQueryController}가 소유한다. */
@Validated
@RestController
@RequestMapping("/api/v1/savings")
public class SavingsAnalysisController {
    private static final String CODE_PATTERN = "^[A-Z][A-Z_]{0,29}$";
    /** 적금 계약 기간의 상한. {@code SavingsTermBand.MAX_MONTHS}와 같은 값이며 넘으면 400이다. */
    private static final int MAX_TERM_MONTHS = 120;

    private final SavingsRateComparisonService rateComparison;
    private final SavingsCashflowService cashflow;
    private final AuthenticatedUserIdResolver userIdResolver;

    public SavingsAnalysisController(
            SavingsRateComparisonService rateComparison, SavingsCashflowService cashflow,
            AuthenticatedUserIdResolver userIdResolver
    ) {
        this.rateComparison = rateComparison;
        this.cashflow = cashflow;
        this.userIdResolver = userIdResolver;
    }

    @GetMapping("/{accountNo}/rate-comparison")
    public ApiResponse<SavingsRateComparisonResponse> rateComparison(
            @PathVariable @Pattern(regexp = SavingsQueryController.ACCOUNT_NO_PATTERN) String accountNo,
            @RequestParam(required = false) @Min(1) @Max(MAX_TERM_MONTHS) Integer term,
            @RequestParam(required = false) @Pattern(regexp = CODE_PATTERN) String industry,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int topN,
            @RequestParam Map<String, String> parameters,
            Authentication authentication
    ) {
        rejectBlank(parameters, "term");
        rejectBlank(parameters, "industry");
        rejectBlank(parameters, "topN");
        return ApiResponse.success(rateComparison.rateComparison(
                userIdResolver.resolve(authentication), accountNo, term, industry, topN));
    }

    @GetMapping("/cashflow-burden")
    public ApiResponse<SavingsCashflowResponse> cashflowBurden(
            @RequestParam(required = false) String yearMonth, Authentication authentication
    ) {
        return ApiResponse.success(
                cashflow.cashflowBurden(userIdResolver.resolve(authentication), yearMonth));
    }

    @GetMapping("/disposable-balance")
    public ApiResponse<SavingsCashflowResponse> disposableBalance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate,
            Authentication authentication
    ) {
        return ApiResponse.success(
                cashflow.disposableBalance(userIdResolver.resolve(authentication), baseDate));
    }

    /** Spring이 빈 문자열을 생략이나 null로 바꾸는 것을 막는다. 생략과 빈 값은 다른 입력이다. */
    private void rejectBlank(Map<String, String> parameters, String name) {
        if (parameters.containsKey(name) && parameters.get(name).isBlank()) {
            throw new InvalidRequestException(name + "은 빈 값일 수 없습니다.");
        }
    }
}
