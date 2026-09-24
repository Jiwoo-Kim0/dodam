package com.ssafy.dodam.domain.account.dto.response;

public record AccountTransferResponse(Long withdrawalAccountId, long withdrawalBalance,
        Long depositAccountId, long depositBalance, long transferredAmount) {
}
