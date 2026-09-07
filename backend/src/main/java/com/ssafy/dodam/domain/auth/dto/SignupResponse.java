package com.ssafy.dodam.domain.auth.dto;

import com.ssafy.dodam.domain.user.entity.User;
import java.time.LocalDateTime;

public record SignupResponse(
        AuthUserResponse user,
        LocalDateTime createdAt
) {

    public static SignupResponse from(User user) {
        return new SignupResponse(AuthUserResponse.from(user), user.getCreatedAt());
    }
}
