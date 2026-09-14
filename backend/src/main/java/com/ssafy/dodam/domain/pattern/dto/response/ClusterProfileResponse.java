package com.ssafy.dodam.domain.pattern.dto.response;

import java.util.List;

/**
 * 명세 3 `clusterProfile`(군집 라벨·기준 범위·표본 수·모델 버전). 조건 미충족이면 응답에서 null이다.
 *
 * <p>이 형식 그대로 `pattern_analysis.cluster_stats_json`에 저장하고 조회 때 다시 읽는다. 저장 시점의 모델 통계가 응답이 되므로
 * 나중에 모델을 다시 학습해도 과거 결과는 바뀌지 않는다.
 *
 * @param clusterId           군집 번호(0부터)
 * @param clusterLabel        군집 라벨(예: 저축 중심형)
 * @param modelVersion        `spending-kmeans-v1`
 * @param sampleSize          배정된 군집의 표본 수
 * @param populationSize      모델 학습 표본 수(전체)
 * @param confidence          분류 신뢰도 0~1
 * @param featureWindowMonths 특징 산출 기간(개월)
 * @param windowStart         특징 산출 기간 시작 달(yyyy-MM)
 * @param windowEnd           특징 산출 기간 마지막 달
 * @param deviationScore      특징별 범위 이탈의 평균(기획안 "군집 이탈 점수", 가중치 균등). 0이면 모든 특징이 기준 범위 안
 * @param features            특징별 사용자 값과 군집 기준 범위
 * @param modelSource         ARTIFACT(Python 학습 아티팩트) | IN_PROCESS(Java 인프로세스 학습). 2026-09-14 이전 저장분은 null
 * @param trainedBy           학습 도구(예: python-sklearn-1.9.0)
 */
public record ClusterProfileResponse(
        int clusterId,
        String clusterLabel,
        String modelVersion,
        int sampleSize,
        int populationSize,
        double confidence,
        int featureWindowMonths,
        String windowStart,
        String windowEnd,
        double deviationScore,
        List<FeatureRange> features,
        String modelSource,
        String trainedBy
) {

    public ClusterProfileResponse {
        features = List.copyOf(features);
    }

    /**
     * @param feature     특징 키(`spendingToIncomeRatio` 등)
     * @param displayName 표시 이름
     * @param userValue   사용자 값
     * @param centroid    군집 중심값
     * @param min         군집 실제 최소
     * @param max         군집 실제 최대
     * @param p5          기준 범위 하한(5분위)
     * @param p95         기준 범위 상한(95분위)
     * @param mean        군집 평균
     * @param std         군집 표준편차
     * @param deviation   범위 이탈(0이면 기준 범위 안). scale = max(p95 − p5, std)
     * @param direction   BELOW / WITHIN / ABOVE
     */
    public record FeatureRange(
            String feature,
            String displayName,
            double userValue,
            double centroid,
            double min,
            double max,
            double p5,
            double p95,
            double mean,
            double std,
            double deviation,
            String direction
    ) {
    }
}
