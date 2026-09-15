package com.ssafy.dodam.domain.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.dodam.domain.loan.entity.LoanStatus;
import com.ssafy.dodam.domain.loan.entity.LoanType;
import com.ssafy.dodam.domain.loan.entity.RepaymentType;
import com.ssafy.dodam.domain.loan.service.LoanRepaymentScheduleCalculator;
import com.ssafy.dodam.domain.product.entity.ProductType;
import com.ssafy.dodam.domain.simulation.dto.LoanNewInput;
import com.ssafy.dodam.domain.simulation.dto.LoanPrepaymentInput;
import com.ssafy.dodam.domain.simulation.dto.LoanRefinanceInput;
import com.ssafy.dodam.domain.simulation.dto.LoanSnapshot;
import com.ssafy.dodam.domain.simulation.dto.ProductSnapshot;
import com.ssafy.dodam.domain.simulation.dto.SavingProductInput;
import com.ssafy.dodam.domain.simulation.dto.SimulationBaseline;
import com.ssafy.dodam.domain.simulation.dto.SimulationInput;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.GoalImpactDetail;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Item;
import com.ssafy.dodam.domain.simulation.service.SimulationAssumptions;
import com.ssafy.dodam.domain.simulation.service.calculator.LoanNewCalculator;
import com.ssafy.dodam.domain.simulation.service.calculator.LoanPrepaymentCalculator;
import com.ssafy.dodam.domain.simulation.service.calculator.LoanRefinanceCalculator;
import com.ssafy.dodam.domain.simulation.service.calculator.SavingProductCalculator;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/**
 * 585 계산기 4종. DB 없이 §10.8 검산표를 그대로 검증한다. 모든 금리·금액은 손계산 가능한 합성 값이다.
 */
class ProductLoanSimulationCalculatorTest {
    private static final LocalDate JAN_1 = LocalDate.of(2026, 1, 1);
    private static final LocalDateTime SYNCED = LocalDateTime.of(2025, 12, 31, 9, 0);
    private final LoanRepaymentScheduleCalculator schedules = new LoanRepaymentScheduleCalculator();
    private final SavingProductCalculator savingProduct = new SavingProductCalculator();
    private final LoanNewCalculator loanNew = new LoanNewCalculator(schedules);
    private final LoanPrepaymentCalculator prepayment = new LoanPrepaymentCalculator(schedules);
    private final LoanRefinanceCalculator refinance = new LoanRefinanceCalculator(schedules);

    private static SimulationBaseline baseline(LocalDate base, SimulationBaseline.SavingGoal... goals) {
        return new SimulationBaseline(base, 3_000_000L, 1_000_000L, Map.of(), List.of(goals), List.of(), List.of());
    }

    private static SimulationBaseline.SavingGoal goal(long id, long remaining, long pace, LocalDate base) {
        return new SimulationBaseline.SavingGoal(id, "목표" + id, 1_000_000L, 1_000_000L - remaining, remaining, pace,
                base.plusYears(1), com.ssafy.dodam.domain.simulation.service.GoalAchievementProjection.expectedDate(remaining, pace, base),
                List.of());
    }

    private static ProductSnapshot savingProduct(ProductType type, String savingType, int term, String rate) {
        return new ProductSnapshot(10L, type == ProductType.DEPOSIT ? "정기예금" : "적금", "은행", type, savingType, null, null, SYNCED,
                List.of(new ProductSnapshot.Option(100L, term, new BigDecimal(rate), "FIXED", null, null, null, null)));
    }

    private static ProductSnapshot loanProduct(String loanType, int term, String minRate, String code, Long limit) {
        return new ProductSnapshot(20L, "신용대출", "은행", ProductType.LOAN, null, loanType, null, SYNCED,
                List.of(new ProductSnapshot.Option(200L, term, null, "FIXED", new BigDecimal(minRate), new BigDecimal("15"), code, limit)));
    }

