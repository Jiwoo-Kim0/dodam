package com.ssafy.dodam.infrastructure.external.ssafy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 2.4 계좌 거래내역 조회 응답 `REC.list` 원소. 숫자는 문자열로, 날짜는 yyyyMMdd, 시각은 HHmmss로 내려온다.
 *
 * @param transactionUniqueNo     거래고유번호. 우리 원장의 `transaction_id`
 * @param transactionType         "1" 입금, "2" 출금
 * @param transactionAccountNo    상대 계좌번호(이체가 아니면 빈 문자열). 로그·저장하지 않는다
 * @param transactionBalance      거래 금액(항상 양수)
 * @param transactionAfterBalance 거래 후 잔액
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SsafyAccountTransaction(
        String transactionUniqueNo,
        String transactionDate,
        String transactionTime,
        String transactionType,
        String transactionTypeName,
        String transactionAccountNo,
        Long transactionBalance,
        Long transactionAfterBalance,
        String transactionSummary,
        String transactionMemo
) {
}
