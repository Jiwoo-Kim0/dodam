package com.ssafy.dodam.domain.pattern.service;

import com.ssafy.dodam.domain.pattern.repository.JdbcPatternFeatureReader;
import com.ssafy.dodam.domain.pattern.repository.JdbcPatternFeatureReader.Demographics;
import com.ssafy.dodam.domain.pattern.repository.JdbcPatternFeatureReader.MonthlyPatternRow;
import com.ssafy.dodam.domain.pattern.service.ClusterModel.ClusterSummary;
import com.ssafy.dodam.domain.pattern.service.ClusterModel.FeatureStats;
import com.ssafy.dodam.domain.pattern.service.ClusterModel.GroupStats;
import com.ssafy.dodam.domain.pattern.service.ClusterModel.PeerGroup;
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
 * 배정에 쓸 소비 유형 군집 모델을 고른다(518).
 *
 * <p>순서: (1) Python 학습 스크립트가 만든 아티팩트({@link ClusterModelArtifactLoader}, `app.pattern.model-location`) →
 * (2) 아티팩트가 없고 `train-in-process=true`면 Java 안에서 학습 → (3) 둘 다 아니면 모델 없음.
 *
 * <p>인프로세스 학습은 개발·시연 안전장치다. 입력은 전체 사용자의 월별 집계에서 만든 비율 특징이고(거래 원문·식별정보 없음),
 * `model-ttl` 동안 같은 기간의 모델을 재사용한다. `forceRefresh` 요청은 다시 학습한다. 학습 가능한 사용자 수가 `min-population`
 * (또는 군집 수)보다 적으면 모델 없이 표본 수만 돌려주고 호출자가 {@link FallbackReason#INSUFFICIENT_POPULATION}으로 처리한다.
 * 이 결과도 TTL 동안 캐시한다.
 */
@Component
public class ClusterModelProvider {

    public static final String MODEL_VERSION = "spending-kmeans-v2";
    static final String IN_PROCESS_TRAINER = "java-kmeans";

    private static final Logger log = LoggerFactory.getLogger(ClusterModelProvider.class);

    private final ClusterModelArtifactLoader artifacts;
    private final JdbcPatternFeatureReader reader;
    private final PatternFeatureCalculator calculator;
    private final PatternProperties properties;
    private final PatternClock clock;
    private final KMeansClusterer clusterer = new KMeansClusterer();
    private final AtomicReference<Cached> cache = new AtomicReference<>();

    public ClusterModelProvider(ClusterModelArtifactLoader artifacts, JdbcPatternFeatureReader reader,
            PatternFeatureCalculator calculator, PatternProperties properties, PatternClock clock) {
        this.artifacts = artifacts;
        this.reader = reader;
        this.calculator = calculator;
        this.properties = properties;
        this.clock = clock;
    }

    public Lookup lookup(PatternWindow window, boolean forceRefresh) {
        java.util.Optional<ClusterModel> artifact = artifacts.load();
        if (artifact.isPresent()) {
            return new Lookup(artifact.get(), artifact.get().populationSize(), null);
        }
        if (!properties.trainInProcess()) {
            return new Lookup(null, 0, FallbackReason.MODEL_NOT_AVAILABLE);
        }
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
        List<Long> userIds = new ArrayList<>();
        for (Map.Entry<Long, List<MonthlyPatternRow>> entry : byUser.entrySet()) {
            calculator.clusterVector(window, entry.getValue(), properties.minUserMonths()).ifPresent(vector -> {
                vectors.add(vector);
                userIds.add(entry.getKey());
            });
        }
        int populationSize = vectors.size();
        if (populationSize < properties.minPopulation() || populationSize < properties.clusterCount()) {
            log.info("Pattern cluster model skipped: population={} min={} window={}~{}",
                    populationSize, properties.minPopulation(), window.start(), window.end());
            return new Lookup(null, populationSize, FallbackReason.INSUFFICIENT_POPULATION);
        }
        KMeansClusterer.Result result = clusterer.cluster(vectors, properties.clusterCount());
        // 632: 동년배 집단 통계는 학습 표본의 생년월일·성별로 만든다. 나이대는 학습 기준일(창의 다음 달 1일 = 오늘) 기준.
        Map<Long, Demographics> demographics = reader.readDemographics();
        ClusterModel model = build(window, vectors, userIds, demographics, result, now);
        log.info("Pattern cluster model trained in process: version={} population={} clusters={} peerGroups={} iterations={} window={}~{}",
                MODEL_VERSION, populationSize, model.clusterCount(), model.peerGroups().size(), result.iterations(),
                window.start(), window.end());
        return new Lookup(model, populationSize, null);
    }

    /**
     * 학습 결과를 모델로 만든다. v1 통계(p5·p95·평균·표준편차)에 632의 분위 격자·동년배 집단·모집단 통계를 더한다.
     * Python `train_cluster_model.py`의 `build_artifact`와 같은 정의라 아티팩트 모델과 인프로세스 모델의 응답 형태가 같다.
     */
    public static ClusterModel build(PatternWindow window, List<double[]> vectors, List<Long> userIds, Map<Long, Demographics> demographics,
            KMeansClusterer.Result result, LocalDateTime now) {
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
            summaries.add(new ClusterSummary(c, labels.get(c), members.get(c).size(), stats, quantiles(members.get(c))));
        }

        // 전체 모집단
        int[] assignments = result.assignments();
        GroupStats population = groupStats(vectors, indices(vectors.size(), i -> true), assignments, k);

        // 동년배 집단: 나이대 × 성별(성별 null = 나이대 전체), 성별만, 군집 × 나이대. 학습 기준일은 창의 다음 달 1일이다.
        java.time.LocalDate referenceDate = window.end().plusMonths(1).atDay(1);
        String[] ageBands = new String[vectors.size()];
        String[] genders = new String[vectors.size()];
        for (int i = 0; i < vectors.size(); i++) {
            Demographics d = userIds == null || demographics == null ? null : demographics.get(userIds.get(i));
            ageBands[i] = d == null ? null : AgeBand.of(d.birthDate(), referenceDate);
            genders[i] = d == null ? null : d.gender();
        }
        List<PeerGroup> peerGroups = new ArrayList<>();
        for (String band : AgeBand.ORDERED) {
            addGroup(peerGroups, vectors, assignments, k, band, null, null,
                    indices(vectors.size(), i -> band.equals(ageBands[i])));
            for (String gender : List.of("MALE", "FEMALE")) {
                addGroup(peerGroups, vectors, assignments, k, band, gender, null,
                        indices(vectors.size(), i -> band.equals(ageBands[i]) && gender.equals(genders[i])));
            }
            for (int c = 0; c < k; c++) {
                int clusterId = c;
                addGroup(peerGroups, vectors, assignments, k, band, null, clusterId,
                        indices(vectors.size(), i -> band.equals(ageBands[i]) && assignments[i] == clusterId));
            }
        }
        for (String gender : List.of("MALE", "FEMALE")) {
            addGroup(peerGroups, vectors, assignments, k, null, gender, null, indices(vectors.size(), i -> gender.equals(genders[i])));
        }
        return new ClusterModel(MODEL_VERSION, window, vectors.size(), result.means(), result.stds(), result.centroids(),
                summaries, now, ClusterModel.SOURCE_IN_PROCESS, IN_PROCESS_TRAINER, ClusterModel.DEFAULT_QUANTILE_GRID.clone(),
                peerGroups, population);
    }

    private static void addGroup(List<PeerGroup> into, List<double[]> vectors, int[] assignments, int k, String ageBand, String gender,
            Integer clusterId, int[] indices) {
        if (indices.length == 0) {
            return;
        }
        into.add(new PeerGroup(ageBand, gender, clusterId, groupStats(vectors, indices, assignments, k)));
    }

    private static int[] indices(int size, java.util.function.IntPredicate include) {
        return java.util.stream.IntStream.range(0, size).filter(include).toArray();
    }

    private static GroupStats groupStats(List<double[]> vectors, int[] indices, int[] assignments, int k) {
        List<double[]> members = new ArrayList<>(indices.length);
        int[] counts = new int[k];
        for (int i : indices) {
            members.add(vectors.get(i));
            counts[assignments[i]]++;
        }
        Map<Integer, Double> share = new LinkedHashMap<>();
        for (int c = 0; c < k; c++) {
            share.put(c, indices.length == 0 ? 0.0 : Stats.round4((double) counts[c] / indices.length));
        }
        return new GroupStats(indices.length, quantiles(members), share);
    }

    /** 특징별 분위 격자 값({@link ClusterModel#DEFAULT_QUANTILE_GRID}). 표본이 없으면 빈 맵. */
    static Map<ClusterFeature, double[]> quantiles(List<double[]> members) {
        if (members.isEmpty()) {
            return Map.of();
        }
        Map<ClusterFeature, double[]> result = new EnumMap<>(ClusterFeature.class);
        for (ClusterFeature feature : ClusterFeature.ordered()) {
            int d = feature.ordinal();
            double[] column = members.stream().mapToDouble(v -> v[d]).toArray();
            double[] values = new double[ClusterModel.DEFAULT_QUANTILE_GRID.length];
            for (int q = 0; q < values.length; q++) {
                values[q] = Stats.round4(Stats.percentile(column, ClusterModel.DEFAULT_QUANTILE_GRID[q] / 100.0));
            }
            result.put(feature, values);
        }
        return result;
    }

    private record Cached(PatternWindow window, Lookup lookup, LocalDateTime builtAt) {
    }

    /**
     * @param model             배정에 쓸 모델. 없으면 null
     * @param populationSize    모델 학습 표본 수(모델이 없으면 학습 가능했던 표본 수, 알 수 없으면 0)
     * @param unavailableReason 모델이 없는 사유. 모델이 있으면 null
     */
    public record Lookup(ClusterModel model, int populationSize, FallbackReason unavailableReason) {
        public boolean hasModel() {
            return model != null;
        }
    }
}
