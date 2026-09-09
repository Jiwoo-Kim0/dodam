package com.ssafy.dodam.domain.finance.dto;

import com.ssafy.dodam.infrastructure.external.ssafy.SsafyCreditRatingCriterion;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 금융망 「신용등급 기준 조회」 결과. 등급별 요구 입출금 자산과 비율 표를 금융망 순서 그대로 돌려준다.
 */
public record CreditRatingCriteriaResponse(
        List<Criterion> criteria,
        LocalDateTime inquiredAt
) {

    public record Criterion(
            String ratingName,
            Long demandDepositAssetValue,
            String ratingPercent
    ) {

        static Criterion from(SsafyCreditRatingCriterion criterion) {
            return new Criterion(
                    criterion.ratingName(),
                    criterion.demandDepositAssetValue(),
                    criterion.ratingPercent()
            );
        }
    }

    public static CreditRatingCriteriaResponse of(List<SsafyCreditRatingCriterion> criteria, LocalDateTime inquiredAt) {
        return new CreditRatingCriteriaResponse(criteria.stream().map(Criterion::from).toList(), inquiredAt);
    }
}
