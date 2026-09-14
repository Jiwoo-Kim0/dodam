package com.ssafy.dodam.domain.pattern.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 군집 번호에 사람이 읽을 라벨을 붙인다(518 군집 메타데이터).
 *
 * <p>K-Means 번호는 학습마다 뒤바뀔 수 있어 그대로 노출하면 뜻이 없다. 원 단위 중심점을 보고 결정적으로 이름을 정한다:
 * 우선순위 순으로 (특징, 라벨) 쌍을 돌며 **아직 이름이 없는 군집 중 그 특징의 중심값이 가장 큰 군집**에 라벨을 준다.
 * 마지막 남은 하나는 "균형형"이다. 군집 수가 후보보다 많으면 "소비 유형 N"으로 채운다.
 *
 * <p>라벨은 운영자 부여 성격(ERD 주석)이라 50자 안에서 바꿔도 저장 구조에는 영향이 없다.
 */
public final class ClusterLabelPolicy {

    public static final String BALANCED_LABEL = "균형형";

    /** 우선순위 순. 저축률이 가장 높은 군집이 먼저 "저축 중심형"을 받는다. */
    static final List<Map.Entry<ClusterFeature, String>> PRIORITY = List.of(
            Map.entry(ClusterFeature.SAVING_RATE, "저축 중심형"),
            Map.entry(ClusterFeature.SPENDING_VOLATILITY, "소비 변동형"),
            Map.entry(ClusterFeature.SPENDING_TO_INCOME_RATIO, "소비 집중형"),
            Map.entry(ClusterFeature.DISCRETIONARY_SHARE, "재량소비 집중형"),
            Map.entry(ClusterFeature.FIXED_EXPENSE_SHARE, "고정지출 집중형")
    );

    private ClusterLabelPolicy() {
    }

    /**
     * @param rawCentroids 원 단위 군집 중심 [k][d] (d 순서는 {@link ClusterFeature})
     * @return 군집 번호 순 라벨. 항목마다 유일하다
     */
    public static List<String> label(double[][] rawCentroids) {
        int k = rawCentroids.length;
        String[] labels = new String[k];
        Set<Integer> named = new HashSet<>();
        int remainingForBalanced = 1;
        for (Map.Entry<ClusterFeature, String> candidate : PRIORITY) {
            if (k - named.size() <= remainingForBalanced) {
                break;
            }
            int best = -1;
            double bestValue = -Double.MAX_VALUE;
            for (int c = 0; c < k; c++) {
                if (named.contains(c)) {
                    continue;
                }
                double value = rawCentroids[c][candidate.getKey().ordinal()];
                if (value > bestValue) {
                    bestValue = value;
                    best = c;
                }
            }
            labels[best] = candidate.getValue();
            named.add(best);
        }
        int extra = 1;
        List<String> result = new ArrayList<>(k);
        boolean balancedUsed = false;
        for (int c = 0; c < k; c++) {
            if (labels[c] != null) {
                result.add(labels[c]);
            } else if (!balancedUsed) {
                result.add(BALANCED_LABEL);
                balancedUsed = true;
            } else {
                result.add("소비 유형 " + extra++);
            }
        }
        return result;
    }
}
