package com.ssafy.dodam.global.policy;

import java.time.LocalDate;

/**
 * 조회 정책이 적용된 기간. 시작일과 종료일은 모두 포함한다.
 */
public record QueryPeriod(LocalDate startDate, LocalDate endDate) {
}
