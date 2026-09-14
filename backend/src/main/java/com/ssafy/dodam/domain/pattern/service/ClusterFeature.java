package com.ssafy.dodam.domain.pattern.service;

import java.util.Arrays;
import java.util.List;

/**
 * 소비 유형 군집의 입력 특징 5종. 모두 **비율**이라 소득 규모가 달라도 비교할 수 있고, 소득·연령·성별 같은 민감 속성은
 * 넣지 않는다(기획안 14-6). 순서가 특징 벡터의 차원 순서다.
 *
 * <p>상한(`cap`)은 이상치 하나가 표준화 통계를 흔드는 것을 막는 클리핑 값이다. 소득이 매우 작은 달이 섞이면 소비/소득 비율이
 * 수십 배로 튀는데, 그 값을 그대로 쓰면 나머지 사용자가 모두 한 군집으로 몰린다.
 */
public enum ClusterFeature {
    /** 소비 / 소득 */
    SPENDING_TO_INCOME_RATIO("spendingToIncomeRatio", "소득 대비 소비율", 3.0),
    /** 저축성 지출 / 소득 */
    SAVING_RATE("savingRate", "저축률", 2.0),
    /** 고정지출 / 소비 */
    FIXED_EXPENSE_SHARE("fixedExpenseShare", "고정지출 비중", 1.0),
    /** 재량 소비(외식·카페·쇼핑·뷰티·문화·여행·택시) / 소비 */
    DISCRETIONARY_SHARE("discretionaryShare", "재량소비 비중", 1.0),
    /** 월 소비 변동계수(표준편차 / 평균) */
    SPENDING_VOLATILITY("spendingVolatility", "소비 변동성", 2.0);

    private final String key;
    private final String displayName;
    private final double cap;

    ClusterFeature(String key, String displayName, double cap) {
        this.key = key;
        this.displayName = displayName;
        this.cap = cap;
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public double clip(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(cap, value));
    }

    public static List<ClusterFeature> ordered() {
        return Arrays.asList(values());
    }

    public static int dimension() {
        return values().length;
    }
}
