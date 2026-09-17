package com.ssafy.dodam.domain.pattern.service;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;

/**
 * 패턴 분석 정책값(`app.pattern`). 명세·기획안에 수치가 없는 항목은 여기 기본값을 **서비스 기본안**으로 쓰고 문서에 기록한다.
 *
 * @param lookbackMonths        분석 기간(완료된 달 기준). 기획안 "최근 3~6개월"의 상한 6
 * @param minUserMonths         군집 비교에 필요한 사용자 데이터 최소 개월 수. 기획안 노출 조건 "사용자 데이터 3개월 이상"
 * @param minPopulation         모델을 학습할 최소 사용자 수. 이보다 적으면 RULE_ONLY로 전환한다
 * @param minClusterSampleSize  배정된 군집의 최소 표본 수. 기획안 "표본 수가 작은 군집의 범위는 노출하지 않는다"
 * @param minConfidence         분류 신뢰도 하한(0~1)
 * @param clusterCount          K-Means 군집 수. seed 성향 4종(균형·소비집중·저축집중·변동)에 맞춘 4
 * @param modelTtl              인프로세스 학습 모델을 메모리에 재사용하는 시간. 지나면 다음 분석에서 다시 학습한다
 * @param leaseDuration         진행 중 접수가 유효한 시간. 지나면 죽은 접수로 보고 새 요청을 받는다
 * @param modelLocation         Python 학습 스크립트가 만든 모델 아티팩트 위치(Spring Resource 표기). 비우면 아티팩트를 쓰지 않는다
 * @param trainInProcess        아티팩트가 없을 때 Java 안에서 K-Means를 학습할지. 운영에서는 false로 두고 아티팩트만 쓰는 것을 권장
 * @param peerMinSampleSize     (632) 동년배 비교에 필요한 집단 최소 표본 수(나이대·성별·나이대×성별). 미달이면 더 넓은 축으로 확장한다
 * @param clusterPeerMinSampleSize (632) 군집 × 나이대 집단의 최소 표본 수
 */
@ConfigurationProperties(prefix = "app.pattern")
public record PatternProperties(
        Integer lookbackMonths,
        Integer minUserMonths,
        Integer minPopulation,
        Integer minClusterSampleSize,
        Double minConfidence,
        Integer clusterCount,
        Duration modelTtl,
        Duration leaseDuration,
        String modelLocation,
        Boolean trainInProcess,
        Integer peerMinSampleSize,
        Integer clusterPeerMinSampleSize
) {

    public static final int DEFAULT_LOOKBACK_MONTHS = 6;
    public static final int MAX_LOOKBACK_MONTHS = 12;
    public static final String DEFAULT_MODEL_LOCATION = "classpath:ml/spending-kmeans-v2.json";

    /** 632 이전 형식(동년배 표본 하한 없음). */
    public PatternProperties(Integer lookbackMonths, Integer minUserMonths, Integer minPopulation, Integer minClusterSampleSize,
            Double minConfidence, Integer clusterCount, Duration modelTtl, Duration leaseDuration, String modelLocation,
            Boolean trainInProcess) {
        this(lookbackMonths, minUserMonths, minPopulation, minClusterSampleSize, minConfidence, clusterCount, modelTtl, leaseDuration,
                modelLocation, trainInProcess, null, null);
    }

    @ConstructorBinding
    public PatternProperties {
        peerMinSampleSize = peerMinSampleSize == null ? 30 : peerMinSampleSize;
        clusterPeerMinSampleSize = clusterPeerMinSampleSize == null ? 10 : clusterPeerMinSampleSize;
        modelLocation = modelLocation == null ? DEFAULT_MODEL_LOCATION : modelLocation.strip();
        trainInProcess = trainInProcess == null || trainInProcess;
        lookbackMonths = lookbackMonths == null ? DEFAULT_LOOKBACK_MONTHS : lookbackMonths;
        minUserMonths = minUserMonths == null ? 3 : minUserMonths;
        minPopulation = minPopulation == null ? 30 : minPopulation;
        minClusterSampleSize = minClusterSampleSize == null ? 10 : minClusterSampleSize;
        minConfidence = minConfidence == null ? 0.2 : minConfidence;
        clusterCount = clusterCount == null ? 4 : clusterCount;
        modelTtl = modelTtl == null ? Duration.ofHours(1) : modelTtl;
        leaseDuration = leaseDuration == null ? Duration.ofMinutes(5) : leaseDuration;
        if (lookbackMonths < 1 || lookbackMonths > MAX_LOOKBACK_MONTHS) {
            throw new IllegalArgumentException("app.pattern.lookback-months는 1~12여야 합니다.");
        }
        if (minUserMonths < 1 || minUserMonths > lookbackMonths) {
            throw new IllegalArgumentException("app.pattern.min-user-months는 1 이상, lookback-months 이하여야 합니다.");
        }
        if (clusterCount < 2) {
            throw new IllegalArgumentException("app.pattern.cluster-count는 2 이상이어야 합니다.");
        }
        if (minConfidence < 0 || minConfidence > 1) {
            throw new IllegalArgumentException("app.pattern.min-confidence는 0~1이어야 합니다.");
        }
    }
}
