package com.ssafy.dodam.infrastructure.external.ssafy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 내 신용등급 조회(inquireMyCreditRating) 응답 REC.
 * 금융망은 숫자도 문자열로 내려주는 경우가 있어 Jackson의 문자열→숫자 강제 변환에 의존한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SsafyCreditRating(
        String ratingName,
        Long demandDepositAssetValue,
        Long depositSavingsAssetValue,
        Long totalAssetValue,
        Integer creditScore
) {
}
