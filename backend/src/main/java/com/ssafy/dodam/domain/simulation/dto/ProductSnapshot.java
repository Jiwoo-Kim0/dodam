package com.ssafy.dodam.domain.simulation.dto;

import com.ssafy.dodam.domain.product.entity.ProductType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 실행 시점에 읽은 금융상품 조건. 계산기는 엔티티가 아니라 이 값만 받는다 — 지연 로딩·영속성 컨텍스트에 기대지 않고
 * 단위 테스트가 DB 없이 돌며, 저장 결과에 남기는 "그때의 상품 조건"이 이 값과 정확히 같다.
 *
 * @param maxLimit  상품 공통 한도(원). 없으면 null
 * @param syncedAt  상품 DB 동기화 시각. 결과에 저장해 "언제 기준 조건인지"를 남긴다
 * @param options   기간 오름차순·id 오름차순
 */
public record ProductSnapshot(
        Long productId,
        String productName,
        String companyName,
        ProductType productType,
        String savingType,
        String loanType,
        Long maxLimit,
        LocalDateTime syncedAt,
        List<Option> options
) {
    public ProductSnapshot {
        options = List.copyOf(options);
    }

    /**
     * @param rate          예적금 기본금리(%). 우대금리는 적용하지 않으므로 preferredRate는 싣지 않는다
     * @param minRate       대출 공시 최저금리(%). 시뮬레이션 적용금리다
     * @param repaymentType 상품 옵션의 상환 코드 문자열(Loan enum과 다름, {@code LoanComparisonPolicy} 대응표 사용)
     * @param limitAmount   옵션 한도(원). 없으면 null
     */
    public record Option(
            Long optionId,
            Integer term,
            BigDecimal rate,
            String rateType,
            BigDecimal minRate,
            BigDecimal maxRate,
            String repaymentType,
            Long limitAmount
    ) {
    }
}
