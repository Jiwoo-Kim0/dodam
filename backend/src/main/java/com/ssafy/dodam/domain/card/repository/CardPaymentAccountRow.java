package com.ssafy.dodam.domain.card.repository;

public record CardPaymentAccountRow(
        Long accountId,
        String bankCode,
        String bankName,
        String accountName,
        String accountNoMasked,
        String accountType
) {
}
