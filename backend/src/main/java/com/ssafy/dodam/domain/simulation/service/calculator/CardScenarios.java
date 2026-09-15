package com.ssafy.dodam.domain.simulation.service.calculator;

import com.ssafy.dodam.domain.simulation.dto.ScenarioLedger;
import com.ssafy.dodam.domain.simulation.dto.SimulationInput;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Item;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 카드 3유형(할부·리볼빙·현금서비스) 공통 계산. 기획안의 일할 이자식 {@code 잔액 × 연율 / 100 × 경과일수 / 365}(ACT/365,
 * 윤년도 분모 365)를 회차마다 원 단위 HALF_UP으로 확정하고, 합계는 확정된 회차 금액의 합이다.
 *
 * <p>530의 저장된 할부 일정과 달리 신규 행동의 추정치이며 실제 약정·기관별 최소결제액을 재현하지 않는다.
 */
final class CardScenarios {
    static final String INPUT_SOURCE_USER = "USER_INPUT";
    private static final BigDecimal DAYS_PERCENT_YEAR = BigDecimal.valueOf(36500);

    private CardScenarios() {
    }

    /** {@code balance × rate × days / 36500}, 원 단위 HALF_UP. */
    static long dailyInterest(long balance, BigDecimal annualRatePercent, long days) {
        if (balance <= 0 || days <= 0 || annualRatePercent.signum() == 0) {
            return 0L;
        }
        return BigDecimal.valueOf(balance).multiply(annualRatePercent).multiply(BigDecimal.valueOf(days))
                .divide(DAYS_PERCENT_YEAR, 0, RoundingMode.HALF_UP).longValueExact();
    }

    /** {@code ceil(balance × ratio / 100)}. 결제비율 원금 계산에 쓴다. */
    static long ratioCeil(long balance, BigDecimal ratioPercent) {
        return BigDecimal.valueOf(balance).multiply(ratioPercent).divide(BigDecimal.valueOf(100), 0, RoundingMode.CEILING).longValueExact();
    }

    static long daysBetween(LocalDate from, LocalDate to) {
        return ChronoUnit.DAYS.between(from, to);
    }

    /**
     * 공개 상환 일정 한 회차. 모든 유형에서 {@code payment = principalPayment + interest + fee},
     * {@code closingPrincipal = openingPrincipal − principalPayment}가 성립한다. 해당 없는 비용은 0이다.
     */
    record ScheduleRow(LocalDate date, long openingPrincipal, long principalPayment, long interest, long fee) {
        long payment() {
            return Math.addExact(Math.addExact(principalPayment, interest), fee);
        }

        long closingPrincipal() {
            return Math.subtractExact(openingPrincipal, principalPayment);
        }

        ScenarioLedger.Payment toPayment() {
            return new ScenarioLedger.Payment(date, principalPayment, interest, fee);
        }

        Map<String, Object> toMap() {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("date", date);
            row.put("openingPrincipal", openingPrincipal);
            row.put("principalPayment", principalPayment);
            row.put("interest", interest);
            row.put("fee", fee);
            row.put("payment", payment());
            row.put("closingPrincipal", closingPrincipal());
            return row;
        }
    }

    static List<Map<String, Object>> scheduleMaps(List<ScheduleRow> rows) {
        List<Map<String, Object>> maps = new ArrayList<>(rows.size());
        for (ScheduleRow row : rows) {
            maps.add(row.toMap());
        }
        return maps;
    }

    static List<ScenarioLedger.Payment> payments(List<ScheduleRow> rows) {
        List<ScenarioLedger.Payment> payments = new ArrayList<>(rows.size());
        for (ScheduleRow row : rows) {
            payments.add(row.toPayment());
        }
        return payments;
    }

    static long sumPayments(List<ScheduleRow> rows) {
        long total = 0;
        for (ScheduleRow row : rows) {
            total = Math.addExact(total, row.payment());
        }
        return total;
    }

    static long sumInterest(List<ScheduleRow> rows) {
        long total = 0;
        for (ScheduleRow row : rows) {
            total = Math.addExact(total, row.interest());
        }
        return total;
    }

    static long sumFee(List<ScheduleRow> rows) {
        long total = 0;
        for (ScheduleRow row : rows) {
            total = Math.addExact(total, row.fee());
        }
        return total;
    }

    static long sumPrincipal(List<ScheduleRow> rows) {
        long total = 0;
        for (ScheduleRow row : rows) {
            total = Math.addExact(total, row.principalPayment());
        }
        return total;
    }

    /** {@code MONTHLY_PAYMENT_yyyy_MM} 전후 항목. 기준일 다음 달부터 마지막 회차 월까지(최대 120개월), 회차가 없는 달은 0. */
    static List<Item> monthlyPaymentItems(LocalDate baseDate, List<ScheduleRow> before, List<ScheduleRow> after, LocalDate end) {
        TreeMap<YearMonth, Long> beforeByMonth = paymentByMonth(before);
        TreeMap<YearMonth, Long> afterByMonth = paymentByMonth(after);
        YearMonth base = YearMonth.from(baseDate);
        long months = Math.min(ChronoUnit.MONTHS.between(base, YearMonth.from(end)), SimulationInput.MAX_DURATION_MONTHS);
        List<Item> items = new ArrayList<>();
        for (int i = 1; i <= months; i++) {
            YearMonth month = base.plusMonths(i);
            items.add(Item.amount("MONTHLY_PAYMENT_" + month.toString().replace('-', '_'), month + " 월 납부액",
                    beforeByMonth.getOrDefault(month, 0L), afterByMonth.getOrDefault(month, 0L)));
        }
        return items;
    }

    private static TreeMap<YearMonth, Long> paymentByMonth(List<ScheduleRow> rows) {
        TreeMap<YearMonth, Long> byMonth = new TreeMap<>();
        for (ScheduleRow row : rows) {
            byMonth.merge(YearMonth.from(row.date()), row.payment(), Math::addExact);
        }
        return byMonth;
    }

    static String percentText(BigDecimal rate) {
        return rate.stripTrailingZeros().toPlainString() + "%";
    }
}
