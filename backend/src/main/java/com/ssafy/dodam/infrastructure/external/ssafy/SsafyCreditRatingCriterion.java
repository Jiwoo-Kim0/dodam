package com.ssafy.dodam.infrastructure.external.ssafy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 2.7.1 신용등급 기준 조회(inquireAssetBasedCreditRatingList) 응답 REC 원소.
 * 자산 기준은 수시입출금 + 예·적금 합산 금액이며, 최대 자산 가치 중 가장 큰 금액을 넘으면 최고 등급이다.
 *
 * @param ratingUniqueNo 신용등급 기준 고유번호 (예: RT-0fa85f6425e811ea4). 대출 상품 생성 API에 쓰인다
 * @param ratingName     신용등급 (A~E)
 * @param minAssetValue  최소 자산 가치 (원)
 * @param maxAssetValue  최대 자산 가치 (원)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SsafyCreditRatingCriterion(
        String ratingUniqueNo,
        String ratingName,
        Long minAssetValue,
        Long maxAssetValue
) {
}
