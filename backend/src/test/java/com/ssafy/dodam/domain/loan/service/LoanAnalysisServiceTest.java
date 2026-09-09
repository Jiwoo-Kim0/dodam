package com.ssafy.dodam.domain.loan.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ssafy.dodam.domain.loan.entity.Loan;
import com.ssafy.dodam.domain.loan.entity.LoanStatus;
import com.ssafy.dodam.domain.loan.entity.RepaymentType;
import com.ssafy.dodam.domain.loan.repository.LoanRepository;
import com.ssafy.dodam.domain.user.entity.FinancialProfile;
import com.ssafy.dodam.domain.user.repository.FinancialProfileRepository;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.policy.FutureYearMonthPeriodPolicy;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.jpa.domain.Specification;

class LoanAnalysisServiceTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 9);
    private final LoanRepository repository = mock(LoanRepository.class);
    private final FinancialProfileRepository profiles = mock(FinancialProfileRepository.class);
    private final SensitiveNumberHasher hasher = mock(SensitiveNumberHasher.class);
    private final LoanAnalysisClock clock = mock(LoanAnalysisClock.class);
    private final LoanForecastCalculator calculator = new LoanForecastCalculator(new LoanRepaymentScheduleCalculator());
    private final LoanAnalysisService service = new LoanAnalysisService(repository, profiles, hasher, calculator,
            new LoanAnalysisPeriodPolicy(new FutureYearMonthPeriodPolicy()), clock);

    @BeforeEach
    void setUp() {
        when(clock.today()).thenReturn(TODAY);
        when(profiles.findById(1L)).thenReturn(Optional.empty());
    }

    @Test
    void 세_API가_같은_기간의_이자를_계산하고_비용순으로_정렬한다() {
        Loan principal = loan(1, 1200000, "12", RepaymentType.EQUAL_PRINCIPAL, 3);
        Loan bullet = loan(2, 1200000, "12", RepaymentType.BULLET, 3);
        owned(List.of(principal, bullet));
        var summary = service.interestSummary(1L, 3);
        var comparison = service.costComparison(1L, 3);
        var monthly = service.monthlyRepayments(1L, "2026-09", "2026-11");
        assertThat(summary.totalInterest()).isEqualTo(60000);
        assertThat(summary.months()).extracting(item -> item.totalInterest()).containsExactly(24000L, 20000L, 16000L);
        assertThat(comparison.totalInterest()).isEqualTo(summary.totalInterest());
        assertThat(comparison.loans()).extracting(item -> item.loanId()).containsExactly(2L, 1L);
        assertThat(comparison.loans().getFirst().interestShare()).isEqualByComparingTo("60.00");
        assertThat(monthly.months().getFirst().remaining().remainingRepaymentAmount()).isEqualTo(424000);
        assertThat(monthly.months().getFirst().monthlyTotal().monthlyRepaymentAmount()).isNull();
        assertThat(monthly.months().get(1).remaining().totalInterest()).isEqualTo(20000);
    }

    @Test
    void 미래_시작월에도_앞선_상환으로_줄어든_잔액을_적용한다() {
        owned(List.of(loan(1, 1200000, "12", RepaymentType.EQUAL_PRINCIPAL, 3)));
        var result = service.monthlyRepayments(1L, "2026-10", "2026-10");
        assertThat(result.complete()).isTrue();
        assertThat(result.months().getFirst().remaining().totalInterest()).isEqualTo(8000);
        assertThat(result.months().getFirst().monthlyTotal().monthlyRepaymentAmount()).isEqualTo(408000);
    }

    @Test
    void 미래_월전체_금액으로_부담률을_계산한다() {
        Loan loan = loan(1, 1440000, "0", RepaymentType.EQUAL_PRINCIPAL, 2);
        owned(List.of(loan));
        income(2800000L);
        var result = service.repaymentBurden(1L, "2026-10");
        assertThat(result.complete()).isTrue();
        assertThat(result.monthlyTotal().monthlyRepaymentAmount()).isEqualTo(720000);
        assertThat(result.burdenRate()).isEqualByComparingTo("25.71");
    }

    @Test
    void 현재월_상환을_마쳤어도_월부담률을_0으로_만들지_않는다() {
        Loan loan = loan(1, 1200000, "12", RepaymentType.BULLET, 3);
        when(loan.getNextRepaymentDate()).thenReturn(TODAY.plusMonths(1));
        owned(List.of(loan));
        income(2800000L);
        var result = service.repaymentBurden(1L, null);
        assertThat(result.complete()).isFalse();
        assertThat(result.remaining().remainingRepaymentAmount()).isZero();
        assertThat(result.remaining().complete()).isTrue();
        assertThat(result.burdenRate()).isNull();
        assertThat(result.unavailableReasons()).containsExactly("MONTH_TOTAL_UNAVAILABLE");
    }

    @Test
    void 종료대출도_현재월전체_판정에_포함하지만_미래예측은_0이다() {
        Loan loan = loan(1, 0, "12", RepaymentType.BULLET, 3);
        when(loan.getStatus()).thenReturn(LoanStatus.COMPLETED);
        owned(List.of(loan));
        income(2800000L);
        assertThat(service.repaymentBurden(1L, null).burdenRate()).isNull();
        assertThat(service.repaymentBurden(1L, "2026-10").burdenRate()).isZero();
        assertThat(service.interestSummary(1L, 12).totalInterest()).isZero();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0})
    void 소득이_없거나_0이면_금액만_제공한다(Long income) {
        owned(List.of());
        income(income);
        var result = service.repaymentBurden(1L, "2026-10");
        assertThat(result.monthlyTotal().monthlyRepaymentAmount()).isZero();
        assertThat(result.burdenRate()).isNull();
        assertThat(result.unavailableReasons()).containsExactly(income == null ? "INCOME_MISSING" : "INCOME_ZERO");
    }

    @Test
    void 대출이_없으면_월을_0으로_채우고_양수소득의_부담률은_0이다() {
        owned(List.of());
        income(1L);
        assertThat(service.monthlyRepayments(1L, null, null).months()).hasSize(6);
        assertThat(service.repaymentBurden(1L, null).burdenRate()).isZero();
        assertThat(service.costComparison(1L, 12).loans()).isEmpty();
    }

    @Test
    void 불완전한_대출은_전체합계와_비중을_무효화하지만_알려진_금액은_남긴다() {
        Loan invalid = loan(2, 1200000, "12", RepaymentType.BULLET, 3);
        when(invalid.getMaturityDate()).thenReturn(null);
        owned(List.of(loan(1, 1200000, "12", RepaymentType.BULLET, 3), invalid));
        var result = service.costComparison(1L, 3);
        assertThat(result.knownInterest()).isEqualTo(36000);
        assertThat(result.totalInterest()).isNull();
        assertThat(result.loans().getFirst().interestShare()).isNull();
        assertThat(result.unavailableLoans().getFirst().reasons()).containsExactly("MISSING_MATURITY_DATE");
    }

    @Test
    void 회차_범위초과는_해당대출만_격리한다() {
        Loan invalid = loan(2, Long.MAX_VALUE, "1200", RepaymentType.BULLET, 1);
        owned(List.of(loan(1, 1200000, "12", RepaymentType.BULLET, 3), invalid));
        var result = service.interestSummary(1L, 3);
        assertThat(result.knownInterest()).isEqualTo(36000);
        assertThat(result.totalInterest()).isNull();
        assertThat(result.unavailableLoans().getFirst().reasons()).containsExactly("CALCULATION_AMOUNT_OUT_OF_RANGE");
    }

    @Test
    void 정상대출들의_월합산_초과는_개별항목을_버리지_않는다() {
        Loan first = loan(1, 6000000000000000000L, "1200", RepaymentType.BULLET, 24);
        Loan second = loan(2, 6000000000000000000L, "1200", RepaymentType.BULLET, 24);
        owned(List.of(first, second));
        var result = service.costComparison(1L, 1);
        assertThat(result.knownInterest()).isNull();
        assertThat(result.totalInterest()).isNull();
        assertThat(result.unavailableLoans()).isEmpty();
        assertThat(result.unavailableReasons()).containsExactly(LoanAnalysisAmounts.OUT_OF_RANGE);
        assertThat(result.loans()).allSatisfy(item -> {
            assertThat(item.forecastInterest()).isEqualTo(6000000000000000000L);
            assertThat(item.status()).isEqualTo("COMPLETE");
        });
        owned(List.of(second, first));
        assertThat(service.costComparison(1L, 1)).isEqualTo(result);
    }

    @ParameterizedTest
    @ValueSource(ints = {120, 361})
    void 개별기간합계_초과는_정상_월과_날짜를_보존한다(int count) {
        Loan loan = loan(1, 9000000000000000000L, "12", RepaymentType.BULLET, count);
        when(hasher.hash("synthetic")).thenReturn("hash");
        when(repository.findByUserIdAndAccountNoHash(1L, "hash")).thenReturn(Optional.of(loan));
        var result = service.interestForecast(1L, "synthetic");
        assertThat(result.status()).isEqualTo("PARTIAL");
        assertThat(result.reasons()).contains(LoanAnalysisAmounts.OUT_OF_RANGE);
        assertThat(result.truncated()).isEqualTo(count > 360);
        assertThat(result.knownInterest()).isNull();
        assertThat(result.fullTermInterest()).isNull();
        assertThat(result.months()).hasSize(Math.min(count, 360)).allSatisfy(month -> {
            assertThat(month.complete()).isTrue();
            assertThat(month.totalInterest()).isEqualTo(90000000000000000L);
        });
        assertThat(result.coveredThrough()).isEqualTo(TODAY.plusMonths(Math.min(count, 360) - 1L));
        owned(List.of(loan));
        assertThat(service.interestSummary(1L, 12).totalInterest()).isEqualTo(1080000000000000000L);
    }

    @Test
    void 요청기간_밖의_회차오류를_현재기간으로_전파하지_않는다() {
        Loan loan = loan(1, Long.MAX_VALUE, "12", RepaymentType.BULLET, 24);
        when(loan.getNextRepaymentDate()).thenReturn(TODAY.plusMonths(20));
        owned(List.of(loan));
        var result = service.interestSummary(1L, 12);
        assertThat(result.complete()).isTrue();
        assertThat(result.totalInterest()).isZero();
    }

    @Test
    void 저장된_날짜모순은_보정하지_않고_추론날짜는_만기로_보정한다() {
        Loan loan = loan(1, 1200000, "12", RepaymentType.BULLET, 1);
        when(loan.getMaturityDate()).thenReturn(TODAY.plusDays(11));
        when(loan.getNextRepaymentDate()).thenReturn(TODAY.plusDays(16));
        assertThat(calculator.calculate(loan, TODAY, 12).reasons()).contains("INVALID_TERMS");
        when(loan.getNextRepaymentDate()).thenReturn(null);
        when(loan.getRepaymentDay()).thenReturn(25);
        var result = calculator.calculate(loan, TODAY, 12);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().repaymentDate()).isEqualTo(TODAY.plusDays(11));
        when(loan.getMaturityDate()).thenReturn(TODAY);
        assertThat(calculator.calculate(loan, TODAY, 12).items().getFirst().repaymentDate()).isEqualTo(TODAY);
    }

    @Test
    void 디비오류는_계산불가로_삼키지_않는다() {
        when(repository.findAll(org.mockito.ArgumentMatchers.<Specification<Loan>>any()))
                .thenThrow(new IllegalStateException("test failure"));
        assertThatThrownBy(() -> service.interestSummary(1L, 12)).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "2026-9", "2026-13", "0000-01", "2026-08", "2027-09", "9999-12"})
    void 단일월_형식과_범위를_검증한다(String month) {
        assertThatThrownBy(() -> service.repaymentBurden(1L, month)).isInstanceOf(InvalidRequestException.class);
    }

    private void owned(List<Loan> values) {
        when(repository.findAll(org.mockito.ArgumentMatchers.<Specification<Loan>>any())).thenReturn(values);
    }

    private void income(Long value) {
        FinancialProfile profile = mock(FinancialProfile.class);
        when(profile.getMonthlyIncome()).thenReturn(value);
        when(profiles.findById(1L)).thenReturn(Optional.of(profile));
    }

    private Loan loan(long id, long balance, String rate, RepaymentType type, int months) {
        Loan loan = mock(Loan.class);
        when(loan.getId()).thenReturn(id);
        when(loan.getStatus()).thenReturn(LoanStatus.ACTIVE);
        when(loan.getBalance()).thenReturn(balance);
        when(loan.getInterestRate()).thenReturn(new BigDecimal(rate));
        when(loan.getRepaymentType()).thenReturn(type);
        when(loan.getRepaymentDay()).thenReturn(null);
        when(loan.getNextRepaymentDate()).thenReturn(TODAY);
        when(loan.getMaturityDate()).thenReturn(TODAY.plusMonths(months - 1L));
        return loan;
    }
}
