package com.ssafy.dodam.domain.minusaccount.dto.response;

import java.time.LocalDateTime;

/**
 * 원금 상환 결과. 상환 후의 사용액·가용액과 출금계좌 잔액을 함께 돌려준다.
 *
 * <p>이 API는 거래내역(`transaction`)에 기록을 남기지 않는다. 거래 원장은 BE-A 소관이고
 * 시스템이 만드는 거래의 규약이 정해지지 않았기 때문이다. 따라서 상환 직후 거래내역 조회에는
 * 이 상환이 나타나지 않는다.
 */
public record MinusAccountRepaymentResponse(
        Long accountId,
        String accountNoMasked,
        Long repaidAmount,
        Long limitAmount,
        Long usedAmount,
        Long availableAmount,
        Long accruedInterest,
        String repayAccountNoMasked,
        Long repayAccountBalance,
        LocalDateTime processedAt
) {
}
