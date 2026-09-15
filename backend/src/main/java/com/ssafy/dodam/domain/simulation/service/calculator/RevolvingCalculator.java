package com.ssafy.dodam.domain.simulation.service.calculator;

import com.ssafy.dodam.domain.simulation.dto.RevolvingInput;
import com.ssafy.dodam.domain.simulation.dto.ScenarioComputation;
import com.ssafy.dodam.domain.simulation.dto.ScenarioLedger;
import com.ssafy.dodam.domain.simulation.dto.SimulationBaseline;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Cashflow;
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
 * 리볼빙(POST /simulations/revolving). 기준일 카드대금 {@code balance}를 전액 결제하는 경우(전)와 결제비율만큼 결제하고
 * 나머지를 이월하는 경우(후)를 비교한다.
 *
 * <p>{@code PRINCIPAL_RATIO_WITH_INTEREST_PAID_SEPARATELY} 모형: 기준일 원금 결제 {@code min(B, max(1, ceil(B × p / 100)))},
 * 이후 회차 k의 날짜는 {@code 기준일.plusMonths(k)}, 이자는 회차 시작 잔액에 직전 회차일부터의 실제 일수로 ACT/365, 원금은
 * 같은 결제비율 식, 이자는 매회 전액 납부(이자에 이자를 붙이지 않는다). 최초 결제 전의 과거 이자는 계산하지 않는다.
 * 금융기관의 실제 약정결제비율 계산·최소청구액은 재현하지 않는다.
 *
 * <p>최대 120회차까지만 계산한다. 잔액이 남으면 강제 전액 상환으로 조작하지 않고 {@code paidOff=false}, 잔액, 평가 종료일,
 * {@code terminationReason=HORIZON_LIMIT}을 제공하며 이후 이자는 미상이다. 결제비율 100%면 기준일 전액 결제·이자 0·일정 없음이다.
 * 기준일 현금 delta는 +이월액, 이후 −(원금+이자)이며 계산 기간 합은 {@code 잔액 − 누적이자}다. 미상환 잔액을 이익으로 표시하지 않는다.
 */
@Component
public class RevolvingCalculator implements SimulationCalculator<RevolvingInput> {
    public static final String CALCULATION_POLICY = "PRINCIPAL_RATIO_WITH_INTEREST_PAID_SEPARATELY";
    public static final String TERMINATION_PAID_OFF = "PAID_OFF";
    public static final String TERMINATION_HORIZON_LIMIT = "HORIZON_LIMIT";
    public static final String TERMINATION_NO_CARRY_OVER = "NO_CARRY_OVER";

    @Override
    public SimulationType type() {
        return SimulationType.REVOLVING;
    }

    @Override
    public Class<RevolvingInput> inputType() {
        return RevolvingInput.class;
    }

    @Override
    public ScenarioComputation compute(SimulationBaseline baseline, RevolvingInput input) {
        return SimulationScenarios.exact(() -> build(baseline, input));
    }

