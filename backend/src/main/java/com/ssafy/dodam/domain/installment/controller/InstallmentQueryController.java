package com.ssafy.dodam.domain.installment.controller;

import com.ssafy.dodam.domain.installment.dto.response.InstallmentDetailResponse;
import com.ssafy.dodam.domain.installment.dto.response.InstallmentResponse;
import com.ssafy.dodam.domain.installment.dto.response.InstallmentScheduleResponse;
import com.ssafy.dodam.domain.installment.service.InstallmentQueryService;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.response.PageResponse;
import com.ssafy.dodam.global.security.AuthenticatedUserIdResolver;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.Map;
import java.util.Set;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/installments")
public class InstallmentQueryController {

    private final InstallmentQueryService service;
    private final AuthenticatedUserIdResolver userIdResolver;

    public InstallmentQueryController(
            InstallmentQueryService service, AuthenticatedUserIdResolver userIdResolver
    ) {
        this.service = service;
        this.userIdResolver = userIdResolver;
    }

    @GetMapping
    public ApiResponse<PageResponse<InstallmentResponse>> getInstallments(
            @RequestParam(required = false) @Min(1) Long cardId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam Map<String, String> parameters,
            Authentication authentication
    ) {
        rejectBlank(parameters, Set.of("cardId", "status", "page", "size"));
        return ApiResponse.success(
                service.getInstallments(userIdResolver.resolve(authentication), cardId, status, page, size));
    }

    @GetMapping("/{installmentId}")
    public ApiResponse<InstallmentDetailResponse> getInstallment(
            @PathVariable @Min(1) Long installmentId,
            Authentication authentication
    ) {
        return ApiResponse.success(service.getInstallment(userIdResolver.resolve(authentication), installmentId));
    }

    @GetMapping("/monthly-schedule")
    public ApiResponse<InstallmentScheduleResponse> getSchedule(
            @RequestParam(required = false) String startMonth,
            @RequestParam(required = false) String endMonth,
            @RequestParam Map<String, String> parameters,
            Authentication authentication
    ) {
        rejectBlank(parameters, Set.of("startMonth", "endMonth"));
        return ApiResponse.success(
                service.getSchedule(userIdResolver.resolve(authentication), startMonth, endMonth));
    }

    @GetMapping("/forecast")
    public ApiResponse<InstallmentScheduleResponse> getForecast(
            @RequestParam(defaultValue = "6") @Min(1) @Max(12) int months,
            @RequestParam Map<String, String> parameters,
            Authentication authentication
    ) {
        rejectBlank(parameters, Set.of("months"));
        return ApiResponse.success(service.getForecast(userIdResolver.resolve(authentication), months));
    }

    private void rejectBlank(Map<String, String> parameters, Set<String> names) {
        // Spring은 명시한 빈 숫자 값에도 defaultValue를 적용하므로 원본 입력을 따로 검사한다.
        for (String name : names) {
            if (parameters.containsKey(name) && parameters.get(name).isBlank()) {
                throw new InvalidRequestException(name + "는 빈 값일 수 없습니다.");
            }
        }
    }
}
