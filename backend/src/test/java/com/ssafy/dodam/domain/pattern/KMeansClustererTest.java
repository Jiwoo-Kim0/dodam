package com.ssafy.dodam.domain.pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.dodam.domain.pattern.service.KMeansClusterer;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class KMeansClustererTest {

    @Test
    void 잘_분리된_세_집단을_같은_군집으로_묶고_결정적으로_동작한다() {
        List<double[]> vectors = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            vectors.add(new double[] {0.5 + i * 0.001, 0.30, 0.45, 0.25, 0.05});
            vectors.add(new double[] {0.9 + i * 0.001, 0.00, 0.30, 0.60, 0.10});
            vectors.add(new double[] {0.6 + i * 0.001, 0.05, 0.40, 0.30, 0.90});
        }

        KMeansClusterer.Result first = new KMeansClusterer().cluster(vectors, 3);
        KMeansClusterer.Result second = new KMeansClusterer().cluster(vectors, 3);

        assertThat(first.assignments()).containsExactly(second.assignments());
        for (int group = 0; group < 3; group++) {
            int expected = first.assignments()[group];
            for (int i = 0; i < 10; i++) {
                assertThat(first.assignments()[i * 3 + group]).isEqualTo(expected);
            }
        }
        assertThat(first.assignments()[0]).isNotEqualTo(first.assignments()[1]);
        assertThat(first.assignments()[1]).isNotEqualTo(first.assignments()[2]);
        assertThat(first.iterations()).isLessThanOrEqualTo(KMeansClusterer.MAX_ITERATIONS);
    }

    @Test
    void 표준편차가_0인_차원도_표준화에서_0으로_나누지_않는다() {
        List<double[]> vectors = List.of(new double[] {1.0, 5.0}, new double[] {2.0, 5.0}, new double[] {9.0, 5.0});

        KMeansClusterer.Result result = new KMeansClusterer().cluster(vectors, 2);

        assertThat(result.stds()[1]).isEqualTo(1.0);
        assertThat(result.assignments()[0]).isEqualTo(result.assignments()[1]);
        assertThat(result.assignments()[2]).isNotEqualTo(result.assignments()[0]);
    }

    @Test
    void 군집_수가_표본_수를_넘으면_거부한다() {
        assertThatThrownBy(() -> new KMeansClusterer().cluster(List.of(new double[] {1.0}), 2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KMeansClusterer().cluster(List.of(), 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
