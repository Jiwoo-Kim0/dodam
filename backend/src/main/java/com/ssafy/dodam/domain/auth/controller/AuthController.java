package com.ssafy.dodam.domain.auth.controller;

import com.ssafy.dodam.domain.auth.dto.LoginRequest;
import com.ssafy.dodam.domain.auth.dto.LoginResponse;
import com.ssafy.dodam.domain.auth.dto.RefreshRequest;
import com.ssafy.dodam.domain.auth.dto.SessionDeleteResponse;
import com.ssafy.dodam.domain.auth.dto.SessionResponse;
import com.ssafy.dodam.domain.auth.dto.SignupRequest;
import com.ssafy.dodam.domain.auth.dto.SignupResponse;
import com.ssafy.dodam.domain.auth.service.AuthService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 명세 1. Auth (S15P21D105-494 회원가입·로그인·세션조회, S15P21D105-495 토큰 갱신·로그아웃·세션삭제).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SignupResponse> signup(@Valid @RequestBody SignupRequest request) {
        return ApiResponse.success(authService.signup(request));
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(authService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.success(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal AuthPrincipal principal) {
        authService.logout(principal);
        return ApiResponse.success(null);
    }

    @GetMapping("/session")
    public ApiResponse<SessionResponse> session(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.success(authService.getSession(principal));
    }

    /**
     * sessionId 없이 호출하면 본인의 모든 활성 세션(모든 기기)을 폐기한다.
     */
    @DeleteMapping("/session")
    public ApiResponse<SessionDeleteResponse> deleteSession(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) Long sessionId
    ) {
        return ApiResponse.success(authService.deleteSessions(principal, sessionId));
    }
}