    /** 검산표 공통 대출: 잔액 1,200,000, 12%, BULLET, 2/1·3/1 상환(만기 3/1). */
    private static LoanSnapshot bulletLoan(LoanStatus status) {
        return new LoanSnapshot(7L, "신용대출", "은행", LoanType.CREDIT, RepaymentType.BULLET, status, 1_200_000L,
                new BigDecimal("12"), 1, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1), 20L, "PERSONAL_CREDIT");
    }

    private static Item item(SimulationResult result, String key) {
        return result.comparison().items().stream().filter(i -> i.key().equals(key)).findFirst().orElseThrow();
    }

    private static Item section(List<Item> items, String key) {
        return items.stream().filter(i -> i.key().equals(key)).findFirst().orElseThrow();
    }

    private static void assertCode(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(code);
    }

    @Test
    void depositComputesPreTaxInterestOverActualDays() {
        // 2026-01-01, 1,000,000, 12개월, 3.65% → 365일, 이자 36,500, 수령 1,036,500
        SimulationResult result = savingProduct.calculate(baseline(JAN_1, goal(1, 500_000, 100_000, JAN_1)),
                new SavingProductInput(10L, 1_000_000L, 12).withProduct(savingProduct(ProductType.DEPOSIT, "FIXED_DEPOSIT", 12, "3.65")));
        assertThat(item(result, "GROSS_INTEREST").after()).isEqualTo(36_500L);
        assertThat(item(result, "GROSS_PAYOUT").after()).isEqualTo(1_036_500L);
        assertThat(result.details()).containsEntry("grossInterest", 36_500L).containsEntry("maturityDate", LocalDate.of(2027, 1, 1))
                .containsEntry("taxBasis", "PRE_TAX");
        assertThat(section(result.comparison().assets(), "INITIAL_CASH_CHANGE").after()).isEqualTo(-1_000_000L);
        assertThat(section(result.comparison().assets(), "INITIAL_NET_ASSET_CHANGE").after()).isEqualTo(0L);
        assertThat(result.cashflow().basis()).isEqualTo(SimulationResult.Cashflow.SCENARIO_NET_CASH_CHANGE);
        assertThat(result.cashflow().before()).isNull();
        assertThat(result.cashflow().currentMonth().delta()).isEqualTo(-1_000_000L);
        assertThat(result.cashflow().months()).hasSize(12);
        assertThat(result.cashflow().months().get(11)).isEqualTo(new SimulationResult.Month("2027-01", 1_036_500L, 36_500L));
        assertThat(result.cashflow().truncated()).isFalse();
        // 참고 기준선은 전후 동일, 목표는 자금 출처 불명
        assertThat(result.after().monthlySpending()).isEqualTo(result.before().monthlySpending()).isEqualTo(1_000_000L);
        assertThat(result.goalImpacts()).containsExactly(new SimulationResult.GoalImpact(1L, JAN_1.plusMonths(5), null));
        assertThat(result.goalImpactDetails()).containsExactly(new GoalImpactDetail(1L, GoalImpactDetail.NOT_COMPUTABLE,
                List.of(SimulationAssumptions.FUNDING_SOURCE_UNKNOWN)));
        assertThat(result.assumptions()).contains(SimulationAssumptions.SIMPLE_INTEREST_ACT_365, SimulationAssumptions.EXCLUDES_TAX_AND_FEES)
                .doesNotContain(SimulationAssumptions.FIXED_MONTHLY_INSTALLMENT);
    }

    @Test
    void savingsWeightsEachInstallmentByItsOwnDays() {
        // 2026-01-01, 월 100,000, 2개월, 3.65% → 납입 1/1(59일)·2/1(28일), 만기 3/1, 이자 870, 원금 200,000
        SimulationResult result = savingProduct.calculate(baseline(JAN_1),
                new SavingProductInput(10L, 100_000L, 2).withProduct(savingProduct(ProductType.SAVINGS, "FLEXIBLE", 2, "3.65")));
        assertThat(item(result, "PRODUCT_PRINCIPAL").after()).isEqualTo(200_000L);
        assertThat(item(result, "GROSS_INTEREST").after()).isEqualTo(870L);
        assertThat(result.cashflow().currentMonth().delta()).isEqualTo(-100_000L);
        assertThat(result.cashflow().months()).containsExactly(new SimulationResult.Month("2026-02", -100_000L, -200_000L),
                new SimulationResult.Month("2026-03", 200_870L, 870L));
        assertThat(result.goalImpacts()).isEmpty();
        assertThat(result.goalImpactDetails()).isEmpty();
        assertThat(result.assumptions()).contains(SimulationAssumptions.FIXED_MONTHLY_INSTALLMENT);
        // 윤년: 2028-01-01 12개월 예금은 366일
        SimulationResult leap = savingProduct.calculate(baseline(LocalDate.of(2028, 1, 1)),
                new SavingProductInput(10L, 1_000_000L, 12).withProduct(savingProduct(ProductType.DEPOSIT, "FIXED_DEPOSIT", 12, "3.65")));
        assertThat(item(leap, "GROSS_INTEREST").after()).isEqualTo(36_600L);
        assertCode(() -> savingProduct.calculate(baseline(JAN_1),
                new SavingProductInput(10L, 1L, 6).withProduct(savingProduct(ProductType.DEPOSIT, "FIXED_DEPOSIT", 12, "3.65"))),
                ErrorCode.SIMULATION_PRODUCT_OPTION_UNAVAILABLE);
    }

    @Test
    void newLoanAtZeroRateRepaysPrincipalEvenly() {
        // 1,200,000, 0%, 12개월 원금균등 → 월 100,000, 이자 0, 마지막 잔액 0; 최초 현금/부채 +1,200,000
        SimulationResult result = loanNew.calculate(baseline(JAN_1, goal(1, 500_000, 100_000, JAN_1)),
                new LoanNewInput(20L, 1_200_000L, 12, RepaymentType.EQUAL_PRINCIPAL)
                        .withProduct(loanProduct("PERSONAL_CREDIT", 12, "0", "EQUAL_PRINCIPAL", 5_000_000L)));
        assertThat(item(result, "INTEREST_TOTAL").after()).isEqualTo(0L);
        assertThat(item(result, "FIRST_MONTHLY_REPAYMENT").after()).isEqualTo(100_000L);
        assertThat(item(result, "MONTHLY_REPAYMENT_2027_01").after()).isEqualTo(100_000L);
        assertThat(result.comparison().items().stream().filter(i -> i.key().startsWith("MONTHLY_REPAYMENT_")).count()).isEqualTo(12);
        assertThat(section(result.comparison().assets(), "INITIAL_CASH_CHANGE").after()).isEqualTo(1_200_000L);
        assertThat(section(result.comparison().debts(), "INITIAL_DEBT_CHANGE").after()).isEqualTo(1_200_000L);
        assertThat(section(result.comparison().costs(), "INTEREST_TOTAL").after()).isEqualTo(0L);
        assertThat(result.cashflow().currentMonth().delta()).isEqualTo(1_200_000L);
        assertThat(result.cashflow().months().get(11).cumulative()).isEqualTo(0L);
        assertThat(result.details()).containsEntry("resolvedRepaymentType", "EQUAL_PRINCIPAL").containsEntry("repaymentTypeMode", "EXPLICIT")
                .containsEntry("firstRepaymentDate", LocalDate.of(2026, 2, 1)).containsEntry("maturityDate", LocalDate.of(2027, 1, 1));
        assertThat(result.goalImpactDetails().get(0).reasons()).containsExactly(SimulationAssumptions.GOAL_ALLOCATION_UNKNOWN);

        // AUTO: 옵션 코드가 한 방식이면 자동 선택. 12% BULLET 12개월 → 이자 12,000×12
        SimulationResult auto = loanNew.calculate(baseline(JAN_1), new LoanNewInput(20L, 1_200_000L, 12, null)
                .withProduct(loanProduct("PERSONAL_CREDIT", 12, "12", "BULLET", null)));
        assertThat(auto.details()).containsEntry("resolvedRepaymentType", "BULLET").containsEntry("repaymentTypeMode", "AUTO");
        assertThat(item(auto, "INTEREST_TOTAL").after()).isEqualTo(144_000L);
        assertThat(auto.assumptions()).contains(SimulationAssumptions.PRODUCT_LIMIT_UNKNOWN, SimulationAssumptions.LOAN_RATE_PUBLISHED_MINIMUM);
        assertCode(() -> loanNew.calculate(baseline(JAN_1), new LoanNewInput(20L, 1L, 12, null)
                .withProduct(loanProduct("PERSONAL_CREDIT", 12, "12", "AMORTIZING", null))), ErrorCode.SIMULATION_REPAYMENT_TYPE_REQUIRED);
        assertCode(() -> loanNew.calculate(baseline(JAN_1), new LoanNewInput(20L, 6_000_000L, 12, RepaymentType.EQUAL_PRINCIPAL)
                .withProduct(loanProduct("PERSONAL_CREDIT", 12, "0", "EQUAL_PRINCIPAL", 5_000_000L))), ErrorCode.SIMULATION_PRODUCT_LIMIT_EXCEEDED);
    }

    @Test
    void newLoanKeepsContractDayAcrossMonthEndsAndTruncatesLongTables() {
        // 1/31 기준 3개월: 2/28, 3/31, 4/30
        SimulationResult result = loanNew.calculate(baseline(LocalDate.of(2026, 1, 31)),
                new LoanNewInput(20L, 300_000L, 3, RepaymentType.EQUAL_PRINCIPAL).withProduct(loanProduct("PERSONAL_CREDIT", 3, "0", "EQUAL_PRINCIPAL", null)));
        assertThat(result.cashflow().months()).extracting(SimulationResult.Month::yearMonth).containsExactly("2026-02", "2026-03", "2026-04");
        assertThat(result.details()).containsEntry("maturityDate", LocalDate.of(2026, 4, 30));

        // 360개월: 계산은 전체, 표는 120개월에서 잘림
        SimulationResult mortgage = loanNew.calculate(baseline(JAN_1),
                new LoanNewInput(20L, 360_000_000L, 360, RepaymentType.EQUAL_PAYMENT).withProduct(loanProduct("MORTGAGE", 360, "0", "AMORTIZING", null)));
        assertThat(mortgage.cashflow().months()).hasSize(SimulationInput.MAX_DURATION_MONTHS);
        assertThat(mortgage.cashflow().truncated()).isTrue();
        assertThat(mortgage.cashflow().coveredThrough()).isEqualTo("2036-01");
        assertThat(mortgage.cashflow().fullTermEndDate()).isEqualTo(LocalDate.of(2056, 1, 1));
        assertThat(mortgage.details()).containsEntry("installmentCount", 360).containsEntry("totalRepayment", 360_000_000L);
        assertThat(mortgage.assumptions()).contains(SimulationAssumptions.CASHFLOW_TABLE_TRUNCATED, SimulationAssumptions.REPAYMENT_CODE_AMBIGUOUS_MAPPED);
        assertThat(mortgage.comparison().items().stream().filter(i -> i.key().startsWith("MONTHLY_REPAYMENT_")).count()).isEqualTo(120);
    }

    @Test
    void prepaymentOnBaseDateReducesInterestFromNextInstallment() {
        // 기준 1/1, 잔액 1,200,000, 12%, BULLET, 2/1·3/1; 1/1에 200,000 → 전 24,000, 후 20,000, 절감 4,000, 당월 현금 -200,000
        SimulationResult result = prepayment.calculate(baseline(JAN_1, goal(1, 500_000, 100_000, JAN_1)),
                new LoanPrepaymentInput(7L, 200_000L, JAN_1).withLoan(bulletLoan(LoanStatus.ACTIVE)));
        assertThat(item(result, "INTEREST_TOTAL")).isEqualTo(Item.amount("INTEREST_TOTAL", "만기까지 총 이자", 24_000L, 20_000L));
        assertThat(item(result, "INTEREST_SAVING").after()).isEqualTo(4_000L);
        assertThat(result.cashflow().currentMonth().delta()).isEqualTo(-200_000L);
        // 2월: 이자 12,000 → 10,000 (+2,000), 3월: 1,212,000 → 1,010,000 (+202,000)
        assertThat(result.cashflow().months()).containsExactly(new SimulationResult.Month("2026-02", 2_000L, -198_000L),
                new SimulationResult.Month("2026-03", 202_000L, 4_000L));
        assertThat(section(result.comparison().assets(), "INITIAL_CASH_CHANGE").after()).isEqualTo(-200_000L);
        assertThat(section(result.comparison().debts(), "INITIAL_DEBT_CHANGE").after()).isEqualTo(-200_000L);
        assertThat(section(result.comparison().assets(), "PREPAYMENT_CASH_CHANGE").after()).isEqualTo(-200_000L);
        assertThat(item(result, "MONTHLY_REPAYMENT_2026_02")).isEqualTo(Item.amount("MONTHLY_REPAYMENT_2026_02", "2026-02 월 상환액", 12_000L, 10_000L));
        assertThat(result.goalImpactDetails().get(0).reasons()).containsExactly(SimulationAssumptions.FUNDING_SOURCE_UNKNOWN);
        assertThat(result.assumptions()).contains(SimulationAssumptions.NO_ACCRUED_INTEREST_SETTLEMENT,
                SimulationAssumptions.MONTHLY_RATE_NOT_DAILY_PRORATED, SimulationAssumptions.MATURITY_KEPT_PAYMENT_RECALCULATED);

        // 전액 상환: 이후 이자 0, 잔액 0
        SimulationResult full = prepayment.calculate(baseline(JAN_1), new LoanPrepaymentInput(7L, 1_200_000L, JAN_1).withLoan(bulletLoan(LoanStatus.ACTIVE)));
        assertThat(item(full, "INTEREST_TOTAL").after()).isEqualTo(0L);
        assertThat(item(full, "BALANCE_AT_PAYMENT_DATE").after()).isEqualTo(0L);
        assertThat(full.cashflow().months().get(1).cumulative()).isEqualTo(24_000L);
    }

    @Test
    void futurePrepaymentAppliesScheduledInstallmentsFirst() {
        // 2/1에 200,000: 2/1 기존 이자 12,000 먼저, 3/1 이자 10,000 → 후 22,000
        SimulationResult result = prepayment.calculate(baseline(JAN_1),
                new LoanPrepaymentInput(7L, 200_000L, LocalDate.of(2026, 2, 1)).withLoan(bulletLoan(LoanStatus.ACTIVE)));
        assertThat(item(result, "INTEREST_TOTAL").after()).isEqualTo(22_000L);
        assertThat(item(result, "MONTHLY_REPAYMENT_2026_02").after()).isEqualTo(12_000L);
        // 최초 변화 0, 행동일 변화 -200,000·effectiveDate 2/1, 1월 cashflow 0, 2월에 한 번만
        assertThat(section(result.comparison().assets(), "INITIAL_CASH_CHANGE").after()).isEqualTo(0L);
        assertThat(section(result.comparison().debts(), "INITIAL_DEBT_CHANGE").after()).isEqualTo(0L);
        assertThat(section(result.comparison().debts(), "PREPAYMENT_DEBT_CHANGE").after()).isEqualTo(-200_000L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> timing = (List<Map<String, Object>>) result.details().get("comparisonTiming");
        assertThat(timing).anySatisfy(t -> {
            assertThat(t).containsEntry("key", "PREPAYMENT_CASH_CHANGE").containsEntry("effectiveDate", LocalDate.of(2026, 2, 1))
                    .containsEntry("basis", "ACTION_EVENT");
        });
        assertThat(result.cashflow().currentMonth().delta()).isEqualTo(0L);
        assertThat(result.cashflow().months()).containsExactly(new SimulationResult.Month("2026-02", -200_000L, -200_000L),
                new SimulationResult.Month("2026-03", 202_000L, 2_000L));

        // 만기 당일: 약정상환 후 잔액 0이라 양수 추가상환 불가. 과거·만기 이후 날짜는 거부
        assertCode(() -> prepayment.calculate(baseline(JAN_1), new LoanPrepaymentInput(7L, 1L, LocalDate.of(2026, 3, 1)).withLoan(bulletLoan(LoanStatus.ACTIVE))),
                ErrorCode.SIMULATION_AMOUNT_EXCEEDS_BALANCE);
        assertCode(() -> prepayment.calculate(baseline(JAN_1), new LoanPrepaymentInput(7L, 1_200_001L, JAN_1).withLoan(bulletLoan(LoanStatus.ACTIVE))),
                ErrorCode.SIMULATION_AMOUNT_EXCEEDS_BALANCE);
        assertCode(() -> prepayment.calculate(baseline(JAN_1), new LoanPrepaymentInput(7L, 1L, LocalDate.of(2025, 12, 31)).withLoan(bulletLoan(LoanStatus.ACTIVE))),
                ErrorCode.SIMULATION_INVALID_PAYMENT_DATE);
        assertCode(() -> prepayment.calculate(baseline(JAN_1), new LoanPrepaymentInput(7L, 1L, LocalDate.of(2026, 3, 2)).withLoan(bulletLoan(LoanStatus.ACTIVE))),
                ErrorCode.SIMULATION_INVALID_PAYMENT_DATE);
        assertCode(() -> prepayment.calculate(baseline(JAN_1), new LoanPrepaymentInput(7L, 1L, JAN_1).withLoan(bulletLoan(LoanStatus.OVERDUE))),
                ErrorCode.SIMULATION_LOAN_DATA_UNAVAILABLE);
        // 기준일이 다음 상환일을 지난 오래된 스냅샷
        assertCode(() -> prepayment.calculate(baseline(LocalDate.of(2026, 2, 2)), new LoanPrepaymentInput(7L, 1L, LocalDate.of(2026, 2, 2)).withLoan(bulletLoan(LoanStatus.ACTIVE))),
                ErrorCode.SIMULATION_LOAN_DATA_UNAVAILABLE);
    }

    @Test
    void equalPaymentPrepaymentRecalculatesRemainingPaymentsAndPreservesDates() {
        // 1/31 약정, 원리금균등 1,000,000·12%·잔여 3회(2/28·3/31·4/30). 2/28 이후 300,000 추가상환
        LoanSnapshot loan = new LoanSnapshot(8L, "대출", "은행", LoanType.CREDIT, RepaymentType.EQUAL_PAYMENT, LoanStatus.ACTIVE, 1_000_000L,
                new BigDecimal("12"), 31, LocalDate.of(2026, 2, 28), LocalDate.of(2026, 4, 30), null, null);
        SimulationResult result = prepayment.calculate(baseline(LocalDate.of(2026, 1, 31)),
                new LoanPrepaymentInput(8L, 300_000L, LocalDate.of(2026, 3, 1)).withLoan(loan));
        assertThat(result.comparison().items().stream().filter(i -> i.key().startsWith("MONTHLY_REPAYMENT_")).map(Item::key))
                .containsExactly("MONTHLY_REPAYMENT_2026_02", "MONTHLY_REPAYMENT_2026_03", "MONTHLY_REPAYMENT_2026_04");
        // 첫 회차는 공통, 이후 두 회차는 같은 원리금균등액으로 줄어든다
        assertThat(item(result, "MONTHLY_REPAYMENT_2026_02").delta()).isEqualTo(0L);
        assertThat(item(result, "MONTHLY_REPAYMENT_2026_03").delta()).isNegative();
        // 마지막 회차는 잔액 보정으로 1원 차이가 날 수 있다
        assertThat((Long) item(result, "MONTHLY_REPAYMENT_2026_03").after())
                .isCloseTo((Long) item(result, "MONTHLY_REPAYMENT_2026_04").after(), org.assertj.core.data.Offset.offset(1L));
        assertThat((Long) item(result, "INTEREST_SAVING").after()).isPositive();
        assertThat(result.details()).containsEntry("installmentsBeforePaymentDate", 1).containsEntry("installmentsRecalculated", 2);
        assertThat(result.cashflow().months().get(2).cumulative()).isEqualTo(
                (Long) item(result, "REPAYMENT_TOTAL").before() - (Long) item(result, "REPAYMENT_TOTAL").after() - 300_000L);
    }

    @Test
    void partialRefinanceSplitsSchedulesAndPreservesPrincipal() {
        // 잔액 1,200,000 12% BULLET 2/1·3/1, 600,000을 6%로 대환 → 기존분 12,000 + 신규분 6,000 = 18,000
        SimulationResult result = refinance.calculate(baseline(JAN_1, goal(1, 500_000, 100_000, JAN_1)),
                new LoanRefinanceInput(7L, 20L, 600_000L).withSnapshots(bulletLoan(LoanStatus.ACTIVE), loanProduct("CREDIT_REFINANCE", 2, "6", "BULLET", null)));
        assertThat(item(result, "INTEREST_TOTAL")).isEqualTo(Item.amount("INTEREST_TOTAL", "만기까지 총 이자", 24_000L, 18_000L));
        assertThat(item(result, "LOAN_BALANCE")).isEqualTo(Item.amount("LOAN_BALANCE", "대출 잔액(원금 보존)", 1_200_000L, 1_200_000L));
        assertThat(section(result.comparison().assets(), "INITIAL_CASH_CHANGE").after()).isEqualTo(0L);
        assertThat(section(result.comparison().debts(), "INITIAL_DEBT_CHANGE").after()).isEqualTo(0L);
        assertThat(item(result, "MONTHLY_REPAYMENT_2026_03")).isEqualTo(Item.amount("MONTHLY_REPAYMENT_2026_03", "2026-03 월 상환액", 1_212_000L, 1_209_000L));
        assertThat(result.cashflow().currentMonth().delta()).isEqualTo(0L);
        assertThat(result.cashflow().months()).containsExactly(new SimulationResult.Month("2026-02", 3_000L, 3_000L),
                new SimulationResult.Month("2026-03", 3_000L, 6_000L));
        assertThat(result.details()).containsEntry("keptBalance", 600_000L).containsEntry("remainingInstallments", 2)
                .containsEntry("refinanceAmountMode", "EXPLICIT").containsEntry("comparisonGroup", "CREDIT");
        assertThat(result.assumptions()).contains(SimulationAssumptions.PARTIAL_REFINANCE_SPLIT_SCHEDULES, SimulationAssumptions.REMAINING_SCHEDULE_AS_TERM);
        // 목표: 남은 500,000·속도 100,000, 3,000×2 절감 → 5개월 그대로(작은 절감)
        assertThat(result.goalImpacts()).containsExactly(new SimulationResult.GoalImpact(1L, JAN_1.plusMonths(5), JAN_1.plusMonths(5)));
        assertThat(result.goalImpactDetails().get(0).status()).isEqualTo(GoalImpactDetail.COMPUTED);
        assertThat(result.goalImpactDetails().get(0).reasons()).contains(SimulationAssumptions.REPAYMENT_DELTA_ALLOCATED_TO_GOALS);
        assertThat(result.after().goals().get(0).expectedAchievementDate()).isEqualTo(JAN_1.plusMonths(5));
    }

    @Test
    void refinanceGoalProjectionMatchesVerificationTable() {
        // 남은 30,000·속도 10,000, 3,000/월×2 → 3개월 유지; 남은 25,000이면 2개월로 단축
        ProductSnapshot target = loanProduct("CREDIT_REFINANCE", 2, "6", "BULLET", null);
        LoanRefinanceInput input = new LoanRefinanceInput(7L, 20L, 600_000L).withSnapshots(bulletLoan(LoanStatus.ACTIVE), target);
        SimulationResult same = refinance.calculate(baseline(JAN_1, goal(1, 30_000, 10_000, JAN_1)), input);
        assertThat(same.goalImpacts().get(0)).isEqualTo(new SimulationResult.GoalImpact(1L, JAN_1.plusMonths(3), JAN_1.plusMonths(3)));
        SimulationResult shorter = refinance.calculate(baseline(JAN_1, goal(1, 25_000, 10_000, JAN_1)), input);
        assertThat(shorter.goalImpacts().get(0)).isEqualTo(new SimulationResult.GoalImpact(1L, JAN_1.plusMonths(3), JAN_1.plusMonths(2)));
        // 여러 목표: 배분 합 = 3,000, 남은 금액 비례(2:1), 달성 목표는 기준일 유지
        SimulationResult multi = refinance.calculate(baseline(JAN_1, goal(1, 20_000, 10_000, JAN_1), goal(2, 10_000, 0, JAN_1), goal(3, 0, 0, JAN_1)), input);
        assertThat(multi.goalImpacts()).containsExactly(
                new SimulationResult.GoalImpact(1L, JAN_1.plusMonths(2), JAN_1.plusMonths(2)),
                new SimulationResult.GoalImpact(2L, null, null),
                new SimulationResult.GoalImpact(3L, JAN_1, JAN_1));
        assertThat(multi.goalImpactDetails()).extracting(GoalImpactDetail::status)
                .containsExactly(GoalImpactDetail.COMPUTED, GoalImpactDetail.NOT_COMPUTABLE, GoalImpactDetail.COMPUTED);
        assertThat(multi.goalImpactDetails().get(1).reasons()).contains(SimulationAssumptions.SAVING_PACE_ZERO);
        assertThat(multi.goalImpactDetails().get(2).reasons()).containsExactly(SimulationAssumptions.ALREADY_ACHIEVED);
        assertThat(multi.assumptions()).contains("SPLIT_BY_REMAINING");
        // 속도 0이지만 절감으로 달성: 남은 5,000, 2/1·3/1에 3,000씩 → 3/1
        SimulationResult paceZero = refinance.calculate(baseline(JAN_1, goal(1, 5_000, 0, JAN_1)), input);
        assertThat(paceZero.goalImpacts().get(0)).isEqualTo(new SimulationResult.GoalImpact(1L, null, LocalDate.of(2026, 3, 1)));
    }

    @Test
    void fullRefinanceAtSameRateReproducesOriginalScheduleAndKeepsGoalDates() {
        SimulationResult result = refinance.calculate(baseline(JAN_1, goal(1, 500_000, 100_000, JAN_1), goal(2, 100, 0, JAN_1)),
                new LoanRefinanceInput(7L, 20L, null).withSnapshots(bulletLoan(LoanStatus.ACTIVE), loanProduct("PERSONAL_CREDIT", 2, "12", "BULLET", null)));
        assertThat(item(result, "INTEREST_TOTAL").delta()).isEqualTo(0L);
        assertThat(item(result, "REPAYMENT_TOTAL").delta()).isEqualTo(0L);
        assertThat(result.cashflow().months()).extracting(SimulationResult.Month::delta).containsOnly(0L);
        assertThat(result.goalImpacts()).containsExactly(new SimulationResult.GoalImpact(1L, JAN_1.plusMonths(5), JAN_1.plusMonths(5)),
                new SimulationResult.GoalImpact(2L, null, null));
        assertThat(result.details()).containsEntry("refinanceAmountMode", "FULL_BALANCE").containsEntry("refinanceAmount", 1_200_000L);
        assertThat(result.assumptions()).doesNotContain(SimulationAssumptions.PARTIAL_REFINANCE_SPLIT_SCHEDULES);
    }

    @Test
    void refinanceRejectsGroupMismatchTermMismatchDueTodayAndExcessAmount() {
        LoanSnapshot loan = bulletLoan(LoanStatus.ACTIVE);
        assertCode(() -> refinance.calculate(baseline(JAN_1), new LoanRefinanceInput(7L, 20L, null)
                .withSnapshots(loan, loanProduct("MORTGAGE", 2, "3", "BULLET", null))), ErrorCode.SIMULATION_PRODUCT_NOT_SUPPORTED);
        // 연결 상품이 없으면 비교군을 추측하지 않는다
        LoanSnapshot unlinked = new LoanSnapshot(7L, "대출", "은행", LoanType.CREDIT, RepaymentType.BULLET, LoanStatus.ACTIVE, 1_200_000L,
                new BigDecimal("12"), 1, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1), null, null);
        assertCode(() -> refinance.calculate(baseline(JAN_1), new LoanRefinanceInput(7L, 20L, null)
                .withSnapshots(unlinked, loanProduct("PERSONAL_CREDIT", 2, "3", "BULLET", null))), ErrorCode.SIMULATION_PRODUCT_NOT_SUPPORTED);
        // 기간 밴드만 맞는 후보(12개월 옵션)는 실행 불가
        assertCode(() -> refinance.calculate(baseline(JAN_1), new LoanRefinanceInput(7L, 20L, null)
                .withSnapshots(loan, loanProduct("PERSONAL_CREDIT", 12, "3", "BULLET", null))), ErrorCode.SIMULATION_PRODUCT_OPTION_UNAVAILABLE);
        // 상환방식이 다른 옵션도 불가
        assertCode(() -> refinance.calculate(baseline(JAN_1), new LoanRefinanceInput(7L, 20L, null)
                .withSnapshots(loan, loanProduct("PERSONAL_CREDIT", 2, "3", "EQUAL_PRINCIPAL", null))), ErrorCode.SIMULATION_PRODUCT_OPTION_UNAVAILABLE);
        // 당일 대환은 거부, 잔액 초과 거부, 한도는 신규분 기준
        assertCode(() -> refinance.calculate(baseline(LocalDate.of(2026, 2, 1)), new LoanRefinanceInput(7L, 20L, null)
                .withSnapshots(loan, loanProduct("PERSONAL_CREDIT", 2, "3", "BULLET", null))), ErrorCode.SIMULATION_LOAN_DATA_UNAVAILABLE);
        assertCode(() -> refinance.calculate(baseline(JAN_1), new LoanRefinanceInput(7L, 20L, 1_200_001L)
                .withSnapshots(loan, loanProduct("PERSONAL_CREDIT", 2, "3", "BULLET", null))), ErrorCode.SIMULATION_AMOUNT_EXCEEDS_BALANCE);
        assertCode(() -> refinance.calculate(baseline(JAN_1), new LoanRefinanceInput(7L, 20L, null)
                .withSnapshots(loan, loanProduct("PERSONAL_CREDIT", 2, "3", "BULLET", 1_000_000L))), ErrorCode.SIMULATION_PRODUCT_LIMIT_EXCEEDED);
        assertThat(refinance.calculate(baseline(JAN_1), new LoanRefinanceInput(7L, 20L, 1_000_000L)
                .withSnapshots(loan, loanProduct("PERSONAL_CREDIT", 2, "3", "BULLET", 1_000_000L))).details()).containsEntry("keptBalance", 200_000L);
    }

    @Test
    void longRemainingSchedulesAndOverflowsAreRejected() {
        LoanSnapshot longLoan = new LoanSnapshot(9L, "대출", "은행", LoanType.MORTGAGE, RepaymentType.BULLET, LoanStatus.ACTIVE, 1_000L,
                new BigDecimal("1"), 1, LocalDate.of(2026, 2, 1), LocalDate.of(2056, 2, 1), null, "MORTGAGE");
        assertCode(() -> prepayment.calculate(baseline(JAN_1), new LoanPrepaymentInput(9L, 1L, JAN_1).withLoan(longLoan)),
                ErrorCode.SIMULATION_PERIOD_NOT_SUPPORTED);
        assertCode(() -> savingProduct.calculate(baseline(JAN_1), new SavingProductInput(10L, Long.MAX_VALUE, 120)
                .withProduct(savingProduct(ProductType.SAVINGS, "FLEXIBLE", 120, "3.65"))), ErrorCode.SIMULATION_AMOUNT_OUT_OF_RANGE);
        assertCode(() -> loanNew.calculate(baseline(JAN_1), new LoanNewInput(20L, Long.MAX_VALUE, 2, RepaymentType.BULLET)
                .withProduct(loanProduct("PERSONAL_CREDIT", 2, "12", "BULLET", null))), ErrorCode.SIMULATION_AMOUNT_OUT_OF_RANGE);
    }

    @Test
    void inputsValidateRangesAndFingerprintsDistinguishModes() {
        assertThatThrownBy(() -> new SavingProductInput(1L, 0L, 12)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SavingProductInput(1L, 1L, 121)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoanNewInput(1L, 1L, 361, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoanPrepaymentInput(1L, 0L, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LoanRefinanceInput(1L, 2L, 0L)).isInstanceOf(IllegalArgumentException.class);
        assertThat(new LoanNewInput(1L, 10L, 12, null).fingerprint()).isEqualTo("1|10|12|AUTO")
                .isNotEqualTo(new LoanNewInput(1L, 10L, 12, RepaymentType.BULLET).fingerprint());
        assertThat(new LoanRefinanceInput(1L, 2L, null).fingerprint()).isEqualTo("1|2|FULL_BALANCE")
                .isNotEqualTo(new LoanRefinanceInput(1L, 2L, 100L).fingerprint());
        LoanPrepaymentInput omitted = new LoanPrepaymentInput(1L, 10L, null);
        assertThatThrownBy(omitted::fingerprint).isInstanceOf(IllegalStateException.class);
        assertThat(omitted.withBaseDate(JAN_1).fingerprint()).isEqualTo(new LoanPrepaymentInput(1L, 10L, JAN_1).withBaseDate(JAN_1.plusDays(1)).fingerprint())
                .isEqualTo("1|10|2026-01-01");
        assertThat(new LoanNewInput(1L, 10L, 360, null).durationMonths()).isEqualTo(120);
        assertThat(new SavingProductInput(1L, 10L, 7).durationMonths()).isEqualTo(7);
    }
}
