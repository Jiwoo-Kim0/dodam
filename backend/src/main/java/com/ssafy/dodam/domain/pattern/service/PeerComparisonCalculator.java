package com.ssafy.dodam.domain.pattern.service;

import com.ssafy.dodam.domain.pattern.dto.response.PatternInsight;
import com.ssafy.dodam.domain.pattern.dto.response.PeerComparisonResponse;
import com.ssafy.dodam.domain.pattern.dto.response.PeerComparisonResponse.ClusterShare;
import com.ssafy.dodam.domain.pattern.dto.response.PeerComparisonResponse.PeerFeature;
import com.ssafy.dodam.domain.pattern.service.ClusterAssigner.ClusterResult;
import com.ssafy.dodam.domain.pattern.service.ClusterModel.GroupStats;
import com.ssafy.dodam.domain.pattern.service.ClusterModel.PeerGroup;
import com.ssafy.dodam.domain.user.entity.Gender;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 동년배 비교 계산(632). 배정된 군집·사용자 특징 값·인구통계로 축별 {@link PeerComparisonResponse}를 만든다. 분석 완료 시점에
 * 네 축을 모두 계산해 저장하고, 조회는 저장값만 읽는다.
 *
 * <p>표본 부족은 자동 확장한다(AGE_BAND_GENDER·CLUSTER_AGE_BAND → AGE_BAND → ALL, GENDER·AGE_BAND → ALL). 실제로 쓴 축을
 * {@code peerGroup.dimension}에, 원래 요청을 {@code fallbackFrom}에 적는다. 전체 모집단까지 갔으면 값은 주되
 * {@code unavailableReason = PEER_GROUP_TOO_SMALL}로 표시한다. 최소 표본은 {@code app.pattern.peer-min-sample-size}(30),
 * 군집 × 나이대는 {@code cluster-peer-min-sample-size}(10)다 — 5명짜리 집단의 "상위 10%"는 통계로 의미가 없다.
 */
@Component
public class PeerComparisonCalculator {

    private final PatternProperties properties;

    public PeerComparisonCalculator(PatternProperties properties) {
        this.properties = properties;
    }

    /** 요청 가능한 네 축 전부. 저장용. */
    public Map<PeerDimension, PeerComparisonResponse> calculateAll(Long analysisId, ClusterResult cluster, LocalDate birthDate,
            Gender gender, LocalDate referenceDate) {
        Map<PeerDimension, PeerComparisonResponse> result = new EnumMap<>(PeerDimension.class);
        for (PeerDimension dimension : PeerDimension.values()) {
            if (dimension != PeerDimension.ALL) {
                result.put(dimension, calculate(analysisId, cluster, birthDate, gender, referenceDate, dimension));
            }
        }
        return result;
    }

    public PeerComparisonResponse calculate(Long analysisId, ClusterResult cluster, LocalDate birthDate, Gender gender,
            LocalDate referenceDate, PeerDimension requested) {
        ClusterModel model = cluster.model();
        String modelVersion = model.modelVersion();
        if (!model.supportsPercentiles()) {
            return PeerComparisonResponse.unavailable(analysisId, modelVersion, PeerComparisonResponse.MODEL_NOT_SUPPORTED);
        }
        String ageBand = AgeBand.of(birthDate, referenceDate);
        if ((requested.usesAgeBand() && ageBand == null) || (requested.usesGender() && gender == null)) {
            return PeerComparisonResponse.unavailable(analysisId, modelVersion, PeerComparisonResponse.DEMOGRAPHICS_MISSING);
        }
        int myClusterId = cluster.summary().clusterId();

        PeerDimension current = requested;
        while (current != null) {
            Optional<GroupStats> group = groupFor(model, current, ageBand, gender, myClusterId);
            int minimum = current.usesCluster() ? properties.clusterPeerMinSampleSize() : properties.peerMinSampleSize();
            if (group.isPresent() && (current == PeerDimension.ALL || group.get().sampleSize() >= minimum)) {
                String fallbackFrom = current == requested ? null : requested.name();
                String reason = current == PeerDimension.ALL && requested != PeerDimension.ALL
                        ? PeerComparisonResponse.PEER_GROUP_TOO_SMALL : null;
                return build(analysisId, cluster, group.get(), current, ageBand, gender, myClusterId, fallbackFrom, reason);
            }
            current = current.broader();
        }
        return PeerComparisonResponse.unavailable(analysisId, modelVersion, PeerComparisonResponse.PEER_GROUP_TOO_SMALL);
    }

