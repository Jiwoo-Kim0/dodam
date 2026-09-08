package com.ssafy.dodam.domain.auth.dto;

import java.time.LocalDateTime;

/**
 * 클라이언트가 네이티브 앱(APK)이라 Refresh Token도 쿠키가 아닌 Body로 전달한다.
 * 앱은 Refresh Token을 SecureStore 같은 안전한 저장소에 보관해야 한다.
 */
public record LoginResponse(
        String tokenType,
        String accessToken,
        LocalDateTime accessTokenExpiresAt,
        String refreshToken,
        LocalDateTime refreshTokenExpiresAt,
        AuthUserResponse user
) {

    public static final String TOKEN_TYPE_BEARER = "Bearer";
}
