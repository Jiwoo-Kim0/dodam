package com.ssafy.dodam.domain.loan.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

/**
 * 고금리 점검 대상 한 건.
 *
 * <p>서로 다른 비교군의 편차를 한 목록에서 정렬하므로 {@code comparisonGroup}과 기준금리·표본 수를 항목마다
 * 함께 반환한다. 편차 숫자만으로 다른 군의 대출과 직접 비교되지 않게 한다.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record LoanHighInterestItemResponse(
        Long loanId,
        String accountName,
        String accountNoMasked,
        String institutionName,
        Long balance,
        BigDecimal interestRate,
        boolean requiresAttention,
        String comparisonGroup,
        String referenceMethod,
        BigDecimal referenceRate,
        String referenceConfidence,
        BigDecimal rateDifference,
        int comparableProductCount,
        String status,
        List<String> reasons
) {
}
