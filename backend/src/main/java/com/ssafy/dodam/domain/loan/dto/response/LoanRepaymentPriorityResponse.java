package com.ssafy.dodam.domain.loan.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDate;
import java.util.List;

/**
 * 상환 우선순위 응답.
 *
 * <p>목표 반영 여부를 나타내는 필드는 두지 않는다. {@code goalId}가 미지원으로 400 처리되어 값이 항상
 * 같아지므로 의미가 없고, 목표 미반영 사실은 {@code assumptions}로 설명한다.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record LoanRepaymentPriorityResponse(
        LocalDate asOfDate,
        List<String> assumptions,
        boolean complete,
        List<String> unavailableReasons,
        List<LoanUnavailableResponse> unavailableLoans,
        String strategy,
        int evaluatedCount,
        List<LoanRepaymentPriorityItemResponse> loans
) {
}
