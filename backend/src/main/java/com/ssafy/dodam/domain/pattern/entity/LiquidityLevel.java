package com.ssafy.dodam.domain.pattern.entity;

/**
 * ERD `pattern_analysis.liquidity_level`. 입출금(DEMAND_DEPOSIT) 활성 계좌 잔액이 월평균 소비의 몇 달분인지로 정한다.
 *
 * <pre>
 * runwayMonths = liquidBalance / monthlyAverageSpending
 * HIGH   : 3개월 이상
 * MEDIUM : 1개월 이상 3개월 미만
 * LOW    : 1개월 미만
 * </pre>
 *
 * 월평균 소비가 0이면 잔액이 있을 때 HIGH, 없을 때 LOW다. 계좌 정보가 전혀 없으면 null로 남긴다.
 */
public enum LiquidityLevel {
    HIGH,
    MEDIUM,
    LOW;

    public static final double HIGH_RUNWAY_MONTHS = 3.0;
    public static final double MEDIUM_RUNWAY_MONTHS = 1.0;

    public static LiquidityLevel ofRunway(Double runwayMonths, long liquidBalance) {
        if (runwayMonths == null) {
            return liquidBalance > 0 ? HIGH : LOW;
        }
        if (runwayMonths >= HIGH_RUNWAY_MONTHS) {
            return HIGH;
        }
        if (runwayMonths >= MEDIUM_RUNWAY_MONTHS) {
            return MEDIUM;
        }
        return LOW;
    }
}
