package com.ssafy.dodam.domain.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * ERD `auth_session` 테이블. Refresh Token은 평문을 저장하지 않고 해시만 보관한다.
 * User와의 관계(1:N)는 DB FK로 보장하고, 엔티티에서는 `user_id` 값만 들고 있는다.
 * 세션 조회에 사용자 연관 그래프가 필요하지 않아 지연로딩 문제를 만들지 않는 쪽을 택했다.
 */
@Entity
@Table(name = "auth_session")
public class AuthSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "refresh_token_hash", nullable = false, unique = true, length = 255)
    private String refreshTokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    protected AuthSession() {
    }

    private AuthSession(Long userId, String refreshTokenHash, LocalDateTime expiresAt) {
        this.userId = userId;
        this.refreshTokenHash = refreshTokenHash;
        this.expiresAt = expiresAt;
    }

    public static AuthSession issue(Long userId, String refreshTokenHash, LocalDateTime expiresAt) {
        return new AuthSession(userId, refreshTokenHash, expiresAt);
    }

    @PrePersist
    void onPersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.lastAccessedAt = now;
    }

    public boolean isActiveAt(LocalDateTime at) {
        return revokedAt == null && expiresAt.isAfter(at);
    }

    public void revoke(LocalDateTime at) {
        if (revokedAt == null) {
            this.revokedAt = at;
        }
    }

    public void markAccessed(LocalDateTime at) {
        this.lastAccessedAt = at;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getRefreshTokenHash() {
        return refreshTokenHash;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastAccessedAt() {
        return lastAccessedAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }
}
