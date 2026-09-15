package com.ssafy.dodam.domain.pattern;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.dodam.domain.pattern.service.ClusterLabelPolicy;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClusterLabelPolicyTest {

    // 차원 순서: 소비/소득, 저축률, 고정지출 비중, 재량소비 비중, 변동성
    @Test
    void 중심값이_가장_큰_군집부터_우선순위대로_라벨을_붙이고_마지막은_균형형이다() {
        double[][] centroids = {
                {0.90, 0.00, 0.30, 0.60, 0.10}, // 소비 집중
                {0.50, 0.33, 0.47, 0.27, 0.02}, // 저축 중심
                {0.67, 0.17, 0.45, 0.25, 0.05}, // 균형
                {0.63, 0.07, 0.40, 0.35, 0.37}, // 변동
        };

        List<String> labels = ClusterLabelPolicy.label(centroids);

        assertThat(labels).containsExactly("소비 집중형", "저축 중심형", ClusterLabelPolicy.BALANCED_LABEL, "소비 변동형");
        assertThat(labels).doesNotHaveDuplicates();
    }

    @Test
    void 군집이_둘이면_저축_중심형과_균형형이다() {
        List<String> labels = ClusterLabelPolicy.label(new double[][] {
                {0.7, 0.05, 0.4, 0.3, 0.1},
                {0.5, 0.30, 0.4, 0.3, 0.1},
        });

        assertThat(labels).containsExactly(ClusterLabelPolicy.BALANCED_LABEL, "저축 중심형");
    }

    @Test
    void 후보보다_군집이_많으면_번호_라벨로_채운다() {
        double[][] centroids = new double[8][5];
        for (int c = 0; c < 8; c++) {
            for (int d = 0; d < 5; d++) {
                centroids[c][d] = (c * 7 + d * 3) % 11 / 10.0;
            }
        }

        List<String> labels = ClusterLabelPolicy.label(centroids);

        assertThat(labels).hasSize(8).doesNotHaveDuplicates().contains(ClusterLabelPolicy.BALANCED_LABEL)
                .anyMatch(label -> label.startsWith("소비 유형 "));
    }
}
