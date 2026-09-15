package com.ssafy.dodam.domain.simulation;

import static com.ssafy.dodam.domain.simulation.CardSimulationCalculatorTest.baseline;
import static com.ssafy.dodam.domain.simulation.CardSimulationCalculatorTest.goal;
import static com.ssafy.dodam.domain.simulation.CardSimulationCalculatorTest.item;
import static com.ssafy.dodam.domain.simulation.CardSimulationCalculatorTest.netCashSum;
import static com.ssafy.dodam.domain.simulation.CardSimulationCalculatorTest.section;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.dodam.domain.loan.entity.LoanStatus;
import com.ssafy.dodam.domain.loan.entity.LoanType;
import com.ssafy.dodam.domain.loan.entity.RepaymentType;
import com.ssafy.dodam.domain.loan.service.LoanRepaymentScheduleCalculator;
import com.ssafy.dodam.domain.product.entity.ProductType;
import com.ssafy.dodam.domain.simulation.dto.CashAdvanceInput;
import com.ssafy.dodam.domain.simulation.dto.CombinedInput;
import com.ssafy.dodam.domain.simulation.dto.InstallmentInput;
import com.ssafy.dodam.domain.simulation.dto.LoanNewInput;
import com.ssafy.dodam.domain.simulation.dto.LoanRefinanceInput;
import com.ssafy.dodam.domain.simulation.dto.LoanSnapshot;
import com.ssafy.dodam.domain.simulation.dto.ProductSnapshot;
import com.ssafy.dodam.domain.simulation.dto.RevolvingInput;
import com.ssafy.dodam.domain.simulation.dto.SavingChangeInput;
import com.ssafy.dodam.domain.simulation.dto.SimulationBaseline;
import com.ssafy.dodam.domain.simulation.dto.SimulationInput;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.GoalImpactDetail;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Month;
import com.ssafy.dodam.domain.simulation.dto.SpendingReductionInput;
import com.ssafy.dodam.domain.simulation.service.SimulationAssumptions;
import com.ssafy.dodam.domain.simulation.service.SimulationCalculator;
import com.ssafy.dodam.domain.simulation.service.SimulationCalculatorRegistry;
import com.ssafy.dodam.domain.simulation.service.calculator.CashAdvanceCalculator;
import com.ssafy.dodam.domain.simulation.service.calculator.CombinedCalculator;
import com.ssafy.dodam.domain.simulation.service.calculator.InstallmentCalculator;
import com.ssafy.dodam.domain.simulation.service.calculator.LoanNewCalculator;
import com.ssafy.dodam.domain.simulation.service.calculator.LoanPrepaymentCalculator;
import com.ssafy.dodam.domain.simulation.service.calculator.LoanRefinanceCalculator;
import com.ssafy.dodam.domain.simulation.service.calculator.RevolvingCalculator;
import com.ssafy.dodam.domain.simulation.service.calculator.SavingChangeCalculator;
import com.ssafy.dodam.domain.simulation.service.calculator.SavingProductCalculator;
import com.ssafy.dodam.domain.simulation.service.calculator.SpendingReductionCalculator;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 586 복합 계산기. 자식 단독 결과와의 delta 일치, 순서 교환 불변, 공통 평가기간 비용, 기간 제한 목표 투영을 DB 없이 검증한다.
 * 기준일 2026-09-15.
 */
class CombinedSimulationCalculatorTest {
    private static final LocalDate SEP_15 = LocalDate.of(2026, 9, 15);
    private static final LocalDateTime SYNCED = LocalDateTime.of(2026, 9, 1, 9, 0);
    private final SimulationCalculatorRegistry registry;
    private final CombinedCalculator combined;

