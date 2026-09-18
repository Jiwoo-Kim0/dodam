package com.ssafy.dodam.domain.diagnosis.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 진단에서 탐지된 문제 1건.
 *
 * <p>`domain`과 `type`을 enum으로 두지 않았다. ERD 코드값 표가 두 컬럼의 값 목록을 정의하지 않고,
 * 명세 14의 `domain`·`type` 필터는 진단 규칙이 늘어날 때마다 값이 추가되는 자리다. 여기서 집합을
 * 닫으면 규칙을 추가할 때마다 이 파일을 고쳐야 한다. 값 집합은 규칙을 만드는 544·545에서 정한다.
 *
 * <p>JSON 컬럼 4종(`currentValue`, `benchmarkValue`, `evidence`, `clusterContext`)은 스키마를
 * 엔티티로 고정하지 않는다. 문제 유형마다 담기는 항목이 달라 공통 스키마를 만들 수 없다. 응답
 * 변환은 545가 맡는다. `String`이 아니라 {@link JsonNode}인 이유는 {@link Diagnosis#getInputSnapshot()}
 * 주석 참고.
 *
 * <p>`userId`가 없다. 소유권 조건은 상위 `diagnosis`를 조인해서 건다
 * ({@code DiagnosisIssueRepository} 참고).
 */
@Getter
@Entity
@Table(name = "diagnosis_issue")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DiagnosisIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "issue_id")
    private Long issueId;

    @Column(name = "diagnosis_id", nullable = false)
    private Long diagnosisId;

    @Column(name = "domain", nullable = false, length = 50)
    private String domain;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 30)
    private IssueSeverity severity;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "current_value", columnDefinition = "JSON")
    private JsonNode currentValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "benchmark_value", columnDefinition = "JSON")
    private JsonNode benchmarkValue;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", columnDefinition = "JSON")
    private JsonNode evidence;

    @Enumerated(EnumType.STRING)
    @Column(name = "detection_source", nullable = false, length = 20)
    private DetectionSource detectionSource;

    // 군집 모델이 붙기 전에는 NULL이다. `detectionSource`가 RULE인 행에는 채우지 않는다.
    @Column(name = "model_version", length = 50)
    private String modelVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cluster_context_json", columnDefinition = "JSON")
    private JsonNode clusterContext;

    @Column(name = "detected_at", nullable = false)
    private LocalDateTime detectedAt;

    private DiagnosisIssue(
            Long diagnosisId,
            String domain,
            String type,
            IssueSeverity severity,
            String title,
            String description,
            JsonNode currentValue,
            JsonNode benchmarkValue,
            JsonNode evidence,
            DetectionSource detectionSource,
            String modelVersion,
            JsonNode clusterContext,
            LocalDateTime detectedAt
    ) {
        this.diagnosisId = diagnosisId;
        this.domain = domain;
        this.type = type;
        this.severity = severity;
        this.title = title;
        this.description = description;
        this.currentValue = currentValue;
        this.benchmarkValue = benchmarkValue;
        this.evidence = evidence;
        this.detectionSource = detectionSource;
        this.modelVersion = modelVersion;
        this.clusterContext = clusterContext;
        this.detectedAt = detectedAt;
    }

    /**
     * 규칙 기반으로 탐지된 문제. 군집 신호가 붙기 전까지 모든 문제가 이 경로로 만들어진다
     * (명세 14: 군집 결과가 없으면 규칙 기반으로 진단한다).
     */
    public static DiagnosisIssue ofRule(
            Long diagnosisId,
            String domain,
            String type,
            IssueSeverity severity,
            String title,
            String description,
            JsonNode currentValue,
            JsonNode benchmarkValue,
            JsonNode evidence,
            LocalDateTime detectedAt
    ) {
        return new DiagnosisIssue(
                diagnosisId, domain, type, severity, title, description,
                currentValue, benchmarkValue, evidence,
                DetectionSource.RULE, null, null, detectedAt);
    }

    /**
     * 군집 신호가 반영된 문제.
     *
     * @param detectionSource `CLUSTER` 또는 `RULE_AND_CLUSTER`. `RULE`이면 군집 정보를 함께 넘길 이유가
     *                       없으므로 거부한다
     * @param modelVersion 군집 모델 버전. 어떤 모델이 만든 판단인지 남지 않으면 재현할 수 없다
     */
    public static DiagnosisIssue ofCluster(
            Long diagnosisId,
            String domain,
            String type,
            IssueSeverity severity,
            String title,
            String description,
            JsonNode currentValue,
            JsonNode benchmarkValue,
            JsonNode evidence,
            DetectionSource detectionSource,
            String modelVersion,
            JsonNode clusterContext,
            LocalDateTime detectedAt
    ) {
        if (detectionSource == null || detectionSource == DetectionSource.RULE) {
            throw new IllegalArgumentException("군집 탐지 결과가 아닙니다: " + detectionSource);
        }
        if (modelVersion == null || modelVersion.isBlank()) {
            throw new IllegalArgumentException("군집 탐지 결과에는 모델 버전이 필요합니다.");
        }
        return new DiagnosisIssue(
                diagnosisId, domain, type, severity, title, description,
                currentValue, benchmarkValue, evidence,
                detectionSource, modelVersion, clusterContext, detectedAt);
    }

    /**
     * 규칙으로 탐지한 문제에 군집 근거를 병합한다(641, 기획안 5-2 "근거만 병합"). 심각도·제목·설명·값은 규칙 것을 그대로 두고
     * {@code detectionSource}만 {@code RULE_AND_CLUSTER}로 바꾼다 — 군집은 판정 주체가 아니라 보조 근거다.
     * 이미 군집 근거가 있는 문제(단독 CLUSTER 문제 포함)에는 붙이지 않는다.
     */
    public void attachClusterEvidence(String modelVersion, JsonNode clusterContext) {
        if (detectionSource != DetectionSource.RULE) {
            throw new IllegalStateException("규칙 문제에만 군집 근거를 병합할 수 있습니다: " + detectionSource);
        }
        if (modelVersion == null || modelVersion.isBlank()) {
            throw new IllegalArgumentException("군집 근거에는 모델 버전이 필요합니다.");
        }
        this.detectionSource = DetectionSource.RULE_AND_CLUSTER;
        this.modelVersion = modelVersion;
        this.clusterContext = clusterContext;
    }

    public boolean isHighSeverity() {
        return severity == IssueSeverity.HIGH || severity == IssueSeverity.CRITICAL;
    }
}
