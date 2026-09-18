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
    SPENDING_TO_INCOME_RATIO("spendingToIncomeRatio", "소득 대비 소비율", 3.0, Direction.LOWER_IS_BETTER),
    /** 저축성 지출 / 소득 */
    SAVING_RATE("savingRate", "저축률", 2.0, Direction.HIGHER_IS_BETTER),
    /** 고정지출 / 소비 */
    FIXED_EXPENSE_SHARE("fixedExpenseShare", "고정지출 비중", 1.0, Direction.LOWER_IS_BETTER),
    /** 재량 소비(외식·카페·쇼핑·뷰티·문화·여행·택시) / 소비 */
    DISCRETIONARY_SHARE("discretionaryShare", "재량소비 비중", 1.0, Direction.LOWER_IS_BETTER),
    /** 월 소비 변동계수(표준편차 / 평균) */
    SPENDING_VOLATILITY("spendingVolatility", "소비 변동성", 2.0, Direction.LOWER_IS_BETTER);

    /**
     * 값이 클수록 좋은지(635, FE 오각형 그래프 색·문구 분기). 저축률만 HIGHER_IS_BETTER다. 고정지출·재량소비 비중은 "낮을수록
     * 여유"라는 일반 해석을 따른 서비스 초기값이며, 필수 지출이 많은 사용자에게는 중립일 수 있다.
     */
    public enum Direction {
        HIGHER_IS_BETTER, LOWER_IS_BETTER
    }

    private final String key;
    private final String displayName;
    private final double cap;
    private final Direction direction;

    ClusterFeature(String key, String displayName, double cap, Direction direction) {
        this.key = key;
        this.displayName = displayName;
        this.cap = cap;
        this.direction = direction;
    }

    public Direction direction() {
        return direction;
    }

    /** 특징 키로 방향을 찾는다. 모르는 키면 null(저장된 과거 JSON을 읽을 때). */
    public static Direction directionOf(String key) {
        for (ClusterFeature feature : values()) {
            if (feature.key.equals(key)) {
                return feature.direction;
            }
        }
        return null;
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
