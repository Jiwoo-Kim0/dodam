package com.ssafy.dodam.domain.loan.service;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 비교 후보 한 건.
 *
 * @param representativeRate 조건에 맞는 옵션 minRate들의 중앙값. 상품마다 옵션 수가 달라도 동일 가중이 되게 한다.
 * @param term 채택한 옵션의 실제 개월 수. 요청·보유 기간과 다를 수 있어 근거로 함께 반환한다.
 */
record LoanRateCandidate(
        Long productId,
        String productName,
        String institutionName,
        String sourceType,
        BigDecimal representativeRate,
        BigDecimal disclosedMinRate,
        BigDecimal disclosedMaxRate,
        Integer term,
        String repaymentType,
        String rateType,
        LocalDateTime syncedAt
) {
}
