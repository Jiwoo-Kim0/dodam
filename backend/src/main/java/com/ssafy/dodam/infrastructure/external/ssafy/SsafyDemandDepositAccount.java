package com.ssafy.dodam.infrastructure.external.ssafy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 2.4 수시입출금 계좌 목록 조회(inquireDemandDepositAccountList) 응답 REC 원소.
 * 금융망은 숫자 항목도 문자열("1089427976")로, 날짜는 "yyyyMMdd"로 내려준다. 값 해석은 도메인이 한다.
 *
 * @param accountNo           계좌번호 원문(16자리). 로그에 남기지 않는다.
 * @param accountTypeCode     계좌 유형 코드("1" 수시입출금)
 * @param accountBalance      계좌 잔액
 * @param lastTransactionDate 최종 거래일(yyyyMMdd). 거래가 없으면 빈 문자열
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SsafyDemandDepositAccount(
        String bankCode,
        String bankName,
        String userName,
        String accountNo,
        String accountName,
        String accountTypeCode,
        String accountTypeName,
        String accountCreatedDate,
        String accountExpiryDate,
        Long dailyTransferLimit,
        Long oneTimeTransferLimit,
        Long accountBalance,
        String lastTransactionDate,
        String currency
) {
}
