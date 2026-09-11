package com.ssafy.dodam.domain.account.dto.response;

import com.ssafy.dodam.domain.account.repository.AccountTransactionRow;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 계좌 거래 한 건. 마이너스통장(10-1) 거래내역 응답과 필드 이름·순서를 맞췼다(응답 계약이 갈라지지 않게).
 *
 * @param amount       항상 양수. 입출금 방향은 transactionType(INCOME / EXPENSE / TRANSFER)이 정한다.
 * @param balanceAfter 거래 후 잔액. 금융망이 주지 않은 거래는 null.
 * @param hidden       숨김 여부. 목록에서는 항상 false(숨긴 거래는 제외), 단건 조회에서만 true가 나올 수 있다.
 */
public record AccountTransactionResponse(
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
        String categoryName,
        boolean hidden
) {

    public static AccountTransactionResponse from(AccountTransactionRow row) {
        return new AccountTransactionResponse(
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
                row.categoryName(),
                row.hidden()
        );
    }
}
