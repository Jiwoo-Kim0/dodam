package com.ssafy.dodam.domain.pattern.service;

import com.ssafy.dodam.domain.pattern.repository.JdbcPatternFeatureReader;
import com.ssafy.dodam.domain.pattern.repository.JdbcPatternFeatureReader.MonthlyPatternRow;
import com.ssafy.dodam.domain.pattern.service.ClusterModel.ClusterSummary;
import com.ssafy.dodam.domain.pattern.service.ClusterModel.FeatureStats;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 소비 유형 군집 모델을 학습하고 메모리에 재사용한다(518).
 *
 * <p>학습 입력은 전체 사용자의 월별 집계에서 만든 비율 특징이다(거래 원문·식별정보 없음). 4,000명 × 6개월 집계를 매 요청마다
 * 다시 학습할 이유가 없어 `app.pattern.model-ttl` 동안 같은 기간의 모델을 재사용한다. `forceRefresh` 요청은 모델도 다시 학습한다.
 *
 * <p>학습 가능한 사용자 수가 `min-population`(또는 군집 수)보다 적으면 모델 없이 표본 수만 돌려준다. 호출자는 이를
 * {@link FallbackReason#INSUFFICIENT_POPULATION}으로 처리한다. 이 결과도 TTL 동안 캐시한다(빈 모집단을 반복 조회하지 않기 위해).
 */
@Component
public class ClusterModelProvider {

    public static final String MODEL_VERSION = "spending-kmeans-v1";

    private static final Logger log = LoggerFactory.getLogger(ClusterModelProvider.class);

    private final JdbcPatternFeatureReader reader;
    private final PatternFeatureCalculator calculator;
    private final PatternProperties properties;
    private final PatternClock clock;
    private final KMeansClusterer clusterer = new KMeansClusterer();
    private final AtomicReference<Cached> cache = new AtomicReference<>();

    public ClusterModelProvider(JdbcPatternFeatureReader reader, PatternFeatureCalculator calculator,
            PatternProperties properties, PatternClock clock) {
        this.reader = reader;
        this.calculator = calculator;
        this.properties = properties;
        this.clock = clock;
    }

    public Lookup lookup(PatternWindow window, boolean forceRefresh) {
        LocalDateTime now = clock.now();
        Cached cached = cache.get();
        if (!forceRefresh && cached != null && cached.window().equals(window)
                && cached.builtAt().plus(properties.modelTtl()).isAfter(now)) {
            return cached.lookup();
        }
        Lookup fresh = train(window, now);
        cache.set(new Cached(window, fresh, now));
        return fresh;
    }

    /** 테스트·운영 점검용. 다음 조회에서 다시 학습한다. */
    public void invalidate() {
        cache.set(null);
    }

    private Lookup train(PatternWindow window, LocalDateTime now) {
        List<MonthlyPatternRow> rows = reader.readPopulationMonthly(window.startDate(), window.endDate());
        Map<Long, List<MonthlyPatternRow>> byUser = new LinkedHashMap<>();
        for (MonthlyPatternRow row : rows) {
            byUser.computeIfAbsent(row.userId(), key -> new ArrayList<>()).add(row);
        }
        List<double[]> vectors = new ArrayList<>();
        for (List<MonthlyPatternRow> userRows : byUser.values()) {
            calculator.clusterVector(window, userRows, properties.minUserMonths()).ifPresent(vectors::add);
        }
        int populationSize = vectors.size();
        if (populationSize < properties.minPopulation() || populationSize < properties.clusterCount()) {
            log.info("Pattern cluster model skipped: population={} min={} window={}~{}",
                    populationSize, properties.minPopulation(), window.start(), window.end());
            return new Lookup(null, populationSize);
        }
        KMeansClusterer.Result result = clusterer.cluster(vectors, properties.clusterCount());
        ClusterModel model = build(window, vectors, result, now);
        log.info("Pattern cluster model trained: version={} population={} clusters={} iterations={} window={}~{}",
                MODEL_VERSION, populationSize, model.clusterCount(), result.iterations(), window.start(), window.end());
        return new Lookup(model, populationSize);
    }

    private static ClusterModel build(PatternWindow window, List<double[]> vectors, KMeansClusterer.Result result,
            LocalDateTime now) {
        int k = result.clusterCount();
        int dimension = ClusterFeature.dimension();
        double[][] rawCentroids = new double[k][dimension];
        for (int c = 0; c < k; c++) {
            for (int d = 0; d < dimension; d++) {
                rawCentroids[c][d] = result.centroids()[c][d] * result.stds()[d] + result.means()[d];
            }
        }
        List<String> labels = ClusterLabelPolicy.label(rawCentroids);

        List<List<double[]>> members = new ArrayList<>();
        for (int c = 0; c < k; c++) {
            members.add(new ArrayList<>());
        }
        for (int i = 0; i < vectors.size(); i++) {
            members.get(result.assignments()[i]).add(vectors.get(i));
        }

        List<ClusterSummary> summaries = new ArrayList<>(k);
        for (int c = 0; c < k; c++) {
            Map<ClusterFeature, FeatureStats> stats = new EnumMap<>(ClusterFeature.class);
            for (ClusterFeature feature : ClusterFeature.ordered()) {
                int d = feature.ordinal();
                double[] column = members.get(c).stream().mapToDouble(v -> v[d]).toArray();
                stats.put(feature, new FeatureStats(
                        Stats.round4(rawCentroids[c][d]),
                        Stats.round4(column.length == 0 ? 0.0 : Stats.percentile(column, 0.0)),
                        Stats.round4(column.length == 0 ? 0.0 : Stats.percentile(column, 1.0)),
                        Stats.round4(Stats.percentile(column, 0.05)),
                        Stats.round4(Stats.percentile(column, 0.95)),
                        Stats.round4(Stats.mean(column)),
                        Stats.round4(Stats.std(column))));
            }
            summaries.add(new ClusterSummary(c, labels.get(c), members.get(c).size(), stats));
        }
        return new ClusterModel(MODEL_VERSION, window, vectors.size(), result.means(), result.stds(), result.centroids(),
                summaries, now);
    }

    private record Cached(PatternWindow window, Lookup lookup, LocalDateTime builtAt) {
    }

    /** 모델(모집단 부족이면 null)과 학습 가능했던 표본 수. */
    public record Lookup(ClusterModel model, int populationSize) {
        public boolean hasModel() {
            return model != null;
        }
    }
}
