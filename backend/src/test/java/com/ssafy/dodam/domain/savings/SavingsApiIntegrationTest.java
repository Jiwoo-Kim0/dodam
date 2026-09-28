package com.ssafy.dodam.domain.savings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.dodam.domain.savings.service.SavingsClock;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 적금 9개 API 통합 테스트.
 *
 * <p>기준일은 {@link SavingsClock}을 대체해 2026-09-09로 고정한다. 납입 일정과 경과 개월 경계를 실제
 * 실행일과 무관하게 단정하기 위해서다.
 *
 * <p>fixture 계좌번호는 {@code 10000000<계좌ID>}다. 저장된 해시는 실제 {@link SensitiveNumberHasher}로
 * 다시 채워, 서비스가 평문이 아니라 해시로만 소유자를 찾는지 함께 확인한다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:savings537;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@Sql("/sql/savings-test-data.sql")
class SavingsApiIntegrationTest {
    private static final String BASE = "/api/v1/savings";
    private static final String RUNNING = "1000000001";
    private static final String SECOND = "1000000002";
    private static final String NO_DETAIL = "1000000003";
    private static final String CLOSED = "1000000004";
    private static final String DEMAND_DEPOSIT = "1000000005";
    private static final String DEPOSIT_ACCOUNT = "1000000006";
    private static final String OTHER_USER = "1000000007";
    private static final String UNKNOWN = "1000009999";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SensitiveNumberHasher hasher;
    @MockitoBean private SavingsClock clock;

    @BeforeEach
    void fixedDateAndRealHashes() {
        when(clock.today()).thenReturn(LocalDate.of(2026, 9, 9));
        for (int accountId = 1; accountId <= 7; accountId++) {
            jdbc.update("UPDATE account SET account_no_hash = ? WHERE account_id = ?",
                    hasher.hash("100000000" + accountId), accountId);
        }
    }

