package com.ssafy.dodam.domain.auth.entity;

import com.ssafy.dodam.domain.auth.social.SocialProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 소셜 계정 연결(ERD user_oauth_account, V34). 공급자 사용자 ID는 해시로만, 공급자 이메일은 암호문·해시·마스킹으로만 저장한다.
 *
 * <p>(provider, providerUserIdHash) UNIQUE라 한 소셜 계정은 한 회원에게만 연결되고, (userId, provider) UNIQUE라 회원은 공급자별로
 * 소셜 계정 하나만 연결한다.
 */
@Getter
@Entity
@Table(name = "user_oauth_account")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserOauthAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "oauth_account_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 30)
    private SocialProvider provider;

    @Column(name = "provider_user_id_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String providerUserIdHash;

    @Column(name = "provider_email_encrypted", columnDefinition = "VARBINARY(512)")
    private byte[] providerEmailEncrypted;

    @Column(name = "provider_email_hash", length = 64, columnDefinition = "CHAR(64)")
    private String providerEmailHash;

    @Column(name = "provider_email_masked", length = 255)
    private String providerEmailMasked;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private UserOauthAccount(Long userId, SocialProvider provider, String providerUserIdHash, ProviderEmail email,
            LocalDateTime now) {
        this.userId = userId;
        this.provider = provider;
        this.providerUserIdHash = providerUserIdHash;
        applyEmail(email);
        this.createdAt = now;
    }

    public static UserOauthAccount link(Long userId, SocialProvider provider, String providerUserIdHash, ProviderEmail email,
            LocalDateTime now) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자를 지정해야 합니다.");
        }
        if (provider == null) {
            throw new IllegalArgumentException("provider를 지정해야 합니다.");
        }
        if (providerUserIdHash == null || providerUserIdHash.length() != 64) {
            throw new IllegalArgumentException("공급자 사용자 ID 해시가 올바르지 않습니다.");
        }
        return new UserOauthAccount(userId, provider, providerUserIdHash, email, now);
    }

    /** 같은 소셜 계정으로 다시 로그인: 공급자 이메일이 바뀌었을 수 있어 갱신하고 시각을 남긴다. */
    public void touch(ProviderEmail email, LocalDateTime now) {
        applyEmail(email);
        this.updatedAt = now;
    }

    private void applyEmail(ProviderEmail email) {
        if (email == null) {
            this.providerEmailEncrypted = null;
            this.providerEmailHash = null;
            this.providerEmailMasked = null;
            return;
        }
        this.providerEmailEncrypted = email.encrypted();
        this.providerEmailHash = email.hash();
        this.providerEmailMasked = email.masked();
    }

    /**
     * 공급자 이메일의 저장 형태(암호문·해시·마스킹). 원문은 어디에도 남기지 않는다.
     */
    public record ProviderEmail(byte[] encrypted, String hash, String masked) {
    }
}
