package com.ssafy.dodam.domain.deposit.controller;

import com.ssafy.dodam.domain.deposit.dto.response.DepositRateComparisonResponse;
import com.ssafy.dodam.domain.deposit.service.DepositRateComparisonService;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.security.AuthenticatedUserIdResolver;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/deposits")
public class DepositRateComparisonController {
    private static final String CODE_PATTERN = "^[A-Z][A-Z_]{0,29}$";
    /** 예금 계약 기간의 상한. {@code DepositTermBand.MAX_MONTHS}와 같은 값이며 넘으면 400이다. */
    private static final int MAX_TERM_MONTHS = 120;

    private final DepositRateComparisonService service;
    private final AuthenticatedUserIdResolver userIdResolver;

    public DepositRateComparisonController(
            DepositRateComparisonService service, AuthenticatedUserIdResolver userIdResolver
    ) {
        this.service = service;
        this.userIdResolver = userIdResolver;
    }

    @GetMapping("/{accountNo}/rate-comparison")
    public ApiResponse<DepositRateComparisonResponse> rateComparison(
            @PathVariable @Pattern(regexp = DepositQueryController.ACCOUNT_NO_PATTERN) String accountNo,
            @RequestParam(required = false) @Min(1) @Max(MAX_TERM_MONTHS) Integer term,
            @RequestParam(required = false) @Pattern(regexp = CODE_PATTERN) String industry,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int topN,
            @RequestParam Map<String, String> parameters,
            Authentication authentication
    ) {
        rejectBlank(parameters, "term");
        rejectBlank(parameters, "industry");
        rejectBlank(parameters, "topN");
        return ApiResponse.success(service.rateComparison(
                userIdResolver.resolve(authentication), accountNo, term, industry, topN));
    }

    /** Spring이 빈 문자열을 생략이나 null로 바꾸는 것을 막는다. 생략과 빈 값은 다른 입력이다. */
    private void rejectBlank(Map<String, String> parameters, String name) {
        if (parameters.containsKey(name) && parameters.get(name).isBlank()) {
            throw new InvalidRequestException(name + "은 빈 값일 수 없습니다.");
        }
    }
}