    @Test
    @WithMockUser(username = "1")
    void detailReturnsContractAndMaskedNumberOnly() throws Exception {
        mvc.perform(get(BASE + "/" + RUNNING)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").value(1))
                .andExpect(jsonPath("$.data.accountNoMasked").value("110-***-**0001"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.balance").value(600000))
                .andExpect(jsonPath("$.data.detailsAvailable").value(true))
                .andExpect(jsonPath("$.data.interestRate").value(3.6))
                .andExpect(jsonPath("$.data.monthlyAmount").value(300000))
                .andExpect(jsonPath("$.data.paymentDay").value(10))
                .andExpect(jsonPath("$.data.nextPaymentDate").value("2026-09-10"))
                .andExpect(jsonPath("$.data.asOfDate").value("2026-09-09"));

        mvc.perform(get(BASE + "/" + NO_DETAIL)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.detailsAvailable").value(false))
                .andExpect(jsonPath("$.data.interestRate", nullValue()))
                .andExpect(jsonPath("$.data.monthlyAmount", nullValue()));

        String body = mvc.perform(get(BASE + "/" + RUNNING)).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(RUNNING, "accountNoHash", "accountNoEncrypted", "\"accountNo\"");
    }

    @Test
    @WithMockUser(username = "1")
    void paymentsHonourTheSharedQueryPeriodAndPaging() throws Exception {
        // 기본 조회기간은 기준일 기준 최근 3개월이라 기준일 이후의 예정 회차는 들어오지 않는다.
        mvc.perform(get(BASE + "/" + RUNNING + "/payments")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].paymentDate").value("2026-08-10"))
                .andExpect(jsonPath("$.data.content[0].installmentNumber").value(2))
                .andExpect(jsonPath("$.data.content[1].paymentDate").value("2026-07-10"));
        mvc.perform(get(BASE + "/" + RUNNING + "/payments").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mvc.perform(get(BASE + "/" + RUNNING + "/payments").param("page", "99"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(0)));
        mvc.perform(get(BASE + "/" + RUNNING + "/payments")
                        .param("startDate", "2026-08-01").param("endDate", "2026-08-31"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = "1")
    void paymentSchedulesNeverDoubleCountAStoredInstallmentAndItsMonthlyAmount() throws Exception {
        // 기본 기간은 기준일부터 3개월이다. 9월은 저장된 예정 회차가 있어 약정액을 보충하지 않는다.
        mvc.perform(get(BASE + "/payment-schedules")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.periodStart").value("2026-09-09"))
                .andExpect(jsonPath("$.data.periodEnd").value("2026-12-08"))
                .andExpect(jsonPath("$.data.schedules", hasSize(6)))
                .andExpect(jsonPath("$.data.totalAmount").value(2400000))
                .andExpect(jsonPath("$.data.schedules[0].paymentDate").value("2026-09-10"))
                .andExpect(jsonPath("$.data.schedules[0].source").value("STORED_SCHEDULED"))
                .andExpect(jsonPath("$.data.schedules[0].paymentId").value(3))
                .andExpect(jsonPath("$.data.schedules[1].paymentDate").value("2026-09-15"))
                .andExpect(jsonPath("$.data.schedules[1].source").value("ESTIMATED_MONTHLY"))
                .andExpect(jsonPath("$.data.schedules[1].paymentId", nullValue()))
                // 상세행이 없는 3번 계좌 때문에 기간 전체가 완전하지 않다고 표시된다.
                .andExpect(jsonPath("$.data.complete").value(false))
                .andExpect(jsonPath("$.data.reasons", contains("INCOMPLETE_SAVINGS_TERMS")));
    }

    @Test
    @WithMockUser(username = "2")
    void monthlyAmountAddsPaidStoredAndEstimatedWithoutOverlap() throws Exception {
        mvc.perform(get(BASE + "/monthly-amount")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearMonth").value("2026-09"))
                .andExpect(jsonPath("$.data.asOfDate").value("2026-09-09"))
                .andExpect(jsonPath("$.data.paidAmount").value(0))
                .andExpect(jsonPath("$.data.estimatedAmount").value(300000))
                .andExpect(jsonPath("$.data.expectedMonthlyAmount").value(300000))
                .andExpect(jsonPath("$.data.complete").value(true))
                .andExpect(jsonPath("$.data.calculationBasis")
                        .value("PAID_PLUS_REMAINING_MONTHLY_SCHEDULE"));

        // 지난 달은 저장된 실적만 본다. 예정·추정을 과거에 덧붙이지 않는다.
        mvc.perform(get(BASE + "/monthly-amount").param("yearMonth", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paidAmount").value(300000))
                .andExpect(jsonPath("$.data.scheduledAmount").value(0))
                .andExpect(jsonPath("$.data.estimatedAmount").value(0))
                .andExpect(jsonPath("$.data.expectedMonthlyAmount").value(300000))
                .andExpect(jsonPath("$.data.calculationBasis").value("STORED_PAID_ONLY"));
    }

    @Test
    @WithMockUser(username = "1")
    void monthlyAmountWithholdsTheTotalWhenAnAccountHasNoTerms() throws Exception {
        // 한 계좌의 약정을 모르면 그 달의 저축 총액도 알 수 없다. 0으로 대체하지 않는다.
        mvc.perform(get(BASE + "/monthly-amount")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scheduledAmount").value(300000))
                .andExpect(jsonPath("$.data.estimatedAmount").value(500000))
                .andExpect(jsonPath("$.data.expectedMonthlyAmount", nullValue()))
                .andExpect(jsonPath("$.data.complete").value(false))
                .andExpect(jsonPath("$.data.reasons", contains("INCOMPLETE_SAVINGS_TERMS")));
    }

    @Test
    @WithMockUser(username = "1")
    void earlyTerminationAppliesTieredRateOnEachInstallmentHoldingPeriod() throws Exception {
        // 경과 1개월 → 계약이율 3.6%의 20% = 0.72%
        // 300,000 × 61일 + 300,000 × 30일 = 27,300,000 → × 0.72 ÷ 36500 = 538원, 세액 538 × 15.4% 절사 = 82원
        mvc.perform(get(BASE + "/" + RUNNING + "/early-termination")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETE"))
                .andExpect(jsonPath("$.data.calculationDate").value("2026-09-09"))
                .andExpect(jsonPath("$.data.elapsedMonths").value(1))
                .andExpect(jsonPath("$.data.annualRate").value(0.72))
                .andExpect(jsonPath("$.data.ratePolicy").value("TIERED_CONTRACT_RATE_RATIO"))
                .andExpect(jsonPath("$.data.rateTier").value("ELAPSED_MONTHS_GTE_1"))
                .andExpect(jsonPath("$.data.paidPrincipal").value(600000))
                .andExpect(jsonPath("$.data.futurePrincipal").value(0))
                .andExpect(jsonPath("$.data.expectedPrincipal").value(600000))
                .andExpect(jsonPath("$.data.expectedInterest").value(538))
                .andExpect(jsonPath("$.data.tax").value(82))
                .andExpect(jsonPath("$.data.expectedNetInterest").value(456))
                .andExpect(jsonPath("$.data.expectedAmount").value(600456))
                .andExpect(jsonPath("$.data.assumptions", hasItems("GENERAL_INTEREST_INCOME_TAX_15_4", "EXCLUDES_FEES")));
    }

    @Test
    @WithMockUser(username = "1")
    void maturityForecastAddsRemainingMonthlyPaymentsUntilMaturity() throws Exception {
        mvc.perform(get(BASE + "/" + RUNNING + "/maturity-forecast")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETE"))
                .andExpect(jsonPath("$.data.calculationDate").value("2027-07-10"))
                .andExpect(jsonPath("$.data.annualRate").value(3.6))
                .andExpect(jsonPath("$.data.ratePolicy", nullValue()))
                .andExpect(jsonPath("$.data.paidPrincipal").value(600000))
                // 9월 저장 회차 1건 + 2026-10 ~ 2027-06 추정 9건 = 3,000,000
                .andExpect(jsonPath("$.data.futurePrincipal").value(3000000))
                .andExpect(jsonPath("$.data.expectedPrincipal").value(3600000));
    }

    @Test
    @WithMockUser(username = "1")
    void forecastNamesTheReasonItCannotCompute() throws Exception {
        mvc.perform(get(BASE + "/" + CLOSED + "/maturity-forecast")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.expectedAmount", nullValue()));
        mvc.perform(get(BASE + "/" + NO_DETAIL + "/maturity-forecast")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.reasons", contains("INCOMPLETE_SAVINGS_TERMS")));
    }

    @Test
    @WithMockUser(username = "1")
    void rateComparisonGroupsByAccumulationTypeAndExcludesRestrictedProducts() throws Exception {
        // FLEXIBLE 비교군: 보유 상품(1)과 자격 한정으로 분류된 GROUP_FLEXIBLE(4)을 뺀 3.2 / 3.4의 중앙값
        mvc.perform(get(BASE + "/" + RUNNING + "/rate-comparison")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETE"))
                .andExpect(jsonPath("$.data.comparisonGroup").value("FLEXIBLE"))
                .andExpect(jsonPath("$.data.termBand").value("7-12"))
                .andExpect(jsonPath("$.data.industry").value("BANK"))
                .andExpect(jsonPath("$.data.referenceRate").value(3.3))
                .andExpect(jsonPath("$.data.confidence").value("LOW"))
                .andExpect(jsonPath("$.data.rateDifference").value(0.3))
                .andExpect(jsonPath("$.data.comparisonDirection").value("HIGHER_IS_BETTER"))
                .andExpect(jsonPath("$.data.comparableProductCount").value(2))
                .andExpect(jsonPath("$.data.candidates", hasSize(2)))
                .andExpect(jsonPath("$.data.candidates[0].representativeRate").value(3.2))
                .andExpect(jsonPath("$.data.candidates[1].savingType").value("FLEXIBLE_PROMOTION"))
                .andExpect(jsonPath("$.data.candidates[1].disclosedPreferredRate").value(8.0));

        mvc.perform(get(BASE + "/" + RUNNING + "/rate-comparison").param("topN", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates", hasSize(1)))
                // topN은 표시만 자른다. 기준금리와 표본 수는 전체 후보로 계산한다.
                .andExpect(jsonPath("$.data.comparableProductCount").value(2))
                .andExpect(jsonPath("$.data.referenceRate").value(3.3));
    }

    @Test
    @WithMockUser(username = "1")
    void rateComparisonNamesTheReasonItCannotCompare() throws Exception {
        mvc.perform(get(BASE + "/" + CLOSED + "/rate-comparison")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.unavailableReasons", contains("NOT_COMPARABLE_STATUS")));
        mvc.perform(get(BASE + "/" + NO_DETAIL + "/rate-comparison")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unavailableReasons", contains("MISSING_SAVINGS_DETAIL")));

        // 적금이 아닌 상품이 연결되면 비교군을 만들지 않는다.
        jdbc.update("UPDATE deposit_saving SET financial_product_id = 8 WHERE account_id = 1");
        mvc.perform(get(BASE + "/" + RUNNING + "/rate-comparison")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unavailableReasons", contains("COMPARISON_GROUP_UNKNOWN")));

        jdbc.update("UPDATE deposit_saving SET financial_product_id = 1 WHERE account_id = 1");
        jdbc.update("UPDATE account SET bank_code = 'NO_SECTOR_BANK' WHERE account_id = 1");
        mvc.perform(get(BASE + "/" + RUNNING + "/rate-comparison")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unavailableReasons", contains("INDUSTRY_UNKNOWN")));
    }

    @Test
    @WithMockUser(username = "2")
    void cashflowBurdenCombinesIncomeFixedExpenseSavingsAndLoanRepayment() throws Exception {
        // 소득 9,000,000 − (저축 300,000 + 고정지출 1,000,000) = 7,700,000
        mvc.perform(get(BASE + "/cashflow-burden")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearMonth").value("2026-09"))
                .andExpect(jsonPath("$.data.monthlyIncome").value(9000000))
                .andExpect(jsonPath("$.data.fixedExpense").value(1000000))
                .andExpect(jsonPath("$.data.savingsAmount").value(300000))
                .andExpect(jsonPath("$.data.loanRepaymentAmount").value(0))
                .andExpect(jsonPath("$.data.disposableAmount").value(7700000))
                .andExpect(jsonPath("$.data.savingsBurdenRate").value(3.33))
                .andExpect(jsonPath("$.data.totalBurdenRate").value(14.44))
                .andExpect(jsonPath("$.data.negativeDisposable").value(false))
                .andExpect(jsonPath("$.data.complete").value(true));

        // 가용금액 API는 같은 달이면 같은 값을 낸다.
        mvc.perform(get(BASE + "/disposable-balance").param("baseDate", "2026-09-20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearMonth").value("2026-09"))
                .andExpect(jsonPath("$.data.baseDate").value("2026-09-20"))
                .andExpect(jsonPath("$.data.disposableAmount").value(7700000));
    }

    @Test
    @WithMockUser(username = "1")
    void cashflowSeparatesPaidRepaymentFromRemainingAndIgnoresFailedOnes() throws Exception {
        // 상세행 없는 계좌를 지우면 저축 총액을 계산할 수 있다.
        jdbc.update("DELETE FROM account WHERE account_id = 3");
        mvc.perform(get(BASE + "/cashflow-burden")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.savingsAmount").value(800000))
                .andExpect(jsonPath("$.data.loanRepaymentAmount").value(300000))
                .andExpect(jsonPath("$.data.remainingLoanRepaymentAmount").value(100000))
                // 소득 3,000,000 − (저축 800,000 + 고정지출 800,000 + 상환 400,000) = 1,000,000
                .andExpect(jsonPath("$.data.disposableAmount").value(1000000))
                .andExpect(jsonPath("$.data.complete").value(true));
    }

    @Test
    @WithMockUser(username = "1")
    void cashflowDistinguishesMissingIncomeFromZeroIncome() throws Exception {
        jdbc.update("DELETE FROM account WHERE account_id = 3");
        jdbc.update("UPDATE financial_profile SET monthly_income = 0 WHERE user_id = 1");
        mvc.perform(get(BASE + "/cashflow-burden")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.savingsBurdenRate", nullValue()))
                .andExpect(jsonPath("$.data.reasons", contains("ZERO_INCOME")));
        jdbc.update("UPDATE financial_profile SET monthly_income = NULL WHERE user_id = 1");
        mvc.perform(get(BASE + "/cashflow-burden")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.disposableAmount", nullValue()))
                .andExpect(jsonPath("$.data.reasons", contains("INCOME_NOT_REGISTERED")));
        jdbc.update("UPDATE financial_profile SET monthly_income = 3000000, fixed_expense = NULL"
                + " WHERE user_id = 1");
        mvc.perform(get(BASE + "/cashflow-burden")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.disposableAmount", nullValue()))
                // 고정지출을 모르면 총 부담은 알 수 없지만 저축 부담률은 낼 수 있다.
                .andExpect(jsonPath("$.data.savingsBurdenRate").value(26.67))
                .andExpect(jsonPath("$.data.reasons", contains("FIXED_EXPENSE_NOT_REGISTERED")));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "/1000000001", "/1000000001/payments", "/1000000001/rate-comparison",
            "/1000000001/early-termination", "/1000000001/maturity-forecast",
            "/payment-schedules", "/monthly-amount", "/cashflow-burden", "/disposable-balance"
    })
    void allNineEndpointsRequireAuthentication(String path) throws Exception {
        mvc.perform(get(BASE + path)).andExpect(status().isUnauthorized());
    }

    /**
     * 타인 계좌, 없는 계좌, 적금이 아닌 계좌는 모두 같은 404여야 한다. 셋을 구분하면 응답만으로 타인
     * 계좌번호의 존재를 확인할 수 있게 된다.
     */
    @ParameterizedTest
    @CsvSource({
            OTHER_USER + ",", OTHER_USER + ",/payments", OTHER_USER + ",/rate-comparison",
            OTHER_USER + ",/early-termination", OTHER_USER + ",/maturity-forecast",
            DEMAND_DEPOSIT + ",", DEMAND_DEPOSIT + ",/payments", DEMAND_DEPOSIT + ",/rate-comparison",
            DEPOSIT_ACCOUNT + ",", DEPOSIT_ACCOUNT + ",/payments", DEPOSIT_ACCOUNT + ",/maturity-forecast",
            UNKNOWN + ",", UNKNOWN + ",/payments", UNKNOWN + ",/rate-comparison",
            UNKNOWN + ",/early-termination", UNKNOWN + ",/maturity-forecast"
    })
    @WithMockUser(username = "1")
    void foreignMissingAndNonSavingsAccountsShareOneNotFound(String accountNo, String suffix)
            throws Exception {
        mvc.perform(get(BASE + "/" + accountNo + (suffix == null ? "" : suffix)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    /** 400·404 오류 본문에도 계좌번호 원문과 해시가 남지 않아야 한다. */
    @ParameterizedTest
    @ValueSource(strings = {"12345", "abcdefghij", UNKNOWN, OTHER_USER, DEPOSIT_ACCOUNT})
    @WithMockUser(username = "1")
    void errorBodiesNeverEchoTheSubmittedAccountNumber(String accountNo) throws Exception {
        for (String suffix : new String[] {"", "/payments", "/rate-comparison", "/maturity-forecast"}) {
            String body = mvc.perform(get(BASE + "/" + accountNo + suffix))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain(accountNo);
            assertThat(body).doesNotContain(hasher.hash(accountNo));
        }
    }

    @Test
    @WithMockUser(username = "2")
    void ownerSeesOnlyTheirOwnSavings() throws Exception {
        mvc.perform(get(BASE + "/" + OTHER_USER)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").value(7));
        mvc.perform(get(BASE + "/" + RUNNING)).andExpect(status().isNotFound());
        mvc.perform(get(BASE + "/payment-schedules")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.schedules", hasSize(3)));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/123456789||", "/abcdefghij||", "/123456789012345678901||",
            "/1000000001/payments|page|-1",
            "/1000000001/payments|size|0",
            "/1000000001/payments|size|101",
            "/1000000001/payments|page|abc",
            "/1000000001/payments|page|''",
            "/1000000001/payments|startDate|2026-13-01",
            "/1000000001/rate-comparison|term|0",
            "/1000000001/rate-comparison|term|121",
            "/1000000001/rate-comparison|term|''",
            "/1000000001/rate-comparison|topN|0",
            "/1000000001/rate-comparison|topN|21",
            "/1000000001/rate-comparison|industry|bank",
            "/monthly-amount|yearMonth|2026-13",
            "/monthly-amount|yearMonth|2026-9",
            "/monthly-amount|yearMonth|''",
            "/disposable-balance|baseDate|2026-13-01"
    }, delimiter = '|')
    @WithMockUser(username = "1")
    void invalidInputsReturnBadRequest(String path, String parameter, String value) throws Exception {
        if (parameter == null) {
            mvc.perform(get(BASE + path)).andExpect(status().isBadRequest());
            return;
        }
        mvc.perform(get(BASE + path).param(parameter, value)).andExpect(status().isBadRequest());
    }
}
