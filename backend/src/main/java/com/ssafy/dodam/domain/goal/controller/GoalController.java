package com.ssafy.dodam.domain.goal.controller;

import com.ssafy.dodam.domain.goal.dto.request.CreateGoalRequest;
import com.ssafy.dodam.domain.goal.dto.request.UpdateGoalRequest;
import com.ssafy.dodam.domain.goal.dto.response.GoalResponse;
import com.ssafy.dodam.domain.goal.dto.response.GoalSummaryResponse;
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
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 명세 12. Goal 중 S15P21D105-508 범위(목록·요약·생성·상세·수정·삭제).
 * 연결계좌(509), 진행률·필요 저축액·예상 달성일·소비목표 진행률·위험상태(510), 변화 이력(511)은 다음 티켓에서 이어 붙인다.
 */
@Validated
@RestController
@RequestMapping("/api/v1/goals")
public class GoalController {

    /** 코드 계열 파라미터. 빈 값은 "전체"로 서비스가 해석한다. */
    private static final String CODE_PATTERN = "^\\s*$|^[A-Za-z0-9_]+$";

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
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
}
