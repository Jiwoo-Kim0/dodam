package com.ssafy.dodam.infrastructure.external.ssafy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * 2.4 계좌 거래내역 조회(inquireTransactionHistoryList) 응답 REC.
 *
 * @param totalCount 전체 건수(문자열로 내려오는 숫자)
 * @param list       거래 목록. 거래가 없으면 빈 배열
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SsafyTransactionHistory(
        Long totalCount,
        List<SsafyAccountTransaction> list
) {

    public static SsafyTransactionHistory empty() {
        return new SsafyTransactionHistory(0L, List.of());
    }
}
