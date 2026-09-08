package com.ssafy.dodam.domain.auth.dto;

import com.ssafy.dodam.domain.finance.entity.FinanceConnectionStatus;
import com.ssafy.dodam.domain.user.entity.User;
import java.time.LocalDateTime;

/**
 * financeConnectionStatus가 FAILED면 회원은 생성됐지만 금융망 userKey를 아직 못 받은 상태다.
 */
public record SignupResponse(
        AuthUserResponse user,
        FinanceConnectionStatus financeConnectionStatus,
        LocalDateTime createdAt
) {

    public static SignupResponse of(User user, FinanceConnectionStatus financeConnectionStatus) {
        return new SignupResponse(AuthUserResponse.from(user), financeConnectionStatus, user.getCreatedAt());
    }
}
