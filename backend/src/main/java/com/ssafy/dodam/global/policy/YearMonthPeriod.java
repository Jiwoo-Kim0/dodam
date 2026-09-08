package com.ssafy.dodam.global.policy;

import java.time.YearMonth;

/**
 * 월 단위 조회 정책이 적용된 기간. 시작월과 종료월은 모두 포함한다.
 */
public record YearMonthPeriod(YearMonth startMonth, YearMonth endMonth) {
}
