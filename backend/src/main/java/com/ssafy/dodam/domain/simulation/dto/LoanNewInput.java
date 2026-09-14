package com.ssafy.dodam.domain.simulation.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.loan.entity.RepaymentType;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;

/**
 * 신규 대출 입력(POST /simulations/loan/new).
 *
 * <p>{@code repaymentType}이 null이면 내부 요청 모드 {@code AUTO}다 — 상품 옵션이 한 방식으로만 확정될 때만 자동 선택한다.
 * AUTO와 명시는 결과가 같아도 별개 지문이다. 실제 선택된 방식은 결과 details에만 저장하고 지문에 역으로 섞지 않는다
 * (같은 날 상품 옵션이 바뀌어도 AUTO 재요청은 첫 결과를 재사용한다).
 *
 * @param product 준비 단계에서 채우는 상품 스냅샷. 지문·저장 파라미터 제외
 */
public record LoanNewInput(Long productId, long principal, int term, RepaymentType repaymentType, ProductSnapshot product)
        implements SimulationInput {
    /** 신규 대출 계산 상한. 기존 상환일정 계산기의 {@code MAX_SCHEDULE_SIZE}와 같다. */
    public static final int MAX_TERM_MONTHS = 360;
    public static final String MODE_AUTO = "AUTO";
    public static final String MODE_EXPLICIT = "EXPLICIT";

    public LoanNewInput {
        if (productId == null || productId <= 0) {
            throw new IllegalArgumentException("productId는 양수여야 합니다.");
        }
        if (principal <= 0) {
            throw new IllegalArgumentException("principal은 양수여야 합니다.");
        }
        if (term < 1 || term > MAX_TERM_MONTHS) {
            throw new IllegalArgumentException("term은 1~" + MAX_TERM_MONTHS + " 사이여야 합니다.");
        }
    }

    public LoanNewInput(Long productId, long principal, int term, RepaymentType repaymentType) {
        this(productId, principal, term, repaymentType, null);
    }

    public LoanNewInput withProduct(ProductSnapshot snapshot) {
        return new LoanNewInput(productId, principal, term, repaymentType, snapshot);
    }

    public boolean isAuto() {
        return repaymentType == null;
    }

    public String repaymentTypeMode() {
        return isAuto() ? MODE_AUTO : MODE_EXPLICIT;
    }

    @Override
    public SimulationType type() {
        return SimulationType.LOAN_NEW;
    }

    /** 현금흐름 표시 상한. 실제 표는 만기 월까지이며 계산기가 {@code min(term, 120)}으로 자른다. */
    @Override
    public int durationMonths() {
        return Math.min(term, MAX_DURATION_MONTHS);
    }

    @Override
    public String fingerprint() {
        return productId + "|" + principal + "|" + term + "|" + (isAuto() ? MODE_AUTO : repaymentType.name());
    }

    @Override
    public ObjectNode parameters(ObjectMapper json) {
        ObjectNode node = json.createObjectNode();
        node.put("productId", productId);
        node.put("principal", principal);
        node.put("term", term);
        node.put("repaymentType", repaymentType == null ? null : repaymentType.name());
        node.put("repaymentTypeMode", repaymentTypeMode());
        return node;
    }
}
