package com.ssafy.dodam.domain.loan.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.util.List;

/**
 * 상환 우선순위 항목.
 *
 * <p>필드 구성은 기존 {@link LoanCostItemResponse}를 따른다. {@code rank}는 1부터 순차이며 금리·잔액이
 * 같아도 공유하지 않는다. 정렬 기준에 유일한 loanId가 포함되어 순서가 항상 하나로 정해지기 때문이다.
 *
 * <p>{@code requiresAttention}은 연체 상태를 표시할 뿐이며 연체 해소의 최적 순서를 계산한 결과가 아니다.
 * 미납액과 연체 가산금리 자료가 없다.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record LoanRepaymentPriorityItemResponse(
        Integer rank,
        Long loanId,
        String accountName,
        String accountNoMasked,
        String institutionName,
        Long balance,
        BigDecimal interestRate,
        boolean requiresAttention,
        String status,
        List<String> reasons
) {
}
