package com.ssafy.dodam.domain.user.dto;

import com.ssafy.dodam.domain.finance.entity.FinanceConnectionStatus;
import com.ssafy.dodam.domain.user.entity.CreditScoreSource;
import com.ssafy.dodam.domain.user.entity.Gender;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.entity.UserRole;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 629: birthDate·gender는 사용자 입력, age는 서비스 시간대(Asia/Seoul) 오늘 기준 만 나이로 계산해 내려준다.
 * creditRating은 금융망 자산 기준 등급이다. creditScore는 도담 예상 점수 또는 실제 CB 조회값이며
 * source/type/modelVersion으로 둘을 구분한다.
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
        Integer creditScore,
        String creditScoreType,
        CreditScoreSource creditScoreSource,
        String creditScoreModelVersion,
        LocalDateTime creditScoreUpdatedAt,
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
                user.getCreditScore(),
                scoreType(user.getCreditScoreSource()),
                user.getCreditScoreSource(),
                user.getCreditScoreModelVersion(),
                user.getCreditScoreUpdatedAt(),
                user.getRole(),
                financeConnectionStatus,
                user.getCreatedAt(),
                user.getUpdatedAt()
        );
    }

    private static String scoreType(CreditScoreSource source) {
        if (source == null) {
            return null;
        }
        return source == CreditScoreSource.SYNTHETIC ? "ESTIMATED" : "VERIFIED";
    }
}
