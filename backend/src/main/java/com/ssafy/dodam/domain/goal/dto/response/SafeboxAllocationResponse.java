package com.ssafy.dodam.domain.goal.dto.response;

public record SafeboxAllocationResponse(Long goalId, Long accountId, long allocatedAmount,
        long accountBalance, long availableAmount, long shortfallAmount) {
}
