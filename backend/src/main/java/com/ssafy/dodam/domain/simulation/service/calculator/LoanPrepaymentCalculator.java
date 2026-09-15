package com.ssafy.dodam.domain.simulation.service.calculator;

import com.ssafy.dodam.domain.loan.service.LoanRepaymentScheduleCalculator;
import com.ssafy.dodam.domain.loan.service.LoanRepaymentScheduleItem;
import com.ssafy.dodam.domain.loan.service.LoanRepaymentTerms;
import com.ssafy.dodam.domain.simulation.dto.LoanPrepaymentInput;
import com.ssafy.dodam.domain.simulation.dto.LoanSnapshot;
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
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 대출 추가상환(POST /simulations/loan/prepayment). 만기를 유지하고 남은 회차 상환액을 재계산하는 방식이다(만기 단축 아님).
 *
 * <p>순서(585 §10.4): 원래 일정을 만든 뒤 {@code paymentDate} 이하의 약정 회차는 전후 양쪽에 그대로 반영한다(같은 날짜면
 * 약정상환이 먼저). 그 시점 잔액 P에 대해 {@code 0 < A ≤ P}를 검증하고, P − A로 이후 원래 회차 날짜·만기를 유지해 다시
 * 계산한다. 전액 상환이면 이후 원금·이자는 0이다. 만기 당일 약정상환으로 P = 0이면 양수 추가상환은 불가다.
 *
 * <p>월중 상환까지의 경과이자는 정산하지 않는다 — 다음 약정 회차 이자는 감소한 잔액 × 월이율이다
 * ({@code NO_ACCRUED_INTEREST_SETTLEMENT}, {@code MONTHLY_RATE_NOT_DAILY_PRORATED}). 중도상환 수수료는 자료가 없어
 * 계산하지 않으며 0원으로 표시하지도 않는다. 확정 중도상환 견적이 아니다.
 *
 * <p>비교 항목의 시점: {@code INITIAL_*}는 기준일 상태(미래 추가상환이면 0), {@code PREPAYMENT_*}는 행동일(paymentDate) 사건이다.
 * 당일 상환이면 둘은 같은 사건의 두 표시이므로 합산하지 않는다. cashflow는 날짜별 사건을 한 번만 더한다.
 */
@Component
public class LoanPrepaymentCalculator implements SimulationCalculator<LoanPrepaymentInput> {
    private final LoanRepaymentScheduleCalculator scheduleCalculator;

    public LoanPrepaymentCalculator(LoanRepaymentScheduleCalculator scheduleCalculator) {
        this.scheduleCalculator = scheduleCalculator;
    }

    @Override
    public SimulationType type() {
        return SimulationType.LOAN_PREPAYMENT;
    }

    @Override
    public Class<LoanPrepaymentInput> inputType() {
        return LoanPrepaymentInput.class;
    }

    @Override
    public ScenarioComputation compute(SimulationBaseline baseline, LoanPrepaymentInput input) {
        LoanSnapshot loan = Objects.requireNonNull(input.loan(), "loan 스냅샷이 준비되지 않았습니다.");
        LocalDate paymentDate = Objects.requireNonNull(input.paymentDate(), "paymentDate가 기준일로 채워지지 않았습니다.");
        LoanScenarios.requireComputable(loan, baseline.baseDate());
        if (paymentDate.isBefore(baseline.baseDate()) || paymentDate.isAfter(loan.maturityDate())) {
            throw new BusinessException(ErrorCode.SIMULATION_INVALID_PAYMENT_DATE);
        }
        return SimulationScenarios.exact(() -> build(baseline, input, loan, paymentDate));
    }

