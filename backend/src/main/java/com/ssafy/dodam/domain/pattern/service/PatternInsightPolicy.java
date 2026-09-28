package com.ssafy.dodam.domain.pattern.service;

import com.ssafy.dodam.domain.pattern.dto.response.PatternInsight;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 군집 안 위치·동년배 위치를 문장으로 바꾸는 규칙(632). 임계값은 {@link RankBand} 경계(90/75/50/10)와 같고 서비스 초기값이다 —
 * 기획과 확정되면 바꾼다. 문장은 앱 문구 톤('-어요')을 따르고 내부 코드·영문을 쓰지 않는다(604·589 문구 정비 규칙).
 *
 * <p>"과소비 집단 중에서도 특히 많이 쓴다" = 배정 군집이 소비 집중형이고 소득 대비 소비율의 군집 안 백분위가 90 이상.
 * 백분위는 "그 값 이하인 표본의 비율"이라 소비율 90은 많이 쓰는 쪽 상위 10%, 저축률 10 미만은 적게 모으는 쪽 하위 10%다.
 */
public final class PatternInsightPolicy {
    public static final String CLUSTER_TOP_DECILE_SPENDING = "CLUSTER_TOP_DECILE_SPENDING";
    public static final String CLUSTER_BOTTOM_DECILE_SAVING = "CLUSTER_BOTTOM_DECILE_SAVING";
    public static final String CLUSTER_VOLATILITY_HIGH = "CLUSTER_VOLATILITY_HIGH";
    public static final String PEER_TOP_DECILE_SPENDING = "PEER_TOP_DECILE_SPENDING";
    public static final String PEER_ABOVE_MEDIAN_SPENDING = "PEER_ABOVE_MEDIAN_SPENDING";
    public static final String PEER_TOP_QUARTILE_SAVING = "PEER_TOP_QUARTILE_SAVING";
    public static final String PEER_VOLATILITY_HIGH = "PEER_VOLATILITY_HIGH";
    public static final String PEER_CLUSTER_SHARE = "PEER_CLUSTER_SHARE";

    static final double TOP_DECILE_FROM = RankBand.TOP_10_FROM;
    static final double TOP_QUARTILE_FROM = RankBand.UPPER_FROM;
    static final double ABOVE_MEDIAN_FROM = 50.0;
    static final double BOTTOM_DECILE_BELOW = RankBand.BOTTOM_10_BELOW;

    private PatternInsightPolicy() {
    }

    /**
     * 군집 안 위치 문장. 라벨이 무엇이든 소비율·저축률·변동성 세 특징만 본다 — 나머지 두 비중(고정·재량)은 "많다/적다"가 곧바로
     * 좋고 나쁨이 아니어서 문장으로 굳히지 않는다.
     */
    public static List<PatternInsight> clusterInsights(String clusterLabel, Map<ClusterFeature, Double> percentiles) {
        List<PatternInsight> insights = new ArrayList<>();
        Double spending = percentiles.get(ClusterFeature.SPENDING_TO_INCOME_RATIO);
        if (spending != null && spending >= TOP_DECILE_FROM) {
            insights.add(new PatternInsight(CLUSTER_TOP_DECILE_SPENDING, ClusterFeature.SPENDING_TO_INCOME_RATIO.key(), null, spending, null,
                    clusterLabel + " 중에서도 소득 대비 소비율이 상위 " + topPercent(spending) + "%예요."));
        }
        Double saving = percentiles.get(ClusterFeature.SAVING_RATE);
        if (saving != null && saving < BOTTOM_DECILE_BELOW) {
            insights.add(new PatternInsight(CLUSTER_BOTTOM_DECILE_SAVING, ClusterFeature.SAVING_RATE.key(), null, saving, null,
                    clusterLabel + " 중에서도 저축률이 하위 " + bottomPercent(saving) + "%예요."));
        }
        Double volatility = percentiles.get(ClusterFeature.SPENDING_VOLATILITY);
        if (volatility != null && volatility >= TOP_DECILE_FROM) {
            insights.add(new PatternInsight(CLUSTER_VOLATILITY_HIGH, ClusterFeature.SPENDING_VOLATILITY.key(), null, volatility, null,
                    clusterLabel + " 중에서도 월별 소비 변동이 상위 " + topPercent(volatility) + "%예요."));
        }
        return insights;
    }

