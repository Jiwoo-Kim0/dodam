package com.ssafy.dodam.domain.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 소셜 로그인 요청. token은 공급자 SDK가 클라이언트에 준 값이다 — 카카오는 access token, 구글은 ID token(JWT).
 */
public record SocialLoginRequest(
        @NotBlank(message = "소셜 로그인 토큰은 필수입니다.")
        @Size(max = 4096, message = "소셜 로그인 토큰이 너무 깁니다.")
        String token
) {
}
