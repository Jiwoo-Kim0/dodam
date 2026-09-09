package com.ssafy.dodam.infrastructure.external.ssafy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 신용등급 기준 조회(inquireCreditRatingList) 응답 REC 원소.
 *
 * @param ratingPercent 금융망이 "5%"처럼 문자열로 내려주므로 그대로 보존한다
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SsafyCreditRatingCriterion(
        String ratingName,
        Long demandDepositAssetValue,
        String ratingPercent
) {
}
