package com.ssafy.dodam.domain.auth.dto;

import com.ssafy.dodam.domain.auth.social.SocialProvider;

/**
 * 소셜 로그인 결과. newUser가 true면 이 소셜 계정으로 회원을 새로 만들었다(컨트롤러가 201로 구분).
 */
public record SocialLoginResult(
        LoginResponse login,
        boolean newUser,
        SocialProvider provider
) {
}
