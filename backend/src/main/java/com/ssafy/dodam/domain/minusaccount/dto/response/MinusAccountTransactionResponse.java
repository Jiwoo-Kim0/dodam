package com.ssafy.dodam.domain.minusaccount.dto.response;

import com.ssafy.dodam.domain.minusaccount.repository.MinusAccountTransactionRow;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 마이너스통장 거래 한 건.
 *
 * @param amount       항상 양수이며 입출금 방향은 transactionType이 정한다.
 * @param balanceAfter 거래 후 잔액. MINUS 계좌는 0 이하이거나 NULL일 수 있고 저장값 그대로 전달한다.
 */
public record MinusAccountTransactionResponse(
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

    public static MinusAccountTransactionResponse from(MinusAccountTransactionRow row) {
        return new MinusAccountTransactionResponse(
                row.transactionId(),
                row.transactionDate(),
                row.transactionTime(),
                row.transactionType(),
                row.amount(),
                row.balanceAfter(),
                row.merchantName(),
                row.summary(),
                row.memo(),
                row.categoryCode(),
                row.categoryName()
        );
    }
}
