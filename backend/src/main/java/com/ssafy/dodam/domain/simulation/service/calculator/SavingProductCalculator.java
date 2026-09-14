package com.ssafy.dodam.domain.simulation.service.calculator;

import com.ssafy.dodam.domain.product.entity.ProductType;
import com.ssafy.dodam.domain.simulation.dto.ProductSnapshot;
import com.ssafy.dodam.domain.simulation.dto.SavingProductInput;
import com.ssafy.dodam.domain.simulation.dto.SimulationBaseline;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Comparison;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Item;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult.Snapshot;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import com.ssafy.dodam.domain.simulation.service.ProductOptionSelector;
import com.ssafy.dodam.domain.simulation.service.SimulationAssumptions;
import com.ssafy.dodam.domain.simulation.service.SimulationCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 예적금 신규가입(POST /simulations/saving-product). 저장된 상품 기본금리로 만기 세전 이자를 계산한다.
 *
 * <p>계산식(536·537과 같은 단리·ACT/365, 전체 이자 합산 후 원 단위 절사):
 * <ul>
 *   <li>예금: 기준일에 {@code monthlyAmount}를 일시 예치, 만기 = 기준일 + term개월.
 *       {@code grossInterest = floor(principal × rate × days / 36500)}, days = [가입일, 만기일) 실제 일수.</li>
 *   <li>적금: i = 0..term−1에 기준일 + i개월마다 {@code monthlyAmount} 납입(만기일 납입 없음), 만기 = 기준일 + term개월.
 *       {@code grossInterest = floor(Σ(납입액 × 예치일수) × rate / 36500)}. 회차별로 따로 절사하지 않는다.</li>
 * </ul>
 * 세금·우대금리는 반영하지 않는다({@code EXCLUDES_TAX_AND_FEES}, {@code BASE_RATE_WITHOUT_PREFERENTIAL}). 예금 상세(536)의
 * 세후 수령액과 직접 비교하면 안 된다. 예치 원금 이동은 순자산 변화 0이며 수익은 이자뿐이다.
 *
 * <p>목표 영향: 가입 자금이 어느 계좌·목표에서 빠지는지 입력에 없어 목표별 달성일을 계산하지 않는다({@code FUNDING_SOURCE_UNKNOWN}).
 */
@Component
public class SavingProductCalculator implements SimulationCalculator<SavingProductInput> {
    static final String TAX_BASIS_PRE_TAX = "PRE_TAX";
    private static final BigDecimal DAYS_PERCENT_YEAR = BigDecimal.valueOf(36500);

    @Override
    public SimulationType type() {
        return SimulationType.SAVING_PRODUCT;
    }

    @Override
    public Class<SavingProductInput> inputType() {
        return SavingProductInput.class;
    }

    @Override
    public SimulationResult calculate(SimulationBaseline baseline, SavingProductInput input) {
        ProductSnapshot product = Objects.requireNonNull(input.product(), "product 스냅샷이 준비되지 않았습니다.");
        return SimulationScenarios.exact(() -> build(baseline, input, product));
    }

