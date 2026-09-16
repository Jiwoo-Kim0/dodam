package com.ssafy.dodam.domain.improvement.controller;

import com.ssafy.dodam.domain.improvement.dto.ActionComparisonResponse;
import com.ssafy.dodam.domain.improvement.dto.AmountTotalResponse;
import com.ssafy.dodam.domain.improvement.dto.GoalImpactSummaryResponse;
import com.ssafy.dodam.domain.improvement.dto.ImprovementActionResponse;
import com.ssafy.dodam.domain.improvement.dto.ImprovementSummaryResponse;
import com.ssafy.dodam.domain.improvement.dto.MonthlyTrendResponse;
import com.ssafy.dodam.domain.improvement.dto.RediagnoseResultResponse;
import com.ssafy.dodam.domain.improvement.entity.ImprovementActionStatus;
import com.ssafy.dodam.domain.improvement.service.ImprovementQueryService;
import com.ssafy.dodam.domain.improvement.service.ImprovementRediagnoseService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.response.PageResponse;
import com.ssafy.dodam.global.security.AuthenticatedUserIdResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 19 Improvement. 리터럴 경로(/summary, /savings, ...)가 먼저 매칭되고 `{actionId}`는 `/actions` 아래에만 있어
 * 경로가 겹치지 않는다. path variable이 Long이라 문자열은 400이다.
 *
 * <p>쓰기 엔드포인트는 재진단 하나뿐이다. 개선 행동은 추천 수락(API 16)으로만 만들어진다.
 */
@Validated
@RestController
@RequestMapping("/api/v1/improvements")
public class ImprovementController {

    private final ImprovementQueryService query;
    private final ImprovementRediagnoseService rediagnosis;
    private final AuthenticatedUserIdResolver users;

    public ImprovementController(ImprovementQueryService query, ImprovementRediagnoseService rediagnosis,
            AuthenticatedUserIdResolver users) {
        this.query = query;
        this.rediagnosis = rediagnosis;
        this.users = users;
    }

    @GetMapping("/summary")
    public ApiResponse<ImprovementSummaryResponse> summary(Authentication authentication) {
        return ApiResponse.success(query.summary(users.resolve(authentication)));
    }

    @GetMapping("/actions")
    public ApiResponse<PageResponse<ImprovementActionResponse>> actions(
            @RequestParam(required = false) ImprovementActionStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, Authentication authentication) {
        return ApiResponse.success(query.list(users.resolve(authentication), status, startDate, endDate, page, size));
    }

    @GetMapping("/actions/{actionId}")
    public ApiResponse<ImprovementActionResponse> action(@PathVariable @Positive Long actionId,
            Authentication authentication) {
        return ApiResponse.success(query.detail(users.resolve(authentication), actionId));
    }

    @GetMapping("/actions/{actionId}/comparison")
    public ApiResponse<ActionComparisonResponse> comparison(@PathVariable @Positive Long actionId,
            Authentication authentication) {
        return ApiResponse.success(query.comparison(users.resolve(authentication), actionId));
    }

    @GetMapping("/savings")
    public ApiResponse<AmountTotalResponse> savings(Authentication authentication) {
        return ApiResponse.success(query.savings(users.resolve(authentication)));
    }

    @GetMapping("/debt-reduction")
    public ApiResponse<AmountTotalResponse> debtReduction(Authentication authentication) {
        return ApiResponse.success(query.debtReduction(users.resolve(authentication)));
    }

    @GetMapping("/spending-reduction")
    public ApiResponse<AmountTotalResponse> spendingReduction(Authentication authentication) {
        return ApiResponse.success(query.spendingReduction(users.resolve(authentication)));
    }

    @GetMapping("/saving-increase")
    public ApiResponse<AmountTotalResponse> savingIncrease(Authentication authentication) {
        return ApiResponse.success(query.savingIncrease(users.resolve(authentication)));
    }

    @GetMapping("/goal-impact")
    public ApiResponse<GoalImpactSummaryResponse> goalImpact(Authentication authentication) {
        return ApiResponse.success(query.goalImpact(users.resolve(authentication)));
    }

    @GetMapping("/monthly-trend")
    public ApiResponse<MonthlyTrendResponse> monthlyTrend(
            @RequestParam(required = false) String startMonth,
            @RequestParam(required = false) String endMonth, Authentication authentication) {
        return ApiResponse.success(query.monthlyTrend(users.resolve(authentication), startMonth, endMonth));
    }

    @PostMapping("/rediagnose")
    public ResponseEntity<ApiResponse<RediagnoseResultResponse>> rediagnose(
            @Valid @RequestBody(required = false) Request request, Authentication authentication) {
        Long goalId = request == null ? null : request.goalId();
        RediagnoseResultResponse result = rediagnosis.rediagnose(users.resolve(authentication), goalId);
        return ResponseEntity.status(result.created() ? 201 : 200).body(ApiResponse.success(result));
    }

    public record Request(@Positive Long goalId) { }
}
