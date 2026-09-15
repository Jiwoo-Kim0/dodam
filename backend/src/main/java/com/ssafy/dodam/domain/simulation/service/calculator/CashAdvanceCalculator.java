package com.ssafy.dodam.domain.simulation.service.calculator;

import com.ssafy.dodam.domain.simulation.dto.CashAdvanceInput;
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
 * 현금서비스(POST /simulations/cash-advance). 미이용(전)과 기준일 {@code amount} 이용 후 {@code repaymentMonths}개월 뒤 만기에
 * 원금과 기준일~만기 ACT/365 단리 이자를 일시 상환하는 경우(후)를 비교한다. 중간 월 납부는 없다.
 *
 * <p>기준일 유입 +A와 같은 금액의 부채 증가로 순자산 변화 0, 전체 현금 delta 합은 −이자다. 기본 1개월과 1개월 초과 모두
 * 사용자 지정 상환기간의 추정이며 실제 기관이 그 기간 약정을 제공한다는 뜻이 아니다. 차입금을 어느 목표에 귀속할지
 * 알 수 없어 목표 달성일은 계산하지 않는다({@code GOAL_ALLOCATION_UNKNOWN}).
 */
@Component
public class CashAdvanceCalculator implements SimulationCalculator<CashAdvanceInput> {
    public static final String CALCULATION_POLICY = "BULLET_SIMPLE_INTEREST_ACT_365";

    @Override
    public SimulationType type() {
        return SimulationType.CASH_ADVANCE;
    }

    @Override
    public Class<CashAdvanceInput> inputType() {
        return CashAdvanceInput.class;
    }

    @Override
    public ScenarioComputation compute(SimulationBaseline baseline, CashAdvanceInput input) {
        return SimulationScenarios.exact(() -> build(baseline, input));
    }

    private ScenarioComputation build(SimulationBaseline baseline, CashAdvanceInput input) {
        LocalDate base = baseline.baseDate();
        LocalDate maturity = base.plusMonths(input.repaymentMonths());
        long days = CardScenarios.daysBetween(base, maturity);
        long amount = input.amount();
        long interest = CardScenarios.dailyInterest(amount, input.rate(), days);
        ScheduleRow row = new ScheduleRow(maturity, amount, amount, interest, 0L);
        long totalRepayment = row.payment();

        List<ScenarioCashflow.Event> events = List.of(
                new ScenarioCashflow.Event(base, amount),
                new ScenarioCashflow.Event(maturity, Math.negateExact(totalRepayment)));
        SimulationResult.Cashflow cashflow = ScenarioCashflow.build(base, events, maturity);

        Snapshot before = SimulationScenarios.baselineSnapshot(baseline);
        SimulationScenarios.NotComputableGoals goals = SimulationScenarios.notComputableGoals(baseline,
                SimulationAssumptions.GOAL_ALLOCATION_UNKNOWN);

        List<Item> items = new ArrayList<>();
        items.add(Item.amount("CASH_ADVANCE_AMOUNT", "현금서비스 이용액", 0L, amount));
        items.add(Item.amount("INTEREST_TOTAL", "만기까지 이자", 0L, interest));
        items.add(Item.amount("REPAYMENT_TOTAL", "만기 상환액(원금+이자)", 0L, totalRepayment));
        items.add(Item.date("REPAYMENT_DATE", "상환일", base, maturity));
        items.addAll(CardScenarios.monthlyPaymentItems(base, List.of(), List.of(row), maturity));
        List<Item> assets = List.of(
                Item.amount("INITIAL_CASH_CHANGE", "기준일 현금 변화", 0L, amount),
                Item.amount("INITIAL_NET_ASSET_CHANGE", "기준일 순자산 변화", 0L, 0L));
        List<Item> debts = List.of(Item.amount("INITIAL_DEBT_CHANGE", "기준일 부채 변화", 0L, amount));
        List<Item> costs = List.of(Item.amount("INTEREST_TOTAL", "만기까지 이자", 0L, interest));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("calculationPolicy", CALCULATION_POLICY);
        details.put("inputSource", CardScenarios.INPUT_SOURCE_USER);
        details.put("calculationAsOfDate", base);
        details.put("amount", amount);
        details.put("rate", input.rate());
        details.put("repaymentMonths", input.repaymentMonths());
        details.put("repaymentDate", maturity);
        details.put("days", days);
        details.put("interest", interest);
        details.put("totalPrincipalPaid", amount);
        details.put("totalRepayment", totalRepayment);
        details.put("paidOff", true);
        details.put("evaluatedThrough", maturity);
        details.put("repaymentSchedule", CardScenarios.scheduleMaps(List.of(row)));
        details.put("comparisonTiming", List.of(
                LoanScenarios.timing("INITIAL_CASH_CHANGE", base, LoanScenarios.BASIS_INITIAL_STATE),
                LoanScenarios.timing("INITIAL_DEBT_CHANGE", base, LoanScenarios.BASIS_INITIAL_STATE),
                LoanScenarios.timing("INITIAL_NET_ASSET_CHANGE", base, LoanScenarios.BASIS_INITIAL_STATE),
                LoanScenarios.periodTiming("INTEREST_TOTAL", base, maturity)));

        List<String> assumptions = List.of(SimulationAssumptions.CARD_TERMS_USER_INPUT, SimulationAssumptions.SIMPLE_INTEREST_ACT_365,
                SimulationAssumptions.NO_LATE_FEES_OR_ADDITIONAL_USAGE);

        String title = "현금서비스 " + SimulationScenarios.won(amount) + " · " + input.repaymentMonths() + "개월 · 연 "
                + CardScenarios.percentText(input.rate());
        String summary = days + "일 이자 " + SimulationScenarios.won(interest) + ", " + maturity + " 상환액 "
                + SimulationScenarios.won(totalRepayment);
        SimulationResult result = new SimulationResult(title, summary, before, goals.after(), new Comparison(items, assets, debts, costs),
                cashflow, goals.impacts(), assumptions, details, goals.details());
        return new ScenarioComputation(result, new ScenarioLedger(List.of(), List.of(row.toPayment()), maturity, maturity, true));
    }
}
