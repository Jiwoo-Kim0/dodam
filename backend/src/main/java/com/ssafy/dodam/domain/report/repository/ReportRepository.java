package com.ssafy.dodam.domain.report.repository;

import com.ssafy.dodam.domain.report.entity.Report;
import com.ssafy.dodam.domain.report.entity.ReportType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 리포트 상위행 조회. 모든 조회에 `userId` 조건을 건다 — 타인 리포트와 없는 리포트는 모두 빈 결과다.
 *
 * <p>목록 정렬은 `periodStart DESC, reportId DESC`다. `period`는 DAILY(`yyyy-MM-dd`)와 MONTHLY(`yyyy-MM`)가
 * 섞인 문자열이라 문자열 정렬로는 두 유형을 함께 늘어놓을 수 없다.
 */
public interface ReportRepository extends JpaRepository<Report, Long> {

    Optional<Report> findByReportIdAndUserId(Long reportId, Long userId);

    /** 중복 생성 검사용. `(userId, type, period)`는 UNIQUE다. */
    Optional<Report> findByUserIdAndTypeAndPeriod(Long userId, ReportType type, String period);

    /** API 20 #3 목록. `type`이 null이면 조건에서 빠진다. 기간은 리포트가 다루는 구간과 겹치는지로 본다. */
    @Query("""
            select r
              from Report r
             where r.userId = :userId
               and (:type is null or r.type = :type)
               and r.periodEnd >= :fromDate
               and r.periodStart <= :toDate
             order by r.periodStart desc, r.reportId desc
            """)
    Page<Report> findOwned(
            @Param("userId") Long userId,
            @Param("type") ReportType type,
            @Param("fromDate") LocalDate fromDate,
            @Param("toDate") LocalDate toDate,
            Pageable pageable
    );
}
