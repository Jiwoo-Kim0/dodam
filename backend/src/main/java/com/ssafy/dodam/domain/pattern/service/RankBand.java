package com.ssafy.dodam.domain.pattern.service;

/**
 * 분위(0~100)를 화면 문구용 구간으로 바꾼다(632). 경계 90/75/25/10은 기획 문구 "상위 10%"에 맞춘 서비스 초기값이다.
 * 분위는 "그 값 이하인 표본의 비율"이라 TOP_10은 값이 큰 쪽 10%다 — 소비 비율이면 많이 쓰는 쪽, 저축률이면 많이 모으는 쪽이다.
 */
public enum RankBand {
    TOP_10,
    UPPER,
    MIDDLE,
    LOWER,
    BOTTOM_10;

    public static final double TOP_10_FROM = 90.0;
    public static final double UPPER_FROM = 75.0;
    public static final double LOWER_BELOW = 25.0;
    public static final double BOTTOM_10_BELOW = 10.0;

    public static RankBand of(double percentile) {
        if (percentile >= TOP_10_FROM) {
            return TOP_10;
        }
        if (percentile >= UPPER_FROM) {
            return UPPER;
        }
        if (percentile < BOTTOM_10_BELOW) {
            return BOTTOM_10;
        }
        if (percentile < LOWER_BELOW) {
            return LOWER;
        }
        return MIDDLE;
    }
}
