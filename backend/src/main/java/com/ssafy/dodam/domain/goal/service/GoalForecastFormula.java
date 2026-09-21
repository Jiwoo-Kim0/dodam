package com.ssafy.dodam.domain.goal.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 모으기 목표 예상 달성일의 단일 식(S15P21D105-658). Goal API 510의 예상 달성일, 511 변화 이력의 {@code forecast_date},
 * 진단 546 goal-impact, 시뮬레이션의 목표 영향({@code GoalAchievementProjection}·{@code GoalDateProjection}·복합 투영)이 모두 여기를 쓴다.
 *
 * <p><b>일 단위로 계산한다.</b> 예전 식 {@code 기준일 + ceil(남은 금액 / 속도)개월}은 월 단위 올림이라, 월 333만원 속도의 목표에서
 * 월 5만원을 더 모아도(속도 1.5% 증가) 달성일이 한 달 단위로만 움직여 "변화 없음"으로 나왔다. 기획안의 「예상 목표 달성일 2028.12.20 →
 * 2028.12.02」처럼 며칠 단위로 움직이는 그림을 만들려면 마지막 달을 일할로 나눠야 한다.
 *
 * <p><b>축은 달력 개월이다.</b> {@code t(k) = 기준일.plusMonths(k)}(최초 기준일에서 매번 구해 1/31 → 2/28 → 3/31로 말일이 복원된다).
 * 남은 금액을 속도로 나눈 정수 개월은 이 축 위의 t(whole)이고, 나머지 금액은 {@code (t(whole), t(whole+1)]} 구간의 실제 일수에
 * 비례해 올림한 날짜다. 시뮬레이션의 투영 루프도 같은 축의 같은 구간 안에서 같은 비례식을 쓰므로, 속도가 변하지 않으면 전·후 날짜가
 * 하루도 어긋나지 않는다(0 변화에서 before≠after가 생기면 "지연 1일"이라는 거짓 결과가 나온다).
 *
 * <p>남은 금액이 없으면 기준일, 속도가 0 이하면 null(현재 속도로는 달성 불가). 이 두 경계는 예전 식과 같다.
 */
public final class GoalForecastFormula {
    private GoalForecastFormula() {
    }

    /**
     * @param remaining   남은 금액
     * @param monthlyPace 월 저축속도
     * @param base        기준일
     * @return 예상 달성일. 남은 금액 ≤ 0이면 base, 속도 ≤ 0이면 null
     */
    public static LocalDate expectedDate(long remaining, long monthlyPace, LocalDate base) {
        return expectedDate(remaining, monthlyPace, base, 0);
    }

    /**
     * 기준일 축 위 {@code offsetMonths}개월 지점부터 {@code remaining}을 {@code monthlyPace}로 모을 때의 달성일. 투영 루프가 K개월을
     * 지난 뒤 기준 속도로 이어 계산할 때 쓴다 — {@code t(K)}에서 새 축을 시작하면 말일 복원이 어긋나 {@code base.plusMonths(K + n)}과
     * 다른 날이 나온다.
     */
    public static LocalDate expectedDate(long remaining, long monthlyPace, LocalDate base, long offsetMonths) {
        if (remaining <= 0) {
            return base.plusMonths(offsetMonths);
        }
        if (monthlyPace <= 0) {
            return null;
        }
        long wholeMonths = remaining / monthlyPace;
        long rest = remaining - wholeMonths * monthlyPace;
        LocalDate at = base.plusMonths(offsetMonths + wholeMonths);
        if (rest == 0) {
            return at;
        }
        return dateWithin(at, base.plusMonths(offsetMonths + wholeMonths + 1), rest, monthlyPace);
    }

    /**
     * 구간 {@code (from, to]}에서 {@code amount}를 고르게 모을 때 {@code remaining}이 채워지는 날. 구간 일수에 비례해 올림한다.
     * {@code remaining ≥ amount}면 to, {@code remaining ≤ 0}이면 from.
     */
    public static LocalDate dateWithin(LocalDate from, LocalDate to, long remaining, long amount) {
        if (remaining <= 0) {
            return from;
        }
        if (amount <= 0 || remaining >= amount) {
            return to;
        }
        long days = ChronoUnit.DAYS.between(from, to);
        return from.plusDays(ceilDivide(Math.multiplyExact(remaining, days), amount));
    }

    /** 남은 금액 / 속도의 올림. 응답 {@code monthsToAchieve}("이 속도로 몇 달 더")에 쓴다. 속도 ≤ 0이면 호출하지 않는다. */
    public static long monthsToAchieve(long remaining, long monthlyPace) {
        if (remaining <= 0) {
            return 0;
        }
        return ceilDivide(remaining, monthlyPace);
    }

    static long ceilDivide(long amount, long divisor) {
        return (amount + divisor - 1) / divisor;
    }
}
