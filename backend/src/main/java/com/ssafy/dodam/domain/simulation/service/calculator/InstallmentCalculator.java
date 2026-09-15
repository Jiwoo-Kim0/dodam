package com.ssafy.dodam.domain.simulation.service.calculator;

import com.ssafy.dodam.domain.simulation.dto.InstallmentInput;
import com.ssafy.dodam.domain.simulation.dto.ScenarioComputation;
import com.ssafy.dodam.domain.simulation.dto.ScenarioLedger;
import com.ssafy.dodam.domain.simulation.dto.SimulationBaseline;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Comparison;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Item;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Snapshot;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import com.ssafy.dodam.domain.simulation.service.SimulationAssumptions;
import com.ssafy.dodam.domain.simulation.service.SimulationCalculator;
import com.ssafy.dodam.domain.simulation.service.calculator.CardScenarios.ScheduleRow;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 할부(POST /simulations/installment). 신규 구매를 기준일 일시불로 결제하는 경우(전)와 다음 달부터 {@code months}회
 * 원금 균등 상환하며 잔액 기준 수수료를 내는 경우(후)를 비교한다.
 *
 * <p>회차 k(1..n)의 날짜는 {@code 기준일.plusMonths(k)}(매번 최초 날짜에서 구해 1/31 → 2월 말 → 3/31로 복원), 원금은
 * {@code floor(P / n)}이고 마지막 회차가 잔액 전액이다. 수수료는 회차 시작 잔액에 직전 회차일부터의 실제 일수로
 * ACT/365 일할 계산한다({@code SIMPLE_INTEREST_ACT_365}). 원금보다 개월 수가 커서 앞 회차 원금이 0인 경우도 허용한다.
 *
 * <p>현금 delta는 기준일 +P(결제하지 않은 현금), 각 회차 −(원금+수수료)이며 전체 합은 −총수수료다. 기준일 부채 +P.
 * 같은 구매 물품의 가치는 전후 공통이므로 자산에 다시 더하지 않는다. 자금 귀속을 알 수 없어 목표 달성일은 계산하지 않는다
 * ({@code GOAL_ALLOCATION_UNKNOWN}). 530의 저장된 할부 일정 조회를 신규 구매 계산기로 재사용하지 않는다.
 */
@Component
public class InstallmentCalculator implements SimulationCalculator<InstallmentInput> {
    public static final String CALCULATION_POLICY = "EQUAL_PRINCIPAL_FEE_ON_BALANCE_ACT_365";

    @Override
    public SimulationType type() {
        return SimulationType.INSTALLMENT;
    }

    @Override
    public Class<InstallmentInput> inputType() {
        return InstallmentInput.class;
    }

    @Override
    public ScenarioComputation compute(SimulationBaseline baseline, InstallmentInput input) {
        return SimulationScenarios.exact(() -> build(baseline, input));
    }

