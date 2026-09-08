package com.ssafy.dodam.domain.card.dto.response;

import com.ssafy.dodam.domain.card.entity.CardWithdrawalAccount;

public record CardWithdrawalAccountResponse(
        String bankCode,
        String bankName,
        String accountName,
        String accountNoMasked,
        String accountType
) {

    public static CardWithdrawalAccountResponse from(CardWithdrawalAccount account) {
        if (account == null) {
            return null;
        }
        return new CardWithdrawalAccountResponse(
                account.getBankCode(),
                account.getBankName(),
                account.getAccountName(),
                account.getAccountNoMasked(),
                account.getAccountType()
        );
    }
}