    /**
     * 동년배 위치 문장.
     *
     * @param groupDisplay 집단 표기(예: "20대 여성", "30대", "전체 사용자")
     * @param percentiles  특징별 집단 안 백분위
     * @param myClusterId  사용자 군집 번호(구성비 문장용). null이면 구성비 문장을 만들지 않는다
     * @param myLabel      사용자 군집 라벨
     * @param myShare      집단 안 그 군집 비율(0~1). null이면 구성비 문장을 만들지 않는다
     */
    public static List<PatternInsight> peerInsights(String groupDisplay, Map<ClusterFeature, Double> percentiles, Integer myClusterId,
            String myLabel, Double myShare) {
        List<PatternInsight> insights = new ArrayList<>();
        Double spending = percentiles.get(ClusterFeature.SPENDING_TO_INCOME_RATIO);
        if (spending != null && spending >= TOP_DECILE_FROM) {
            insights.add(new PatternInsight(PEER_TOP_DECILE_SPENDING, ClusterFeature.SPENDING_TO_INCOME_RATIO.key(), null, spending, null,
                    "같은 " + groupDisplay + " 중 소득 대비 소비율이 상위 " + topPercent(spending) + "%예요."));
        } else if (spending != null && spending >= ABOVE_MEDIAN_FROM) {
            insights.add(new PatternInsight(PEER_ABOVE_MEDIAN_SPENDING, ClusterFeature.SPENDING_TO_INCOME_RATIO.key(), null, spending, null,
                    "같은 " + groupDisplay + "의 절반보다 소득 대비 소비율이 높아요(상위 " + topPercent(spending) + "%)."));
        }
        Double saving = percentiles.get(ClusterFeature.SAVING_RATE);
        if (saving != null && saving >= TOP_QUARTILE_FROM) {
            insights.add(new PatternInsight(PEER_TOP_QUARTILE_SAVING, ClusterFeature.SAVING_RATE.key(), null, saving, null,
                    "같은 " + groupDisplay + " 중 저축률이 상위 " + topPercent(saving) + "%예요."));
        }
        Double volatility = percentiles.get(ClusterFeature.SPENDING_VOLATILITY);
        if (volatility != null && volatility >= TOP_DECILE_FROM) {
            insights.add(new PatternInsight(PEER_VOLATILITY_HIGH, ClusterFeature.SPENDING_VOLATILITY.key(), null, volatility, null,
                    "같은 " + groupDisplay + " 중 월별 소비 변동이 상위 " + topPercent(volatility) + "%예요."));
        }
        if (myClusterId != null && myShare != null && myLabel != null) {
            long outOfTen = Math.round(myShare * 10);
            String share = outOfTen >= 1 ? groupDisplay + " 10명 중 " + outOfTen + "명이 " : groupDisplay + " 중 " + Math.round(myShare * 100) + "%가 ";
            insights.add(new PatternInsight(PEER_CLUSTER_SHARE, null, myClusterId, null, myShare,
                    share + myLabel + "이고, 당신도 그중 한 명이에요."));
        }
        return insights;
    }

    /** 상위 N% 표기: 백분위 92 → 8. 0이 되지 않게 최소 1. */
    static long topPercent(double percentile) {
        return Math.max(1, Math.round(100 - percentile));
    }

    /** 하위 N% 표기: 백분위 4 → 4. 최소 1. */
    static long bottomPercent(double percentile) {
        return Math.max(1, Math.round(percentile));
    }
}
