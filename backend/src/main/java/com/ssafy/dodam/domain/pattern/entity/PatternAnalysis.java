package com.ssafy.dodam.domain.pattern.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 사용자 패턴 분석 실행 1건(ERD `pattern_analysis`, V31).
 *
 * <p>규칙 기반 지표 8종은 완료 시점에 채우고, 군집 컬럼 7종(`modelVersion` ~ `confidence`)은 HYBRID 모드에서
 * 표본·신뢰도 조건을 충족했을 때만 채운다. 조건 미충족·RULE_ONLY 요청이면 모두 NULL이고 그 사유는
 * {@code featureSummary.fallbackReason}에 남는다. 명세 3의 `clusterProfile`은 이 컬럼들이 모두 있을 때만 만들어진다.
 *
 * <p>비율 컬럼은 ERD `DECIMAL(10,4)`에 맞춰 {@link BigDecimal} 소수 4자리로 저장한다. Double로 두면 MySQL
 * `ddl-auto: validate`가 DOUBLE과 DECIMAL을 다른 타입으로 판정한다.
 *
 * <p>JSON 컬럼을 {@link JsonNode}로 두는 이유는 {@code Diagnosis.inputSnapshot}과 같다(String이면 문자열 리터럴로
 * 한 번 더 감싸져 `JSON_EXTRACT`가 통하지 않는다).
 */
