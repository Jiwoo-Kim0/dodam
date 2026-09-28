package com.ssafy.dodam.domain.pattern;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.dodam.domain.pattern.service.ClusterFeature;
import com.ssafy.dodam.domain.pattern.service.ClusterModel;
import com.ssafy.dodam.domain.pattern.service.ClusterModel.GroupStats;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 632 분위 격자 → 백분위 변환. 선형 보간, 평탄 구간 가운데값, 격자 밖 0/100. */
class StatsPercentileRankTest {
    private static final double[] GRID = ClusterModel.DEFAULT_QUANTILE_GRID;

    private static GroupStats group(double... quantiles) {
        return new GroupStats(100, Map.of(ClusterFeature.SAVING_RATE, quantiles), Map.of());
    }

    private static ClusterModel modelWithGrid() {
        return new ClusterModel("v", null, 0, new double[5], new double[5], new double[][] {new double[5]},
                java.util.List.of(new ClusterModel.ClusterSummary(0, "x", 0, Map.of(), Map.of())), null, "T", "t", GRID, java.util.List.of(), null);
    }

    @Test
    void 선형_보간과_격자_밖() {
        // 0.00, 0.05, …, 1.00 (격자 값이 백분위/100과 같게)
        double[] q = new double[21];
        for (int i = 0; i < 21; i++) {
            q[i] = i * 0.05;
        }
        ClusterModel model = modelWithGrid();
        GroupStats g = group(q);
        assertThat(model.percentileIn(g, ClusterFeature.SAVING_RATE, 0.5)).isEqualTo(50.0);
        assertThat(model.percentileIn(g, ClusterFeature.SAVING_RATE, 0.525)).isEqualTo(52.5);
        assertThat(model.percentileIn(g, ClusterFeature.SAVING_RATE, 0.0)).isEqualTo(0.0);
        assertThat(model.percentileIn(g, ClusterFeature.SAVING_RATE, -0.1)).isEqualTo(0.0);
        assertThat(model.percentileIn(g, ClusterFeature.SAVING_RATE, 1.0)).isEqualTo(100.0);
        assertThat(model.percentileIn(g, ClusterFeature.SAVING_RATE, 2.0)).isEqualTo(100.0);
        assertThat(model.quantileValue(g, ClusterFeature.SAVING_RATE, 25)).isEqualTo(0.25);
        assertThat(model.quantileValue(g, ClusterFeature.SAVING_RATE, 50)).isEqualTo(0.5);
        assertThat(model.quantileValue(g, ClusterFeature.SAVING_RATE, 75)).isEqualTo(0.75);
    }

    @Test
    void 평탄_구간은_가운데_백분위를_준다() {
        // 저축률 0이 표본 40%(격자 0~40이 0.0), 그 뒤 선형
        double[] q = new double[21];
        for (int i = 0; i <= 8; i++) {
            q[i] = 0.0;
        }
        for (int i = 9; i < 21; i++) {
            q[i] = (i - 8) * 0.05;
        }
        ClusterModel model = modelWithGrid();
        GroupStats g = group(q);
        // 0원 저축 사용자는 "상위 60%"도 "하위 0%"도 아니라 0~40의 가운데인 20
        assertThat(model.percentileIn(g, ClusterFeature.SAVING_RATE, 0.0)).isEqualTo(20.0);
        assertThat(model.percentileIn(g, ClusterFeature.SAVING_RATE, 0.05)).isEqualTo(45.0);
        assertThat(model.percentileIn(g, ClusterFeature.SAVING_RATE, 0.025)).isEqualTo(42.5);
        // 집단·격자 없음
        assertThat(model.percentileIn(null, ClusterFeature.SAVING_RATE, 0.1)).isNull();
        assertThat(model.percentileIn(new GroupStats(1, Map.of(), Map.of()), ClusterFeature.SAVING_RATE, 0.1)).isNull();
    }
}
