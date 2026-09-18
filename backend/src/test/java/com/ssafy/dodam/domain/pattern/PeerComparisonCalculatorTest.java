package com.ssafy.dodam.domain.pattern;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.dodam.domain.pattern.dto.response.PatternInsight;
import com.ssafy.dodam.domain.pattern.dto.response.PeerComparisonResponse;
import com.ssafy.dodam.domain.pattern.repository.JdbcPatternFeatureReader.Demographics;
import com.ssafy.dodam.domain.pattern.service.AgeBand;
import com.ssafy.dodam.domain.pattern.service.ClusterAssigner.ClusterResult;
import com.ssafy.dodam.domain.pattern.service.ClusterFeature;
import com.ssafy.dodam.domain.pattern.service.ClusterModel;
import com.ssafy.dodam.domain.pattern.service.ClusterModelProvider;
import com.ssafy.dodam.domain.pattern.service.KMeansClusterer;
import com.ssafy.dodam.domain.pattern.service.PatternInsightPolicy;
import com.ssafy.dodam.domain.pattern.service.PatternProperties;
import com.ssafy.dodam.domain.pattern.service.PatternWindow;
import com.ssafy.dodam.domain.pattern.service.PeerComparisonCalculator;
import com.ssafy.dodam.domain.pattern.service.PeerDimension;
import com.ssafy.dodam.domain.pattern.service.RankBand;
import com.ssafy.dodam.domain.user.entity.Gender;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 632 동년배 비교·군집 내 위치의 순수 계산. 인프로세스 학습 경로({@link ClusterModelProvider#build})로 만든 모델을 쓰므로 아티팩트 v2와
 * 같은 격자 정의가 검증된다. 표본: 저축형 60명·소비형 60명, 나이대 20S/30S, 성별 교차.
 */
class PeerComparisonCalculatorTest {
    private static final LocalDate REFERENCE = LocalDate.of(2026, 9, 14);
    private static final PatternWindow WINDOW = PatternWindow.endingBefore(REFERENCE, 6);
    private final PatternProperties properties = new PatternProperties(6, 3, 30, 10, 0.2, 2, null, null, "", true, 30, 10);
    private final PeerComparisonCalculator calculator = new PeerComparisonCalculator(properties);

    /** 저축형(소비/소득 0.5~0.6, 저축률 0.25~0.35)과 소비형(0.85~0.95, 저축률 0~0.05). 순번에 따라 값을 조금씩 바꿔 분포를 만든다. */
    private static Fixture fixture() {
        List<double[]> vectors = new ArrayList<>();
        List<Long> userIds = new ArrayList<>();
        Map<Long, Demographics> demographics = new HashMap<>();
        long id = 1;
        for (int i = 0; i < 60; i++) {
            double t = i / 59.0;
            vectors.add(new double[] {0.5 + 0.1 * t, 0.25 + 0.1 * t, 0.6, 0.2, 0.02});
            userIds.add(id);
            demographics.put(id++, new Demographics(LocalDate.of(i % 2 == 0 ? 2000 : 1990, 5, 1), i % 4 < 2 ? "FEMALE" : "MALE"));
        }
        for (int i = 0; i < 60; i++) {
            double t = i / 59.0;
            vectors.add(new double[] {0.85 + 0.1 * t, 0.05 * (1 - t), 0.4, 0.5, 0.1 + 0.2 * t});
            userIds.add(id);
            demographics.put(id++, new Demographics(LocalDate.of(i % 2 == 0 ? 2000 : 1990, 5, 1), i % 4 < 2 ? "FEMALE" : "MALE"));
        }
        KMeansClusterer.Result result = new KMeansClusterer().cluster(vectors, 2);
        ClusterModel model = ClusterModelProvider.build(WINDOW, vectors, userIds, demographics, result, LocalDateTime.of(2026, 9, 14, 10, 0));
        return new Fixture(model, vectors);
    }

    private record Fixture(ClusterModel model, List<double[]> vectors) {
    }

    private static ClusterResult result(ClusterModel model, double[] vector) {
        ClusterModel.Assignment assignment = model.assign(vector);
        Map<ClusterFeature, Double> values = new EnumMap<>(ClusterFeature.class);
        for (ClusterFeature feature : ClusterFeature.ordered()) {
            values.put(feature, vector[feature.ordinal()]);
        }
        return new ClusterResult(model, model.cluster(assignment.clusterId()), assignment, values);
    }

    @Test
    void 인프로세스_모델은_분위_격자와_동년배_집단을_갖고_군집_내_위치를_계산한다() {
        Fixture f = fixture();
        ClusterModel model = f.model();

        assertThat(model.supportsPercentiles()).isTrue();
        assertThat(model.quantileGrid()).hasSize(21);
        assertThat(model.population().sampleSize()).isEqualTo(120);
        assertThat(model.population().clusterShare().values()).containsExactlyInAnyOrder(0.5, 0.5);
        // 20S·30S × (전체·MALE·FEMALE·군집 2종) + 성별 2 = 2 × 5 + 2 = 12
        assertThat(model.peerGroups()).hasSize(12);
        assertThat(model.peerGroup("20S", Gender.FEMALE, null)).isPresent();
        assertThat(model.peerGroup("20S", Gender.FEMALE, null).get().stats().sampleSize()).isEqualTo(30);
        assertThat(model.peerGroup("40S", null, null)).isEmpty();

        // 소비형 최상위(소비/소득 0.95) → 소비 군집 안에서 백분위 100, 저축형 중간(0.55) → 저축 군집 안에서 50 근처
        ClusterResult topSpender = result(model, new double[] {0.95, 0.0, 0.4, 0.5, 0.3});
        assertThat(model.percentileInCluster(topSpender.summary().clusterId(), ClusterFeature.SPENDING_TO_INCOME_RATIO, 0.95)).isEqualTo(100.0);
        ClusterResult midSaver = result(model, new double[] {0.55, 0.3, 0.6, 0.2, 0.02});
        assertThat(model.percentileInCluster(midSaver.summary().clusterId(), ClusterFeature.SPENDING_TO_INCOME_RATIO, 0.55))
                .isBetween(45.0, 55.0);
        // 격자 밖 값은 0 / 100
        assertThat(model.percentileInCluster(midSaver.summary().clusterId(), ClusterFeature.SPENDING_TO_INCOME_RATIO, 0.1)).isEqualTo(0.0);
    }

    @Test
    void 동년배_비교는_요청_축으로_계산하고_표본이_부족하면_넓힌다() {
        Fixture f = fixture();
        ClusterModel model = f.model();
        ClusterResult topSpender = result(model, new double[] {0.95, 0.0, 0.4, 0.5, 0.3});
        LocalDate birth = LocalDate.of(2000, 5, 1); // 20S

        // 20S 여성 30명 ≥ 최소 30 → 요청 축 그대로
        PeerComparisonResponse byAgeGender = calculator.calculate(11L, topSpender, birth, Gender.FEMALE, REFERENCE, PeerDimension.AGE_BAND_GENDER);
        assertThat(byAgeGender.unavailableReason()).isNull();
        assertThat(byAgeGender.peerGroup().dimension()).isEqualTo("AGE_BAND_GENDER");
        assertThat(byAgeGender.peerGroup().ageBand()).isEqualTo("20S");
        assertThat(byAgeGender.peerGroup().gender()).isEqualTo("FEMALE");
        assertThat(byAgeGender.peerGroup().sampleSize()).isEqualTo(30);
        assertThat(byAgeGender.peerGroup().fallbackFrom()).isNull();
        assertThat(byAgeGender.features()).hasSize(5);
        PeerComparisonResponse.PeerFeature spending = byAgeGender.features().get(0);
        assertThat(spending.feature()).isEqualTo("spendingToIncomeRatio");
        assertThat(spending.percentileInPeer()).isEqualTo(100.0);
        assertThat(spending.rankBand()).isEqualTo(RankBand.TOP_10.name());
        assertThat(spending.peerMedian()).isBetween(0.5, 0.95);
        assertThat(byAgeGender.clusterShare()).hasSize(2).anySatisfy(share -> {
            assertThat(share.mine()).isTrue();
            assertThat(share.peerRatio()).isEqualTo(0.5);
        });
        assertThat(byAgeGender.insights()).extracting(PatternInsight::code)
                .contains(PatternInsightPolicy.PEER_TOP_DECILE_SPENDING, PatternInsightPolicy.PEER_CLUSTER_SHARE);
        assertThat(byAgeGender.insights().get(0).message()).startsWith("같은 20대 여성 중 소득 대비 소비율이 상위 1%");
        assertThat(byAgeGender.insights()).filteredOn(i -> i.code().equals(PatternInsightPolicy.PEER_CLUSTER_SHARE))
                .first().satisfies(i -> assertThat(i.message()).contains("20대 여성 10명 중 5명이"));

        // 군집 × 나이대: 20S 소비 군집 30명 ≥ 최소 10 → 그대로. 구성비 문장은 만들지 않는다(집단이 곧 군집)
        PeerComparisonResponse byClusterAge = calculator.calculate(11L, topSpender, birth, Gender.FEMALE, REFERENCE, PeerDimension.CLUSTER_AGE_BAND);
        assertThat(byClusterAge.peerGroup().dimension()).isEqualTo("CLUSTER_AGE_BAND");
        assertThat(byClusterAge.peerGroup().clusterId()).isEqualTo(topSpender.summary().clusterId());
        assertThat(byClusterAge.peerGroup().sampleSize()).isEqualTo(30);
        assertThat(byClusterAge.insights()).extracting(PatternInsight::code).doesNotContain(PatternInsightPolicy.PEER_CLUSTER_SHARE);

        // 최소 표본을 50으로 올리면 20S 여성(30) → 20S(60)로 넓힌다
        PeerComparisonCalculator strict = new PeerComparisonCalculator(new PatternProperties(6, 3, 30, 10, 0.2, 2, null, null, "", true, 50, 10));
        PeerComparisonResponse widened = strict.calculate(11L, topSpender, birth, Gender.FEMALE, REFERENCE, PeerDimension.AGE_BAND_GENDER);
        assertThat(widened.unavailableReason()).isNull();
        assertThat(widened.peerGroup().dimension()).isEqualTo("AGE_BAND");
        assertThat(widened.peerGroup().gender()).isNull();
        assertThat(widened.peerGroup().sampleSize()).isEqualTo(60);
        assertThat(widened.peerGroup().fallbackFrom()).isEqualTo("AGE_BAND_GENDER");

        // 최소 표본 100이면 전체 모집단까지 넓히고 사유를 남긴다
        PeerComparisonCalculator stricter = new PeerComparisonCalculator(new PatternProperties(6, 3, 30, 10, 0.2, 2, null, null, "", true, 100, 10));
        PeerComparisonResponse population = stricter.calculate(11L, topSpender, birth, Gender.FEMALE, REFERENCE, PeerDimension.AGE_BAND);
        assertThat(population.peerGroup().dimension()).isEqualTo("ALL");
        assertThat(population.peerGroup().sampleSize()).isEqualTo(120);
        assertThat(population.unavailableReason()).isEqualTo(PeerComparisonResponse.PEER_GROUP_TOO_SMALL);
        assertThat(population.features()).isNotEmpty();
    }

    @Test
    void 인구통계가_없거나_격자_없는_모델이면_사유만_준다() {
        Fixture f = fixture();
        ClusterResult saver = result(f.model(), new double[] {0.55, 0.3, 0.6, 0.2, 0.02});

        PeerComparisonResponse noBirth = calculator.calculate(11L, saver, null, Gender.MALE, REFERENCE, PeerDimension.AGE_BAND_GENDER);
        assertThat(noBirth.unavailableReason()).isEqualTo(PeerComparisonResponse.DEMOGRAPHICS_MISSING);
        assertThat(noBirth.peerGroup()).isNull();
        assertThat(noBirth.features()).isEmpty();
        // 성별만 없으면 나이대 축은 된다
        assertThat(calculator.calculate(11L, saver, LocalDate.of(1990, 5, 1), null, REFERENCE, PeerDimension.AGE_BAND).peerGroup().dimension())
                .isEqualTo("AGE_BAND");
        assertThat(calculator.calculate(11L, saver, LocalDate.of(1990, 5, 1), null, REFERENCE, PeerDimension.GENDER).unavailableReason())
                .isEqualTo(PeerComparisonResponse.DEMOGRAPHICS_MISSING);

        // v1 형식(격자 없음) 모델
        ClusterModel v1 = new ClusterModel(f.model().modelVersion(), f.model().window(), f.model().populationSize(), f.model().means(),
                f.model().stds(), f.model().centroids(),
                f.model().clusters().stream().map(c -> new ClusterModel.ClusterSummary(c.clusterId(), c.label(), c.sampleSize(), c.stats())).toList(),
                f.model().trainedAt(), f.model().source(), f.model().trainedBy());
        assertThat(v1.supportsPercentiles()).isFalse();
        ClusterResult onV1 = new ClusterResult(v1, v1.cluster(saver.summary().clusterId()), saver.assignment(), saver.userValues());
        assertThat(calculator.calculate(11L, onV1, LocalDate.of(1990, 5, 1), Gender.MALE, REFERENCE, PeerDimension.AGE_BAND).unavailableReason())
                .isEqualTo(PeerComparisonResponse.MODEL_NOT_SUPPORTED);
        assertThat(calculator.calculateAll(11L, onV1, LocalDate.of(1990, 5, 1), Gender.MALE, REFERENCE)).hasSize(4)
                .doesNotContainKey(PeerDimension.ALL);
    }

    @Test
    void 나이대와_구간_경계() {
        assertThat(AgeBand.of(LocalDate.of(2007, 1, 1), REFERENCE)).isEqualTo("10S");
        assertThat(AgeBand.of(LocalDate.of(2006, 9, 14), REFERENCE)).isEqualTo("20S"); // 만 20세 당일
        assertThat(AgeBand.of(LocalDate.of(2006, 9, 15), REFERENCE)).isEqualTo("10S"); // 하루 전
        assertThat(AgeBand.of(LocalDate.of(1966, 9, 14), REFERENCE)).isEqualTo("60S_PLUS");
        assertThat(AgeBand.of(LocalDate.of(1966, 9, 15), REFERENCE)).isEqualTo("50S");
        assertThat(AgeBand.of(null, REFERENCE)).isNull();
        assertThat(AgeBand.display("60S_PLUS")).isEqualTo("60대 이상");
        assertThat(AgeBand.display("30S")).isEqualTo("30대");

        assertThat(RankBand.of(90)).isEqualTo(RankBand.TOP_10);
        assertThat(RankBand.of(89.9)).isEqualTo(RankBand.UPPER);
        assertThat(RankBand.of(75)).isEqualTo(RankBand.UPPER);
        assertThat(RankBand.of(74.9)).isEqualTo(RankBand.MIDDLE);
        assertThat(RankBand.of(25)).isEqualTo(RankBand.MIDDLE);
        assertThat(RankBand.of(24.9)).isEqualTo(RankBand.LOWER);
        assertThat(RankBand.of(9.9)).isEqualTo(RankBand.BOTTOM_10);
        assertThat(PeerDimension.parseOrDefault(null)).isEqualTo(PeerDimension.AGE_BAND_GENDER);
        assertThat(PeerDimension.parseOrDefault("cluster_age_band")).isEqualTo(PeerDimension.CLUSTER_AGE_BAND);
    }
}
