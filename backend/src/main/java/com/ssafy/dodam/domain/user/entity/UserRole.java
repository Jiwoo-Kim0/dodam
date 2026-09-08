package com.ssafy.dodam.domain.user.entity;

/**
 * ERD `user.role` CHECK 제약과 동일한 값만 사용한다.
 */
public enum UserRole {
    USER,
    ADMIN;

    public String authority() {
        return "ROLE_" + name();
    }
}
