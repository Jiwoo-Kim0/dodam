package com.ssafy.dodam.domain.report.repository;

import com.ssafy.dodam.domain.report.entity.MonthlyReport;
import org.springframework.data.jpa.repository.JpaRepository;

/** 월간 하위행. 소유권은 상위 {@code report} 행에서 확인한 뒤에만 읽는다. */
public interface MonthlyReportRepository extends JpaRepository<MonthlyReport, Long> {
}
