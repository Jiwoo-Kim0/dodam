package com.ssafy.dodam.domain.savings.service;

/**
 * 기간 비교 단위.
 *
 * <p>예금(536)보다 밴드를 넓게 잡는다. 시연 적금 옵션 81건은 12개월(35건)과 36개월(16건)에 몰려 있고
 * 4·9·15·18·60개월은 각각 1건뿐이라, 예금처럼 좁게 자르면 표준 개월이 아닌 계약이 전부 비교 불가가 된다.
 * 채택한 옵션의 실제 term을 후보마다 반환해 근거를 남긴다.
 */
enum SavingsTermBand {
    UP_TO_6("<=6", 6),
    UP_TO_12("7-12", 12),
    UP_TO_24("13-24", 24),
    OVER_24(">24", Integer.MAX_VALUE);

    static final int MIN_MONTHS = 1;
    /** 적금 최장 계약을 넉넉히 덮는 상한. 이를 넘는 개월 수는 밴드를 만들지 않는다. */
    static final int MAX_MONTHS = 120;

    private final String label;
    private final int inclusiveMax;

    SavingsTermBand(String label, int inclusiveMax) {
        this.label = label;
        this.inclusiveMax = inclusiveMax;
    }

    String label() {
        return label;
    }

    /** 범위를 벗어난 개월 수는 인접 밴드로 조용히 대체하지 않고 null을 준다. */
    static SavingsTermBand of(Integer months) {
        if (months == null || months < MIN_MONTHS || months > MAX_MONTHS) {
            return null;
        }
        for (SavingsTermBand band : values()) {
            if (months <= band.inclusiveMax) {
                return band;
            }
        }
        return OVER_24;
    }

    boolean contains(Integer months) {
        return of(months) == this;
    }
}
