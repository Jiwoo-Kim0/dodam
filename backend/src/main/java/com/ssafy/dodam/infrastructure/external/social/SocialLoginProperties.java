package com.ssafy.dodam.infrastructure.external.social;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 소셜 로그인 공급자 접속 설정(app.social). 비밀값(client secret)은 서버 검증 흐름에 필요하지 않아 받지 않는다.
 *
 * <p>카카오 access token 검증에는 앱 설정이 필요 없다. {@code kakao.app-id}(카카오 개발자 콘솔 앱 ID, 숫자)를 주면 토큰이 우리 앱에서 발급된 것인지
 * 추가로 확인한다. 구글은 ID token의 aud가 {@code google.client-ids} 중 하나여야 하므로 최소 하나는 설정해야 동작한다.
 */
@ConfigurationProperties(prefix = "app.social")
public record SocialLoginProperties(
        Kakao kakao,
        Google google,
        Duration connectTimeout,
        Duration readTimeout
) {

    public SocialLoginProperties {
        if (kakao == null) {
            kakao = new Kakao(null, null, null);
        }
        if (google == null) {
            google = new Google(null, null, null);
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(3);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(5);
        }
    }

    public record Kakao(String userInfoUrl, String tokenInfoUrl, String appId) {

        public Kakao {
            if (userInfoUrl == null || userInfoUrl.isBlank()) {
                userInfoUrl = "https://kapi.kakao.com/v2/user/me";
            }
            if (tokenInfoUrl == null || tokenInfoUrl.isBlank()) {
                tokenInfoUrl = "https://kapi.kakao.com/v1/user/access_token_info";
            }
            appId = appId == null || appId.isBlank() ? null : appId.trim();
        }

        public boolean checksAppId() {
            return appId != null;
        }
    }

    public record Google(String jwkSetUri, List<String> issuers, List<String> clientIds) {

        public Google {
            if (jwkSetUri == null || jwkSetUri.isBlank()) {
                jwkSetUri = "https://www.googleapis.com/oauth2/v3/certs";
            }
            if (issuers == null || issuers.isEmpty()) {
                issuers = List.of("https://accounts.google.com", "accounts.google.com");
            }
            clientIds = clientIds == null ? List.of()
                    : clientIds.stream().map(String::trim).filter(id -> !id.isEmpty()).toList();
        }

        public boolean isConfigured() {
            return !clientIds.isEmpty();
        }
    }
}
