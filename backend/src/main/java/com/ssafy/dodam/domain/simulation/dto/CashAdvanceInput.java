package com.ssafy.dodam.domain.simulation.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import java.math.BigDecimal;

/**
 * 현금서비스 입력(POST /simulations/cash-advance). 미이용과 기준일 이용·만기 일시상환을 비교한다.
 *
 * <p>{@code repaymentMonths} 생략·null·1은 모두 같은 지문이다(서비스 기본값 1개월). 기본값이든 명시든 사용자 지정 상환기간의
 * 추정 시나리오이며 실제 기관이 그 기간 약정을 제공한다는 뜻이 아니다.
 *
 * @param amount          이용 금액(원, 양수)
 * @param rate            수수료율(연 %, 0~100, scale 4)
 * @param repaymentMonths 상환까지 개월 수 1~120
 */
public record CashAdvanceInput(long amount, BigDecimal rate, int repaymentMonths) implements SimulationInput {
    public static final int DEFAULT_REPAYMENT_MONTHS = 1;
    public static final int MAX_REPAYMENT_MONTHS = MAX_DURATION_MONTHS;

    public CashAdvanceInput {
        if (amount <= 0) {
            throw new IllegalArgumentException("amount는 양수여야 합니다.");
        }
        rate = CardRates.percent("rate", rate);
        if (repaymentMonths < 1 || repaymentMonths > MAX_REPAYMENT_MONTHS) {
            throw new IllegalArgumentException("repaymentMonths는 1~" + MAX_REPAYMENT_MONTHS + " 사이여야 합니다.");
        }
    }

    public static CashAdvanceInput of(long amount, BigDecimal rate, Integer repaymentMonths) {
        return new CashAdvanceInput(amount, rate, repaymentMonths == null ? DEFAULT_REPAYMENT_MONTHS : repaymentMonths);
    }

    @Override
    public SimulationType type() {
        return SimulationType.CASH_ADVANCE;
    }

    @Override
    public int durationMonths() {
        return repaymentMonths;
    }

    @Override
    public String fingerprint() {
        return amount + "|" + rate.toPlainString() + "|" + repaymentMonths;
    }

    @Override
    public ObjectNode parameters(ObjectMapper json) {
        ObjectNode node = json.createObjectNode();
        node.put("amount", amount);
        node.put("rate", rate);
        node.put("repaymentMonths", repaymentMonths);
        return node;
    }
}
