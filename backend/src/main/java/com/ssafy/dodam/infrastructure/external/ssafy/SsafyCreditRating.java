package com.ssafy.dodam.infrastructure.external.ssafy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 2.7.4 내 신용등급 조회(inquireMyCreditRating) 응답 REC.
 * 금융망은 Long 항목도 문자열("1089427976")로 내려주므로 Jackson의 문자열→숫자 강제 변환에 의존한다.
 *
 * @param ratingName              신용등급명 (A~E)
 * @param demandDepositAssetValue 사용자의 모든 수시입출금 합산 자산
 * @param depositSavingsAssetValue 사용자의 모든 예·적금 합산 자산
 * @param totalAssetValue         전체 합산 자산
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SsafyCreditRating(
        String ratingName,
        Long demandDepositAssetValue,
        Long depositSavingsAssetValue,
        Long totalAssetValue
) {
}
