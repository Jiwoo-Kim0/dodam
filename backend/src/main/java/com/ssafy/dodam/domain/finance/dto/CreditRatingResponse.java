package com.ssafy.dodam.domain.finance.dto;

import com.ssafy.dodam.infrastructure.external.ssafy.SsafyCreditRating;
import java.time.LocalDateTime;

/**
 * 금융망 「내 신용등급 조회」 결과. 자산 값은 금융망이 계산한 금융망 내 자산이며 도담 로컬 집계와 다를 수 있다.
 */
public record CreditRatingResponse(
        String ratingName,
        Integer creditScore,
        Long demandDepositAssetValue,
        Long depositSavingsAssetValue,
        Long totalAssetValue,
        LocalDateTime inquiredAt
) {

    public static CreditRatingResponse of(SsafyCreditRating rating, LocalDateTime inquiredAt) {
        return new CreditRatingResponse(
                rating.ratingName(),
                rating.creditScore(),
                rating.demandDepositAssetValue(),
                rating.depositSavingsAssetValue(),
                rating.totalAssetValue(),
                inquiredAt
        );
    }
}
