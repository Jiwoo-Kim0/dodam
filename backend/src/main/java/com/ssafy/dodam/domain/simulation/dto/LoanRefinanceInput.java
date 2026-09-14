package com.ssafy.dodam.domain.simulation.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;

/**
 * 대환 입력(POST /simulations/loan/refinance).
 *
 * @param loanId          본인 소유로 확인된 기존 대출 id
 * @param targetProductId 대환 대상 상품 id
 * @param refinanceAmount 대환 금액. null이면 기준일 잔액 전액({@code FULL_BALANCE}). 잔액과 같은 금액을 명시해도 별개 지문이다 —
 *                        잔액은 데이터 조회로 정해지므로 지문 전에 알 수 없다
 * @param loan            준비 단계에서 채우는 기존 대출 스냅샷
 * @param product         준비 단계에서 채우는 대상 상품 스냅샷
 */
public record LoanRefinanceInput(Long loanId, Long targetProductId, Long refinanceAmount, LoanSnapshot loan, ProductSnapshot product)
        implements SimulationInput {
    public static final String FULL_BALANCE = "FULL_BALANCE";
    public static final String MODE_EXPLICIT = "EXPLICIT";

    public LoanRefinanceInput {
        if (loanId == null || loanId <= 0) {
            throw new IllegalArgumentException("loanId는 양수여야 합니다.");
        }
        if (targetProductId == null || targetProductId <= 0) {
            throw new IllegalArgumentException("targetProductId는 양수여야 합니다.");
        }
        if (refinanceAmount != null && refinanceAmount <= 0) {
            throw new IllegalArgumentException("refinanceAmount는 양수여야 합니다.");
        }
    }

    public LoanRefinanceInput(Long loanId, Long targetProductId, Long refinanceAmount) {
        this(loanId, targetProductId, refinanceAmount, null, null);
    }

    public LoanRefinanceInput withSnapshots(LoanSnapshot loanSnapshot, ProductSnapshot productSnapshot) {
        return new LoanRefinanceInput(loanId, targetProductId, refinanceAmount, loanSnapshot, productSnapshot);
    }

    public boolean isFullBalance() {
        return refinanceAmount == null;
    }

    @Override
    public SimulationType type() {
        return SimulationType.LOAN_REFINANCE;
    }

    /** 현금흐름 표시 상한. 실제 표는 대출 만기 월까지이며 계산기가 120개월로 자른다. */
    @Override
    public int durationMonths() {
        return MAX_DURATION_MONTHS;
    }

    @Override
    public String fingerprint() {
        return loanId + "|" + targetProductId + "|" + (isFullBalance() ? FULL_BALANCE : refinanceAmount.toString());
    }

    @Override
    public ObjectNode parameters(ObjectMapper json) {
        ObjectNode node = json.createObjectNode();
        node.put("loanId", loanId);
        node.put("targetProductId", targetProductId);
        node.put("refinanceAmount", refinanceAmount);
        node.put("refinanceAmountMode", isFullBalance() ? FULL_BALANCE : MODE_EXPLICIT);
        return node;
    }
}
