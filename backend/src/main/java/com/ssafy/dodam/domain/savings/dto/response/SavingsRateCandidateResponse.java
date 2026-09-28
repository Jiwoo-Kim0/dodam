package com.ssafy.dodam.domain.savings.dto.response;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 비교 후보 상품 한 건.
 *
 * @param representativeRate 기간 밴드에 드는 옵션 기본금리들의 중앙값
 * @param disclosedPreferredRate 근거 옵션의 우대금리. 판정에 쓰지 않은 참고값이며 없을 수 있다.
 * @param term 근거 옵션의 실제 개월 수. 보유 계약 기간과 다를 수 있다.
 * @param unmatchedConditions <b>이 후보에만</b> 해당하는 미일치 조건. 오름차순이며 없으면 빈 배열이다.
 */
public record SavingsRateCandidateResponse(
        Long productId,
        String productName,
        String institutionName,
        String sourceType,
        BigDecimal representativeRate,
        BigDecimal disclosedRate,
        BigDecimal disclosedPreferredRate,
        Integer term,
        String savingType,
        LocalDateTime syncedAt,
        List<String> unmatchedConditions
) {
}
