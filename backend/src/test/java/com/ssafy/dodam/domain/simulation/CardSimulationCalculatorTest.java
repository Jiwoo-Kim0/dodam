package com.ssafy.dodam.domain.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.dodam.domain.simulation.dto.CashAdvanceInput;
import com.ssafy.dodam.domain.simulation.dto.InstallmentInput;
import com.ssafy.dodam.domain.simulation.dto.RevolvingInput;
import com.ssafy.dodam.domain.simulation.dto.ScenarioComputation;
import com.ssafy.dodam.domain.simulation.dto.ScenarioLedger;
import com.ssafy.dodam.domain.simulation.dto.SimulationBaseline;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.GoalImpactDetail;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Item;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Month;
import com.ssafy.dodam.domain.simulation.service.GoalAchievementProjection;
import com.ssafy.dodam.domain.simulation.service.SimulationAssumptions;
import com.ssafy.dodam.domain.simulation.service.calculator.CashAdvanceCalculator;
import com.ssafy.dodam.domain.simulation.service.calculator.InstallmentCalculator;
import com.ssafy.dodam.domain.simulation.service.calculator.RevolvingCalculator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 586 카드 계산기 3종. DB 없이 계획서 §7 검산 fixture를 그대로 검증한다. 모든 금리·금액은 손계산 가능한 합성 값이다.
 * 기준일 2026-09-15: 10/15까지 30일, 11/15까지 31일.
 */
class CardSimulationCalculatorTest {
    private static final LocalDate SEP_15 = LocalDate.of(2026, 9, 15);
    private final InstallmentCalculator installment = new InstallmentCalculator();
    private final RevolvingCalculator revolving = new RevolvingCalculator();
    private final CashAdvanceCalculator cashAdvance = new CashAdvanceCalculator();

    static SimulationBaseline baseline(LocalDate base, SimulationBaseline.SavingGoal... goals) {
        return new SimulationBaseline(base, 3_000_000L, 1_000_000L, Map.of(), List.of(goals), List.of(), List.of());
    }

    static SimulationBaseline.SavingGoal goal(long id, long remaining, long pace, LocalDate base) {
        return new SimulationBaseline.SavingGoal(id, "목표" + id, 1_000_000L, 1_000_000L - remaining, remaining, pace,
                base.plusYears(1), GoalAchievementProjection.expectedDate(remaining, pace, base), List.of());
    }

    static Item item(SimulationResult result, String key) {
        return result.comparison().items().stream().filter(i -> i.key().equals(key)).findFirst().orElseThrow();
    }

