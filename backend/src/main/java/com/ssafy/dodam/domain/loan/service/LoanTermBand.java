package com.ssafy.dodam.domain.loan.service;

/**
 * 기간 비교 단위.
 *
 * <p>옵션의 term을 정확히 일치시키지 않는 이유는 데이터 구조에 있다. 상품 옵션의 min/max 금리는 대개
 * 상품 단위 값의 복사이므로 기간을 좁혀도 기준금리는 변하지 않고 표본만 줄어든다. 실제로 개인신용대출
 * 19개 상품 중 36개월 옵션을 가진 것은 1개뿐이어서, 정확 일치를 요구하면 36개월 대출은 항상 비교 불가가
 * 된다. 밴드로 묶고 채택한 옵션의 실제 term을 후보마다 반환해 근거를 남긴다.
 */
enum LoanTermBand {
    UP_TO_24("<=24", 24),
    UP_TO_60("25-60", 60),
    UP_TO_120("61-120", 120),
    OVER_120(">120", Integer.MAX_VALUE);

    static final int MIN_MONTHS = 1;
    static final int MAX_MONTHS = 600;

    private final String label;
    private final int inclusiveMax;

    LoanTermBand(String label, int inclusiveMax) {
        this.label = label;
        this.inclusiveMax = inclusiveMax;
    }

    String label() {
        return label;
    }

    /** 범위를 벗어난 개월 수는 밴드를 만들지 않는다. 잔여기간이나 인접 밴드로 조용히 대체하지 않는다. */
    static LoanTermBand of(Integer months) {
        if (months == null || months < MIN_MONTHS || months > MAX_MONTHS) {
            return null;
        }
        for (LoanTermBand band : values()) {
            if (months <= band.inclusiveMax) {
                return band;
            }
        }
        return OVER_120;
    }

    boolean contains(Integer months) {
        LoanTermBand band = of(months);
        return band == this;
    }
}
