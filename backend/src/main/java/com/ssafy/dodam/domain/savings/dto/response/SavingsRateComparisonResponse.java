package com.ssafy.dodam.domain.savings.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 적금 시장금리 비교. 예금 비교(536)와 같은 필드 구성을 쓴다.
 *
 * @param rateDifference 보유 금리 − 기준금리. <b>적금도 양수가 유리하다.</b>
 * @param comparisonDirection 항상 {@code HIGHER_IS_BETTER}
 * @param unmatchedConditions 판정에 쓰지 않은 조건. <b>전체 후보의 합집합</b>이며 오름차순
 */
public record SavingsRateComparisonResponse(
        LocalDate asOfDate,
        List<String> assumptions,
        Long accountId,
        String accountName,
        String accountNoMasked,
        String bankName,
        Long balance,
        BigDecimal interestRate,
        String status,
        List<String> unavailableReasons,
        String comparisonGroup,
        String termBand,
        String industry,
        String referenceMethod,
        BigDecimal referenceRate,
        String confidence,
        BigDecimal rateDifference,
        String comparisonDirection,
        int comparableProductCount,
        List<String> unmatchedConditions,
        LocalDateTime oldestSyncedAt,
        LocalDateTime latestSyncedAt,
        List<SavingsRateCandidateResponse> candidates
) {
}
