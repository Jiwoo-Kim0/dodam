package com.ssafy.dodam.domain.simulation.service.calculator;

import com.ssafy.dodam.domain.loan.service.LoanRepaymentScheduleCalculator;
import com.ssafy.dodam.domain.loan.service.LoanRepaymentScheduleItem;
import com.ssafy.dodam.domain.loan.service.LoanRepaymentTerms;
import com.ssafy.dodam.domain.simulation.dto.LoanNewInput;
import com.ssafy.dodam.domain.simulation.dto.ProductSnapshot;
import com.ssafy.dodam.domain.simulation.dto.ScenarioComputation;
import com.ssafy.dodam.domain.simulation.dto.ScenarioLedger;
import com.ssafy.dodam.domain.simulation.dto.SimulationBaseline;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Comparison;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Item;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Snapshot;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import com.ssafy.dodam.domain.simulation.service.ProductOptionSelector;
import com.ssafy.dodam.domain.simulation.service.SimulationAssumptions;
import com.ssafy.dodam.domain.simulation.service.SimulationCalculator;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 신규 대출(POST /simulations/loan/new). 상품 옵션의 공시 최저금리로 만기까지의 상환 일정을 만든다.
 *
 * <p>첫 회차는 기준일 + 1개월, 마지막 회차(만기)는 기준일 + term개월이며 약정일은 기준일의 일자다(31일은 말일 보정 후
 * 다음 달에 복원). 회차 계산은 기존 {@link LoanRepaymentScheduleCalculator}(월이율 = 연이율/1200, 회차 이자 HALF_UP,
 * 마지막 회차 잔액 보정)를 그대로 쓴다.
 *
 * <p>차입 원금 유입은 현금과 부채가 함께 늘어 순자산 변화 0이며 소득·수익이 아니다. 비용은 만기까지의 이자 합계다.
 * 승인 가능 여부·실제 적용금리는 제공하지 않는다({@code LOAN_RATE_PUBLISHED_MINIMUM}). 차입금을 어느 목표에 귀속할지
 * 알 수 없어 목표 달성일은 계산하지 않는다({@code GOAL_ALLOCATION_UNKNOWN}).
 */
@Component
public class LoanNewCalculator implements SimulationCalculator<LoanNewInput> {
    private final LoanRepaymentScheduleCalculator scheduleCalculator;

    public LoanNewCalculator(LoanRepaymentScheduleCalculator scheduleCalculator) {
        this.scheduleCalculator = scheduleCalculator;
    }

    @Override
    public SimulationType type() {
        return SimulationType.LOAN_NEW;
    }

    @Override
    public Class<LoanNewInput> inputType() {
        return LoanNewInput.class;
    }

    @Override
    public ScenarioComputation compute(SimulationBaseline baseline, LoanNewInput input) {
        ProductSnapshot product = Objects.requireNonNull(input.product(), "product 스냅샷이 준비되지 않았습니다.");
        return SimulationScenarios.exact(() -> build(baseline, input, product));
    }

