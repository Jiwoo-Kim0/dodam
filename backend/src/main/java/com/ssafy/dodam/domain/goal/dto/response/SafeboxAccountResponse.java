package com.ssafy.dodam.domain.goal.dto.response;

public record SafeboxAccountResponse(Long accountId, String accountNoMasked, long balance,
        long allocatedAmount, long availableAmount, long shortfallAmount) {
}
