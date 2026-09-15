package com.ssafy.dodam.domain.simulation.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;

/**
 * 예적금 신규가입 입력(POST /simulations/saving-product).
 *
 * <p>명세의 {@code monthlyAmount}는 적금이면 월 납입액, 예금이면 일시 예치 원금으로 해석한다(서비스 결정, simulation.md).
 * {@code term}은 상품 옵션의 term과 정확히 일치해야 하며 현금흐름 표시 개월 수도 term이다.
 *
 * @param product 준비 단계에서 채우는 상품 스냅샷. 지문·저장 파라미터에는 들어가지 않는다(같은 날 상품이 바뀌어도 같은 요청)
 */
public record SavingProductInput(Long productId, long monthlyAmount, int term, ProductSnapshot product) implements SimulationInput {
    public static final int MAX_TERM_MONTHS = MAX_DURATION_MONTHS;

    public SavingProductInput {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("productId는 양수여야 합니다.");
        }
        if (monthlyAmount <= 0) {
            throw new IllegalArgumentException("monthlyAmount는 양수여야 합니다.");
        }
        if (term < 1 || term > MAX_TERM_MONTHS) {
            throw new IllegalArgumentException("term은 1~" + MAX_TERM_MONTHS + " 사이여야 합니다.");
        }
    }

    public SavingProductInput(Long productId, long monthlyAmount, int term) {
        this(productId, monthlyAmount, term, null);
    }

    public SavingProductInput withProduct(ProductSnapshot snapshot) {
        return new SavingProductInput(productId, monthlyAmount, term, snapshot);
    }

    @Override
    public SimulationType type() {
        return SimulationType.SAVING_PRODUCT;
    }

    @Override
    public int durationMonths() {
        return term;
    }

    @Override
    public String fingerprint() {
        return productId + "|" + monthlyAmount + "|" + term;
    }

    @Override
    public ObjectNode parameters(ObjectMapper json) {
        ObjectNode node = json.createObjectNode();
        node.put("productId", productId);
        node.put("monthlyAmount", monthlyAmount);
        node.put("term", term);
        return node;
    }
}
