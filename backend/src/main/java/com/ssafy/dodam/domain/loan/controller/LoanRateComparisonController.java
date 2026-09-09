package com.ssafy.dodam.domain.loan.controller;

import com.ssafy.dodam.domain.loan.dto.response.LoanHighInterestResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanRateComparisonResponse;
import com.ssafy.dodam.domain.loan.service.LoanRateComparisonService;
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
@RequestMapping("/api/v1/loans")
public class LoanRateComparisonController {
    private static final String CODE_PATTERN = "^[A-Z][A-Z_]{0,29}$";

    private final LoanRateComparisonService service;
    private final AuthenticatedUserIdResolver userIdResolver;

    public LoanRateComparisonController(
            LoanRateComparisonService service, AuthenticatedUserIdResolver userIdResolver
    ) {
        this.service = service;
        this.userIdResolver = userIdResolver;
    }

    @GetMapping("/{accountNo}/rate-comparison")
    public ApiResponse<LoanRateComparisonResponse> rateComparison(
            @PathVariable @Pattern(regexp = "^[0-9]{10,20}$") String accountNo,
            @RequestParam(required = false) @Min(1) @Max(600) Integer term,
            @RequestParam(required = false) @Pattern(regexp = CODE_PATTERN) String industry,
            @RequestParam(defaultValue = "5") @Min(1) @Max(20) int topN,
            @RequestParam Map<String, String> parameters,
            Authentication authentication
    ) {
        rejectBlank(parameters, "topN");
        rejectBlank(parameters, "term");
        rejectBlank(parameters, "industry");
        return ApiResponse.success(service.rateComparison(
                userIdResolver.resolve(authentication), accountNo, term, industry, topN));
    }

    @GetMapping("/high-interest")
    public ApiResponse<LoanHighInterestResponse> highInterest(
            @RequestParam(required = false) @Pattern(regexp = CODE_PATTERN) String loanType,
            @RequestParam(required = false) @Pattern(regexp = CODE_PATTERN) String industry,
            @RequestParam(required = false) @Pattern(regexp = CODE_PATTERN) String thresholdMode,
            @RequestParam Map<String, String> parameters,
            Authentication authentication
    ) {
        rejectBlank(parameters, "loanType");
        rejectBlank(parameters, "industry");
        rejectBlank(parameters, "thresholdMode");
        rejectUnsupportedThresholdMode(thresholdMode);
        return ApiResponse.success(service.highInterest(
                userIdResolver.resolve(authentication), loanType, industry));
    }

    /**
     * 판정 모드는 MARKET_SPREAD 하나만 구현한다. 다른 값을 받아 조용히 같은 결과를 주면 사용자가 모드를
     * 선택한 것으로 오해한다.
     */
    private void rejectUnsupportedThresholdMode(String thresholdMode) {
        if (thresholdMode != null && !LoanRateComparisonService.THRESHOLD_MODE.equals(thresholdMode)) {
            throw new InvalidRequestException("thresholdMode는 MARKET_SPREAD만 지원합니다.");
        }
    }

    /** Spring이 빈 문자열을 생략이나 null로 바꾸는 것을 막는다. 생략과 빈 값은 다른 입력이다. */
    private void rejectBlank(Map<String, String> parameters, String name) {
        if (parameters.containsKey(name) && parameters.get(name).isBlank()) {
            throw new InvalidRequestException(name + "은 빈 값일 수 없습니다.");
        }
    }
}
