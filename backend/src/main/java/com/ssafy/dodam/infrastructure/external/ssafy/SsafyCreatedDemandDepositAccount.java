package com.ssafy.dodam.infrastructure.external.ssafy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 2.4 수시입출금 계좌 생성(createDemandDepositAccount) 응답 REC.
 * 목록 조회와 달리 은행명·상품명·잔액·유형 코드를 주지 않고 `currency`는 객체로 온다. 목록 record로 받으면
 * 파싱이 실패하므로 확실히 오는 두 값만 읽는다.
 *
 * @param accountNo 계좌번호 원문. 로그에 남기지 않는다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SsafyCreatedDemandDepositAccount(String bankCode, String accountNo) {
}
