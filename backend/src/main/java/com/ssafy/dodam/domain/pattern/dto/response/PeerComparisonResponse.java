package com.ssafy.dodam.domain.pattern.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * `GET /patterns/me/peer-comparison` 응답(632). 분석 완료 시점에 차원별로 계산해 `pattern_analysis.peer_stats_json`에 굳혀 두고
 * 조회 때 그대로 읽는다 — 모델을 다시 학습해도 과거 분석 결과가 바뀌지 않고, AI 코치가 재계산 없이 읽는다.
 *
 * @param analysisId        근거 패턴 분석
 * @param modelVersion      군집 모델 버전. 군집이 배정되지 않았으면 null
 * @param peerGroup         실제로 비교한 집단. 비교할 수 없으면 null
 * @param features          특징별 사용자 값·집단 분포·백분위. 비교할 수 없으면 빈 목록
 * @param clusterShare      집단 안 군집 구성비. 비교할 수 없으면 빈 목록
 * @param insights          문장. 비교할 수 없으면 빈 목록
 * @param unavailableReason 값이 없는 이유. {@code DEMOGRAPHICS_MISSING}(생년월일·성별 없음), {@code PEER_GROUP_TOO_SMALL}(요청 집단 표본 부족으로
 *                          전체 모집단까지 넓힘 — 이때 features는 전체 기준), {@code MODEL_NOT_SUPPORTED}(v1 모델, 분위 격자 없음),
 *                          {@code CLUSTER_NOT_ASSIGNED}(규칙 기반 분석), {@code NOT_COMPUTED}(632 이전 분석 — 다시 분석하면 채워짐)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PeerComparisonResponse(
        Long analysisId,
        String modelVersion,
        PeerGroup peerGroup,
        List<PeerFeature> features,
        List<ClusterShare> clusterShare,
        List<PatternInsight> insights,
        @JsonInclude(JsonInclude.Include.NON_NULL) String unavailableReason
) {
    public static final String DEMOGRAPHICS_MISSING = "DEMOGRAPHICS_MISSING";
    public static final String PEER_GROUP_TOO_SMALL = "PEER_GROUP_TOO_SMALL";
    public static final String MODEL_NOT_SUPPORTED = "MODEL_NOT_SUPPORTED";
    public static final String CLUSTER_NOT_ASSIGNED = "CLUSTER_NOT_ASSIGNED";
    public static final String NOT_COMPUTED = "NOT_COMPUTED";

    public PeerComparisonResponse {
        features = features == null ? List.of() : List.copyOf(features);
        clusterShare = clusterShare == null ? List.of() : List.copyOf(clusterShare);
        insights = insights == null ? List.of() : List.copyOf(insights);
    }

    public static PeerComparisonResponse unavailable(Long analysisId, String modelVersion, String reason) {
        return new PeerComparisonResponse(analysisId, modelVersion, null, List.of(), List.of(), List.of(), reason);
    }

    /**
     * @param dimension    실제로 쓴 축(AGE_BAND / GENDER / AGE_BAND_GENDER / CLUSTER_AGE_BAND / ALL)
     * @param ageBand      나이대(10S~60S_PLUS). 축에 없으면 null
     * @param gender       MALE / FEMALE. 축에 없으면 null
     * @param clusterId    군집 번호. CLUSTER_AGE_BAND에만 있다
     * @param sampleSize   집단 표본 수
     * @param fallbackFrom 표본 부족으로 넓히기 전 원래 요청 축. 넓히지 않았으면 null
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PeerGroup(String dimension, String ageBand, String gender, Integer clusterId, int sampleSize, String fallbackFrom) {
    }

    /**
     * @param feature          특징 키
     * @param displayName      표시 이름
     * @param userValue        사용자 값
     * @param peerP25          집단 25분위 값
     * @param peerMedian       집단 중앙값
     * @param peerP75          집단 75분위 값
     * @param percentileInPeer 집단 안에서 사용자 값의 백분위(0~100)
     * @param rankBand         TOP_10 / UPPER / MIDDLE / LOWER / BOTTOM_10
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PeerFeature(String feature, String displayName, double userValue, Double peerP25, Double peerMedian, Double peerP75,
            double percentileInPeer, String rankBand) {
    }

    /**
     * @param clusterId    군집 번호
     * @param clusterLabel 군집 라벨
     * @param peerRatio    이 집단에서 그 군집의 비율(0~1)
     * @param mine         사용자가 배정된 군집이면 true
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ClusterShare(int clusterId, String clusterLabel, double peerRatio, boolean mine) {
    }
}
