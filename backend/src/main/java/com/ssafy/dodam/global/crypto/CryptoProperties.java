package com.ssafy.dodam.global.crypto;

import java.util.Base64;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 민감 컬럼 암호화(AES-256-GCM)와 조회용 해시(HMAC-SHA-256) 키. 둘 다 Base64로 인코딩된 32바이트.
 * 키를 잃으면 암호화 컬럼은 복구할 수 없으므로 DB 백업과 함께 별도 보관해야 한다 (infra/todo/security.md R5).
 */
@ConfigurationProperties(prefix = "app.crypto")
public record CryptoProperties(
        String dataKey,
        String hashKey
) {

    private static final int KEY_BYTES = 32;

    public CryptoProperties {
        requireKey(dataKey, "app.crypto.data-key");
        requireKey(hashKey, "app.crypto.hash-key");
    }

    public byte[] dataKeyBytes() {
        return Base64.getDecoder().decode(dataKey);
    }

    public byte[] hashKeyBytes() {
        return Base64.getDecoder().decode(hashKey);
    }

    private static void requireKey(String value, String propertyName) {
        byte[] decoded;
        try {
            decoded = value == null ? new byte[0] : Base64.getDecoder().decode(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException(propertyName + " must be Base64 encoded", exception);
        }
        if (decoded.length != KEY_BYTES) {
            throw new IllegalStateException(
                    propertyName + " must decode to exactly " + KEY_BYTES + " bytes (openssl rand -base64 32)"
            );
        }
    }
}
