package com.ssafy.dodam.global.policy;

import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * API 명세서 전반에 반복되는 "조회기간 정책 제한"을 한 곳에서 정의한다.
 *
 * <p>거래·사용내역처럼 기간 조회를 지원하는 모든 도메인이 이 정책을 공유한다.
 */
@Component
public class QueryPeriodPolicy {

    public static final int DEFAULT_PERIOD_MONTHS = 3;
    public static final int MAX_PERIOD_MONTHS = 12;

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    public QueryPeriod resolve(LocalDate startDate, LocalDate endDate) {
        return resolve(startDate, endDate, LocalDate.now(SERVICE_ZONE));
    }

    public QueryPeriod resolve(LocalDate startDate, LocalDate endDate, LocalDate today) {
        LocalDate resolvedEnd = endDate == null ? today : endDate;
        LocalDate resolvedStart = startDate == null
                ? resolvedEnd.minusMonths(DEFAULT_PERIOD_MONTHS).plusDays(1)
                : startDate;

        if (resolvedStart.isAfter(resolvedEnd)) {
            throw new InvalidRequestException("시작일은 종료일보다 늦을 수 없습니다.");
        }
        if (resolvedEnd.isAfter(today)) {
            throw new InvalidRequestException("종료일은 오늘 이후일 수 없습니다.");
        }
        if (resolvedStart.isBefore(resolvedEnd.minusMonths(MAX_PERIOD_MONTHS).plusDays(1))) {
            throw new InvalidRequestException(
                    "조회 기간은 최대 " + MAX_PERIOD_MONTHS + "개월까지 가능합니다."
            );
        }

        return new QueryPeriod(resolvedStart, resolvedEnd);
    }
}
