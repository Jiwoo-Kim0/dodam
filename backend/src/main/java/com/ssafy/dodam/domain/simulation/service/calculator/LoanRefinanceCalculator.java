package com.ssafy.dodam.domain.simulation.service.calculator;

import com.ssafy.dodam.domain.loan.service.LoanComparisonPolicy;
import com.ssafy.dodam.domain.loan.service.LoanRepaymentScheduleCalculator;
import com.ssafy.dodam.domain.loan.service.LoanRepaymentScheduleItem;
import com.ssafy.dodam.domain.simulation.dto.LoanRefinanceInput;
import com.ssafy.dodam.domain.simulation.dto.LoanSnapshot;
import com.ssafy.dodam.domain.simulation.dto.ProductSnapshot;
import com.ssafy.dodam.domain.simulation.dto.ScenarioComputation;
import com.ssafy.dodam.domain.simulation.dto.ScenarioLedger;
import com.ssafy.dodam.domain.simulation.dto.SimulationBaseline;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Comparison;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.GoalImpact;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.GoalImpactDetail;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Item;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Snapshot;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import com.ssafy.dodam.domain.simulation.service.GoalDateProjection;
import com.ssafy.dodam.domain.simulation.service.ProductOptionSelector;
import com.ssafy.dodam.domain.simulation.service.SimulationAssumptions;
import com.ssafy.dodam.domain.simulation.service.SimulationCalculator;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 대환(POST /simulations/loan/refinance). 기존 대출의 남은 일정(날짜·회차 수·상환방식)을 유지하고 금리만 대상 상품의
 * 공시 최저금리로 바꾼다.
 *
 * <p>대상 옵션은 잔여 회차 수 n = option.term, 기존 상환방식과 대응하는 상환 코드여야 한다(기간 밴드 후보는 실행 불가 —
 * 533의 비교 가능과 계약 조건 지원은 다르다, {@code REMAINING_SCHEDULE_AS_TERM}). 비교군은 기존 대출의 연결 상품
 * loan_type과 대상 상품 loan_type이 같은 군이어야 하며 연결 상품이 없으면 추측하지 않는다.
 *
 * <p>실행일은 기준일이다. 다음 상환일이 기준일 당일이면 오늘 약정상환 반영 여부가 불명확해 거부한다. 잔액 P 중 A를 대환:
 * A = P면 신규 금리 일정만, A &lt; P면 기존 금리의 P − A와 신규 금리의 A를 같은 날짜·만기·방식으로 따로 계산해 합산한다
 * ({@code PARTIAL_REFINANCE_SPLIT_SCHEDULES}). 총 원금은 P로 보존되고 대환 유입 A와 상환 A는 순현금 0이다.
 * 부대비용은 계산하지 않는다.
 *
 * <p>목표 영향: 순원금 유출이 없으므로 각 약정 상환일의 상환 절감·증가분을 남은 목표 금액 비례로 기존 저축속도에 반영한다
 * ({@code REPAYMENT_DELTA_ALLOCATED_TO_GOALS}, {@link GoalDateProjection}).
 */
@Component
public class LoanRefinanceCalculator implements SimulationCalculator<LoanRefinanceInput> {
    private final LoanRepaymentScheduleCalculator scheduleCalculator;

    public LoanRefinanceCalculator(LoanRepaymentScheduleCalculator scheduleCalculator) {
        this.scheduleCalculator = scheduleCalculator;
    }

    @Override
    public SimulationType type() {
        return SimulationType.LOAN_REFINANCE;
    }

    @Override
    public Class<LoanRefinanceInput> inputType() {
        return LoanRefinanceInput.class;
    }

