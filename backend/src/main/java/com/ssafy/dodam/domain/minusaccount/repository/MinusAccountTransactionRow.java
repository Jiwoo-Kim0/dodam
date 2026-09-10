package com.ssafy.dodam.domain.minusaccount.repository;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 마이너스통장 거래 한 건의 조회 결과.
 *
 * @param amount       `chk_transaction_amount`에 따라 항상 양수다. 방향은 transactionType이 정한다.
 * @param balanceAfter 거래 후 잔액. MINUS 계좌는 0 이하이며 NULL일 수 있다. 부호를 뒤집지 않는다.
 */
public record MinusAccountTransactionRow(
        String transactionId,
        LocalDate transactionDate,
        LocalTime transactionTime,
        String transactionType,
        long amount,
        Long balanceAfter,
        String merchantName,
        String summary,
        String memo,
        String categoryCode,
        String categoryName
) {
}