    CombinedSimulationCalculatorTest() {
        List<SimulationCalculator<?>> calculators = new ArrayList<>();
        registry = new SimulationCalculatorRegistry(() -> calculators);
        combined = new CombinedCalculator(registry);
        LoanRepaymentScheduleCalculator schedules = new LoanRepaymentScheduleCalculator();
        calculators.addAll(List.of(new SpendingReductionCalculator(), new SavingChangeCalculator(), new SavingProductCalculator(),
                new LoanNewCalculator(schedules), new LoanPrepaymentCalculator(schedules), new LoanRefinanceCalculator(schedules),
                new InstallmentCalculator(), new RevolvingCalculator(), new CashAdvanceCalculator(), combined));
    }

    private static SimulationBaseline withCategories(LocalDate base, SimulationBaseline.SavingGoal... goals) {
        return new SimulationBaseline(base, 3_000_000L, 1_000_000L, Map.of("FOOD", 300_000L, "UNCATEGORIZED", 700_000L),
                List.of(goals), List.of(), List.of());
    }

    private static ProductSnapshot loanProduct(int term, String minRate, String code) {
        return new ProductSnapshot(20L, "신용대출", "은행", ProductType.LOAN, null, "PERSONAL_CREDIT", null, SYNCED,
                List.of(new ProductSnapshot.Option(200L, term, null, "FIXED", new BigDecimal(minRate), new BigDecimal("15"), code, null)));
    }

