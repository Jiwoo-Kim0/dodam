package com.ssafy.dodam.global.policy;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 예·적금 이자소득세 원천징수 정책.
 *
 * <p>사용자별 과세 구분(비과세·세금우대·분리과세)을 담은 테이블이 없어 일반과세 한 가지로 계산한다.
 * 예금(536)이 먼저 적용했고 적금(537)은 세전만 주다가 2026-09-16에 같은 정책으로 맞췄다 — 두 예상 수령액을
 * 나란히 놓았을 때 적금만 세전이라 유리해 보이는 문제를 없애기 위해서다.
 */
public final class InterestIncomeTaxPolicy {

    /** 이자소득세 15.4% = 소득세 14% + 지방소득세 1.4%. 2026년 일반 과세 기준이다. */
    public static final BigDecimal TAX_RATE_PERCENT = new BigDecimal("15.4");
    public static final String POLICY_CODE = "GENERAL_INTEREST_INCOME_TAX_15_4";
    private static final BigDecimal PERCENT = new BigDecimal("100");

    private InterestIncomeTaxPolicy() {
    }

    /**
     * 세전이자에서 원천징수할 세액. 원 단위 절사라 {@code 세전 = 세액 + 세후}가 항상 성립한다.
     * 세후이자를 먼저 절사하면 그 등식이 1원 어긋날 수 있어 세액 쪽을 절사한다.
     */
    public static long withhold(long grossInterest) {
        return BigDecimal.valueOf(grossInterest)
                .multiply(TAX_RATE_PERCENT)
                .divide(PERCENT, 0, RoundingMode.DOWN)
                .longValueExact();
    }
}
