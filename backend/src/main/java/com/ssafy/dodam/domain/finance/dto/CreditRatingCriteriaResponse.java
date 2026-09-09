package com.ssafy.dodam.domain.finance.dto;

import com.ssafy.dodam.infrastructure.external.ssafy.SsafyCreditRatingCriterion;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 금융망 「신용등급 기준 조회」(2.7.1) 결과. 등급별 자산 구간(수시입출금 + 예·적금 합산 기준)을 금융망 순서(A→E) 그대로 돌려준다.
 */
public record CreditRatingCriteriaResponse(
        List<Criterion> criteria,
        LocalDateTime inquiredAt
) {

    /**
     * @param minAssetValue 이 등급의 최소 자산 가치 (원)
     * @param maxAssetValue 이 등급의 최대 자산 가치 (원). 최고 등급의 최대값을 넘어도 최고 등급이다
     */
    public record Criterion(
            String ratingUniqueNo,
            String ratingName,
            Long minAssetValue,
            Long maxAssetValue
    ) {

        static Criterion from(SsafyCreditRatingCriterion criterion) {
            return new Criterion(
                    criterion.ratingUniqueNo(),
                    criterion.ratingName(),
                    criterion.minAssetValue(),
                    criterion.maxAssetValue()
            );
        }
    }

    public static CreditRatingCriteriaResponse of(List<SsafyCreditRatingCriterion> criteria, LocalDateTime inquiredAt) {
        return new CreditRatingCriteriaResponse(criteria.stream().map(Criterion::from).toList(), inquiredAt);
    }
}
