package com.ssafy.dodam.infrastructure.external.ssafy;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SSAFY 교육용 금융망 OPEN API 접속 설정. API Key는 앱 관리자 키이며 환경변수(SSAFY_FINANCE_API_KEY)로만 주입한다.
 */
@ConfigurationProperties(prefix = "app.ssafy-finance")
public record SsafyFinanceProperties(
        String baseUrl,
        String apiKey,
        Duration connectTimeout,
        Duration readTimeout
) {

    public SsafyFinanceProperties {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("app.ssafy-finance.base-url must not be blank");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("app.ssafy-finance.api-key must not be blank");
        }
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(3);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(5);
        }
    }
}
