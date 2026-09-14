package com.ssafy.dodam.domain.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.dodam.domain.loan.entity.RepaymentType;
import com.ssafy.dodam.domain.product.entity.ProductType;
import com.ssafy.dodam.domain.simulation.dto.ProductSnapshot;
import com.ssafy.dodam.domain.simulation.service.ProductOptionSelector;
import com.ssafy.dodam.domain.simulation.service.SimulationAssumptions;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

/** 585 §10.2 상품 옵션 선택 정책. 모든 금리·한도는 계산 검증용 합성 값이다. */
class ProductOptionSelectorTest {
    private static final LocalDateTime SYNCED = LocalDateTime.of(2026, 9, 1, 0, 0);

    private static ProductSnapshot product(ProductType type, String savingType, String loanType, Long maxLimit,
            ProductSnapshot.Option... options) {
        return new ProductSnapshot(1L, "상품", "은행", type, savingType, loanType, maxLimit, SYNCED, List.of(options));
    }

    private static ProductSnapshot.Option savingOption(long id, int term, String rate) {
        return new ProductSnapshot.Option(id, term, rate == null ? null : new BigDecimal(rate), "FIXED", null, null, null, null);
    }

    private static ProductSnapshot.Option loanOption(long id, int term, String minRate, String maxRate, String code, Long limit) {
        return new ProductSnapshot.Option(id, term, null, "VARIABLE", minRate == null ? null : new BigDecimal(minRate),
                maxRate == null ? null : new BigDecimal(maxRate), code, limit);
    }

    private static void assertCode(ThrowingCallable call, ErrorCode code) {
        assertThatThrownBy(call).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(code);
    }

    @Test
    void savingSelectsExactTermLowestRateAndRejectsUnsupportedTypes() {
        ProductSnapshot deposit = product(ProductType.DEPOSIT, "FIXED_DEPOSIT", null, null,
                savingOption(1, 6, "3.0"), savingOption(2, 12, "3.5"), savingOption(3, 12, "3.2"), savingOption(4, 12, null),
                savingOption(5, 24, "0"));
        ProductOptionSelector.Selection selection = ProductOptionSelector.selectSaving(deposit, 12);
        assertThat(selection.option().optionId()).isEqualTo(3L);
        assertThat(selection.appliedRate()).isEqualByComparingTo("3.2");
        assertThat(selection.assumptions()).containsExactly(SimulationAssumptions.MULTIPLE_OPTIONS_LOWEST_RATE_SELECTED);
        // 0%는 유효, null 금리만 있는 기간은 옵션 없음
        assertThat(ProductOptionSelector.selectSaving(deposit, 24).appliedRate()).isEqualByComparingTo("0");
        assertCode(() -> ProductOptionSelector.selectSaving(deposit, 36), ErrorCode.SIMULATION_PRODUCT_OPTION_UNAVAILABLE);
        assertCode(() -> ProductOptionSelector.selectSaving(product(ProductType.DEPOSIT, "FIXED_DEPOSIT", null, null, savingOption(1, 12, null)), 12),
                ErrorCode.SIMULATION_PRODUCT_OPTION_UNAVAILABLE);

        assertCode(() -> ProductOptionSelector.selectSaving(product(ProductType.DEPOSIT, "INDEX_LINKED_DEPOSIT", null, null, savingOption(1, 12, "3")), 12),
                ErrorCode.SIMULATION_PRODUCT_NOT_SUPPORTED);
        assertCode(() -> ProductOptionSelector.selectSaving(product(ProductType.DEPOSIT, null, null, null, savingOption(1, 12, "3")), 12),
                ErrorCode.SIMULATION_PRODUCT_NOT_SUPPORTED);
        assertCode(() -> ProductOptionSelector.selectSaving(product(ProductType.LOAN, null, "PERSONAL_CREDIT", null), 12),
                ErrorCode.SIMULATION_PRODUCT_NOT_SUPPORTED);
        assertThat(ProductOptionSelector.selectSaving(product(ProductType.SAVINGS, "FLEXIBLE", null, null, savingOption(1, 12, "4")), 12)
                .assumptions()).isEmpty();
    }

