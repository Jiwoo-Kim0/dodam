package com.ssafy.dodam.domain.finance.dto;

import com.ssafy.dodam.infrastructure.external.ssafy.SsafyCreditRating;
import java.time.LocalDateTime;

/**
 * 금융망 「내 신용등급 조회」(2.7.4) 결과. 등급은 A~E이며 금융망 내 수시입출금·예적금 자산 합산으로 산정된다.
 * 자산 값은 금융망이 계산한 값이라 도담 로컬 집계와 다를 수 있다. 금융망은 신용점수를 제공하지 않는다.
 */
public record CreditRatingResponse(
        String ratingName,
        Long demandDepositAssetValue,
        Long depositSavingsAssetValue,
        Long totalAssetValue,
        LocalDateTime inquiredAt
) {

    public static CreditRatingResponse of(SsafyCreditRating rating, LocalDateTime inquiredAt) {
        return new CreditRatingResponse(
                rating.ratingName(),
                rating.demandDepositAssetValue(),
                rating.depositSavingsAssetValue(),
                rating.totalAssetValue(),
                inquiredAt
        );
    }
}
