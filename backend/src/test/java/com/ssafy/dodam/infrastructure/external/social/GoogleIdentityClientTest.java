package com.ssafy.dodam.infrastructure.external.social;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.dodam.domain.auth.social.SocialIdentity;
import com.ssafy.dodam.domain.auth.social.SocialProvider;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * 서명·만료 검증은 NimbusJwtDecoder(Google JWK)에 맡기므로 여기서는 디코더를 대체해 iss·aud·클레임 매핑과 오류 구분만 본다.
 */
class GoogleIdentityClientTest {

    private static final String CLIENT_ID = "test-client.apps.googleusercontent.com";

    private static SocialLoginProperties.Google google(List<String> clientIds) {
        return new SocialLoginProperties.Google(null, null, clientIds);
    }

    private static Jwt jwt(Consumer<Jwt.Builder> customizer) {
        Instant now = Instant.now();
        Jwt.Builder builder = Jwt.withTokenValue("id-token")
                .header("alg", "RS256")
                .issuer("https://accounts.google.com")
                .audience(List.of(CLIENT_ID))
                .subject("110000000000000000001")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .claim("email", "Dodam@Gmail.com")
                .claim("email_verified", true)
                .claim("name", "도담");
        customizer.accept(builder);
        return builder.build();
    }

    @Test
    void mapsClaimsToIdentity() {
        GoogleIdentityClient client = new GoogleIdentityClient(google(List.of(CLIENT_ID)), token -> jwt(b -> { }));

        SocialIdentity identity = client.verify("id-token");

        assertThat(identity.provider()).isEqualTo(SocialProvider.GOOGLE);
        assertThat(identity.providerUserId()).isEqualTo("110000000000000000001");
        assertThat(identity.email()).isEqualTo("dodam@gmail.com");
        assertThat(identity.emailVerified()).isTrue();
        assertThat(identity.name()).isEqualTo("도담");
    }

    @Test
    void acceptsAnyConfiguredClientIdAndStringEmailVerified() {
        GoogleIdentityClient client = new GoogleIdentityClient(
                google(List.of("android-client", CLIENT_ID)),
                token -> jwt(b -> b.issuer("accounts.google.com").claim("email_verified", "true")));

        SocialIdentity identity = client.verify("id-token");

        assertThat(identity.emailVerified()).isTrue();
    }

    @Test
    void audienceMismatchIsInvalidToken() {
        GoogleIdentityClient client = new GoogleIdentityClient(
                google(List.of(CLIENT_ID)), token -> jwt(b -> b.audience(List.of("other-client"))));

        assertThatThrownBy(() -> client.verify("id-token"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SOCIAL_TOKEN_INVALID));
    }

    @Test
    void issuerMismatchIsInvalidToken() {
        GoogleIdentityClient client = new GoogleIdentityClient(
                google(List.of(CLIENT_ID)), token -> jwt(b -> b.issuer("https://evil.example.com")));

        assertThatThrownBy(() -> client.verify("id-token"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SOCIAL_TOKEN_INVALID));
    }

    @Test
    void decoderRejectionIsInvalidToken() {
        JwtDecoder failing = token -> {
            throw new JwtException("An error occurred while attempting to decode the Jwt: Jwt expired at 2026-09-15T00:00:00Z");
        };
        GoogleIdentityClient client = new GoogleIdentityClient(google(List.of(CLIENT_ID)), failing);

        assertThatThrownBy(() -> client.verify("id-token"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SOCIAL_TOKEN_INVALID));
    }

    @Test
    void jwkFetchFailureIsProviderUnavailable() {
        JwtDecoder failing = token -> {
            throw new JwtException("An error occurred while attempting to decode the Jwt: Couldn't retrieve remote JWK set: connect timed out");
        };
        GoogleIdentityClient client = new GoogleIdentityClient(google(List.of(CLIENT_ID)), failing);

        assertThatThrownBy(() -> client.verify("id-token"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE));
    }

    @Test
    void missingClientIdConfigurationIsProviderUnavailable() {
        GoogleIdentityClient client = new GoogleIdentityClient(google(List.of()), token -> jwt(b -> { }));

        assertThatThrownBy(() -> client.verify("id-token"))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.SOCIAL_PROVIDER_UNAVAILABLE));
    }

    @Test
    void blankClientIdsAreIgnoredWhenBinding() {
        SocialLoginProperties.Google properties = google(List.of("", " "));

        assertThat(properties.isConfigured()).isFalse();
        assertThat(properties.issuers()).contains("https://accounts.google.com", "accounts.google.com");
    }
}