    private ScenarioComputation build(SimulationBaseline baseline, LoanPrepaymentInput input, LoanSnapshot loan, LocalDate paymentDate) {
        LocalDate base = baseline.baseDate();
        long amount = input.prepaymentAmount();
        List<LoanRepaymentScheduleItem> before = LoanScenarios.schedule(scheduleCalculator, LoanScenarios.terms(loan, loan.balance()), base);

        // paymentDate 이하 회차는 공통. 그 뒤 첫 회차부터 재계산한다
        List<LoanRepaymentScheduleItem> common = before.stream().filter(item -> !item.repaymentDate().isAfter(paymentDate)).toList();
        List<LoanRepaymentScheduleItem> rest = before.subList(common.size(), before.size());
        long balanceAtPayment = common.isEmpty() ? loan.balance() : common.get(common.size() - 1).remainingBalance();
        if (amount > balanceAtPayment) {
            throw new BusinessException(ErrorCode.SIMULATION_AMOUNT_EXCEEDS_BALANCE);
        }
        long balanceAfter = balanceAtPayment - amount;
        List<LoanRepaymentScheduleItem> recalculated = List.of();
        if (balanceAfter > 0 && !rest.isEmpty()) {
            LoanRepaymentTerms terms = LoanScenarios.terms(loan, balanceAfter, loan.interestRate(), rest.get(0).repaymentDate());
            recalculated = LoanScenarios.schedule(scheduleCalculator, terms, paymentDate);
        }
        List<LoanRepaymentScheduleItem> after = new ArrayList<>(common);
        after.addAll(recalculated);
        long beforeInterest = LoanScenarios.totalInterest(before);
        long afterInterest = LoanScenarios.totalInterest(after);
        long interestSaving = Math.subtractExact(beforeInterest, afterInterest);

        List<ScenarioCashflow.Event> events = new ArrayList<>(LoanScenarios.repaymentDeltaEvents(before, after));
        events.add(new ScenarioCashflow.Event(paymentDate, Math.negateExact(amount)));
        SimulationResult.Cashflow cashflow = ScenarioCashflow.build(base, events, loan.maturityDate());

        Snapshot beforeSnapshot = SimulationScenarios.baselineSnapshot(baseline);
        SimulationScenarios.NotComputableGoals goals = SimulationScenarios.notComputableGoals(baseline,
                SimulationAssumptions.FUNDING_SOURCE_UNKNOWN);

        boolean sameDay = paymentDate.equals(base);
        long initialChange = sameDay ? Math.negateExact(amount) : 0L;
        List<Item> items = new ArrayList<>();
        items.add(Item.amount("PREPAYMENT_AMOUNT", "추가상환액", 0L, amount));
        items.add(Item.amount("BALANCE_AT_PAYMENT_DATE", paymentDate + " 상환 후 잔액", balanceAtPayment, balanceAfter));
        items.add(Item.amount("INTEREST_TOTAL", "만기까지 총 이자", beforeInterest, afterInterest));
        items.add(Item.amount("INTEREST_SAVING", "이자 절감액(세전, 수수료 제외)", 0L, interestSaving));
        items.add(Item.amount("REPAYMENT_TOTAL", "만기까지 총 상환액(추가상환 제외)", LoanScenarios.totalAmount(before),
                LoanScenarios.totalAmount(after)));
        items.addAll(LoanScenarios.monthlyRepaymentItems(base, before, after, loan.maturityDate()));
        List<Item> assets = List.of(
                Item.amount("INITIAL_CASH_CHANGE", "기준일 현금 변화", 0L, initialChange),
                Item.amount("INITIAL_NET_ASSET_CHANGE", "기준일 순자산 변화", 0L, 0L),
                Item.amount("PREPAYMENT_CASH_CHANGE", "추가상환일 현금 변화", 0L, Math.negateExact(amount)));
        List<Item> debts = List.of(
                Item.amount("INITIAL_DEBT_CHANGE", "기준일 부채 변화", 0L, initialChange),
                Item.amount("PREPAYMENT_DEBT_CHANGE", "추가상환일 부채 변화", 0L, Math.negateExact(amount)));
        List<Item> costs = List.of(Item.amount("INTEREST_TOTAL", "만기까지 총 이자", beforeInterest, afterInterest));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("dataSource", LoanScenarios.DATA_SOURCE_LOAN_LOCAL);
        details.put("calculationAsOfDate", base);
        details.put("loanId", loan.loanId());
        details.put("loan", LoanScenarios.loanDetails(loan));
        details.put("paymentDate", paymentDate);
        details.put("prepaymentAmount", amount);
        details.put("balanceAtPaymentDate", balanceAtPayment);
        details.put("balanceAfterPrepayment", balanceAfter);
        details.put("installmentsBeforePaymentDate", common.size());
        details.put("installmentsRecalculated", recalculated.size());
        details.put("beforeInterest", beforeInterest);
        details.put("afterInterest", afterInterest);
        details.put("interestSaving", interestSaving);
        details.put("nextRepaymentBefore", rest.isEmpty() ? null : rest.get(0).amount());
        details.put("nextRepaymentAfter", recalculated.isEmpty() ? null : recalculated.get(0).amount());
        details.put("comparisonTiming", List.of(
                LoanScenarios.timing("INITIAL_CASH_CHANGE", base, LoanScenarios.BASIS_INITIAL_STATE),
                LoanScenarios.timing("INITIAL_DEBT_CHANGE", base, LoanScenarios.BASIS_INITIAL_STATE),
                LoanScenarios.timing("INITIAL_NET_ASSET_CHANGE", base, LoanScenarios.BASIS_INITIAL_STATE),
                LoanScenarios.timing("PREPAYMENT_CASH_CHANGE", paymentDate, LoanScenarios.BASIS_ACTION_EVENT),
                LoanScenarios.timing("PREPAYMENT_DEBT_CHANGE", paymentDate, LoanScenarios.BASIS_ACTION_EVENT),
                LoanScenarios.periodTiming("INTEREST_TOTAL", base, loan.maturityDate())));

        List<String> assumptions = new ArrayList<>(List.of(SimulationAssumptions.LOAN_LOCAL_SNAPSHOT,
                SimulationAssumptions.MATURITY_KEPT_PAYMENT_RECALCULATED, SimulationAssumptions.NO_ACCRUED_INTEREST_SETTLEMENT,
                SimulationAssumptions.MONTHLY_RATE_NOT_DAILY_PRORATED, SimulationAssumptions.EXCLUDES_TAX_AND_FEES));
        if (ScenarioCashflow.isTruncated(cashflow)) {
            assumptions.add(SimulationAssumptions.CASHFLOW_TABLE_TRUNCATED);
        }

        String title = loan.accountName() + " " + SimulationScenarios.won(amount) + " 추가상환 (" + paymentDate + ")";
        String summary = "만기까지 이자 " + SimulationScenarios.won(beforeInterest) + " → " + SimulationScenarios.won(afterInterest)
                + " (절감 " + SimulationScenarios.won(interestSaving) + ", 수수료 제외)"
                + (balanceAfter == 0 ? ", 전액 상환" : ", 잔액 " + SimulationScenarios.won(balanceAfter));
        SimulationResult result = new SimulationResult(title, summary, beforeSnapshot, goals.after(),
                new Comparison(items, assets, debts, costs), cashflow, goals.impacts(), assumptions, details, goals.details());
        // 추가상환액은 행동일의 원금 납부다. 비용(이자)이 아니므로 평가기간 합산에는 영향이 없다
        List<ScenarioLedger.Payment> afterPayments = new ArrayList<>(LoanScenarios.payments(after));
        afterPayments.add(new ScenarioLedger.Payment(paymentDate, amount, 0L, 0L));
        return new ScenarioComputation(result, new ScenarioLedger(LoanScenarios.payments(before), afterPayments,
                loan.maturityDate(), loan.maturityDate(), true));
    }
}
