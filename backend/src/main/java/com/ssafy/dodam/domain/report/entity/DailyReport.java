package com.ssafy.dodam.domain.report.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 데일리 브리핑 하위행. PK가 상위 {@code report_id}와 같고 {@code (report_id, report_type)} 복합 FK로
 * DAILY 상위행에만 붙는다.
 *
 * <p>ERD의 {@code upcoming_events} / {@code risks} / {@code recommendations} 컬럼은 브리핑 확장용으로 남겨 두고
 * 매핑하지 않는다. 현재 응답 계약이 요구하는 값은 {@code financialSummary}(합계·상위 카테고리)와
 * {@code notableEvents}(그날 일어난 일)다.
 *
 * <p>{@code coachMessage}는 <b>API 21의 응답을 끼워 넣지 않는다</b>(계약 3-5). 브리핑 전용 문구가 생길 자리이며
 * 당분간 null이다. 확인되지 않은 문장을 리포트에 굳혀 두면 출처를 되짚을 수 없다.
 */
@Getter
@Entity
@Table(name = "daily_report")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DailyReport {

    @Id
    @Column(name = "report_id")
    private Long reportId;

    @Column(name = "report_type", nullable = false, length = 20)
    private String reportType;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_status", nullable = false, length = 30)
    private ReportDataStatus dataStatus;

    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;

    @Column(name = "available_today")
    private Long availableToday;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "financial_summary", columnDefinition = "JSON")
    private JsonNode financialSummary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "notable_events", columnDefinition = "JSON")
    private JsonNode notableEvents;

    @Column(name = "coach_message", columnDefinition = "TEXT")
    private String coachMessage;

    private DailyReport(Long reportId, LocalDate reportDate, ReportDataStatus dataStatus, String summary,
            JsonNode financialSummary, JsonNode notableEvents) {
        this.reportId = reportId;
        this.reportType = ReportType.DAILY.name();
        this.reportDate = reportDate;
        this.dataStatus = dataStatus;
        this.summary = summary;
        this.financialSummary = financialSummary;
        this.notableEvents = notableEvents;
    }

    public static DailyReport of(Long reportId, LocalDate reportDate, ReportDataStatus dataStatus, String summary,
            JsonNode financialSummary, JsonNode notableEvents) {
        if (reportId == null || reportDate == null || dataStatus == null) {
            throw new IllegalArgumentException("리포트 식별자·기준일·상태가 필요합니다.");
        }
        return new DailyReport(reportId, reportDate, dataStatus, summary, financialSummary, notableEvents);
    }

    /** 저장본을 다시 계산했다. READY가 아닌 리포트만 이 경로로 들어온다. */
    public void refresh(ReportDataStatus dataStatus, String summary, JsonNode financialSummary, JsonNode notableEvents) {
        this.dataStatus = dataStatus;
        this.summary = summary;
        this.financialSummary = financialSummary;
        this.notableEvents = notableEvents;
    }
}
