package com.ssafy.dodam.domain.simulation.service.calculator;

import com.ssafy.dodam.domain.simulation.dto.SimulationInput;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Cashflow;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.CurrentMonth;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Month;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * 585 유형의 월별 순현금 변화표. 날짜별 사건(가입 원금, 회차 상환 차이, 추가상환 등)을 한 번씩만 월에 합산한다.
 *
 * <p>기준일부터 당월 말까지는 {@code currentMonth}, 다음 달부터 만기 월까지는 {@code months}(최대 120개)다. 누적액은
 * currentMonth부터 시작한다 — 가입 원금·당일 추가상환 같은 시작일 유출을 누락하지 않기 위해서다. 표가 상한에서 끝나면
 * {@code truncated=true}이며 누적액은 표시 기간 합계이지 만기 합계가 아니다.
 */
final class ScenarioCashflow {
    /** 날짜별 순현금 변화. 양수는 유입. */
    record Event(LocalDate date, long delta) {
    }

    private ScenarioCashflow() {
    }

    /**
     * @param fullTermEnd 시나리오 종료일(만기). 표는 이 날짜의 월까지(상한 120개월) 만든다
     */
    static Cashflow build(LocalDate baseDate, List<Event> events, LocalDate fullTermEnd) {
        YearMonth base = YearMonth.from(baseDate);
        TreeMap<YearMonth, Long> byMonth = new TreeMap<>();
        for (Event event : events) {
            if (event.date().isBefore(baseDate)) {
                throw new IllegalArgumentException("기준일 이전 사건은 현금흐름에 넣을 수 없습니다: " + event.date());
            }
            byMonth.merge(YearMonth.from(event.date()), event.delta(), Math::addExact);
        }
        long current = byMonth.getOrDefault(base, 0L);
        long totalMonths = ChronoUnit.MONTHS.between(base, YearMonth.from(fullTermEnd));
        boolean truncated = totalMonths > SimulationInput.MAX_DURATION_MONTHS;
        int shown = (int) Math.min(totalMonths, SimulationInput.MAX_DURATION_MONTHS);
        List<Month> months = new ArrayList<>(shown);
        long cumulative = current;
        for (int i = 1; i <= shown; i++) {
            YearMonth month = base.plusMonths(i);
            long delta = byMonth.getOrDefault(month, 0L);
            cumulative = Math.addExact(cumulative, delta);
            months.add(new Month(month.toString(), delta, cumulative));
        }
        String coveredThrough = shown == 0 ? base.toString() : base.plusMonths(shown).toString();
        return new Cashflow(Cashflow.SCENARIO_NET_CASH_CHANGE, null, null, months,
                new CurrentMonth(base.toString(), current), truncated, coveredThrough, fullTermEnd);
    }

    static boolean isTruncated(SimulationResult.Cashflow cashflow) {
        return Boolean.TRUE.equals(cashflow.truncated());
    }
}
