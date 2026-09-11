package com.ssafy.dodam.domain.account.dto.response;

import com.ssafy.dodam.domain.account.entity.Account;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 계좌 잔액. 잔액 화면이 계좌 상세 전체를 받지 않아도 되도록 식별 정보와 잔액만 준다.
 *
 * @param balanceAsOf 잔액이 마지막으로 갱신된 시각(`account.updated_at`). 동기화·상환 등 잔액을 바꾸는 모든 경로가 이 값을 찍는다.
 */
public record AccountBalanceResponse(
        Long accountId,
        String bankName,
        String accountNoMasked,
        String accountName,
        String accountType,
        String status,
        long balance,
        String currency,
        LocalDate lastTransactionDate,
        LocalDateTime balanceAsOf
) {

    public static AccountBalanceResponse from(Account account) {
        return new AccountBalanceResponse(
                account.getId(),
                account.getBankName(),
                account.getAccountNoMasked(),
                account.getAccountName(),
                account.getAccountType().name(),
                account.getStatus().name(),
                account.getBalance(),
                account.getCurrency(),
                account.getLastTransactionDate(),
                account.getUpdatedAt()
        );
    }
}