    private static Optional<GroupStats> groupFor(ClusterModel model, PeerDimension dimension, String ageBand, Gender gender,
            int myClusterId) {
        if (dimension == PeerDimension.ALL) {
            return Optional.ofNullable(model.population());
        }
        return model.peerGroup(dimension.usesAgeBand() ? ageBand : null, dimension.usesGender() ? gender : null,
                dimension.usesCluster() ? myClusterId : null).map(PeerGroup::stats);
    }

    private PeerComparisonResponse build(Long analysisId, ClusterResult cluster, GroupStats group, PeerDimension dimension, String ageBand,
            Gender gender, int myClusterId, String fallbackFrom, String unavailableReason) {
        ClusterModel model = cluster.model();
        Map<ClusterFeature, Double> percentiles = new EnumMap<>(ClusterFeature.class);
        List<PeerFeature> features = new ArrayList<>();
        for (ClusterFeature feature : ClusterFeature.ordered()) {
            double userValue = Stats.round4(cluster.userValues().getOrDefault(feature, 0.0));
            Double percentile = model.percentileIn(group, feature, userValue);
            if (percentile == null) {
                continue;
            }
            percentiles.put(feature, percentile);
            features.add(new PeerFeature(feature.key(), feature.displayName(), userValue,
                    model.quantileValue(group, feature, 25), model.quantileValue(group, feature, 50), model.quantileValue(group, feature, 75),
                    percentile, RankBand.of(percentile).name()));
        }
        List<ClusterShare> shares = new ArrayList<>();
        Double myShare = null;
        for (int c = 0; c < model.clusterCount(); c++) {
            Double ratio = group.clusterShare().get(c);
            if (ratio == null) {
                continue;
            }
            boolean mine = c == myClusterId;
            if (mine) {
                myShare = ratio;
            }
            shares.add(new ClusterShare(c, model.cluster(c).label(), ratio, mine));
        }
        String display = groupDisplay(dimension, ageBand, gender, cluster.summary().label());
        List<PatternInsight> insights = PatternInsightPolicy.peerInsights(display, percentiles,
                dimension.usesCluster() ? null : myClusterId, cluster.summary().label(), dimension.usesCluster() ? null : myShare);
        PeerComparisonResponse.PeerGroup peerGroup = new PeerComparisonResponse.PeerGroup(dimension.name(),
                dimension.usesAgeBand() ? ageBand : null, dimension.usesGender() ? gender.name() : null,
                dimension.usesCluster() ? myClusterId : null, group.sampleSize(), fallbackFrom);
        return new PeerComparisonResponse(analysisId, model.modelVersion(), peerGroup, features, shares, insights, unavailableReason);
    }

    /** 문장용 집단 표기. */
    public static String groupDisplay(PeerDimension dimension, String ageBand, Gender gender, String clusterLabel) {
        return switch (dimension) {
            case AGE_BAND -> AgeBand.display(ageBand);
            case GENDER -> genderDisplay(gender);
            case AGE_BAND_GENDER -> AgeBand.display(ageBand) + " " + genderDisplay(gender);
            case CLUSTER_AGE_BAND -> AgeBand.display(ageBand) + " " + clusterLabel;
            case ALL -> "전체 사용자";
        };
    }

    private static String genderDisplay(Gender gender) {
        return gender == Gender.FEMALE ? "여성" : "남성";
    }
}