    /** 585 검산표 대출: 잔액 1,200,000, 12%, BULLET, 10/15·11/15 상환(만기 11/15). */
    private static LoanSnapshot bulletLoan() {
        return new LoanSnapshot(7L, "신용대출", "은행", LoanType.CREDIT, RepaymentType.BULLET, LoanStatus.ACTIVE, 1_200_000L,
                new BigDecimal("12"), 15, LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 15), 20L, "PERSONAL_CREDIT");
    }

    private SimulationResult run(SimulationBaseline baseline, SimulationInput... children) {
        return combined.calculate(baseline, new CombinedInput(List.of(children)));
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> children(SimulationResult result) {
        return (List<Map<String, Object>>) result.details().get("children");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> financeCost(SimulationResult result) {
        return (Map<String, Object>) result.details().get("financeCost");
    }

    @Test
    void combinesSpendingAndInstallmentCashflowAndKeepsChildDetails() {
        SimulationBaseline baseline = baseline(SEP_15, goal(1, 500_000, 100_000, SEP_15));
        SpendingReductionInput spending = new SpendingReductionInput(null, 50_000L, 3);
        InstallmentInput installment = InstallmentInput.of(1_200_000L, 2, new BigDecimal("12"));
        SimulationResult result = run(baseline, spending, installment);

        // 현금흐름 = 자식 delta 합, 누적은 currentMonth부터 재계산. 종료는 자식 종료 중 최댓값(3개월)
        assertThat(result.cashflow().basis()).isEqualTo(SimulationResult.Cashflow.SCENARIO_NET_CASH_CHANGE);
        assertThat(result.cashflow().currentMonth().delta()).isEqualTo(1_200_000L);
        assertThat(result.cashflow().months()).containsExactly(
                new Month("2026-10", 50_000L - 611_836L, 1_200_000L + 50_000L - 611_836L),
                new Month("2026-11", 50_000L - 606_115L, 1_200_000L + 100_000L - 1_217_951L),
                new Month("2026-12", 50_000L, 150_000L - 17_951L));
        assertThat(result.cashflow().fullTermEndDate()).isEqualTo(LocalDate.of(2026, 12, 15));
        assertThat(result.cashflow().truncated()).isFalse();
        // 집계 항목: 기준일 최초 변화와 평가기간 비용
        assertThat(item(result, "FINANCE_COST_EVALUATED")).extracting(i -> i.before(), i -> i.after(), i -> i.delta())
                .containsExactly(0L, 17_951L, 17_951L);
        assertThat(section(result.comparison().assets(), "INITIAL_CASH_CHANGE").after()).isEqualTo(1_200_000L);
        assertThat(section(result.comparison().debts(), "INITIAL_DEBT_CHANGE").after()).isEqualTo(1_200_000L);
        assertThat(section(result.comparison().costs(), "FINANCE_COST_EVALUATED").after()).isEqualTo(17_951L);
        assertThat(result.details()).containsEntry("evaluationFromDate", SEP_15).containsEntry("evaluationThroughDate", LocalDate.of(2026, 12, 15))
                .containsEntry("costBasis", "PAYMENT_DATE").containsEntry("costComplete", true).containsEntry("projectionIncomplete", false)
                .containsEntry("scenarioCount", 2).containsEntry("goalEffectPolicy", CombinedCalculator.GOAL_POLICY_NOT_COMPUTABLE);
        // 참고 기준선 수준값은 유지, 목표 날짜는 부모 투영 결과(계산 불가 → null) 하나로 통일
        assertThat(result.after().monthlySpending()).isEqualTo(result.before().monthlySpending());
        assertThat(result.after().goals().get(0).expectedAchievementDate()).isNull();
        assertThat(result.goalImpacts()).containsExactly(new SimulationResult.GoalImpact(1L, SEP_15.plusMonths(5), null));
        assertThat(result.goalImpactDetails()).containsExactly(new GoalImpactDetail(1L, GoalImpactDetail.NOT_COMPUTABLE,
                List.of(SimulationAssumptions.COMBINED_GOAL_EFFECT_NOT_COMPUTABLE, SimulationAssumptions.GOAL_ALLOCATION_UNKNOWN)));
        assertThat(item(result, "GOAL_1_EXPECTED_DATE").after()).isNull();
        // 자식은 순서대로 보존되고 단독 목표 날짜(소비 감소만이면 앞당겨짐)는 details.children에만 있다
        List<Map<String, Object>> children = children(result);
        assertThat(children).hasSize(2);
        assertThat(children.get(0)).containsEntry("sequenceNo", 1).containsEntry("type", "SPENDING").containsEntry("costComplete", true);
        assertThat(children.get(1)).containsEntry("sequenceNo", 2).containsEntry("type", "INSTALLMENT")
                .containsEntry("fullTermEndDate", LocalDate.of(2026, 11, 15));
        assertThat(result.assumptions()).contains(SimulationAssumptions.CHILD_SCENARIOS_INDEPENDENT, SimulationAssumptions.FINANCE_COST_EVALUATED,
                SimulationAssumptions.COMBINED_GOAL_EFFECT_NOT_COMPUTABLE, SimulationAssumptions.CARD_TERMS_USER_INPUT)
                .doesNotContain(SimulationAssumptions.FINANCE_COST_PARTIAL, SimulationAssumptions.ZERO_FEE_ASSUMED);
        assertThat(result.title()).startsWith("복합 시나리오 2건: ");

        // 순서를 바꿔도 합계는 같다
        SimulationResult swapped = run(baseline, installment, spending);
        assertThat(swapped.cashflow().months()).isEqualTo(result.cashflow().months());
        assertThat(swapped.comparison().items()).isEqualTo(result.comparison().items());
        assertThat(swapped.assumptions()).isEqualTo(result.assumptions());
        assertThat(children(swapped).get(0)).containsEntry("type", "INSTALLMENT");
    }

    @Test
    void projectsGoalsWithDurationLimitedSpendingReductions() {
        // 잔여 1,000,000·속도 100,000, 감소액 합 100,000이 1개월만 유효 → 첫 달 200,000, 이후 100,000씩 → 9개월째
        SimulationBaseline baseline = withCategories(SEP_15, goal(1, 1_000_000, 100_000, SEP_15));
        SimulationResult result = run(baseline, new SpendingReductionInput("FOOD", 60_000L, 1),
                new SpendingReductionInput("UNCATEGORIZED", 40_000L, 1));
        assertThat(result.goalImpacts()).containsExactly(new SimulationResult.GoalImpact(1L, SEP_15.plusMonths(10), SEP_15.plusMonths(9)));
        assertThat(result.after().goals().get(0).monthlySaving()).isEqualTo(200_000L);
        assertThat(result.after().goals().get(0).expectedAchievementDate()).isEqualTo(SEP_15.plusMonths(9));
        assertThat(item(result, "GOAL_1_EXPECTED_DATE").after()).isEqualTo(SEP_15.plusMonths(9).toString());
        assertThat(item(result, "GOAL_1_MONTHLY_SAVING").after()).isEqualTo(200_000L);
        assertThat(result.goalImpactDetails()).containsExactly(new GoalImpactDetail(1L, GoalImpactDetail.COMPUTED,
                List.of(SimulationAssumptions.GOAL_EFFECT_LIMITED_TO_SCENARIO_DURATION, "REDUCTION_DIRECTED_TO_SAVING_GOALS")));
        assertThat(result.details()).containsEntry("goalEffectPolicy", CombinedCalculator.GOAL_POLICY_DURATION_LIMITED);
        assertThat(result.assumptions()).contains(SimulationAssumptions.GOAL_EFFECT_LIMITED_TO_SCENARIO_DURATION)
                .doesNotContain(SimulationAssumptions.SAVING_AMOUNT_IS_FINAL_TARGET);
        // 현금흐름: 두 자식 모두 1개월 → 10월 +100,000, 비용 0
        assertThat(result.cashflow().months()).containsExactly(new Month("2026-10", 100_000L, 100_000L));
        assertThat(item(result, "FINANCE_COST_EVALUATED").after()).isEqualTo(0L);
        // 자식 단독 날짜(지속 적용, 5개월)는 details.children에만 있다
        assertThat(children(result).get(0).get("goalImpacts")).asString().contains(SEP_15.plusMonths(10).toString());
        // 카테고리별로는 상한 안이어도 합이 평균 지출(1,000,000)을 넘는 조합은 거부
        SimulationBaseline overlapping = new SimulationBaseline(SEP_15, null, 1_000_000L,
                Map.of("FOOD", 800_000L, "UNCATEGORIZED", 700_000L), List.of(), List.of(), List.of());
        assertThatThrownBy(() -> run(overlapping, new SpendingReductionInput("FOOD", 600_000L, 1),
                new SpendingReductionInput("UNCATEGORIZED", 500_000L, 1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.SIMULATION_REDUCTION_EXCEEDS_SPENDING);
    }

    @Test
    void savingReplacesPaceAndSpendingResumesAfterSavingEnds() {
        // 잔여 1,000,000·속도 100,000. SAVING 300,000(2개월) + SPENDING 100,000(3개월):
        // k1·k2 = 300,000(절감액 미배분), k3 = 100,000 + 100,000, 이후 100,000 → 700,000·400,000·200,000·100,000·0 → 5개월째
        SimulationBaseline baseline = baseline(SEP_15, goal(1, 1_000_000, 100_000, SEP_15));
        SimulationResult result = run(baseline, new SavingChangeInput(null, 300_000L, 2), new SpendingReductionInput(null, 100_000L, 3));
        assertThat(result.goalImpacts()).containsExactly(new SimulationResult.GoalImpact(1L, SEP_15.plusMonths(10), SEP_15.plusMonths(5)));
        assertThat(result.after().goals().get(0).monthlySaving()).isEqualTo(300_000L);
        assertThat(result.goalImpactDetails().get(0).reasons()).contains(SimulationAssumptions.SAVING_AMOUNT_IS_FINAL_TARGET,
                SimulationAssumptions.GOAL_EFFECT_LIMITED_TO_SCENARIO_DURATION);
        // 현금 delta는 자식별 유효기간 그대로: 10·11월 −200,000 + 100,000, 12월 +100,000
        assertThat(result.cashflow().months()).containsExactly(new Month("2026-10", -100_000L, -100_000L),
                new Month("2026-11", -100_000L, -200_000L), new Month("2026-12", 100_000L, -100_000L));
        // 속도 0인 목표는 기간 종료 후 미달성이면 계산 불가
        SimulationBaseline zeroPace = baseline(SEP_15, goal(2, 1_000_000, 0, SEP_15));
        SimulationResult stalled = run(zeroPace, new SpendingReductionInput(null, 100_000L, 1), new SavingChangeInput(null, 0L, 1));
        assertThat(stalled.goalImpacts().get(0).after()).isNull();
        assertThat(stalled.goalImpactDetails().get(0).status()).isEqualTo(GoalImpactDetail.NOT_COMPUTABLE);
        assertThat(stalled.goalImpactDetails().get(0).reasons()).contains(SimulationAssumptions.SAVING_PACE_ZERO);
        // 이미 달성한 목표는 어느 조합에서든 기준일·ALREADY_ACHIEVED
        SimulationResult achieved = run(baseline(SEP_15, goal(3, 0, 100_000, SEP_15)), new SpendingReductionInput(null, 1L, 1),
                InstallmentInput.of(1L, 1, null));
        assertThat(achieved.goalImpacts()).containsExactly(new SimulationResult.GoalImpact(3L, SEP_15, SEP_15));
        assertThat(achieved.goalImpactDetails()).containsExactly(new GoalImpactDetail(3L, GoalImpactDetail.COMPUTED,
                List.of(SimulationAssumptions.ALREADY_ACHIEVED)));
        // 목표가 없으면 빈 목록
        assertThat(run(baseline(SEP_15), new SpendingReductionInput(null, 1L, 1), new SavingChangeInput(null, 0L, 1)).goalImpacts()).isEmpty();
    }

    @Test
    void evaluatesFinanceCostWithinCommonPeriodAndKeepsFullTermCostsSeparate() {
        // 360개월 대출(월 상환) + 12개월 할부: 평가 종료 = 기준일 + 120개월이 속한 달 말일(2036-09-30). 대출 이자는 그 안의 회차만
        LoanNewInput loan = new LoanNewInput(20L, 120_000_000L, 360, RepaymentType.EQUAL_PRINCIPAL).withProduct(loanProduct(360, "3", "EQUAL_PRINCIPAL"));
        InstallmentInput installment = InstallmentInput.of(1_200_000L, 12, new BigDecimal("12"));
        SimulationBaseline baseline = baseline(SEP_15);
        SimulationResult loanAlone = registry.compute(baseline, loan).result();
        SimulationResult installmentAlone = registry.compute(baseline, installment).result();
        SimulationResult result = run(baseline, loan, installment);

        LocalDate through = LocalDate.of(2036, 9, 30);
        assertThat(result.details()).containsEntry("evaluationThroughDate", through).containsEntry("costComplete", true);
        long loanInterestWithin = registry.compute(baseline, loan).ledger().afterPayments().stream()
                .filter(p -> !p.date().isAfter(through)).mapToLong(p -> p.interest()).sum();
        long installmentFee = (long) item(installmentAlone, "INSTALLMENT_FEE_TOTAL").after();
        assertThat(item(result, "FINANCE_COST_EVALUATED").after()).isEqualTo(loanInterestWithin + installmentFee);
        assertThat(loanInterestWithin).isLessThan((long) item(loanAlone, "INTEREST_TOTAL").after());
        // 자식별 내역과 전체 만기 비용은 따로 보존된다
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> childCosts = (List<Map<String, Object>>) financeCost(result).get("children");
        assertThat(childCosts.get(0)).containsEntry("type", "LOAN_NEW").containsEntry("after", loanInterestWithin).containsEntry("costComplete", true);
        assertThat(childCosts.get(1)).containsEntry("type", "INSTALLMENT").containsEntry("after", installmentFee);
        assertThat(children(result).get(0).get("comparison")).isEqualTo(loanAlone.comparison());
        // 120개월 표 잘림은 대출 자식에서 전파, 종료일은 대출 만기
        assertThat(result.cashflow().truncated()).isTrue();
        assertThat(result.cashflow().months()).hasSize(120);
        assertThat(result.cashflow().fullTermEndDate()).isEqualTo(SEP_15.plusMonths(360));
        assertThat(result.assumptions()).contains(SimulationAssumptions.CASHFLOW_TABLE_TRUNCATED);
        assertThat(result.cashflow().currentMonth().delta()).isEqualTo(120_000_000L + 1_200_000L);
        assertThat(result.cashflow().months().get(0).delta())
                .isEqualTo(loanAlone.cashflow().months().get(0).delta() + installmentAlone.cashflow().months().get(0).delta());
    }

    @Test
    void refinanceSavingIsNegativeCostDeltaAndUnpaidRevolvingMakesCostPartial() {
        // 대환(1,200,000 → 6%, 2회차): 이자 24,000 → 12,000, 비용 delta −12,000. 현금서비스 1개월 이자 9,863 추가
        LoanRefinanceInput refinance = new LoanRefinanceInput(7L, 20L, null).withSnapshots(bulletLoan(), loanProduct(2, "6", "BULLET"));
        SimulationResult result = run(baseline(SEP_15, goal(1, 500_000, 100_000, SEP_15)), refinance,
                CashAdvanceInput.of(1_000_000L, new BigDecimal("12"), 1));
        assertThat(item(result, "FINANCE_COST_EVALUATED")).extracting(i -> i.before(), i -> i.after(), i -> i.delta())
                .containsExactly(24_000L, 12_000L + 9_863L, -2_137L);
        assertThat(result.details()).containsEntry("evaluationThroughDate", LocalDate.of(2026, 11, 15)).containsEntry("costComplete", true);
        // 대환은 단독으로는 목표 날짜를 내지만 부모는 합성하지 않는다. 자식 사유(현금서비스 귀속 불명)를 함께 준다
        assertThat(result.goalImpactDetails().get(0).status()).isEqualTo(GoalImpactDetail.NOT_COMPUTABLE);
        assertThat(result.goalImpactDetails().get(0).reasons()).containsExactly(SimulationAssumptions.COMBINED_GOAL_EFFECT_NOT_COMPUTABLE,
                SimulationAssumptions.GOAL_ALLOCATION_UNKNOWN);
        assertThat(children(result).get(0).get("goalImpacts")).asString().contains("COMPUTED");

        // 미완납 리볼빙 + 360개월 대출: 리볼빙 자료는 120회차(2036-09-15)까지, 평가 종료는 2036-09-30 → 부분 비용
        LoanNewInput loan = new LoanNewInput(20L, 120_000_000L, 360, RepaymentType.BULLET).withProduct(loanProduct(360, "3", "BULLET"));
        RevolvingInput revolving = new RevolvingInput(null, 1_000_000L, new BigDecimal("0.0001"), new BigDecimal("12"));
        SimulationResult partial = run(baseline(SEP_15), loan, revolving);
        assertThat(partial.details()).containsEntry("costComplete", false).containsEntry("projectionIncomplete", true)
                .containsEntry("cashflowEvaluatedThrough", SEP_15.plusMonths(360));
        assertThat(partial.cashflow().fullTermEndDate()).isNull();
        assertThat(partial.assumptions()).contains(SimulationAssumptions.FINANCE_COST_PARTIAL, SimulationAssumptions.REVOLVING_HORIZON_LIMIT);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> childCosts = (List<Map<String, Object>>) financeCost(partial).get("children");
        assertThat(childCosts.get(1)).containsEntry("type", "REVOLVING").containsEntry("costComplete", false)
                .containsEntry("costCoveredThrough", SEP_15.plusMonths(120)).containsEntry("reason", CombinedCalculator.COST_REASON_HORIZON_LIMIT);
        // 리볼빙만 미완납이고 다른 자식이 더 짧으면 평가기간이 120회차일까지라 자료가 전체를 덮는다 → 완전
        SimulationResult covered = run(baseline(SEP_15), revolving, CashAdvanceInput.of(1L, BigDecimal.ONE, 1));
        assertThat(covered.details()).containsEntry("evaluationThroughDate", SEP_15.plusMonths(120)).containsEntry("costComplete", true)
                .containsEntry("projectionIncomplete", true);
        assertThat(netCashSum(covered)).isEqualTo(netCashSum(registry.compute(baseline(SEP_15), revolving).result())
                + netCashSum(registry.compute(baseline(SEP_15), CashAdvanceInput.of(1L, BigDecimal.ONE, 1)).result()));
    }

    @Test
    void convertsChildAllocationOverflowToAmountRangeError() {
        // 목표 비례 배분의 중간 곱이 long 범위를 넘는 기존 소비 계산 경로를 검증한다.
        SimulationBaseline.SavingGoal largeGoal = new SimulationBaseline.SavingGoal(1L, "큰 목표",
                Long.MAX_VALUE, 0L, Long.MAX_VALUE - 1, 0L, SEP_15.plusYears(1), null, List.of());
        SimulationBaseline baseline = withCategories(SEP_15, largeGoal, goal(2L, 1L, 0L, SEP_15));

        assertThatThrownBy(() -> run(baseline, new SpendingReductionInput("FOOD", 2L, 1), InstallmentInput.of(1L, 1, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.SIMULATION_AMOUNT_OUT_OF_RANGE);
    }

    @Test
    void convertsCombinedCashflowOverflowToAmountRangeError() {
        // 각 자식은 계산 가능하지만 기준일 현금 유입 합계가 long 범위를 넘는다.
        assertThatThrownBy(() -> run(withCategories(SEP_15), InstallmentInput.of(Long.MAX_VALUE, 1, BigDecimal.ZERO),
                CashAdvanceInput.of(1L, BigDecimal.ZERO, 1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.SIMULATION_AMOUNT_OUT_OF_RANGE);
    }

    @Test
    void combinedInputRejectsNestingAndCountsAndChildErrorsPropagate() {
        assertThatThrownBy(() -> new CombinedInput(List.of(InstallmentInput.of(1L, 1, null)))).isInstanceOf(IllegalArgumentException.class);
        List<SimulationInput> eleven = new ArrayList<>();
        for (int i = 1; i <= 11; i++) {
            eleven.add(InstallmentInput.of(i, 1, null));
        }
        assertThatThrownBy(() -> new CombinedInput(eleven)).isInstanceOf(IllegalArgumentException.class);
        CombinedInput inner = new CombinedInput(List.of(InstallmentInput.of(1L, 1, null), InstallmentInput.of(2L, 1, null)));
        assertThatThrownBy(() -> new CombinedInput(List.of(inner, InstallmentInput.of(3L, 1, null)))).isInstanceOf(IllegalArgumentException.class);
        // 자식 오류(감소액이 카테고리 지출 초과)는 그대로 전파
        assertThatThrownBy(() -> run(withCategories(SEP_15), new SpendingReductionInput("FOOD", 300_001L, 1), InstallmentInput.of(1L, 1, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.SIMULATION_REDUCTION_EXCEEDS_SPENDING);
        // 지문은 순서·자식 지문을 포함하고 파라미터에는 자식 순서가 보존된다
        CombinedInput a = new CombinedInput(List.of(InstallmentInput.of(1L, 1, null), CashAdvanceInput.of(1L, BigDecimal.ONE, null)));
        CombinedInput b = new CombinedInput(List.of(CashAdvanceInput.of(1L, BigDecimal.ONE, 1), InstallmentInput.of(1L, 1, null)));
        assertThat(a.fingerprint()).isNotEqualTo(b.fingerprint());
        assertThat(a.scenarios()).hasSize(2);
        assertThat(a.durationMonths()).isEqualTo(1);
        assertThat(a.parameters(new com.fasterxml.jackson.databind.ObjectMapper()).toString())
                .contains("\"scenarios\"").contains("\"type\":\"INSTALLMENT\"").contains("\"feeRateMode\":\"ASSUMED_ZERO\"");
    }
}
