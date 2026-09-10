package com.ssafy.dodam.domain.deposit.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 예금 시장금리 비교.
 *
 * @param status COMPLETE·PARTIAL·UNAVAILABLE. 대출 비교(532·533)와 같은 어휘를 쓴다.
 * @param rateDifference 보유 금리 − 기준금리. <b>예금은 양수가 유리하다.</b> 부호 해석이 대출과 반대이므로
 *     {@code comparisonDirection}으로 방향을 명시한다.
 * @param comparisonDirection 항상 {@code HIGHER_IS_BETTER}
 * @param referenceRate 비교군 상품 대표값들의 중앙값. 표본이 부족하면 null이다.
 * @param unmatchedConditions 판정에 사용하지 않은 조건 코드. 오름차순
 */
public record DepositRateComparisonResponse(
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
        List<DepositRateCandidateResponse> candidates
) {
}
