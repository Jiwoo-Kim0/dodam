package com.ssafy.dodam.domain.simulation.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import java.math.BigDecimal;

/**
 * 리볼빙 입력(POST /simulations/revolving). {@code balance}는 기준일에 결제할 카드대금이며 전액 결제와 일부 결제·잔액 이월을 비교한다.
 *
 * <p>{@code cardId}는 소유권·대상 식별에만 쓴다. 입력 잔액·금리·결제비율을 카드 DB 값으로 덮어쓰지 않으며 카드번호는
 * 입력·지문·저장·응답 어디에도 없다.
 *
 * @param cardId       본인 소유로 확인된 카드 id. null이면 카드 미지정
 * @param balance      기준일 결제 대상 카드대금(원, 양수)
 * @param paymentRatio 약정 결제비율(%, 0 초과 100 이하, scale 4)
 * @param rate         리볼빙 수수료율(연 %, 0~100, scale 4)
 */
public record RevolvingInput(Long cardId, long balance, BigDecimal paymentRatio, BigDecimal rate) implements SimulationInput {
    /** 계산 회차 상한. 잔액이 남으면 강제 상환하지 않고 미완납으로 표시한다. */
    public static final int MAX_INSTALLMENTS = MAX_DURATION_MONTHS;

    public RevolvingInput {
        if (cardId != null && cardId <= 0) {
            throw new IllegalArgumentException("cardId는 양수여야 합니다.");
        }
        if (balance <= 0) {
            throw new IllegalArgumentException("balance는 양수여야 합니다.");
        }
        paymentRatio = CardRates.positivePercent("paymentRatio", paymentRatio);
        rate = CardRates.percent("rate", rate);
    }

    @Override
    public SimulationType type() {
        return SimulationType.REVOLVING;
    }

    /** 완납 시점을 입력만으로 알 수 없어 표시 상한을 그대로 둔다. 실제 표는 완납 월 또는 120회차까지다. */
    @Override
    public int durationMonths() {
        return MAX_INSTALLMENTS;
    }

    @Override
    public String fingerprint() {
        return (cardId == null ? "" : cardId.toString()) + "|" + balance + "|" + paymentRatio.toPlainString() + "|" + rate.toPlainString();
    }

    @Override
    public ObjectNode parameters(ObjectMapper json) {
        ObjectNode node = json.createObjectNode();
        node.put("cardId", cardId);
        node.put("balance", balance);
        node.put("paymentRatio", paymentRatio);
        node.put("rate", rate);
        return node;
    }
}
