package com.ssafy.dodam.domain.report.service;

import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * 리포트가 다룰 수 있는 기간(FE 협의안 4번 "과거 월 조회 가능 범위" 확정본).
 *
 * <p>리포트는 <b>끝난 기간</b>만 만든다. 진행 중인 오늘·이번 달을 리포트로 내보내면 같은 기간을 볼 때마다
 * 숫자가 달라진다. 그래서 기본값은 어제(데일리)와 직전 완료 월(월간)이고, 오늘 이후·이번 달은 400이다.
 *
 * <p>과거 상한은 데이터 보관·성능이 아니라 의미 때문이다. 금융망 동기화가 닿지 않는 과거 구간은 거래가
 * 비어 있어 "소비 0원" 리포트가 만들어진다. 상한을 넘는 요청은 빈 리포트를 만들지 않고 400으로 돌려보낸다.
 */
@Component
public class ReportPeriodPolicy {

    /** 월간 리포트를 만들 수 있는 가장 오래된 월(기준월로부터). */
    public static final int MAX_MONTHS_BACK = 24;
    /** 데일리 브리핑을 만들 수 있는 가장 오래된 날(오늘로부터). */
    public static final int MAX_DAYS_BACK = 400;
    /** 예정 비용을 집계할 수 있는 가장 먼 미래 월(이번 달로부터). 현금흐름 수집기의 상한과 같다. */
    public static final int MAX_UPCOMING_MONTHS_AHEAD = 12;

    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile("\\d{4}-(0[1-9]|1[0-2])");

    /** 월간 리포트의 기준월. 생략하면 직전 완료 월이다. */
    public YearMonth resolveMonth(String yearMonth, LocalDate today) {
        YearMonth current = YearMonth.from(today);
        if (yearMonth == null) {
            return current.minusMonths(1);
        }
        YearMonth resolved = parse(yearMonth);
        if (!resolved.isBefore(current)) {
            throw new InvalidRequestException("아직 끝나지 않은 월의 리포트는 만들 수 없습니다. 직전 완료 월까지 조회할 수 있습니다.");
        }
        if (ChronoUnit.MONTHS.between(resolved, current) > MAX_MONTHS_BACK) {
            throw new InvalidRequestException("리포트는 최근 " + MAX_MONTHS_BACK + "개월까지 조회할 수 있습니다.");
        }
        return resolved;
    }

    /** 데일리 브리핑의 기준일. 생략하면 어제다 — 브리핑은 "전날에 대한" 요약이다. */
    public LocalDate resolveDate(LocalDate date, LocalDate today) {
        LocalDate resolved = date == null ? today.minusDays(1) : date;
        if (!resolved.isBefore(today)) {
            throw new InvalidRequestException("아직 끝나지 않은 날의 브리핑은 만들 수 없습니다. 어제까지 조회할 수 있습니다.");
        }
        if (ChronoUnit.DAYS.between(resolved, today) > MAX_DAYS_BACK) {
            throw new InvalidRequestException("브리핑은 최근 " + MAX_DAYS_BACK + "일까지 조회할 수 있습니다.");
        }
        return resolved;
    }

    /**
     * 예정 비용을 집계할 월. 생략하면 <b>오늘의 다음 달</b>이다.
     *
     * <p>과거 월도 형식만 맞으면 받는다. 이미 지난 구간이라는 사실은 400이 아니라 응답의
     * {@code unavailableReason}으로 알린다 — 지난 달 리포트를 보는 화면이 예정 비용 카드 때문에 통째로
     * 실패하면 안 된다.
     */
    public YearMonth resolveUpcomingMonth(String yearMonth, LocalDate today) {
        YearMonth current = YearMonth.from(today);
        if (yearMonth == null) {
            return current.plusMonths(1);
        }
        YearMonth resolved = parse(yearMonth);
        if (ChronoUnit.MONTHS.between(current, resolved) > MAX_UPCOMING_MONTHS_AHEAD) {
            throw new InvalidRequestException("예정 비용은 " + MAX_UPCOMING_MONTHS_AHEAD + "개월 뒤까지 조회할 수 있습니다.");
        }
        return resolved;
    }

    private static YearMonth parse(String value) {
        if (!YEAR_MONTH_PATTERN.matcher(value).matches()) {
            throw new InvalidRequestException("yearMonth는 YYYY-MM 형식의 유효한 연월이어야 합니다.");
        }
        return YearMonth.parse(value);
    }
}
