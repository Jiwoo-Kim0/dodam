package com.ssafy.dodam.domain.report.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 리포트 상위행. 유형별 하위행({@link DailyReport} / {@link MonthlyReport})과 한 트랜잭션에서 함께 만든다.
 *
 * <p>{@code (userId, type, period)}가 UNIQUE라 같은 기간 리포트는 한 번만 생성된다. 동시 요청이 겹치면
 * 두 번째 INSERT가 제약에 걸리므로 생성 경로는 사용자 행을 잠그고 들어간다.
 *
 * <p>{@code period}는 타입에 따라 {@code yyyy-MM-dd}(DAILY) 또는 {@code yyyy-MM}(MONTHLY)인 <b>문자열</b>이다.
 * 날짜 범위 조회는 문자열이 아니라 {@code periodStart}/{@code periodEnd}로 한다 — 두 형식이 섞여 있어
 * 문자열 비교로는 DAILY와 MONTHLY를 함께 정렬할 수 없다.
 *
 * <p>{@code @UniqueConstraint}를 엔티티에도 선언한다. H2 테스트는 Flyway를 끄고 엔티티로 스키마를 만들기
 * 때문에 여기 없으면 중복 생성 방지가 테스트에서 검증되지 않는다.
 */
@Getter
@Entity
@Table(name = "report", uniqueConstraints = {
        @UniqueConstraint(name = "uk_report_id_type", columnNames = {"report_id", "type"}),
        @UniqueConstraint(name = "uk_report_user_type_period", columnNames = {"user_id", "type", "period"})
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Report {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long reportId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private ReportType type;

    @Column(name = "period", nullable = false, length = 20)
    private String period;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "period_end", nullable = false)
    private LocalDate periodEnd;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private Report(Long userId, ReportType type, String period, LocalDate periodStart, LocalDate periodEnd,
            String title, LocalDateTime now) {
        this.userId = userId;
        this.type = type;
        this.period = period;
        this.periodStart = periodStart;
        this.periodEnd = periodEnd;
        this.title = title;
        this.createdAt = now;
    }

    public static Report of(Long userId, ReportType type, String period, LocalDate periodStart, LocalDate periodEnd,
            String title, LocalDateTime now) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자를 지정해야 합니다.");
        }
        if (type == null || period == null || period.isBlank()) {
            throw new IllegalArgumentException("리포트 유형과 기간이 필요합니다.");
        }
        if (periodStart == null || periodEnd == null || periodStart.isAfter(periodEnd)) {
            throw new IllegalArgumentException("기간 시작일은 종료일보다 늦을 수 없습니다.");
        }
        if (title == null || title.isBlank() || now == null) {
            throw new IllegalArgumentException("제목과 생성 시각이 필요합니다.");
        }
        return new Report(userId, type, period, periodStart, periodEnd, title, now);
    }
}
