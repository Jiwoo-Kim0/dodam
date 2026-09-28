package com.ssafy.dodam.domain.report.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 월간 리포트 하위행. PK가 상위 {@code report_id}와 같고 {@code (report_id, report_type)} 복합 FK로
 * MONTHLY 상위행에만 붙는다.
 *
 * <p>하위 응답(소비패턴·목표변화·개선행동 요약)은 생성 시점에 계산해 여기 JSON으로 함께 저장하고, 각
 * 엔드포인트는 <b>저장본을 읽는다</b>. 같은 달을 두 경로로 다시 집계하면 합계가 어긋난다.
 * 다음달 예정비용만 예외로 저장하지 않는다 — 미래 스케줄이라 생성 시점에 굳히면 바로 낡는다
 * (ERD의 {@code upcoming_summary}·{@code llm_comment} 컬럼을 매핑하지 않는 이유다).
 *
 * <p>{@code year_month}는 MySQL의 {@code INTERVAL} 단위 키워드와 철자가 같아 항상 따옴표로 감싼다.
 * 타입은 CHAR(7)이며 {@code columnDefinition}으로 고정한다(아래 주석 참고).
 */
@Getter
@Entity
@Table(name = "monthly_report")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MonthlyReport {

    @Id
    @Column(name = "report_id")
    private Long reportId;

    @Column(name = "report_type", nullable = false, length = 20)
    private String reportType;

    // DDL이 CHAR(7)다(ERD·V38). `columnDefinition`을 빼면 Hibernate가 VARCHAR(7)을 기대해
    // `ddl-auto: validate`가 기동을 막는다 — 2026-09-17 운영 장애의 원인이었다.
    // 해시·연월 컬럼이 전부 쓰는 방식이며 `card.expiry_month`(같은 yyyy-MM CHAR(7))와 같다.
    @Column(name = "`year_month`", nullable = false, length = 7, columnDefinition = "CHAR(7)")
    private String yearMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_status", nullable = false, length = 30)
    private ReportDataStatus dataStatus;

    @Column(name = "diagnosis_id")
    private Long diagnosisId;

    @Column(name = "overall_status", length = 20)
    private String overallStatus;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "financial_summary", columnDefinition = "JSON")
    private JsonNode financialSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "spending_summary", columnDefinition = "JSON")
    private JsonNode spendingSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "goal_summary", columnDefinition = "JSON")
    private JsonNode goalSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "improvement_summary", columnDefinition = "JSON")
    private JsonNode improvementSummary;

    /** 635 금융 효율 점수(전체 + 항목별). 635 이전 리포트는 NULL이며 조회 때 {@link #supplement}로 채운다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "efficiency_summary", columnDefinition = "JSON")
    private JsonNode efficiencySummary;

    /** 697 주요 지표·금융 생활 패턴. 697 이전 리포트는 NULL이며 조회 때 {@link #supplement}로 채운다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "metrics_summary", columnDefinition = "JSON")
    private JsonNode metricsSummary;

    /** 생성·재계산에 쓰는 값 묶음. 필드가 많아 생성자 인자 순서를 틀리기 쉬워 record로 받는다. */
    public record Content(
            ReportDataStatus dataStatus,
            Long diagnosisId,
            String overallStatus,
            JsonNode financialSummary,
            JsonNode spendingSummary,
            JsonNode goalSummary,
            JsonNode improvementSummary,
            JsonNode efficiencySummary,
            JsonNode metricsSummary
    ) {
    }

    private MonthlyReport(Long reportId, String yearMonth, Content content) {
        this.reportId = reportId;
        this.reportType = ReportType.MONTHLY.name();
        this.yearMonth = yearMonth;
        apply(content);
    }

    public static MonthlyReport of(Long reportId, String yearMonth, Content content) {
        if (reportId == null || yearMonth == null || content == null || content.dataStatus() == null) {
            throw new IllegalArgumentException("리포트 식별자·기준월·내용이 필요합니다.");
        }
        return new MonthlyReport(reportId, yearMonth, content);
    }

    /** 저장본을 다시 계산했다. READY가 아닌 리포트만 이 경로로 들어온다. */
    public void refresh(Content content) {
        apply(content);
    }

    /**
     * 635 이전에 굳힌 READY 리포트에 카드 사용액(financial JSON 안)과 효율 점수만 덧붙인다. 합계·상태·다른 요약은
     * 건드리지 않는다 — READY 리포트의 숫자는 고정이라는 계약을 지키면서 새 필드만 채우는 경로다.
     */
    public void supplement(JsonNode financialSummary, JsonNode efficiencySummary, JsonNode metricsSummary) {
        this.financialSummary = financialSummary;
        this.efficiencySummary = efficiencySummary;
        this.metricsSummary = metricsSummary;
    }

    public boolean hasEfficiencySummary() {
        return efficiencySummary != null && !efficiencySummary.isNull();
    }

    public boolean hasMetricsSummary() {
        return metricsSummary != null && !metricsSummary.isNull();
    }

    private void apply(Content content) {
        this.dataStatus = content.dataStatus();
        this.diagnosisId = content.diagnosisId();
        this.overallStatus = content.overallStatus();
        this.financialSummary = content.financialSummary();
        this.spendingSummary = content.spendingSummary();
        this.goalSummary = content.goalSummary();
        this.improvementSummary = content.improvementSummary();
        this.efficiencySummary = content.efficiencySummary();
        this.metricsSummary = content.metricsSummary();
    }
}
