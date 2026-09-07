package com.ssafy.dodam.domain.auth.controller;

import com.ssafy.dodam.domain.auth.dto.LoginRequest;
import com.ssafy.dodam.domain.auth.dto.LoginResponse;
import com.ssafy.dodam.domain.auth.dto.SessionResponse;
import com.ssafy.dodam.domain.auth.dto.SignupRequest;
import com.ssafy.dodam.domain.auth.dto.SignupResponse;
import com.ssafy.dodam.domain.auth.service.AuthService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 명세 1. Auth 중 회원가입·로그인·로그인 상태 조회 (S15P21D105-494).
 * 로그아웃 / Access Token 재발급 / 세션 삭제는 후속 티켓에서 이 컨트롤러에 추가한다.
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

    @GetMapping("/session")
    public ApiResponse<SessionResponse> session(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.success(authService.getSession(principal));
    }
}
