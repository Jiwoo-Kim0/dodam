package com.ssafy.dodam.domain.goal.controller;

import com.ssafy.dodam.domain.goal.dto.request.CreateGoalRequest;
import com.ssafy.dodam.domain.goal.dto.request.UpdateGoalAccountsRequest;
import com.ssafy.dodam.domain.goal.dto.request.UpdateGoalRequest;
import com.ssafy.dodam.domain.goal.dto.response.GoalAccountsResponse;
import com.ssafy.dodam.domain.goal.dto.response.GoalForecastResponse;
import com.ssafy.dodam.domain.goal.dto.response.GoalHistoryResponse;
import com.ssafy.dodam.domain.goal.dto.response.GoalProgressResponse;
import com.ssafy.dodam.domain.goal.dto.response.GoalResponse;
import com.ssafy.dodam.domain.goal.dto.response.GoalRiskResponse;
import com.ssafy.dodam.domain.goal.dto.response.GoalSummaryResponse;
import com.ssafy.dodam.domain.goal.dto.response.RequiredSavingResponse;
import com.ssafy.dodam.domain.goal.dto.response.SpendingGoalProgressResponse;
import com.ssafy.dodam.domain.goal.service.GoalAccountService;
import com.ssafy.dodam.domain.goal.service.GoalCalculationService;
import com.ssafy.dodam.domain.goal.service.GoalHistoryService;
import com.ssafy.dodam.domain.goal.service.GoalService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.response.PageResponse;
import com.ssafy.dodam.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 명세 12. Goal 전체(14개).
 * S15P21D105-508 목록·요약·생성·상세·수정·삭제, 509 연결계좌 조회·변경,
 * 510 진행률·필요 저축액·예상 달성일·소비목표 진행률·위험상태, 511 변화 이력.
 */
@Validated
@RestController
@RequestMapping("/api/v1/goals")
public class GoalController {

    /** 코드 계열 파라미터. 빈 값은 "전체"로 서비스가 해석한다. */
    private static final String CODE_PATTERN = "^\\s*$|^[A-Za-z0-9_]+$";

    private final GoalService goalService;
    private final GoalAccountService goalAccountService;
    private final GoalCalculationService goalCalculationService;
    private final GoalHistoryService goalHistoryService;

    public GoalController(
            GoalService goalService,
            GoalAccountService goalAccountService,
            GoalCalculationService goalCalculationService,
            GoalHistoryService goalHistoryService
    ) {
        this.goalService = goalService;
        this.goalAccountService = goalAccountService;
        this.goalCalculationService = goalCalculationService;
        this.goalHistoryService = goalHistoryService;
    }

    @GetMapping
    public ApiResponse<PageResponse<GoalResponse>> getGoals(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) @Size(max = 30) @Pattern(regexp = CODE_PATTERN) String status,
            @RequestParam(required = false) @Size(max = 20) @Pattern(regexp = CODE_PATTERN) String type,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(goalService.getGoals(principal.userId(), status, type, page, size));
    }

    @GetMapping("/summary")
    public ApiResponse<GoalSummaryResponse> getSummary(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.success(goalService.getSummary(principal.userId()));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<GoalResponse> create(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody CreateGoalRequest request
    ) {
        return ApiResponse.success(goalService.create(principal.userId(), request));
    }

    @GetMapping("/{goalId}")
    public ApiResponse<GoalResponse> getGoal(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive Long goalId
    ) {
        return ApiResponse.success(goalService.getGoal(principal.userId(), goalId));
    }

    @PatchMapping("/{goalId}")
    public ApiResponse<GoalResponse> update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive Long goalId,
            @Valid @RequestBody UpdateGoalRequest request
    ) {
        return ApiResponse.success(goalService.update(principal.userId(), goalId, request));
    }

    @DeleteMapping("/{goalId}")
    public ApiResponse<Void> delete(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive Long goalId
    ) {
        goalService.delete(principal.userId(), goalId);
        return ApiResponse.success(null);
    }

    // ---------- S15P21D105-509 목표 연결계좌 ----------

    @GetMapping("/{goalId}/accounts")
    public ApiResponse<GoalAccountsResponse> getAccounts(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive Long goalId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(goalAccountService.getAccounts(principal.userId(), goalId, page, size));
    }

    @PutMapping("/{goalId}/accounts")
    public ApiResponse<GoalAccountsResponse> replaceAccounts(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive Long goalId,
            @Valid @RequestBody UpdateGoalAccountsRequest request
    ) {
        return ApiResponse.success(goalAccountService.replaceAccounts(principal.userId(), goalId, request.accountIds()));
    }

    // ---------- S15P21D105-510 목표 진행률·필요 저축액·예상 달성일·소비목표 진행률·위험상태 ----------

    @GetMapping("/{goalId}/progress")
    public ApiResponse<GoalProgressResponse> getProgress(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive Long goalId
    ) {
        return ApiResponse.success(goalCalculationService.getProgress(principal.userId(), goalId));
    }

    @GetMapping("/{goalId}/required-saving")
    public ApiResponse<RequiredSavingResponse> getRequiredSaving(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive Long goalId
    ) {
        return ApiResponse.success(goalCalculationService.getRequiredSaving(principal.userId(), goalId));
    }

    /**
     * @param baseDate 예측 기준일(기본 오늘)
     * @param months   예측 구간 개월 수(기본 12, 1~120)
     */
    @GetMapping("/{goalId}/forecast")
    public ApiResponse<GoalForecastResponse> getForecast(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive Long goalId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate,
            @RequestParam(required = false) @Min(1) @Max(120) Integer months
    ) {
        return ApiResponse.success(goalCalculationService.getForecast(principal.userId(), goalId, baseDate, months));
    }

    @GetMapping("/{goalId}/spending-progress")
    public ApiResponse<SpendingGoalProgressResponse> getSpendingProgress(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive Long goalId
    ) {
        return ApiResponse.success(goalCalculationService.getSpendingProgress(principal.userId(), goalId));
    }

    @GetMapping("/{goalId}/risk")
    public ApiResponse<GoalRiskResponse> getRisk(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive Long goalId
    ) {
        return ApiResponse.success(goalCalculationService.getRisk(principal.userId(), goalId));
    }

    // ---------- S15P21D105-511 목표 변화 이력 ----------

    @GetMapping("/{goalId}/history")
    public ApiResponse<PageResponse<GoalHistoryResponse>> getHistory(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive Long goalId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(goalHistoryService.getHistory(principal.userId(), goalId, startDate, endDate, page, size));
    }
}
