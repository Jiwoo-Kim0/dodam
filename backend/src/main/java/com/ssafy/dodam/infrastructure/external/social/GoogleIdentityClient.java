package com.ssafy.dodam.infrastructure.external.social;

import com.ssafy.dodam.domain.auth.social.SocialIdentity;
import com.ssafy.dodam.domain.auth.social.SocialIdentityClient;
import com.ssafy.dodam.domain.auth.social.SocialProvider;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import java.util.List;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

/**
 * Google 로그인 토큰 검증. 클라이언트가 Google Sign-In으로 받은 ID token(JWT)을 Google 공개키(JWK Set)로 서명·만료를 검증하고
 * iss·aud를 대조한다. aud는 설정된 클라이언트 ID(Android·웹 등 여러 개 가능) 중 하나여야 한다.
 *
 * <p>클라이언트 ID가 하나도 설정되지 않았으면 어떤 토큰도 받지 않는다(SOCIAL_PROVIDER_UNAVAILABLE). 공개키 조회 실패는 공급자 장애로,
 * 서명·만료·iss·aud 불일치는 토큰 무효로 구분한다.
 */
@Component
public class GoogleIdentityClient implements SocialIdentityClient {

    private static final Logger log = LoggerFactory.getLogger(GoogleIdentityClient.class);

    private final SocialLoginProperties.Google properties;
    private volatile JwtDecoder decoder;

    @Autowired
    public GoogleIdentityClient(SocialLoginProperties properties) {
        this(properties.google(), null);
    }

    GoogleIdentityClient(SocialLoginProperties.Google properties, JwtDecoder decoder) {
        this.properties = properties;
        this.decoder = decoder;
    }

    @Override
    public SocialProvider provider() {
        return SocialProvider.GOOGLE;
    }

    @Override
    public SocialIdentity verify(String idToken) {
        if (!properties.isConfigured()) {
            log.warn("Google login is not configured: app.social.google.client-ids is empty");
            throw new BusinessException(ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
        }

        Jwt jwt;
        try {
            jwt = decoder().decode(idToken);
        } catch (JwtException exception) {
            if (isKeyFetchFailure(exception)) {
                log.warn("Google JWK set unavailable: {}", exception.getMessage());
                throw new BusinessException(ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE);
            }
            log.info("Google ID token rejected: {}", exception.getMessage());
            throw new BusinessException(ErrorCode.SOCIAL_TOKEN_INVALID);
        }

        // getIssuer()는 URL로 파싱하므로 "accounts.google.com" 형태의 iss에서 예외가 난다. 문자열로 비교한다.
        String issuer = jwt.getClaimAsString("iss");
        if (issuer == null || !properties.issuers().contains(issuer)) {
            log.info("Google ID token has unexpected issuer: {}", issuer);
            throw new BusinessException(ErrorCode.SOCIAL_TOKEN_INVALID);
        }
        List<String> audience = jwt.getAudience();
        if (audience == null || audience.stream().noneMatch(properties.clientIds()::contains)) {
            log.info("Google ID token audience does not match configured client ids: {}", audience);
            throw new BusinessException(ErrorCode.SOCIAL_TOKEN_INVALID);
        }
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new BusinessException(ErrorCode.SOCIAL_TOKEN_INVALID);
        }

        String email = jwt.getClaimAsString("email");
        boolean verified = isTrue(jwt.getClaim("email_verified"));
        String name = jwt.getClaimAsString("name");
        return new SocialIdentity(SocialProvider.GOOGLE, subject, email, verified, name);
    }

    private JwtDecoder decoder() {
        JwtDecoder current = decoder;
        if (current == null) {
            synchronized (this) {
                if (decoder == null) {
                    // 공개키는 첫 검증 때 내려받고 Nimbus가 캐시한다. 기본 검증기가 exp·nbf를 확인한다.
                    decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
                }
                current = decoder;
            }
        }
        return current;
    }

    private static boolean isKeyFetchFailure(JwtException exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase(Locale.ROOT);
        return message.contains("jwk") || message.contains("remote key") || exception.getCause() instanceof java.io.IOException;
    }

    private static boolean isTrue(Object claim) {
        if (claim instanceof Boolean value) {
            return value;
        }
        return claim != null && "true".equalsIgnoreCase(claim.toString());
    }
}
