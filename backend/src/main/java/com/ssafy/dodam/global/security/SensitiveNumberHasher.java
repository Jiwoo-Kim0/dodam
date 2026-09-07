package com.ssafy.dodam.global.security;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 계좌번호·카드번호 같은 민감 식별자를 `*_hash` 컬럼 조회용 값으로 변환한다.
 *
 * <p>ERD 공통 정책에 맞춰 HMAC-SHA-256 결과를 64자리 소문자 16진수로 만든다.
 * 원문은 저장하지도, 로그로 남기지도 않는다.
 */
@Component
public class SensitiveNumberHasher {

    private static final String ALGORITHM = "HmacSHA256";

    private final SecretKeySpec key;

    public SensitiveNumberHasher(SecurityHashProperties properties) {
        String configuredKey = properties.numberHashKey();
        if (!StringUtils.hasText(configuredKey)) {
            throw new IllegalStateException(
                    "app.security.number-hash-key 가 설정되지 않았습니다."
            );
        }
        this.key = new SecretKeySpec(
                configuredKey.getBytes(StandardCharsets.UTF_8),
                ALGORITHM
        );
    }

    public String hash(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("해시 대상 값이 비어 있습니다.");
        }
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException | InvalidKeyException exception) {
            throw new IllegalStateException("민감 식별자 해시를 생성할 수 없습니다.", exception);
        }
    }
}
