package com.ssafy.dodam.domain.user.dto;

import com.ssafy.dodam.domain.finance.entity.FinanceConnectionStatus;
import com.ssafy.dodam.domain.user.entity.Gender;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.entity.UserRole;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 629: birthDate·gender는 사용자 입력, age는 서비스 시간대(Asia/Seoul) 오늘 기준 만 나이로 계산해 내려준다.
 * creditRating은 금융망 신용등급명(A~E)이며 조회·동기화가 한 번도 성공하지 않은 회원은 null이다.
 */
public record UserResponse(
        Long userId,
        String email,
        String name,
        LocalDate birthDate,
        Integer age,
        Gender gender,
        String creditRating,
        LocalDateTime creditRatingUpdatedAt,
        UserRole role,
        FinanceConnectionStatus financeConnectionStatus,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static UserResponse of(User user, FinanceConnectionStatus financeConnectionStatus, LocalDate today) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getName(),
                user.getBirthDate(),
                user.ageAt(today),
                user.getGender(),
                user.getCreditRating(),
                user.getCreditRatingUpdatedAt(),
                user.getRole(),
                financeConnectionStatus,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }
}