    private ScenarioComputation build(SimulationBaseline baseline, RevolvingInput input) {
        LocalDate base = baseline.baseDate();
        long balance = input.balance();
        long initialPayment = Math.min(balance, Math.max(1L, CardScenarios.ratioCeil(balance, input.paymentRatio())));
        long carried = balance - initialPayment;

        List<ScheduleRow> schedule = new ArrayList<>();
        long remaining = carried;
        LocalDate previous = base;
        int k = 0;
        while (remaining > 0 && k < RevolvingInput.MAX_INSTALLMENTS) {
            k++;
            LocalDate date = base.plusMonths(k);
            long interest = CardScenarios.dailyInterest(remaining, input.rate(), CardScenarios.daysBetween(previous, date));
            long principal = Math.min(remaining, Math.max(1L, CardScenarios.ratioCeil(remaining, input.paymentRatio())));
            schedule.add(new ScheduleRow(date, remaining, principal, interest, 0L));
            remaining -= principal;
            previous = date;
        }
        boolean paidOff = remaining == 0;
        LocalDate evaluatedThrough = schedule.isEmpty() ? base : schedule.get(schedule.size() - 1).date();
        String termination = carried == 0 ? TERMINATION_NO_CARRY_OVER : paidOff ? TERMINATION_PAID_OFF : TERMINATION_HORIZON_LIMIT;
        long totalInterest = CardScenarios.sumInterest(schedule);
        long principalPaid = Math.addExact(initialPayment, CardScenarios.sumPrincipal(schedule));
        long totalPayment = Math.addExact(initialPayment, CardScenarios.sumPayments(schedule));

        List<ScenarioCashflow.Event> events = new ArrayList<>();
        events.add(new ScenarioCashflow.Event(base, carried));
        for (ScheduleRow row : schedule) {
            events.add(new ScenarioCashflow.Event(row.date(), Math.negateExact(row.payment())));
        }
        Cashflow table = ScenarioCashflow.build(base, events, evaluatedThrough);
        // 완납 시점을 모르면 종료일을 비워 두고 평가 종료일은 details에만 둔다. 표시기간 잘림(truncated)과는 다른 뜻이다
        Cashflow cashflow = paidOff ? table
                : new Cashflow(table.basis(), table.before(), table.after(), table.months(), table.currentMonth(), table.truncated(),
                        table.coveredThrough(), null);

        Snapshot before = SimulationScenarios.baselineSnapshot(baseline);
        SimulationScenarios.NotComputableGoals goals = SimulationScenarios.notComputableGoals(baseline,
                SimulationAssumptions.GOAL_ALLOCATION_UNKNOWN);

        List<Item> items = new ArrayList<>();
        items.add(Item.amount("INITIAL_PAYMENT", "기준일 결제액", balance, initialPayment));
        items.add(Item.amount("CARRIED_BALANCE", "이월 잔액", 0L, carried));
        items.add(Item.amount("REVOLVING_INTEREST_TOTAL", paidOff ? "완납까지 이자 합계" : "계산 기간 이자 합계", 0L, totalInterest));
        items.add(Item.amount("PAYMENT_TOTAL", paidOff ? "총 납부액" : "계산 기간 총 납부액", balance, totalPayment));
        items.add(Item.amount("REMAINING_BALANCE", "계산 종료 시 잔액", 0L, remaining));
        items.add(Item.date("PAYOFF_DATE", "완납일", base, paidOff ? evaluatedThrough : null));
        items.addAll(CardScenarios.monthlyPaymentItems(base, List.of(), schedule, evaluatedThrough));
        List<Item> assets = List.of(
                Item.amount("INITIAL_CASH_CHANGE", "기준일 현금 변화(이월액)", 0L, carried),
                Item.amount("INITIAL_NET_ASSET_CHANGE", "기준일 순자산 변화", 0L, 0L));
        List<Item> debts = List.of(Item.amount("INITIAL_DEBT_CHANGE", "기준일 부채 변화(이월 잔액)", 0L, carried));
        List<Item> costs = List.of(Item.amount("REVOLVING_INTEREST_TOTAL", paidOff ? "완납까지 이자 합계" : "계산 기간 이자 합계",
                0L, totalInterest));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("calculationPolicy", CALCULATION_POLICY);
        details.put("inputSource", CardScenarios.INPUT_SOURCE_USER);
        details.put("calculationAsOfDate", base);
        details.put("cardId", input.cardId());
        details.put("balance", balance);
        details.put("paymentRatio", input.paymentRatio());
        details.put("rate", input.rate());
        details.put("initialPayment", initialPayment);
        details.put("carriedBalance", carried);
        details.put("installmentCount", schedule.size());
        details.put("paidOff", paidOff);
        details.put("remainingBalance", remaining);
        details.put("evaluatedThrough", evaluatedThrough);
        details.put("terminationReason", termination);
        details.put("totalPrincipalPaid", principalPaid);
        details.put("totalInterest", totalInterest);
        details.put("totalPayment", totalPayment);
        details.put("repaymentSchedule", CardScenarios.scheduleMaps(schedule));
        details.put("comparisonTiming", List.of(
                LoanScenarios.timing("INITIAL_CASH_CHANGE", base, LoanScenarios.BASIS_INITIAL_STATE),
                LoanScenarios.timing("INITIAL_DEBT_CHANGE", base, LoanScenarios.BASIS_INITIAL_STATE),
                LoanScenarios.timing("INITIAL_NET_ASSET_CHANGE", base, LoanScenarios.BASIS_INITIAL_STATE),
                LoanScenarios.periodTiming("REVOLVING_INTEREST_TOTAL", base, evaluatedThrough)));

        List<String> assumptions = new ArrayList<>(List.of(SimulationAssumptions.CARD_TERMS_USER_INPUT,
                SimulationAssumptions.SIMPLE_INTEREST_ACT_365, SimulationAssumptions.NO_LATE_FEES_OR_ADDITIONAL_USAGE));
        if (!paidOff) {
            assumptions.add(SimulationAssumptions.REVOLVING_HORIZON_LIMIT);
        }

        String title = "리볼빙 " + SimulationScenarios.won(balance) + " · 결제비율 " + CardScenarios.percentText(input.paymentRatio())
                + " · 연 " + CardScenarios.percentText(input.rate());
        String summary = "기준일 결제 " + SimulationScenarios.won(initialPayment) + ", 이월 " + SimulationScenarios.won(carried)
                + (carried == 0 ? ", 이자 없음"
                        : paidOff ? ", 완납 " + evaluatedThrough + "까지 이자 " + SimulationScenarios.won(totalInterest)
                                : ", 120회차(" + evaluatedThrough + ")까지 이자 " + SimulationScenarios.won(totalInterest)
                                        + " · 잔액 " + SimulationScenarios.won(remaining) + " 미완납");
        SimulationResult result = new SimulationResult(title, summary, before, goals.after(), new Comparison(items, assets, debts, costs),
                cashflow, goals.impacts(), assumptions, details, goals.details());
        // 전: 기준일 전액 결제. 후: 기준일 일부 결제 + 회차 원금·이자. 미완납이면 evaluatedThrough 이후 비용은 미상
        List<ScenarioLedger.Payment> afterPayments = new ArrayList<>();
        afterPayments.add(new ScenarioLedger.Payment(base, initialPayment, 0L, 0L));
        afterPayments.addAll(CardScenarios.payments(schedule));
        ScenarioLedger ledger = new ScenarioLedger(List.of(new ScenarioLedger.Payment(base, balance, 0L, 0L)), afterPayments,
                paidOff ? evaluatedThrough : null, evaluatedThrough, paidOff);
        return new ScenarioComputation(result, ledger);
    }
}
