package com.ssafy.dodam.domain.auth.controller;

import com.ssafy.dodam.domain.auth.dto.LoginRequest;
import com.ssafy.dodam.domain.auth.dto.LoginResponse;
import com.ssafy.dodam.domain.auth.dto.RefreshRequest;
import com.ssafy.dodam.domain.auth.dto.SessionDeleteResponse;
import com.ssafy.dodam.domain.auth.dto.SessionResponse;
import com.ssafy.dodam.domain.auth.dto.SignupRequest;
import com.ssafy.dodam.domain.auth.dto.SignupResponse;
import com.ssafy.dodam.domain.auth.dto.SocialLoginRequest;
import com.ssafy.dodam.domain.auth.dto.SocialLoginResult;
import com.ssafy.dodam.domain.auth.service.AuthService;
import com.ssafy.dodam.domain.auth.service.SocialAuthService;
import com.ssafy.dodam.domain.auth.social.SocialProvider;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 명세 1. Auth (S15P21D105-494 회원가입·로그인·세션조회, S15P21D105-495 토큰 갱신·로그아웃·세션삭제).
 * 소셜 로그인(/social/{provider})은 명세 외 확장이다(backend/docs/auth-social.md).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final SocialAuthService socialAuthService;

    public AuthController(AuthService authService, SocialAuthService socialAuthService) {
        this.authService = authService;
        this.socialAuthService = socialAuthService;
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

    /**
     * 소셜 로그인. provider는 kakao 또는 google(대소문자 무관). body.token은 카카오 access token 또는 구글 ID token.
     * 이 소셜 계정으로 회원을 새로 만들었으면 201, 기존 회원 로그인(또는 이메일 일치로 연결)이면 200. 응답 본문은 로그인과 같다.
     */
    @PostMapping("/social/{provider}")
    public ResponseEntity<ApiResponse<LoginResponse>> socialLogin(
            @PathVariable String provider,
            @Valid @RequestBody SocialLoginRequest request
    ) {
        SocialLoginResult result = socialAuthService.login(SocialProvider.fromPath(provider), request.token());
        return ResponseEntity.status(result.newUser() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.success(result.login()));
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
