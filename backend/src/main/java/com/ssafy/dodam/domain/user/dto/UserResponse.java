package com.ssafy.dodam.domain.user.dto;

import com.ssafy.dodam.domain.finance.entity.FinanceConnectionStatus;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.entity.UserRole;
import java.time.LocalDateTime;

public record UserResponse(
        Long userId,
        String email,
        String name,
        UserRole role,
        FinanceConnectionStatus financeConnectionStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static UserResponse of(User user, FinanceConnectionStatus financeConnectionStatus) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getRole(),
                financeConnectionStatus,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
