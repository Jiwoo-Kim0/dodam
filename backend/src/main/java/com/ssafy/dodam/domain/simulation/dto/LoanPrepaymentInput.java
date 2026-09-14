package com.ssafy.dodam.domain.simulation.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import java.time.LocalDate;

/**
 * 대출 추가상환 입력(POST /simulations/loan/prepayment).
 *
 * @param loanId      본인 소유로 확인된 대출 id. 계좌번호 원문·해시는 여기까지 오지 않는다
 * @param paymentDate 추가상환일. null이면 {@link #withBaseDate}가 기준일로 채운다 — 생략과 "오늘" 명시는 같은 지문이다
 * @param loan        준비 단계에서 채우는 대출 스냅샷. 지문·저장 파라미터 제외
 */
public record LoanPrepaymentInput(Long loanId, long prepaymentAmount, LocalDate paymentDate, LoanSnapshot loan)
        implements SimulationInput {
    public LoanPrepaymentInput {
        if (loanId == null || loanId <= 0) {
            throw new IllegalArgumentException("loanId는 양수여야 합니다.");
        }
        if (prepaymentAmount <= 0) {
            throw new IllegalArgumentException("prepaymentAmount는 양수여야 합니다.");
        }
    }

    public LoanPrepaymentInput(Long loanId, long prepaymentAmount, LocalDate paymentDate) {
        this(loanId, prepaymentAmount, paymentDate, null);
    }

    public LoanPrepaymentInput withLoan(LoanSnapshot snapshot) {
        return new LoanPrepaymentInput(loanId, prepaymentAmount, paymentDate, snapshot);
    }

    @Override
    public SimulationInput withBaseDate(LocalDate baseDate) {
        return paymentDate == null ? new LoanPrepaymentInput(loanId, prepaymentAmount, baseDate, loan) : this;
    }

    @Override
    public SimulationType type() {
        return SimulationType.LOAN_PREPAYMENT;
    }

    /** 현금흐름 표시 상한. 실제 표는 대출 만기 월까지이며 계산기가 120개월로 자른다. */
    @Override
    public int durationMonths() {
        return MAX_DURATION_MONTHS;
    }

    /** {@code paymentDate}가 채워진 뒤에만 의미가 있다. Runner가 {@link #withBaseDate} 다음에 부른다. */
    @Override
    public String fingerprint() {
        if (paymentDate == null) {
            throw new IllegalStateException("paymentDate가 기준일로 채워지지 않았습니다.");
        }
        return loanId + "|" + prepaymentAmount + "|" + paymentDate;
    }

    @Override
    public ObjectNode parameters(ObjectMapper json) {
        ObjectNode node = json.createObjectNode();
        node.put("loanId", loanId);
        node.put("prepaymentAmount", prepaymentAmount);
        node.put("paymentDate", paymentDate == null ? null : paymentDate.toString());
        return node;
    }
}
