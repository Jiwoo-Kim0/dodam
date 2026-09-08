package com.ssafy.dodam.global.security.jwt;

import java.time.LocalDateTime;

public record IssuedAccessToken(
        String token,
        LocalDateTime expiresAt
) {
}
