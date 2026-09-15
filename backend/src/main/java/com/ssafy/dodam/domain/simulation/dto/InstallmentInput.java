package com.ssafy.dodam.domain.simulation.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import java.math.BigDecimal;

/**
 * 할부 입력(POST /simulations/installment). 신규 구매를 일시불로 결제하는 경우와 할부로 결제하는 경우를 비교한다.
 *
 * <p>{@code feeRate}를 생략하면 적용률 0·{@code feeRateMode = ASSUMED_ZERO}이고 명시(0 포함)하면 {@code EXPLICIT}이다.
 * 두 모드는 계산 결과가 같아도 별개 지문이다 — "확인된 무이자"와 "수수료율을 모름"을 같은 결과로 저장하지 않기 위해서다.
 * 모드는 서버가 정하며 클라이언트 입력 필드가 아니다. 동일 행동 충돌 검사({@link #actionKey})에서는 모드를 뺀다.
 *
 * @param purchaseAmount 구매 금액(원, 양수)
 * @param months         할부 개월 수 1~120. 원금보다 개월 수가 커서 앞 회차 원금이 0인 경우도 허용한다
 * @param feeRate        적용 수수료율(연 %, scale 4). 생략 시 0
 */
public record InstallmentInput(long purchaseAmount, int months, BigDecimal feeRate, String feeRateMode) implements SimulationInput {
    public static final int MAX_MONTHS = MAX_DURATION_MONTHS;
    public static final String MODE_ASSUMED_ZERO = "ASSUMED_ZERO";
    public static final String MODE_EXPLICIT = "EXPLICIT";

    public InstallmentInput {
        if (purchaseAmount <= 0) {
            throw new IllegalArgumentException("purchaseAmount는 양수여야 합니다.");
        }
        if (months < 1 || months > MAX_MONTHS) {
            throw new IllegalArgumentException("months는 1~" + MAX_MONTHS + " 사이여야 합니다.");
        }
        if (!MODE_ASSUMED_ZERO.equals(feeRateMode) && !MODE_EXPLICIT.equals(feeRateMode)) {
            throw new IllegalArgumentException("feeRateMode가 올바르지 않습니다.");
        }
        feeRate = CardRates.percent("feeRate", feeRate);
        if (MODE_ASSUMED_ZERO.equals(feeRateMode) && feeRate.signum() != 0) {
            throw new IllegalArgumentException("생략된 수수료율은 0이어야 합니다.");
        }
    }

    /** 요청의 feeRate(null 허용)에서 모드를 정한다. */
    public static InstallmentInput of(long purchaseAmount, int months, BigDecimal feeRate) {
        return feeRate == null
                ? new InstallmentInput(purchaseAmount, months, BigDecimal.ZERO, MODE_ASSUMED_ZERO)
                : new InstallmentInput(purchaseAmount, months, feeRate, MODE_EXPLICIT);
    }

    @Override
    public SimulationType type() {
        return SimulationType.INSTALLMENT;
    }

    @Override
    public int durationMonths() {
        return months;
    }

    @Override
    public String fingerprint() {
        return actionKey() + "|" + feeRateMode;
    }

    /** 같은 금액·개월·적용률이면 같은 행동이다(모드 제외). 복합의 중복 검사에 쓴다. */
    public String actionKey() {
        return purchaseAmount + "|" + months + "|" + feeRate.toPlainString();
    }

    @Override
    public ObjectNode parameters(ObjectMapper json) {
        ObjectNode node = json.createObjectNode();
        node.put("purchaseAmount", purchaseAmount);
        node.put("months", months);
        node.put("feeRate", feeRate);
        node.put("feeRateMode", feeRateMode);
        return node;
    }
}