    private SimulationResult build(SimulationBaseline baseline, SavingProductInput input, ProductSnapshot product) {
        ProductOptionSelector.Selection selection = ProductOptionSelector.selectSaving(product, input.term());
        LocalDate base = baseline.baseDate();
        LocalDate maturity = base.plusMonths(input.term());
        boolean deposit = product.productType() == ProductType.DEPOSIT;
        BigDecimal rate = selection.appliedRate();

        List<Map<String, Object>> installments = new ArrayList<>();
        List<ScenarioCashflow.Event> events = new ArrayList<>();
        BigDecimal weightedPrincipalDays = BigDecimal.ZERO;
        long principal = 0;
        int count = deposit ? 1 : input.term();
        for (int i = 0; i < count; i++) {
            LocalDate paid = base.plusMonths(i);
            long days = ChronoUnit.DAYS.between(paid, maturity);
            weightedPrincipalDays = weightedPrincipalDays.add(BigDecimal.valueOf(input.monthlyAmount()).multiply(BigDecimal.valueOf(days)));
            principal = Math.addExact(principal, input.monthlyAmount());
            events.add(new ScenarioCashflow.Event(paid, Math.negateExact(input.monthlyAmount())));
            Map<String, Object> installment = new LinkedHashMap<>();
            installment.put("date", paid);
            installment.put("amount", input.monthlyAmount());
            installment.put("days", days);
            installments.add(installment);
        }
        long grossInterest = weightedPrincipalDays.multiply(rate).divide(DAYS_PERCENT_YEAR, 0, RoundingMode.FLOOR).longValueExact();
        long grossPayout = Math.addExact(principal, grossInterest);
        events.add(new ScenarioCashflow.Event(maturity, grossPayout));

        Snapshot before = SimulationScenarios.baselineSnapshot(baseline);
        SimulationScenarios.NotComputableGoals goals = SimulationScenarios.notComputableGoals(baseline,
                SimulationAssumptions.FUNDING_SOURCE_UNKNOWN);

        List<Item> items = new ArrayList<>();
        items.add(Item.amount("PRODUCT_PRINCIPAL", deposit ? "예치 원금" : "총 납입 원금", 0L, principal));
        items.add(Item.amount("GROSS_INTEREST", "만기 세전 이자", 0L, grossInterest));
        items.add(Item.amount("GROSS_PAYOUT", "만기 세전 수령액", 0L, grossPayout));
        items.add(Item.amount("MONTHLY_INSTALLMENT", deposit ? "가입 원금(일시)" : "월 납입액", 0L, input.monthlyAmount()));
        List<Item> assets = List.of(
                Item.amount("INITIAL_CASH_CHANGE", "기준일 현금 변화", 0L, Math.negateExact(input.monthlyAmount())),
                Item.amount("INITIAL_PRODUCT_PRINCIPAL_CHANGE", "기준일 예치 원금 변화", 0L, input.monthlyAmount()),
                Item.amount("INITIAL_NET_ASSET_CHANGE", "기준일 순자산 변화", 0L, 0L),
                Item.amount("MATURITY_INTEREST_GAIN", "만기까지 이자 수익(세전)", 0L, grossInterest));

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("dataSource", LoanScenarios.DATA_SOURCE_PRODUCT_DB);
        details.put("taxBasis", TAX_BASIS_PRE_TAX);
        details.put("calculationAsOfDate", base);
        details.put("product", LoanScenarios.productDetails(product, selection));
        details.put("appliedRate", rate);
        details.put("term", input.term());
        details.put("maturityDate", maturity);
        details.put("installments", installments);
        details.put("grossPrincipal", principal);
        details.put("grossInterest", grossInterest);
        details.put("grossPayout", grossPayout);
        details.put("comparisonTiming", List.of(
                LoanScenarios.timing("INITIAL_CASH_CHANGE", base, LoanScenarios.BASIS_INITIAL_STATE),
                LoanScenarios.timing("INITIAL_PRODUCT_PRINCIPAL_CHANGE", base, LoanScenarios.BASIS_INITIAL_STATE),
                LoanScenarios.timing("INITIAL_NET_ASSET_CHANGE", base, LoanScenarios.BASIS_INITIAL_STATE),
                LoanScenarios.periodTiming("MATURITY_INTEREST_GAIN", base, maturity)));

        List<String> assumptions = new ArrayList<>(List.of(SimulationAssumptions.PRODUCT_DB_SNAPSHOT,
                SimulationAssumptions.SIMPLE_INTEREST_ACT_365, SimulationAssumptions.BASE_RATE_WITHOUT_PREFERENTIAL,
                SimulationAssumptions.EXCLUDES_TAX_AND_FEES));
        if (!deposit) {
            assumptions.add(SimulationAssumptions.FIXED_MONTHLY_INSTALLMENT);
        }
        assumptions.addAll(selection.assumptions());
        SimulationResult.Cashflow cashflow = ScenarioCashflow.build(base, events, maturity);

        String kind = deposit ? "정기예금" : "적금";
        String title = kind + " 가입 " + (deposit ? "" : "월 ") + SimulationScenarios.won(input.monthlyAmount()) + " · " + input.term() + "개월";
        String summary = "세전 이자 " + SimulationScenarios.won(grossInterest) + ", 만기(" + maturity + ") 수령 "
                + SimulationScenarios.won(grossPayout) + " · 연 " + rate.stripTrailingZeros().toPlainString() + "% 단리, 세금·우대금리 미반영";
        return new SimulationResult(title, summary, before, goals.after(), new Comparison(items, assets, List.of(), List.of()),
                cashflow, goals.impacts(), assumptions, details, goals.details());
    }
}