@Getter
@Entity
@Table(name = "pattern_analysis")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PatternAnalysis {

    public static final int RATIO_SCALE = 4;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "analysis_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private PatternAnalysisStatus status;

    @Column(name = "lookback_months", nullable = false)
    private int lookbackMonths;

    @Column(name = "data_point_count", nullable = false)
    private int dataPointCount;

    @Column(name = "spending_regularity", precision = 10, scale = 4)
    private BigDecimal spendingRegularity;

    @Column(name = "saving_consistency", precision = 10, scale = 4)
    private BigDecimal savingConsistency;

    @Column(name = "fixed_expense_ratio", precision = 10, scale = 4)
    private BigDecimal fixedExpenseRatio;

    @Column(name = "variable_expense_ratio", precision = 10, scale = 4)
    private BigDecimal variableExpenseRatio;

    @Column(name = "discretionary_spending_ratio", precision = 10, scale = 4)
    private BigDecimal discretionarySpendingRatio;

    @Column(name = "debt_burden", precision = 10, scale = 4)
    private BigDecimal debtBurden;

    @Column(name = "cashflow_stability", precision = 10, scale = 4)
    private BigDecimal cashflowStability;

    @Enumerated(EnumType.STRING)
    @Column(name = "liquidity_level", length = 30)
    private LiquidityLevel liquidityLevel;

    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @Column(name = "cluster_id")
    private Integer clusterId;

    @Column(name = "cluster_label", length = 50)
    private String clusterLabel;

    @Column(name = "feature_window_months")
    private Integer featureWindowMonths;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "feature_summary_json", columnDefinition = "JSON")
    private JsonNode featureSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cluster_stats_json", columnDefinition = "JSON")
    private JsonNode clusterStats;

    /** 632: 차원별 동년배 비교 결과. 군집이 배정된 분석에만 있다(V40). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "peer_stats_json", columnDefinition = "JSON")
    private JsonNode peerStats;

    @Column(name = "sample_size")
    private Integer sampleSize;

    @Column(name = "confidence", precision = 5, scale = 4)
    private BigDecimal confidence;

    @Column(name = "analyzed_at", nullable = false)
    private LocalDateTime analyzedAt;

    private PatternAnalysis(Long userId, int lookbackMonths, LocalDateTime now) {
        this.userId = userId;
        this.status = PatternAnalysisStatus.RUNNING;
        this.lookbackMonths = lookbackMonths;
        this.dataPointCount = 0;
        this.analyzedAt = now;
    }

    /** 분석을 접수한다. 계산 전이므로 지표·군집 컬럼은 비어 있고 상태는 RUNNING이다. */
    public static PatternAnalysis start(Long userId, int lookbackMonths, LocalDateTime now) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자를 지정해야 합니다.");
        }
        if (lookbackMonths <= 0) {
            throw new IllegalArgumentException("분석 기간은 1개월 이상이어야 합니다.");
        }
        return new PatternAnalysis(userId, lookbackMonths, now);
    }

    /**
     * 규칙 기반 지표를 반영하고 완료 처리한다. 군집 결과는 {@link #attachCluster}로 따로 붙인다(없으면 NULL 유지).
     */
    public void complete(
            int dataPointCount,
            Double spendingRegularity,
            Double savingConsistency,
            Double fixedExpenseRatio,
            Double variableExpenseRatio,
            Double discretionarySpendingRatio,
            Double debtBurden,
            Double cashflowStability,
            LiquidityLevel liquidityLevel,
            JsonNode featureSummary,
            LocalDateTime now
    ) {
        requireRunning();
        if (dataPointCount < 0) {
            throw new IllegalArgumentException("데이터 건수는 음수일 수 없습니다.");
        }
        this.dataPointCount = dataPointCount;
        this.spendingRegularity = ratio(spendingRegularity);
        this.savingConsistency = ratio(savingConsistency);
        this.fixedExpenseRatio = ratio(fixedExpenseRatio);
        this.variableExpenseRatio = ratio(variableExpenseRatio);
        this.discretionarySpendingRatio = ratio(discretionarySpendingRatio);
        this.debtBurden = ratio(debtBurden);
        this.cashflowStability = ratio(cashflowStability);
        this.liquidityLevel = liquidityLevel;
        this.featureSummary = featureSummary;
        this.analyzedAt = now;
        this.status = PatternAnalysisStatus.COMPLETED;
    }

    /**
     * 군집 분류 결과를 붙인다. 모델 버전·표본 수·신뢰도가 모두 있어야 한다. 어떤 모델이 만든 판단인지 모르는 군집
     * 결과는 저장하지 않는다(기획안 "모든 군집 기반 판정에는 모델 버전·표본 수·기준 기간을 함께 기록").
     */
    public void attachCluster(
            String modelVersion,
            int clusterId,
            String clusterLabel,
            int featureWindowMonths,
            int sampleSize,
            double confidence,
            JsonNode clusterStats
    ) {
        if (modelVersion == null || modelVersion.isBlank()) {
            throw new IllegalArgumentException("군집 결과에는 모델 버전이 필요합니다.");
        }
        if (clusterLabel == null || clusterLabel.isBlank()) {
            throw new IllegalArgumentException("군집 결과에는 라벨이 필요합니다.");
        }
        if (featureWindowMonths <= 0 || sampleSize < 0) {
            throw new IllegalArgumentException("군집 기준 기간·표본 수가 올바르지 않습니다.");
        }
        if (confidence < 0 || confidence > 1) {
            throw new IllegalArgumentException("신뢰도는 0~1 사이여야 합니다.");
        }
        this.modelVersion = modelVersion;
        this.clusterId = clusterId;
        this.clusterLabel = clusterLabel;
        this.featureWindowMonths = featureWindowMonths;
        this.sampleSize = sampleSize;
        this.confidence = BigDecimal.valueOf(confidence).setScale(RATIO_SCALE, RoundingMode.HALF_UP);
        this.clusterStats = clusterStats;
    }

    /**
     * 동년배 비교 결과(632, V40). 차원별 {@code PeerComparisonResponse}를 담은 JSON 객체. 군집이 배정된 분석에만 붙이며
     * 규칙 기반 분석·632 이전 분석은 NULL이다.
     */
    public void attachPeerStats(JsonNode peerStats) {
        if (!hasCluster()) {
            throw new IllegalStateException("동년배 비교는 군집이 배정된 분석에만 붙일 수 있습니다.");
        }
        this.peerStats = peerStats;
    }

    public JsonNode getPeerStats() {
        return peerStats;
    }

    /** 예상하지 못한 계산 오류. 원인 요약만 남기고 지표는 비운다. */
    public void fail(JsonNode failureSummary, LocalDateTime now) {
        requireRunning();
        this.featureSummary = failureSummary;
        this.analyzedAt = now;
        this.status = PatternAnalysisStatus.FAILED;
    }

    public boolean isCompleted() {
        return status == PatternAnalysisStatus.COMPLETED;
    }

    public boolean isRunning() {
        return status == PatternAnalysisStatus.RUNNING;
    }

    public boolean hasCluster() {
        return modelVersion != null && clusterId != null && clusterLabel != null && sampleSize != null && confidence != null;
    }

    private void requireRunning() {
        if (!isRunning()) {
            throw new IllegalStateException("진행 중인 분석이 아닙니다.");
        }
    }

    private static BigDecimal ratio(Double value) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            return null;
        }
        return BigDecimal.valueOf(value).setScale(RATIO_SCALE, RoundingMode.HALF_UP);
    }
}
