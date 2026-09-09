package com.ssafy.dodam.domain.loan.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 보유 대출 한 건의 시장금리 비교.
 *
 * <p>{@code referenceRate}는 동일 조건에서 공시된 최저금리의 중앙값이며 실제 평균 대출금리나 예상 승인금리가
 * 아니다. 자료의 허용 경과기간 임계값은 두지 않고 {@code oldestSyncedAt}·{@code latestSyncedAt}으로
 * 스냅샷 범위를 알린다. 최신 일시 하나로 전체가 최신인 것처럼 표현하지 않는다.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record LoanRateComparisonResponse(
        LocalDate asOfDate,
        List<String> assumptions,
        Long loanId,
        String accountName,
        String accountNoMasked,
        String institutionName,
        Long balance,
        BigDecimal interestRate,
        String status,
        List<String> reasons,
        String comparisonGroup,
        String termBand,
        String industry,
        String referenceMethod,
        BigDecimal referenceRate,
        String referenceConfidence,
        BigDecimal rateDifference,
        int comparableProductCount,
        List<String> unmatchedConditions,
        LocalDateTime oldestSyncedAt,
        LocalDateTime latestSyncedAt,
        List<LoanRateCandidateResponse> candidates
) {
}
