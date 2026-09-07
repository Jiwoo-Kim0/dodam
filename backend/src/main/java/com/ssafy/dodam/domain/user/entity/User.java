package com.ssafy.dodam.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * ERD `user` 테이블. `user`는 MySQL·H2 모두에서 예약어이므로 백틱으로 인용한다.
 * (Hibernate가 백틱을 Dialect별 인용문자로 변환한다.)
 */
@Entity
@Table(name = "`user`")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long id;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /**
     * 소셜 로그인만 사용하는 계정은 비밀번호가 없으므로 NULL을 허용한다(ERD 기준).
     */
    @Column(name = "password_hash", length = 255)
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private UserRole role;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    protected User() {
    }

    private User(String email, String passwordHash, String name, UserRole role) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = role;
    }

    /**
     * 이메일·비밀번호로 가입하는 자체 회원을 생성한다.
     */
    public static User createLocalUser(String email, String passwordHash, String name) {
        return new User(email, passwordHash, name, UserRole.USER);
    }

    @PrePersist
    void onPersist() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public boolean hasPassword() {
        return passwordHash != null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public UserRole getRole() {
        return role;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }
}
