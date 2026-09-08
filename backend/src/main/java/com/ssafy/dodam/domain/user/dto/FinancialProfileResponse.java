package com.ssafy.dodam.domain.user.dto;

import com.ssafy.dodam.domain.user.entity.FinancialProfile;
import java.time.LocalDateTime;

public record FinancialProfileResponse(
        Long monthlyIncome,
        Long annualIncome,
        Integer payday,
        Long fixedExpense,
        Long emergencyFundTarget,
        LocalDateTime updatedAt
) {

    public static FinancialProfileResponse from(FinancialProfile profile) {
        return new FinancialProfileResponse(
                profile.getMonthlyIncome(),
                profile.getAnnualIncome(),
                profile.getPayday(),
                profile.getFixedExpense(),
                profile.getEmergencyFundTarget(),
                profile.getUpdatedAt()
        );
    }
}
