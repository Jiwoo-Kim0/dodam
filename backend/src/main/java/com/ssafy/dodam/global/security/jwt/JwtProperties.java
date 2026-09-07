package com.ssafy.dodam.global.security.jwt;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Access Token 서명·검증 설정. 값이 비어 있으면 기동 시점에 바로 실패시킨다
 * (운영 중에 401만 쏟아내는 상황보다 기동 실패가 낫다).
 */
@ConfigurationProperties(prefix = "app.auth.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        Duration accessTokenValidity,
        Duration refreshTokenValidity
) {

    /**
     * HS256 서명키 최소 길이(256bit). infra/.env의 JWT_SECRET이 이 조건을 만족해야 한다.
     */
    private static final int MINIMUM_SECRET_BYTES = 32;

    public JwtProperties {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.auth.jwt.secret must be at least " + MINIMUM_SECRET_BYTES + " bytes"
            );
        }
        if (issuer == null || issuer.isBlank()) {
            throw new IllegalStateException("app.auth.jwt.issuer must not be blank");
        }
        requirePositive(accessTokenValidity, "app.auth.jwt.access-token-validity");
        requirePositive(refreshTokenValidity, "app.auth.jwt.refresh-token-validity");
    }

    private static void requirePositive(Duration duration, String propertyName) {
        if (duration == null || duration.isZero() || duration.isNegative()) {
            throw new IllegalStateException(propertyName + " must be a positive duration");
        }
    }
}
