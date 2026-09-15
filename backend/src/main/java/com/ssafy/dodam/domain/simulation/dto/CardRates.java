package com.ssafy.dodam.domain.simulation.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 카드 유형(586) 입력의 연율·비율 정규화. 요청은 소수 4자리까지 받고, 지문·저장에는 항상 scale 4로 쓴다 —
 * {@code 0}과 {@code 0.0000}이 같은 지문이 되고 {@code 12}와 {@code 12.0}이 다른 요청으로 갈리지 않게 하기 위해서다.
 * 범위 0~100은 계산 입력 상한이며 실제 약정이나 법정 상한을 뜻하지 않는다.
 */
final class CardRates {
    static final int SCALE = 4;
    static final BigDecimal MAX_PERCENT = BigDecimal.valueOf(100);

    private CardRates() {
    }

    /** 0 ≤ value ≤ 100, scale 4. 소수 5자리 이상은 절사하지 않고 거부한다. */
    static BigDecimal percent(String name, BigDecimal value) {
        if (value == null) {
            throw new IllegalArgumentException(name + "은(는) 필수입니다.");
        }
        if (value.signum() < 0 || value.compareTo(MAX_PERCENT) > 0) {
            throw new IllegalArgumentException(name + "은(는) 0~100 사이여야 합니다.");
        }
        if (value.stripTrailingZeros().scale() > SCALE) {
            throw new IllegalArgumentException(name + "은(는) 소수 " + SCALE + "자리까지만 허용합니다.");
        }
        return value.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    /** 0 < value ≤ 100, scale 4. */
    static BigDecimal positivePercent(String name, BigDecimal value) {
        BigDecimal normalized = percent(name, value);
        if (normalized.signum() <= 0) {
            throw new IllegalArgumentException(name + "은(는) 0보다 커야 합니다.");
        }
        return normalized;
    }
}
