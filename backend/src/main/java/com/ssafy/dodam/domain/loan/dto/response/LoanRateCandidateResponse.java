package com.ssafy.dodam.domain.loan.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 시장금리 비교 후보.
 *
 * <p>{@code productId}는 명세 17의 대환 시뮬레이션이 받는 {@code targetProductId}로 그대로 쓸 수 있도록
 * 반환한다. 533은 대환 가능 여부나 절감액을 계산하지 않는다.
 *
 * <p>{@code term}은 채택한 옵션의 실제 개월 수이며 요청·보유 기간과 다를 수 있다. {@code syncedAt}과
 * {@code sourceType}은 이 값이 저장된 스냅샷임을 숨기지 않기 위해 함께 제공한다.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record LoanRateCandidateResponse(
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