    static Item section(List<Item> items, String key) {
        return items.stream().filter(i -> i.key().equals(key)).findFirst().orElseThrow();
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> schedule(SimulationResult result) {
        return (List<Map<String, Object>>) result.details().get("repaymentSchedule");
    }

    /** payment = principal + interest + fee, closing = opening − principal, 회차 원금 합 = 원금. */
    static void assertScheduleInvariants(List<Map<String, Object>> rows, long principalTotal) {
        long principalSum = 0;
        for (Map<String, Object> row : rows) {
            long principal = (long) row.get("principalPayment");
            assertThat(row.get("payment")).isEqualTo(principal + (long) row.get("interest") + (long) row.get("fee"));
            assertThat(row.get("closingPrincipal")).isEqualTo((long) row.get("openingPrincipal") - principal);
            principalSum += principal;
        }
        assertThat(principalSum).isEqualTo(principalTotal);
    }

    static long netCashSum(SimulationResult result) {
        long sum = result.cashflow().currentMonth() == null ? 0 : result.cashflow().currentMonth().delta();
        for (Month month : result.cashflow().months()) {
            sum += month.delta();
        }
        return sum;
    }

    // ---------- 할부 ----------

    @Test
    void installmentAtZeroFeeSplitsPrincipalEvenlyAndNetCashIsZero() {
        // 1,200,000 / 12개월 / 생략 → 원금 100,000씩, 수수료 0, 현금 합 0(+1,200,000 − 12 × 100,000)
        ScenarioComputation computed = installment.compute(baseline(SEP_15, goal(1, 500_000, 100_000, SEP_15)),
                InstallmentInput.of(1_200_000L, 12, null));
        SimulationResult result = computed.result();
        assertThat(item(result, "INSTALLMENT_FEE_TOTAL").after()).isEqualTo(0L);
        assertThat(item(result, "INITIAL_PAYMENT")).extracting(Item::before, Item::after).containsExactly(1_200_000L, 0L);
        assertThat(item(result, "FIRST_INSTALLMENT_PAYMENT").after()).isEqualTo(100_000L);
        assertThat(section(result.comparison().assets(), "INITIAL_CASH_CHANGE").after()).isEqualTo(1_200_000L);
        assertThat(section(result.comparison().debts(), "INITIAL_DEBT_CHANGE").after()).isEqualTo(1_200_000L);
        assertThat(section(result.comparison().assets(), "INITIAL_NET_ASSET_CHANGE").after()).isEqualTo(0L);
        assertThat(result.cashflow().currentMonth().delta()).isEqualTo(1_200_000L);
        assertThat(result.cashflow().months()).hasSize(12);
        assertThat(result.cashflow().months().get(0)).isEqualTo(new Month("2026-10", -100_000L, 1_100_000L));
        assertThat(netCashSum(result)).isZero();
        assertThat(result.cashflow().fullTermEndDate()).isEqualTo(LocalDate.of(2027, 9, 15));
        List<Map<String, Object>> rows = schedule(result);
        assertThat(rows).hasSize(12);
        assertThat(rows.get(0)).containsEntry("date", LocalDate.of(2026, 10, 15)).containsEntry("fee", 0L).containsEntry("interest", 0L);
        assertScheduleInvariants(rows, 1_200_000L);
        assertThat(result.details()).containsEntry("feeRateMode", "ASSUMED_ZERO").containsEntry("calculationPolicy",
                InstallmentCalculator.CALCULATION_POLICY).containsEntry("inputSource", "USER_INPUT").containsEntry("paidOff", true);
        assertThat(result.assumptions()).contains(SimulationAssumptions.ZERO_FEE_ASSUMED, SimulationAssumptions.CARD_TERMS_USER_INPUT,
                SimulationAssumptions.SIMPLE_INTEREST_ACT_365);
        // 목표: 귀속 불명
        assertThat(result.goalImpacts()).containsExactly(new SimulationResult.GoalImpact(1L, SEP_15.plusMonths(5), null));
        assertThat(result.goalImpactDetails()).containsExactly(new GoalImpactDetail(1L, GoalImpactDetail.NOT_COMPUTABLE,
                List.of(SimulationAssumptions.GOAL_ALLOCATION_UNKNOWN)));
        // 내부 자료: 전 = 기준일 원금 결제, 후 = 12회차, 비용 0
        ScenarioLedger ledger = computed.ledger();
        assertThat(ledger.beforePayments()).containsExactly(new ScenarioLedger.Payment(SEP_15, 1_200_000L, 0L, 0L));
        assertThat(ledger.afterPayments()).hasSize(12).allSatisfy(p -> assertThat(p.cost()).isZero());
        assertThat(ledger.costComplete()).isTrue();
        assertThat(ledger.fullTermEnd()).isEqualTo(LocalDate.of(2027, 9, 15));

        // 명시적 0은 EXPLICIT이며 가정 코드가 없다. 지문은 다르지만 행동 키는 같다
        SimulationResult explicit = installment.calculate(baseline(SEP_15), InstallmentInput.of(1_200_000L, 12, BigDecimal.ZERO));
        assertThat(explicit.assumptions()).doesNotContain(SimulationAssumptions.ZERO_FEE_ASSUMED);
        assertThat(explicit.details()).containsEntry("feeRateMode", "EXPLICIT");
        InstallmentInput omitted = InstallmentInput.of(1_200_000L, 12, null);
        InstallmentInput zero = InstallmentInput.of(1_200_000L, 12, BigDecimal.ZERO);
        InstallmentInput zeroScaled = InstallmentInput.of(1_200_000L, 12, new BigDecimal("0.0000"));
        assertThat(omitted.fingerprint()).isNotEqualTo(zero.fingerprint());
        assertThat(zero.fingerprint()).isEqualTo(zeroScaled.fingerprint());
        assertThat(omitted.actionKey()).isEqualTo(zero.actionKey());
    }

    @Test
    void installmentFeeIsDailyOnOpeningBalance() {
        // 1,200,000 / 2개월 / 12%: 10/15 잔액 1,200,000 × 30일 → 11,836; 11/15 잔액 600,000 × 31일 → 6,115; 합 17,951
        SimulationResult result = installment.calculate(baseline(SEP_15), InstallmentInput.of(1_200_000L, 2, new BigDecimal("12")));
        List<Map<String, Object>> rows = schedule(result);
        assertThat(rows.get(0)).containsEntry("openingPrincipal", 1_200_000L).containsEntry("principalPayment", 600_000L)
                .containsEntry("fee", 11_836L).containsEntry("payment", 611_836L).containsEntry("closingPrincipal", 600_000L);
        assertThat(rows.get(1)).containsEntry("fee", 6_115L).containsEntry("closingPrincipal", 0L);
        assertThat(item(result, "INSTALLMENT_FEE_TOTAL").after()).isEqualTo(17_951L);
        assertThat(item(result, "PAYMENT_TOTAL")).extracting(Item::before, Item::after).containsExactly(1_200_000L, 1_217_951L);
        assertThat(section(result.comparison().costs(), "INSTALLMENT_FEE_TOTAL").after()).isEqualTo(17_951L);
        assertThat(netCashSum(result)).isEqualTo(-17_951L);
        assertThat(result.assumptions()).doesNotContain(SimulationAssumptions.ZERO_FEE_ASSUMED);
        assertThat(result.comparison().items()).extracting(Item::key).contains("MONTHLY_PAYMENT_2026_10", "MONTHLY_PAYMENT_2026_11");
    }

    @Test
    void installmentRestoresMonthEndDatesAndAllowsZeroPrincipalInstallments() {
        // 1/31 기준 3개월 → 2/28, 3/31, 4/30 (매번 최초 날짜에서 계산)
        SimulationResult result = installment.calculate(baseline(LocalDate.of(2026, 1, 31)), InstallmentInput.of(300_000L, 3, null));
        assertThat(schedule(result)).extracting(row -> row.get("date"))
                .containsExactly(LocalDate.of(2026, 2, 28), LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 30));
        // 원금 5원 / 12개월 → 앞 11회차 원금 0, 마지막 5원
        SimulationResult tiny = installment.calculate(baseline(SEP_15), InstallmentInput.of(5L, 12, new BigDecimal("50")));
        List<Map<String, Object>> rows = schedule(tiny);
        assertThat(rows.subList(0, 11)).allSatisfy(row -> assertThat(row.get("principalPayment")).isEqualTo(0L));
        assertThat(rows.get(11)).containsEntry("principalPayment", 5L).containsEntry("closingPrincipal", 0L);
        assertScheduleInvariants(rows, 5L);
        // 1개월·120개월 경계
        assertThat(schedule(installment.calculate(baseline(SEP_15), InstallmentInput.of(1L, 1, null)))).hasSize(1);
        assertThat(schedule(installment.calculate(baseline(SEP_15), InstallmentInput.of(1L, 120, null)))).hasSize(120);
        assertThat(installment.calculate(baseline(SEP_15), InstallmentInput.of(1L, 120, null)).cashflow().truncated()).isFalse();
        assertThatThrownBy(() -> InstallmentInput.of(1L, 121, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InstallmentInput.of(0L, 12, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InstallmentInput.of(1L, 12, new BigDecimal("100.0001"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InstallmentInput.of(1L, 12, new BigDecimal("1.00001"))).isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- 리볼빙 ----------

    @Test
    void revolvingPaysRatioOfPrincipalAndInterestOnCarriedBalance() {
        // 1,000,000 / 50% / 12%: 기준일 원금 500,000 결제, 10/15 원금 250,000·이자 4,932(500,000 × 30일)·잔액 250,000
        ScenarioComputation computed = revolving.compute(baseline(SEP_15, goal(1, 500_000, 100_000, SEP_15)),
                new RevolvingInput(5L, 1_000_000L, new BigDecimal("50"), new BigDecimal("12")));
        SimulationResult result = computed.result();
        assertThat(item(result, "INITIAL_PAYMENT")).extracting(Item::before, Item::after).containsExactly(1_000_000L, 500_000L);
        assertThat(item(result, "CARRIED_BALANCE").after()).isEqualTo(500_000L);
        List<Map<String, Object>> rows = schedule(result);
        assertThat(rows.get(0)).containsEntry("date", LocalDate.of(2026, 10, 15)).containsEntry("openingPrincipal", 500_000L)
                .containsEntry("principalPayment", 250_000L).containsEntry("interest", 4_932L).containsEntry("fee", 0L)
                .containsEntry("payment", 254_932L).containsEntry("closingPrincipal", 250_000L);
        // 반씩 줄어 1원까지 결제하면 완납된다. 원금 합 + 잔액 = 입력, 순현금 합 = 잔액(0) − 누적이자
        assertThat(result.details()).containsEntry("paidOff", true).containsEntry("remainingBalance", 0L)
                .containsEntry("terminationReason", RevolvingCalculator.TERMINATION_PAID_OFF).containsEntry("cardId", 5L)
                .containsEntry("initialPayment", 500_000L);
        assertScheduleInvariants(rows, 500_000L);
        long totalInterest = (long) item(result, "REVOLVING_INTEREST_TOTAL").after();
        assertThat(totalInterest).isGreaterThan(4_932L);
        assertThat(netCashSum(result)).isEqualTo(-totalInterest);
        assertThat(result.cashflow().currentMonth().delta()).isEqualTo(500_000L);
        assertThat(result.cashflow().fullTermEndDate()).isEqualTo(rows.get(rows.size() - 1).get("date"));
        assertThat(section(result.comparison().assets(), "INITIAL_CASH_CHANGE").after()).isEqualTo(500_000L);
        assertThat(section(result.comparison().debts(), "INITIAL_DEBT_CHANGE").after()).isEqualTo(500_000L);
        assertThat(item(result, "PAYOFF_DATE").after()).isEqualTo(rows.get(rows.size() - 1).get("date").toString());
        assertThat(result.assumptions()).doesNotContain(SimulationAssumptions.REVOLVING_HORIZON_LIMIT);
        assertThat(result.goalImpactDetails()).containsExactly(new GoalImpactDetail(1L, GoalImpactDetail.NOT_COMPUTABLE,
                List.of(SimulationAssumptions.GOAL_ALLOCATION_UNKNOWN)));
        assertThat(computed.ledger().beforePayments()).containsExactly(new ScenarioLedger.Payment(SEP_15, 1_000_000L, 0L, 0L));
        assertThat(computed.ledger().afterPayments().get(0)).isEqualTo(new ScenarioLedger.Payment(SEP_15, 500_000L, 0L, 0L));
        assertThat(computed.ledger().costComplete()).isTrue();
    }

    @Test
    void revolvingFullRatioHasNoScheduleAndZeroRateHasNoInterest() {
        SimulationResult full = revolving.calculate(baseline(SEP_15), new RevolvingInput(null, 1_000_000L, new BigDecimal("100"), new BigDecimal("12")));
        assertThat(item(full, "INITIAL_PAYMENT").after()).isEqualTo(1_000_000L);
        assertThat(item(full, "REVOLVING_INTEREST_TOTAL").after()).isEqualTo(0L);
        assertThat(schedule(full)).isEmpty();
        assertThat(full.cashflow().currentMonth().delta()).isZero();
        assertThat(full.cashflow().months()).isEmpty();
        assertThat(full.details()).containsEntry("terminationReason", RevolvingCalculator.TERMINATION_NO_CARRY_OVER).containsEntry("paidOff", true);
        // 0% 금리·1원: 최소 결제 1원으로 즉시 완납, 이자 0
        SimulationResult one = revolving.calculate(baseline(SEP_15), new RevolvingInput(null, 1L, new BigDecimal("0.0001"), BigDecimal.ZERO));
        assertThat(item(one, "INITIAL_PAYMENT").after()).isEqualTo(1L);
        assertThat(schedule(one)).isEmpty();
        assertThatThrownBy(() -> new RevolvingInput(null, 1L, BigDecimal.ZERO, BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RevolvingInput(0L, 1L, BigDecimal.ONE, BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revolvingStopsAtHorizonWithoutForcingPayoff() {
        // 1,000,000 / 0.0001%: 회차 원금 max(1, ceil(1)) = 1원 → 120회차 뒤에도 미완납
        ScenarioComputation computed = revolving.compute(baseline(SEP_15),
                new RevolvingInput(null, 1_000_000L, new BigDecimal("0.0001"), new BigDecimal("12")));
        SimulationResult result = computed.result();
        assertThat(schedule(result)).hasSize(120);
        assertThat(result.details()).containsEntry("paidOff", false).containsEntry("remainingBalance", 999_879L)
                .containsEntry("terminationReason", RevolvingCalculator.TERMINATION_HORIZON_LIMIT)
                .containsEntry("evaluatedThrough", SEP_15.plusMonths(120));
        assertThat(item(result, "PAYOFF_DATE").after()).isNull();
        assertThat(item(result, "REMAINING_BALANCE").after()).isEqualTo(999_879L);
        // 미상환 잔액은 이익이 아니다: 순현금 합 = 잔액 − 누적이자
        long totalInterest = (long) item(result, "REVOLVING_INTEREST_TOTAL").after();
        assertThat(netCashSum(result)).isEqualTo(999_879L - totalInterest);
        assertThat(result.cashflow().fullTermEndDate()).isNull();
        assertThat(result.cashflow().truncated()).isFalse();
        assertThat(result.cashflow().months()).hasSize(120);
        assertThat(result.assumptions()).contains(SimulationAssumptions.REVOLVING_HORIZON_LIMIT);
        assertThat(computed.ledger().fullTermEnd()).isNull();
        assertThat(computed.ledger().costComplete()).isFalse();
        assertThat(computed.ledger().costCoveredThrough()).isEqualTo(SEP_15.plusMonths(120));
    }

    // ---------- 현금서비스 ----------

    @Test
    void cashAdvanceChargesSimpleInterestUntilBulletRepayment() {
        // 1,000,000 / 12% / 1개월(2026-09-15 → 10-15, 30일) → 이자 9,863, 상환 1,009,863, 순현금 합 −9,863
        ScenarioComputation computed = cashAdvance.compute(baseline(SEP_15, goal(1, 500_000, 100_000, SEP_15)),
                CashAdvanceInput.of(1_000_000L, new BigDecimal("12"), null));
        SimulationResult result = computed.result();
        assertThat(item(result, "INTEREST_TOTAL").after()).isEqualTo(9_863L);
        assertThat(item(result, "REPAYMENT_TOTAL").after()).isEqualTo(1_009_863L);
        assertThat(item(result, "REPAYMENT_DATE").after()).isEqualTo("2026-10-15");
        assertThat(result.details()).containsEntry("days", 30L).containsEntry("repaymentMonths", 1).containsEntry("interest", 9_863L)
                .containsEntry("repaymentDate", LocalDate.of(2026, 10, 15));
        assertThat(schedule(result)).singleElement().satisfies(row -> {
            assertThat(row).containsEntry("principalPayment", 1_000_000L).containsEntry("interest", 9_863L).containsEntry("fee", 0L)
                    .containsEntry("payment", 1_009_863L).containsEntry("closingPrincipal", 0L);
        });
        assertThat(result.cashflow().currentMonth().delta()).isEqualTo(1_000_000L);
        assertThat(result.cashflow().months()).containsExactly(new Month("2026-10", -1_009_863L, -9_863L));
        assertThat(netCashSum(result)).isEqualTo(-9_863L);
        assertThat(section(result.comparison().assets(), "INITIAL_CASH_CHANGE").after()).isEqualTo(1_000_000L);
        assertThat(section(result.comparison().debts(), "INITIAL_DEBT_CHANGE").after()).isEqualTo(1_000_000L);
        assertThat(section(result.comparison().assets(), "INITIAL_NET_ASSET_CHANGE").after()).isEqualTo(0L);
        assertThat(section(result.comparison().costs(), "INTEREST_TOTAL").after()).isEqualTo(9_863L);
        assertThat(result.goalImpactDetails()).containsExactly(new GoalImpactDetail(1L, GoalImpactDetail.NOT_COMPUTABLE,
                List.of(SimulationAssumptions.GOAL_ALLOCATION_UNKNOWN)));
        assertThat(computed.ledger().afterPayments()).containsExactly(new ScenarioLedger.Payment(LocalDate.of(2026, 10, 15), 1_000_000L, 9_863L, 0L));
        // 생략·null·1은 같은 지문
        assertThat(CashAdvanceInput.of(1_000_000L, new BigDecimal("12"), null).fingerprint())
                .isEqualTo(CashAdvanceInput.of(1_000_000L, new BigDecimal("12.0"), 1).fingerprint());
    }

    @Test
    void cashAdvanceHandlesZeroRateLongTermAndLeapYear() {
        assertThat(item(cashAdvance.calculate(baseline(SEP_15), CashAdvanceInput.of(1_000_000L, BigDecimal.ZERO, 1)), "INTEREST_TOTAL").after())
                .isEqualTo(0L);
        // 120개월: 2036-09-15까지 3,653일(윤년 2028·2032·2036 포함), 12% → 1,000,000 × 12 × 3653 / 36500 = 1,200,986.3 → 1,200,986
        SimulationResult longTerm = cashAdvance.calculate(baseline(SEP_15), CashAdvanceInput.of(1_000_000L, new BigDecimal("12"), 120));
        assertThat(longTerm.details()).containsEntry("days", 3_653L);
        assertThat(item(longTerm, "INTEREST_TOTAL").after()).isEqualTo(1_200_986L);
        assertThat(longTerm.cashflow().months()).hasSize(120);
        assertThat(longTerm.cashflow().truncated()).isFalse();
        // 윤년 2월: 2028-02-01 → 03-01은 29일, 분모는 365 유지 → 1,000,000 × 12 × 29 / 36500 = 9,534.2 → 9,534
        SimulationResult leap = cashAdvance.calculate(baseline(LocalDate.of(2028, 2, 1)), CashAdvanceInput.of(1_000_000L, new BigDecimal("12"), 1));
        assertThat(leap.details()).containsEntry("days", 29L);
        assertThat(item(leap, "INTEREST_TOTAL").after()).isEqualTo(9_534L);
        // 반올림 경계: 1원 × 12% × 30일 / 36500 = 0.0099 → 0
        assertThat(item(cashAdvance.calculate(baseline(SEP_15), CashAdvanceInput.of(1L, new BigDecimal("12"), 1)), "INTEREST_TOTAL").after())
                .isEqualTo(0L);
        assertThatThrownBy(() -> CashAdvanceInput.of(1L, BigDecimal.ONE, 121)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CashAdvanceInput.of(1L, new BigDecimal("-1"), 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void amountsBeyondLongRangeAreRejectedAsOutOfRange() {
        // 총 납부액 합산이 long을 넘는 조합은 계산 결과 범위 초과로 거부한다
        assertThatThrownBy(() -> installment.calculate(baseline(SEP_15), InstallmentInput.of(Long.MAX_VALUE, 2, new BigDecimal("100"))))
                .isInstanceOf(com.ssafy.dodam.global.exception.BusinessException.class)
                .extracting(e -> ((com.ssafy.dodam.global.exception.BusinessException) e).getErrorCode())
                .isEqualTo(com.ssafy.dodam.global.exception.ErrorCode.SIMULATION_AMOUNT_OUT_OF_RANGE);
    }
}
