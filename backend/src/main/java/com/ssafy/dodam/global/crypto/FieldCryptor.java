package com.ssafy.dodam.global.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * ERD에서 확정한 `*_encrypted`(AES-256-GCM) / `*_hash`(HMAC-SHA-256) / `*_masked` 컬럼 규칙 구현.
 *
 * 저장 형식: [12바이트 IV][GCM 암호문+태그]. IV는 매 암호화마다 새로 생성한다.
 * 해시는 같은 평문이면 같은 값이 나오므로 UNIQUE 키·조회 조건에 쓰고, 암호문은 복호화 용도로만 쓴다.
 */
@Component
public class FieldCryptor {

    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final String MAC = "HmacSHA256";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec dataKey;
    private final SecretKeySpec hashKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public FieldCryptor(CryptoProperties properties) {
        this.dataKey = new SecretKeySpec(properties.dataKeyBytes(), "AES");
        this.hashKey = new SecretKeySpec(properties.hashKeyBytes(), MAC);
    }

    public byte[] encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.ENCRYPT_MODE, dataKey, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Field encryption failed", exception);
        }
    }

    public String decrypt(byte[] encrypted) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(encrypted);
            byte[] iv = new byte[IV_BYTES];
            buffer.get(iv);
            byte[] ciphertext = new byte[buffer.remaining()];
            buffer.get(ciphertext);
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(Cipher.DECRYPT_MODE, dataKey, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | RuntimeException exception) {
            throw new IllegalStateException("Field decryption failed", exception);
        }
    }

    /**
     * @return 64자 소문자 hex (CHAR(64) 컬럼)
     */
    public String hash(String plaintext) {
        try {
            Mac mac = Mac.getInstance(MAC);
            mac.init(hashKey);
            return HexFormat.of().formatHex(mac.doFinal(plaintext.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Field hashing failed", exception);
        }
    }

    /**
     * 이메일 마스킹: 로컬파트 앞 2글자만 남긴다. 예) do***@example.com
     */
    public static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String visible = local.substring(0, Math.min(2, local.length()));
        return visible + "***" + email.substring(at);
    }
}