    private ScenarioComputation build(SimulationBaseline baseline, LoanNewInput input, ProductSnapshot product) {
        ProductOptionSelector.Selection selection = ProductOptionSelector.selectLoan(product, input.term(), input.repaymentType(),
                input.principal());
        LocalDate base = baseline.baseDate();
        LocalDate first = base.plusMonths(1);
        LocalDate maturity = base.plusMonths(input.term());
        LoanRepaymentTerms terms = new LoanRepaymentTerms(input.principal(), selection.appliedRate(),
                selection.resolvedRepaymentType(), base.getDayOfMonth(), first, maturity);
        List<LoanRepaymentScheduleItem> schedule = LoanScenarios.schedule(scheduleCalculator, terms, base);
        long totalInterest = LoanScenarios.totalInterest(schedule);
        long totalRepayment = LoanScenarios.totalAmount(schedule);

        List<ScenarioCashflow.Event> events = new ArrayList<>();
        events.add(new ScenarioCashflow.Event(base, input.principal()));
        for (LoanRepaymentScheduleItem item : schedule) {
            events.add(new ScenarioCashflow.Event(item.repaymentDate(), Math.negateExact(item.amount())));
        }
        SimulationResult.Cashflow cashflow = ScenarioCashflow.build(base, events, maturity);

        Snapshot before = SimulationScenarios.baselineSnapshot(baseline);
        SimulationScenarios.NotComputableGoals goals = SimulationScenarios.notComputableGoals(baseline,
                SimulationAssumptions.GOAL_ALLOCATION_UNKNOWN);

        List<Item> items = new ArrayList<>();
        items.add(Item.amount("LOAN_PRINCIPAL", "대출 원금", 0L, input.principal()));
        items.add(Item.amount("INTEREST_TOTAL", "만기까지 총 이자", 0L, totalInterest));
        items.add(Item.amount("REPAYMENT_TOTAL", "만기까지 총 상환액", 0L, totalRepayment));
        items.add(Item.amount("FIRST_MONTHLY_REPAYMENT", "첫 회차 상환액", 0L, schedule.get(0).amount()));
        items.addAll(LoanScenarios.monthlyRepaymentItems(base, List.of(), schedule, maturity));
        List<Item> assets = List.of(
                Item.amount("INITIAL_CASH_CHANGE", "기준일 현금 변화", 0L, input.principal()),
                Item.amount("INITIAL_NET_ASSET_CHANGE", "기준일 순자산 변화", 0L, 0L));
        List<Item> debts = List.of(Item.amount("INITIAL_DEBT_CHANGE", "기준일 부채 변화", 0L, input.principal()));
        List<Item> costs = List.of(Item.amount("INTEREST_TOTAL", "만기까지 총 이자", 0L, totalInterest));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("dataSource", LoanScenarios.DATA_SOURCE_PRODUCT_DB);
        details.put("calculationAsOfDate", base);
        details.put("product", LoanScenarios.productDetails(product, selection));
        details.put("appliedRate", selection.appliedRate());
        details.put("principal", input.principal());
        details.put("term", input.term());
        details.put("repaymentTypeMode", input.repaymentTypeMode());
        details.put("resolvedRepaymentType", selection.resolvedRepaymentType().name());
        details.put("firstRepaymentDate", first);
        details.put("maturityDate", maturity);
        details.put("installmentCount", schedule.size());
        details.put("firstMonthlyRepayment", schedule.get(0).amount());
        details.put("afterInterest", totalInterest);
        details.put("totalRepayment", totalRepayment);
        details.put("comparisonTiming", List.of(
                LoanScenarios.timing("INITIAL_CASH_CHANGE", base, LoanScenarios.BASIS_INITIAL_STATE),
                LoanScenarios.timing("INITIAL_DEBT_CHANGE", base, LoanScenarios.BASIS_INITIAL_STATE),
                LoanScenarios.timing("INITIAL_NET_ASSET_CHANGE", base, LoanScenarios.BASIS_INITIAL_STATE),
                LoanScenarios.periodTiming("INTEREST_TOTAL", base, maturity)));

        List<String> assumptions = new ArrayList<>(List.of(SimulationAssumptions.PRODUCT_DB_SNAPSHOT,
                SimulationAssumptions.MONTHLY_RATE_NOT_DAILY_PRORATED, SimulationAssumptions.EXCLUDES_TAX_AND_FEES));
        assumptions.addAll(selection.assumptions());
        if (ScenarioCashflow.isTruncated(cashflow)) {
            assumptions.add(SimulationAssumptions.CASHFLOW_TABLE_TRUNCATED);
        }

        String title = product.productName() + " " + SimulationScenarios.won(input.principal()) + " · " + input.term() + "개월 "
                + repaymentLabel(selection.resolvedRepaymentType().name());
        String summary = "연 " + selection.appliedRate().stripTrailingZeros().toPlainString() + "%(공시 최저) 기준 총 이자 "
                + SimulationScenarios.won(totalInterest) + ", 첫 회차 " + SimulationScenarios.won(schedule.get(0).amount())
                + " (" + first + "), 만기 " + maturity;
        SimulationResult result = new SimulationResult(title, summary, before, goals.after(), new Comparison(items, assets, debts, costs),
                cashflow, goals.impacts(), assumptions, details, goals.details());
        return new ScenarioComputation(result,
                new ScenarioLedger(List.of(), LoanScenarios.payments(schedule), maturity, maturity, true));
    }

    static String repaymentLabel(String repaymentType) {
        return switch (repaymentType) {
            case "EQUAL_PRINCIPAL" -> "원금균등";
            case "EQUAL_PAYMENT" -> "원리금균등";
            case "BULLET" -> "만기일시";
            default -> repaymentType;
        };
    }
}
