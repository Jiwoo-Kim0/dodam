package com.ssafy.dodam.domain.pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.pattern.entity.AnalysisMode;
import com.ssafy.dodam.domain.pattern.service.ClusterAssigner;
import com.ssafy.dodam.domain.pattern.service.ClusterFeature;
import com.ssafy.dodam.domain.pattern.service.ClusterModel;
import com.ssafy.dodam.domain.pattern.service.ClusterModelArtifact;
import com.ssafy.dodam.domain.pattern.service.ClusterModelArtifactLoader;
import com.ssafy.dodam.domain.pattern.service.ClusterModelProvider;
import com.ssafy.dodam.domain.pattern.service.FallbackReason;
import com.ssafy.dodam.domain.pattern.service.PatternClock;
import com.ssafy.dodam.domain.pattern.service.PatternFeatureCalculator;
import com.ssafy.dodam.domain.pattern.service.PatternFeatures;
import com.ssafy.dodam.domain.pattern.service.PatternProperties;
import com.ssafy.dodam.domain.pattern.service.PatternWindow;
import com.ssafy.dodam.domain.pattern.repository.JdbcPatternFeatureReader.LiquidityRow;
import com.ssafy.dodam.domain.pattern.repository.JdbcPatternFeatureReader.MonthlyPatternRow;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * Python 학습 아티팩트를 읽어 배정에 쓰는 경로. DB 없이 클래스패스 JSON으로 검증한다.
 */
class ClusterModelArtifactLoaderTest {

    private static final String TEST_ARTIFACT = "classpath:ml/test-spending-kmeans.json";
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void 아티팩트를_읽어_모델로_만들고_사용자_벡터를_배정한다() {
        PatternProperties properties = properties(TEST_ARTIFACT, false);
        ClusterModelArtifactLoader loader = new ClusterModelArtifactLoader(new DefaultResourceLoader(), json, properties);

        ClusterModel model = loader.load().orElseThrow();

        assertThat(model.source()).isEqualTo(ClusterModelArtifact.SOURCE);
        assertThat(model.trainedBy()).isEqualTo("python-sklearn-test");
        assertThat(model.modelVersion()).isEqualTo("spending-kmeans-v1");
        assertThat(model.clusterCount()).isEqualTo(2);
        assertThat(model.populationSize()).isEqualTo(40);
        assertThat(model.window()).isEqualTo(new PatternWindow(YearMonth.of(2026, 3), YearMonth.of(2026, 8)));
        assertThat(model.cluster(1).label()).isEqualTo("저축 중심형");
        assertThat(model.cluster(1).stats().get(ClusterFeature.SAVING_RATE).p95()).isEqualTo(0.34);

        // 저축형 사용자(소비/소득 .5, 저축률 .33, 고정 .47, 재량 .27, 변동 0) → 군집 1
        ClusterModel.Assignment saver = model.assign(new double[] {0.5, 0.3333, 0.4667, 0.2667, 0.0});
        assertThat(saver.clusterId()).isEqualTo(1);
        assertThat(saver.confidence()).isGreaterThan(0.5);
        ClusterModel.Assignment spender = model.assign(new double[] {0.9, 0.0, 0.35, 0.6, 0.08});
        assertThat(spender.clusterId()).isEqualTo(0);

        // 같은 인스턴스는 다시 읽지 않는다.
        assertThat(loader.load()).containsSame(model);
    }

    @Test
    void 아티팩트가_있으면_인프로세스_학습_없이_그_모델로_배정하고_프로필에_출처가_남는다() {
        PatternProperties properties = properties(TEST_ARTIFACT, false);
        ClusterModelArtifactLoader loader = new ClusterModelArtifactLoader(new DefaultResourceLoader(), json, properties);
        ClusterModelProvider provider = new ClusterModelProvider(loader, null, new PatternFeatureCalculator(), properties, new PatternClock());
        ClusterAssigner assigner = new ClusterAssigner(provider, properties);

        ClusterAssigner.Outcome outcome = assigner.evaluate(AnalysisMode.HYBRID, saverFeatures(), false);

        assertThat(outcome.assigned()).isTrue();
        assertThat(outcome.populationSize()).isEqualTo(40);
        assertThat(outcome.cluster().summary().label()).isEqualTo("저축 중심형");
        assertThat(outcome.cluster().model().source()).isEqualTo("ARTIFACT");
    }

    @Test
    void 아티팩트가_없고_인프로세스_학습이_꺼져_있으면_MODEL_NOT_AVAILABLE로_전환한다() {
        PatternProperties properties = properties("classpath:ml/does-not-exist.json", false);
        ClusterModelArtifactLoader loader = new ClusterModelArtifactLoader(new DefaultResourceLoader(), json, properties);
        ClusterModelProvider provider = new ClusterModelProvider(loader, null, new PatternFeatureCalculator(), properties, new PatternClock());

        assertThat(loader.load()).isEmpty();
        ClusterAssigner.Outcome outcome = new ClusterAssigner(provider, properties).evaluate(AnalysisMode.HYBRID, saverFeatures(), false);

        assertThat(outcome.assigned()).isFalse();
        assertThat(outcome.fallbackReason()).isEqualTo(FallbackReason.MODEL_NOT_AVAILABLE);
    }

    @Test
    void 위치가_비어_있거나_형식이_틀리면_비어_있는_결과다() {
        PatternProperties blank = properties("", true);
        assertThat(new ClusterModelArtifactLoader(new DefaultResourceLoader(), json, blank).load()).isEmpty();

        ClusterModelArtifact wrongOrder = new ClusterModelArtifact("v", "kmeans", null, null,
                new ClusterModelArtifact.Window("2026-03", "2026-08", 6),
                List.of("savingRate", "spendingToIncomeRatio", "fixedExpenseShare", "discretionaryShare", "spendingVolatility"),
                10, 1, null, new ClusterModelArtifact.Standardization(List.of(0.0, 0.0, 0.0, 0.0, 0.0), List.of(1.0, 1.0, 1.0, 1.0, 1.0)),
                List.of(List.of(0.0, 0.0, 0.0, 0.0, 0.0)), List.of());
        assertThatThrownBy(wrongOrder::toModel).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("특징 순서");
    }

    private static PatternProperties properties(String location, boolean trainInProcess) {
        return new PatternProperties(6, 3, 30, 10, 0.2, 4, null, null, location, trainInProcess);
    }

    private static PatternFeatures saverFeatures() {
        PatternWindow window = PatternWindow.endingBefore(LocalDate.of(2026, 9, 14), 6);
        List<MonthlyPatternRow> rows = List.of(
                row(2026, 3), row(2026, 4), row(2026, 5), row(2026, 6), row(2026, 7), row(2026, 8));
        return new PatternFeatureCalculator().calculate(window, rows, new LiquidityRow(6_000_000, 1), null);
    }

    /** 소득 300만, 소비 150만(고정 70만, 재량 40만), 저축 100만 → 벡터 (.5, .3333, .4667, .2667, 0). */
    private static MonthlyPatternRow row(int year, int month) {
        return new MonthlyPatternRow(1L, YearMonth.of(year, month), 3_000_000, 1_500_000, 700_000, 800_000, 400_000, 1_000_000, 0, 6);
    }
}
