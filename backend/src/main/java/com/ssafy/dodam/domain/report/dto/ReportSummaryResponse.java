package com.ssafy.dodam.domain.report.dto;

import com.ssafy.dodam.domain.report.entity.Report;
import java.time.LocalDateTime;

/**
 * 리포트 목록 항목(API 20 #3).
 *
 * <p>{@code period}는 타입에 따라 {@code yyyy-MM-dd}(DAILY) 또는 {@code yyyy-MM}(MONTHLY)인 문자열이다.
 * 상세(#5)는 {@code type}에 따라 {@link DailyReportResponse} 또는 {@link MonthlyReportResponse}를 돌려주며,
 * FE는 공통 필드 {@code reportId}·{@code type}·{@code period}·{@code generatedAt}로 분기한다.
 */
public record ReportSummaryResponse(Long reportId, String type, String period, LocalDateTime generatedAt) {

    public static ReportSummaryResponse from(Report report) {
        return new ReportSummaryResponse(report.getReportId(), report.getType().name(), report.getPeriod(),
                report.getCreatedAt());
    }
}
