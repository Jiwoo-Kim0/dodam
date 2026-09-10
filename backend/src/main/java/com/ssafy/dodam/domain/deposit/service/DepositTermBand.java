package com.ssafy.dodam.domain.deposit.service;

/**
 * 기간 비교 단위.
 *
 * <p>예금 금리는 기간에 따라 실제로 달라지므로 대출보다 밴드를 좁게 잡는다. 그렇다고 정확 일치를 요구하지는
 * 않는다. 시연 데이터의 예금 옵션 56건은 1·3·6·9·12·24·36·48·60개월에만 몰려 있어 9개월 옵션은 단 1건이고,
 * 정확 일치를 요구하면 표준 개월이 아닌 계약은 항상 비교 불가가 된다. 밴드로 묶고 채택한 옵션의 실제 term을
 * 후보마다 반환해 근거를 남긴다.
 *
 * <p>경계는 시연 데이터의 개월 분포에 맞췄다. 12개월(13건)과 24개월(9건)이 각각 단독 밴드의 중심이 되도록
 * 7-12와 13-24를 나눴다.
 */
enum DepositTermBand {
    UP_TO_3("<=3", 3),
    UP_TO_6("4-6", 6),
    UP_TO_12("7-12", 12),
    UP_TO_24("13-24", 24),
    OVER_24(">24", Integer.MAX_VALUE);

    static final int MIN_MONTHS = 1;
    /** 예금 최장 계약을 넉넉히 덮는 상한. 이를 넘는 개월 수는 밴드를 만들지 않는다. */
    static final int MAX_MONTHS = 120;

    private final String label;
    private final int inclusiveMax;

    DepositTermBand(String label, int inclusiveMax) {
        this.label = label;
        this.inclusiveMax = inclusiveMax;
    }

    String label() {
        return label;
    }

    /** 범위를 벗어난 개월 수는 인접 밴드로 조용히 대체하지 않고 null을 준다. */
    static DepositTermBand of(Integer months) {
        if (months == null || months < MIN_MONTHS || months > MAX_MONTHS) {
            return null;
        }
        for (DepositTermBand band : values()) {
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
