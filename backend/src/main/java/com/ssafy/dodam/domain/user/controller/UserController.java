package com.ssafy.dodam.domain.user.controller;

import com.ssafy.dodam.domain.user.dto.FinancialProfileResponse;
import com.ssafy.dodam.domain.user.dto.UpdateFinancialProfileRequest;
import com.ssafy.dodam.domain.user.dto.UpdateUserRequest;
import com.ssafy.dodam.domain.user.dto.UpdateUserSettingRequest;
import com.ssafy.dodam.domain.user.dto.UserResponse;
import com.ssafy.dodam.domain.user.dto.UserSettingResponse;
import com.ssafy.dodam.domain.user.service.UserService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 명세 2. User (S15P21D105-496 정보 조회·수정·탈퇴, S15P21D105-497 설정·금융프로필).
 */
@RestController
@RequestMapping("/api/v1/users/me")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ApiResponse<UserResponse> getMe(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.success(userService.getMe(principal.userId()));
    }

    @PatchMapping
    public ApiResponse<UserResponse> updateMe(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody UpdateUserRequest request
    ) {
        return ApiResponse.success(userService.updateMe(principal.userId(), request));
    }

    @DeleteMapping
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal AuthPrincipal principal) {
        userService.withdraw(principal.userId());
        return ApiResponse.success(null);
    }

    @GetMapping("/settings")
    public ApiResponse<UserSettingResponse> getSettings(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.success(userService.getSettings(principal.userId()));
    }

    @PatchMapping("/settings")
    public ApiResponse<UserSettingResponse> updateSettings(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody UpdateUserSettingRequest request
    ) {
        return ApiResponse.success(userService.updateSettings(principal.userId(), request));
    }

    @GetMapping("/financial-profile")
    public ApiResponse<FinancialProfileResponse> getFinancialProfile(
            @AuthenticationPrincipal AuthPrincipal principal
    ) {
        return ApiResponse.success(userService.getFinancialProfile(principal.userId()));
    }

    @PatchMapping("/financial-profile")
    public ApiResponse<FinancialProfileResponse> updateFinancialProfile(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody UpdateFinancialProfileRequest request
    ) {
        return ApiResponse.success(userService.updateFinancialProfile(principal.userId(), request));
    }
}
