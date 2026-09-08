package com.ssafy.dodam.domain.auth.dto;

import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.entity.UserRole;

/**
 * 인증 응답에 함께 내려주는 최소 사용자 정보. 프로필 전체는 User API(/api/v1/users/me)에서 다룬다.
 */
public record AuthUserResponse(
        Long userId,
        String email,
        String name,
        UserRole role
) {

    public static AuthUserResponse from(User user) {
        return new AuthUserResponse(user.getId(), user.getEmail(), user.getName(), user.getRole());
    }
}