    @Test
    void loanFiltersByLimitBeforeResolvingRepaymentType() {
        // 검산표: 같은 기간·방식, 요청 10,000,000; A 3%·한도 5,000,000, B 4%·한도 20,000,000 → A 제외 후 B
        ProductSnapshot credit = product(ProductType.LOAN, null, "PERSONAL_CREDIT", null,
                loanOption(1, 12, "3", "10", "EQUAL_PRINCIPAL_INTEREST", 5_000_000L),
                loanOption(2, 12, "4", "10", "EQUAL_PRINCIPAL_INTEREST", 20_000_000L));
        ProductOptionSelector.Selection selection = ProductOptionSelector.selectLoan(credit, 12, RepaymentType.EQUAL_PAYMENT, 10_000_000L);
        assertThat(selection.option().optionId()).isEqualTo(2L);
        assertThat(selection.resolvedRepaymentType()).isEqualTo(RepaymentType.EQUAL_PAYMENT);
        assertThat(selection.effectiveLimit()).isEqualTo(20_000_000L);
        assertThat(selection.assumptions()).containsExactly(SimulationAssumptions.LOAN_RATE_PUBLISHED_MINIMUM,
                SimulationAssumptions.VARIABLE_RATE_HELD_CONSTANT);
        assertCode(() -> ProductOptionSelector.selectLoan(credit, 12, RepaymentType.EQUAL_PAYMENT, 30_000_000L),
                ErrorCode.SIMULATION_PRODUCT_LIMIT_EXCEEDED);
        // 명시 방식이 옵션과 맞지 않으면 옵션 없음(한도 초과가 아님)
        assertCode(() -> ProductOptionSelector.selectLoan(credit, 12, RepaymentType.BULLET, 1L), ErrorCode.SIMULATION_PRODUCT_OPTION_UNAVAILABLE);
        assertCode(() -> ProductOptionSelector.selectLoan(credit, 24, RepaymentType.EQUAL_PAYMENT, 1L), ErrorCode.SIMULATION_PRODUCT_OPTION_UNAVAILABLE);

        // 검산표: 방식 생략, EQUAL_PAYMENT 후보는 한도 미달·BULLET 후보만 금액 충족 → BULLET 자동 선택
        ProductSnapshot mixed = product(ProductType.LOAN, null, "PERSONAL_CREDIT", 50_000_000L,
                loanOption(1, 12, "3", "10", "EQUAL_PRINCIPAL_INTEREST", 5_000_000L),
                loanOption(2, 12, "5", "10", "BULLET", null));
        ProductOptionSelector.Selection auto = ProductOptionSelector.selectLoan(mixed, 12, null, 10_000_000L);
        assertThat(auto.resolvedRepaymentType()).isEqualTo(RepaymentType.BULLET);
        assertThat(auto.effectiveLimit()).isEqualTo(50_000_000L); // 옵션 한도 없음 → 상품 한도
        // 금액이 둘 다 충족하면 방식 두 종류 → 명시 요구
        assertCode(() -> ProductOptionSelector.selectLoan(mixed, 12, null, 1_000_000L), ErrorCode.SIMULATION_REPAYMENT_TYPE_REQUIRED);
    }

    @Test
    void loanHandlesAmbiguousCodesLimitsAndRateData() {
        ProductSnapshot amortizing = product(ProductType.LOAN, null, "MORTGAGE", null,
                loanOption(1, 360, "3", "6", "AMORTIZING", null));
        // AMORTIZING 단독 AUTO는 두 방식에 대응해 확정 불가
        assertCode(() -> ProductOptionSelector.selectLoan(amortizing, 360, null, 1L), ErrorCode.SIMULATION_REPAYMENT_TYPE_REQUIRED);
        ProductOptionSelector.Selection explicit = ProductOptionSelector.selectLoan(amortizing, 360, RepaymentType.EQUAL_PRINCIPAL, 1L);
        assertThat(explicit.ambiguousRepaymentCode()).isTrue();
        assertThat(explicit.assumptions()).contains(SimulationAssumptions.REPAYMENT_CODE_AMBIGUOUS_MAPPED,
                SimulationAssumptions.PRODUCT_LIMIT_UNKNOWN);

        // 모호하지 않은 코드가 금리가 높아도 우선
        ProductSnapshot both = product(ProductType.LOAN, null, "MORTGAGE", null,
                loanOption(1, 360, "3", "6", "AMORTIZING", null), loanOption(2, 360, "3.5", "6", "EQUAL_PRINCIPAL", null));
        assertThat(ProductOptionSelector.selectLoan(both, 360, RepaymentType.EQUAL_PRINCIPAL, 1L).option().optionId()).isEqualTo(2L);

        // 금리 null·역전·음수는 부적합, 상품 한도 0은 전체 자료 오류, 옵션 한도 0은 그 옵션만 오류
        ProductSnapshot badRates = product(ProductType.LOAN, null, "PERSONAL_CREDIT", null,
                loanOption(1, 12, null, "10", "BULLET", null), loanOption(2, 12, "8", "5", "BULLET", null),
                loanOption(3, 12, "-1", null, "BULLET", null));
        assertCode(() -> ProductOptionSelector.selectLoan(badRates, 12, RepaymentType.BULLET, 1L), ErrorCode.SIMULATION_PRODUCT_OPTION_UNAVAILABLE);
        assertCode(() -> ProductOptionSelector.selectLoan(product(ProductType.LOAN, null, "PERSONAL_CREDIT", 0L,
                loanOption(1, 12, "3", null, "BULLET", null)), 12, RepaymentType.BULLET, 1L), ErrorCode.SIMULATION_PRODUCT_OPTION_UNAVAILABLE);
        ProductSnapshot zeroLimit = product(ProductType.LOAN, null, "PERSONAL_CREDIT", null,
                loanOption(1, 12, "3", null, "BULLET", 0L), loanOption(2, 12, "4", null, "BULLET", null));
        assertThat(ProductOptionSelector.selectLoan(zeroLimit, 12, RepaymentType.BULLET, 1L).option().optionId()).isEqualTo(2L);
        // 0% 금리는 유효
        assertThat(ProductOptionSelector.selectLoan(product(ProductType.LOAN, null, "PERSONAL_CREDIT", null,
                loanOption(1, 12, "0", "0", "BULLET", null)), 12, RepaymentType.BULLET, 1L).appliedRate()).isEqualByComparingTo("0");

        // 한도대출·자격 한정·비대출 상품은 지원하지 않음
        assertCode(() -> ProductOptionSelector.selectLoan(product(ProductType.LOAN, null, "CREDIT_LINE", null,
                loanOption(1, 12, "3", null, "BULLET", null)), 12, RepaymentType.BULLET, 1L), ErrorCode.SIMULATION_PRODUCT_NOT_SUPPORTED);
        assertCode(() -> ProductOptionSelector.selectLoan(product(ProductType.DEPOSIT, "FIXED_DEPOSIT", null, null), 12, null, 1L),
                ErrorCode.SIMULATION_PRODUCT_NOT_SUPPORTED);
        assertThat(ProductOptionSelector.loanGroupOf(product(ProductType.LOAN, null, "MID_CREDIT", null))).isEqualTo("CREDIT_MID");
    }
}
