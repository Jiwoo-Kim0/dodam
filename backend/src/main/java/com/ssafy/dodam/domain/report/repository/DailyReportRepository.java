package com.ssafy.dodam.domain.report.repository;

import com.ssafy.dodam.domain.report.entity.DailyReport;
import org.springframework.data.jpa.repository.JpaRepository;

/** 데일리 하위행. 소유권은 상위 {@code report} 행에서 확인한 뒤에만 읽는다. */
public interface DailyReportRepository extends JpaRepository<DailyReport, Long> {
}