    private ScenarioComputation build(SimulationBaseline baseline, InstallmentInput input) {
        LocalDate base = baseline.baseDate();
        long amount = input.purchaseAmount();
        int n = input.months();
        long basePrincipal = amount / n;
        List<ScheduleRow> schedule = new ArrayList<>(n);
        long remaining = amount;
        LocalDate previous = base;
        for (int k = 1; k <= n; k++) {
            LocalDate date = base.plusMonths(k);
            long principal = k == n ? remaining : basePrincipal;
            long fee = CardScenarios.dailyInterest(remaining, input.feeRate(), CardScenarios.daysBetween(previous, date));
            schedule.add(new ScheduleRow(date, remaining, principal, 0L, fee));
            remaining -= principal;
            previous = date;
        }
        LocalDate last = schedule.get(n - 1).date();
        long totalFee = CardScenarios.sumFee(schedule);
        long totalPayment = CardScenarios.sumPayments(schedule);

        List<ScenarioCashflow.Event> events = new ArrayList<>();
        events.add(new ScenarioCashflow.Event(base, amount));
        for (ScheduleRow row : schedule) {
            events.add(new ScenarioCashflow.Event(row.date(), Math.negateExact(row.payment())));
        }
        SimulationResult.Cashflow cashflow = ScenarioCashflow.build(base, events, last);

        Snapshot before = SimulationScenarios.baselineSnapshot(baseline);
        SimulationScenarios.NotComputableGoals goals = SimulationScenarios.notComputableGoals(baseline,
                SimulationAssumptions.GOAL_ALLOCATION_UNKNOWN);

        List<Item> items = new ArrayList<>();
        items.add(Item.amount("INITIAL_PAYMENT", "기준일 결제액", amount, 0L));
        items.add(Item.amount("INSTALLMENT_FEE_TOTAL", "할부 수수료 합계", 0L, totalFee));
        items.add(Item.amount("PAYMENT_TOTAL", "총 납부액", amount, Math.addExact(amount, totalFee)));
        items.add(Item.amount("FIRST_INSTALLMENT_PAYMENT", "첫 회차 납부액", 0L, schedule.get(0).payment()));
        items.addAll(CardScenarios.monthlyPaymentItems(base, List.of(), schedule, last));
        List<Item> assets = List.of(
                Item.amount("INITIAL_CASH_CHANGE", "기준일 현금 변화(결제 유예)", 0L, amount),
                Item.amount("INITIAL_NET_ASSET_CHANGE", "기준일 순자산 변화", 0L, 0L));
        List<Item> debts = List.of(Item.amount("INITIAL_DEBT_CHANGE", "기준일 부채 변화", 0L, amount));
        List<Item> costs = List.of(Item.amount("INSTALLMENT_FEE_TOTAL", "할부 수수료 합계", 0L, totalFee));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("calculationPolicy", CALCULATION_POLICY);
        details.put("inputSource", CardScenarios.INPUT_SOURCE_USER);
        details.put("calculationAsOfDate", base);
        details.put("purchaseAmount", amount);
        details.put("months", n);
        details.put("feeRate", input.feeRate());
        details.put("feeRateMode", input.feeRateMode());
        details.put("firstPaymentDate", schedule.get(0).date());
        details.put("lastPaymentDate", last);
        details.put("installmentCount", n);
        details.put("monthlyPrincipal", basePrincipal);
        details.put("totalPrincipalPaid", amount);
        details.put("totalFee", totalFee);
        details.put("totalPayment", totalPayment);
        details.put("paidOff", true);
        details.put("evaluatedThrough", last);
        details.put("repaymentSchedule", CardScenarios.scheduleMaps(schedule));
        details.put("comparisonTiming", List.of(
                LoanScenarios.timing("INITIAL_CASH_CHANGE", base, LoanScenarios.BASIS_INITIAL_STATE),
                LoanScenarios.timing("INITIAL_DEBT_CHANGE", base, LoanScenarios.BASIS_INITIAL_STATE),
                LoanScenarios.timing("INITIAL_NET_ASSET_CHANGE", base, LoanScenarios.BASIS_INITIAL_STATE),
                LoanScenarios.periodTiming("INSTALLMENT_FEE_TOTAL", base, last)));

        List<String> assumptions = new ArrayList<>(List.of(SimulationAssumptions.CARD_TERMS_USER_INPUT,
                SimulationAssumptions.SIMPLE_INTEREST_ACT_365, SimulationAssumptions.NO_LATE_FEES_OR_ADDITIONAL_USAGE));
        if (InstallmentInput.MODE_ASSUMED_ZERO.equals(input.feeRateMode())) {
            assumptions.add(SimulationAssumptions.ZERO_FEE_ASSUMED);
        }

        String title = "할부 " + SimulationScenarios.won(amount) + " · " + n + "개월 " + CardScenarios.percentText(input.feeRate());
        String summary = "일시불 대비 수수료 " + SimulationScenarios.won(totalFee) + ", 첫 회차 "
                + SimulationScenarios.won(schedule.get(0).payment()) + " (" + schedule.get(0).date() + "), 마지막 회차 " + last
                + (InstallmentInput.MODE_ASSUMED_ZERO.equals(input.feeRateMode()) ? " · 수수료율 생략(0% 가정)" : "");
        SimulationResult result = new SimulationResult(title, summary, before, goals.after(), new Comparison(items, assets, debts, costs),
                cashflow, goals.impacts(), assumptions, details, goals.details());
        // 전: 기준일 원금 전액 결제(비용 0). 후: 회차 원금+수수료
        ScenarioLedger ledger = new ScenarioLedger(List.of(new ScenarioLedger.Payment(base, amount, 0L, 0L)),
                CardScenarios.payments(schedule), last, last, true);
        return new ScenarioComputation(result, ledger);
    }
}