    @Override
    public ScenarioComputation compute(SimulationBaseline baseline, LoanRefinanceInput input) {
        LoanSnapshot loan = Objects.requireNonNull(input.loan(), "loan 스냅샷이 준비되지 않았습니다.");
        ProductSnapshot product = Objects.requireNonNull(input.product(), "product 스냅샷이 준비되지 않았습니다.");
        LocalDate base = baseline.baseDate();
        LoanScenarios.requireComputable(loan, base);
        if (loan.nextRepaymentDate().equals(base)) {
            throw new BusinessException(ErrorCode.SIMULATION_LOAN_DATA_UNAVAILABLE);
        }
        String existingGroup = LoanComparisonPolicy.groupOf(loan.productLoanType());
        String targetGroup = ProductOptionSelector.loanGroupOf(product);
        if (existingGroup == null || targetGroup == null || !existingGroup.equals(targetGroup)) {
            throw new BusinessException(ErrorCode.SIMULATION_PRODUCT_NOT_SUPPORTED);
        }
        long balance = loan.balance();
        long amount = input.isFullBalance() ? balance : input.refinanceAmount();
        if (amount > balance) {
            throw new BusinessException(ErrorCode.SIMULATION_AMOUNT_EXCEEDS_BALANCE);
        }
        return SimulationScenarios.exact(() -> build(baseline, input, loan, product, amount));
    }

