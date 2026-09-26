package com.ssafy.dodam.domain.goal.controller;

import com.ssafy.dodam.domain.goal.dto.request.UpdateSafeboxAllocationRequest;
import com.ssafy.dodam.domain.goal.dto.response.SafeboxAccountResponse;
import com.ssafy.dodam.domain.goal.dto.response.SafeboxAllocationResponse;
import com.ssafy.dodam.domain.goal.service.SafeboxAccountService;
import com.ssafy.dodam.domain.goal.service.SafeboxAllocationService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/safebox")
public class SafeboxController {
    private final SafeboxAccountService accounts;
    private final SafeboxAllocationService allocations;

    public SafeboxController(SafeboxAccountService accounts, SafeboxAllocationService allocations) {
        this.accounts = accounts;
        this.allocations = allocations;
    }

    @GetMapping("/account")
    public ApiResponse<SafeboxAccountResponse> getAccount(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.success(accounts.get(principal.userId()));
    }

    @PostMapping("/account")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SafeboxAccountResponse> open(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.success(accounts.open(principal.userId()));
    }

    @GetMapping("/goals/{goalId}")
    public ApiResponse<SafeboxAllocationResponse> getGoal(@AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive Long goalId) {
        return ApiResponse.success(allocations.get(principal.userId(), goalId));
    }

    @PutMapping("/goals/{goalId}")
    public ApiResponse<SafeboxAllocationResponse> allocate(@AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Positive Long goalId, @Valid @RequestBody UpdateSafeboxAllocationRequest request) {
        return ApiResponse.success(allocations.put(principal.userId(), goalId, request.amount()));
    }
}