    private ScenarioComputation build(SimulationBaseline baseline, LoanRefinanceInput input, LoanSnapshot loan, ProductSnapshot product,
            long amount) {
        LocalDate base = baseline.baseDate();
        long balance = loan.balance();
        long kept = balance - amount;
        List<LoanRepaymentScheduleItem> before = LoanScenarios.schedule(scheduleCalculator, LoanScenarios.terms(loan, balance), base);
        // 잔여 회차 수가 옵션 term이다. 옵션 검증은 한도(대환할 신규분 A)까지 포함한다
        ProductOptionSelector.Selection selection = ProductOptionSelector.selectLoan(product, before.size(), loan.repaymentType(), amount);
        List<LoanRepaymentScheduleItem> refinanced = LoanScenarios.schedule(scheduleCalculator,
                LoanScenarios.terms(loan, amount, selection.appliedRate(), loan.nextRepaymentDate()), base);
        List<LoanRepaymentScheduleItem> after = refinanced;
        if (kept > 0) {
            List<LoanRepaymentScheduleItem> existing = LoanScenarios.schedule(scheduleCalculator,
                    LoanScenarios.terms(loan, kept, loan.interestRate(), loan.nextRepaymentDate()), base);
            after = merge(existing, refinanced);
        }
        long beforeInterest = LoanScenarios.totalInterest(before);
        long afterInterest = LoanScenarios.totalInterest(after);
        long interestSaving = Math.subtractExact(beforeInterest, afterInterest);

        List<ScenarioCashflow.Event> events = LoanScenarios.repaymentDeltaEvents(before, after);
        SimulationResult.Cashflow cashflow = ScenarioCashflow.build(base, events, loan.maturityDate());

        // 약정 상환일별 절감분을 목표에 배분해 날짜 기준으로 투영한다
        List<GoalDateProjection.DatedAmount> dated = events.stream()
                .map(event -> new GoalDateProjection.DatedAmount(event.date(), event.delta())).toList();
        NavigableMap<LocalDate, Long> totalDeltas = GoalDateProjection.sumByDate(base, dated);
        Map<Long, NavigableMap<LocalDate, Long>> allocations = GoalDateProjection.allocate(baseline.savingGoals(), totalDeltas);
        Map<Long, LocalDate> afterDates = new LinkedHashMap<>();
        List<GoalImpact> impacts = new ArrayList<>();
        List<GoalImpactDetail> goalDetails = new ArrayList<>();
        for (SimulationBaseline.SavingGoal goal : baseline.savingGoals()) {
            LocalDate afterDate = GoalDateProjection.project(goal.remaining(), goal.averageMonthlySaving(), base,
                    goal.expectedAchievementDate(), allocations.get(goal.goalId()));
            afterDates.put(goal.goalId(), afterDate);
            impacts.add(new GoalImpact(goal.goalId(), goal.expectedAchievementDate(), afterDate));
            List<String> reasons = new ArrayList<>();
            String status = GoalImpactDetail.COMPUTED;
            if (goal.remaining() <= 0) {
                reasons.add(SimulationAssumptions.ALREADY_ACHIEVED);
            } else {
                reasons.add(SimulationAssumptions.REPAYMENT_DELTA_ALLOCATED_TO_GOALS);
                reasons.add(SimulationAssumptions.GOAL_EFFECT_AT_NEXT_PROJECTION_DATE);
                if (afterDate == null) {
                    status = GoalImpactDetail.NOT_COMPUTABLE;
                    reasons.add(SimulationAssumptions.SAVING_PACE_ZERO);
                }
            }
            goalDetails.add(new GoalImpactDetail(goal.goalId(), status, reasons));
        }
        Snapshot beforeSnapshot = SimulationScenarios.baselineSnapshot(baseline);
        Snapshot afterSnapshot = SimulationScenarios.snapshotWithDates(baseline, afterDates);

        List<Item> items = new ArrayList<>();
        items.add(Item.amount("REFINANCE_AMOUNT", "대환 금액", 0L, amount));
        items.add(Item.amount("LOAN_BALANCE", "대출 잔액(원금 보존)", balance, balance));
        items.add(Item.amount("INTEREST_TOTAL", "만기까지 총 이자", beforeInterest, afterInterest));
        items.add(Item.amount("INTEREST_SAVING", "이자 절감액(세전, 부대비용 제외)", 0L, interestSaving));
        items.add(Item.amount("REPAYMENT_TOTAL", "만기까지 총 상환액", LoanScenarios.totalAmount(before), LoanScenarios.totalAmount(after)));
        items.add(Item.amount("NEXT_REPAYMENT", "다음 회차 상환액", before.get(0).amount(), after.get(0).amount()));
        items.addAll(LoanScenarios.monthlyRepaymentItems(base, before, after, loan.maturityDate()));
        items.addAll(SimulationScenarios.goalItems(beforeSnapshot, afterSnapshot));
        List<Item> assets = List.of(
                Item.amount("INITIAL_CASH_CHANGE", "기준일 현금 변화(대환 유입·상환 상쇄)", 0L, 0L),
                Item.amount("INITIAL_NET_ASSET_CHANGE", "기준일 순자산 변화", 0L, 0L));
        List<Item> debts = List.of(Item.amount("INITIAL_DEBT_CHANGE", "기준일 부채 변화(총 원금 보존)", 0L, 0L));
        List<Item> costs = List.of(Item.amount("INTEREST_TOTAL", "만기까지 총 이자", beforeInterest, afterInterest));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("dataSource", LoanScenarios.DATA_SOURCE_LOAN_LOCAL + "+" + LoanScenarios.DATA_SOURCE_PRODUCT_DB);
        details.put("calculationAsOfDate", base);
        details.put("loanId", loan.loanId());
        details.put("loan", LoanScenarios.loanDetails(loan));
        details.put("product", LoanScenarios.productDetails(product, selection));
        details.put("existingRate", loan.interestRate());
        details.put("appliedRate", selection.appliedRate());
        details.put("comparisonGroup", ProductOptionSelector.loanGroupOf(product));
        details.put("refinanceAmount", amount);
        details.put("refinanceAmountMode", input.isFullBalance() ? LoanRefinanceInput.FULL_BALANCE : LoanRefinanceInput.MODE_EXPLICIT);
        details.put("keptBalance", kept);
        details.put("remainingInstallments", before.size());
        details.put("resolvedRepaymentType", selection.resolvedRepaymentType().name());
        details.put("beforeInterest", beforeInterest);
        details.put("afterInterest", afterInterest);
        details.put("interestSaving", interestSaving);
        details.put("comparisonTiming", List.of(
                LoanScenarios.timing("INITIAL_CASH_CHANGE", base, LoanScenarios.BASIS_INITIAL_STATE),
                LoanScenarios.timing("INITIAL_DEBT_CHANGE", base, LoanScenarios.BASIS_INITIAL_STATE),
                LoanScenarios.timing("INITIAL_NET_ASSET_CHANGE", base, LoanScenarios.BASIS_INITIAL_STATE),
                LoanScenarios.periodTiming("INTEREST_TOTAL", base, loan.maturityDate())));

        List<String> assumptions = new ArrayList<>(List.of(SimulationAssumptions.LOAN_LOCAL_SNAPSHOT,
                SimulationAssumptions.PRODUCT_DB_SNAPSHOT, SimulationAssumptions.REMAINING_SCHEDULE_AS_TERM,
                SimulationAssumptions.MONTHLY_RATE_NOT_DAILY_PRORATED, SimulationAssumptions.EXCLUDES_TAX_AND_FEES));
        assumptions.addAll(selection.assumptions());
        if (kept > 0) {
            assumptions.add(SimulationAssumptions.PARTIAL_REFINANCE_SPLIT_SCHEDULES);
        }
        if (!baseline.savingGoals().isEmpty()) {
            assumptions.add(SimulationAssumptions.REPAYMENT_DELTA_ALLOCATED_TO_GOALS);
            assumptions.add(SimulationAssumptions.GOAL_EFFECT_AT_NEXT_PROJECTION_DATE);
        }
        if (baseline.savingGoals().stream().filter(goal -> goal.remaining() > 0).count() > 1) {
            assumptions.add(SimulationScenarios.SPLIT_BY_REMAINING);
        }
        if (ScenarioCashflow.isTruncated(cashflow)) {
            assumptions.add(SimulationAssumptions.CASHFLOW_TABLE_TRUNCATED);
        }

        String title = loan.accountName() + " " + SimulationScenarios.won(amount) + " 대환 → " + product.productName();
        String summary = "연 " + loan.interestRate().stripTrailingZeros().toPlainString() + "% → "
                + selection.appliedRate().stripTrailingZeros().toPlainString() + "%(공시 최저), 만기까지 이자 "
                + SimulationScenarios.won(beforeInterest) + " → " + SimulationScenarios.won(afterInterest)
                + " (절감 " + SimulationScenarios.won(interestSaving) + ", 부대비용 제외)";
        SimulationResult result = new SimulationResult(title, summary, beforeSnapshot, afterSnapshot,
                new Comparison(items, assets, debts, costs), cashflow, impacts, assumptions, details, goalDetails);
        return new ScenarioComputation(result, new ScenarioLedger(LoanScenarios.payments(before), LoanScenarios.payments(after),
                loan.maturityDate(), loan.maturityDate(), true));
    }

    /** 같은 날짜·회차 수의 두 일정을 회차별로 합친다. 부분 대환의 회차별 반올림으로 같은 금리라도 1원 차이가 날 수 있다. */
    private static List<LoanRepaymentScheduleItem> merge(List<LoanRepaymentScheduleItem> a, List<LoanRepaymentScheduleItem> b) {
        if (a.size() != b.size()) {
            throw new IllegalStateException("부분 대환의 두 일정 회차 수가 다릅니다.");
        }
        List<LoanRepaymentScheduleItem> merged = new ArrayList<>(a.size());
        for (int i = 0; i < a.size(); i++) {
            LoanRepaymentScheduleItem x = a.get(i);
            LoanRepaymentScheduleItem y = b.get(i);
            merged.add(new LoanRepaymentScheduleItem(x.sequence(), x.repaymentDate(), Math.addExact(x.amount(), y.amount()),
                    Math.addExact(x.principal(), y.principal()), Math.addExact(x.interest(), y.interest()),
                    Math.addExact(x.remainingBalance(), y.remainingBalance())));
        }
        return merged;
    }
}
