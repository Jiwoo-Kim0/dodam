package com.ssafy.dodam.domain.deposit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.dodam.domain.deposit.service.DepositClock;
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
 * 예금 상세·금리비교·중도해지·만기예상 통합 테스트.
 *
 * <p>기준일은 {@link DepositClock}을 대체해 2026-09-09로 고정한다. 시연 seed 날짜에 서버 시각이 끌려가지
 * 않게 하고, 예치일수·경과 개월 경계를 실제 실행일과 무관하게 단정하기 위해서다.
 *
 * <p>fixture 계좌번호는 {@code 10000000<계좌ID>}다. 저장된 해시는 실제 {@link SensitiveNumberHasher}로
 * 다시 채워, 서비스가 평문이 아니라 해시로만 소유자를 찾는지 함께 확인한다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:deposit536;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@Sql("/sql/deposit-detail-test-data.sql")
class DepositApiIntegrationTest {
    private static final String BASE = "/api/v1/deposits";
    private static final String RUNNING = "1000000001";
    private static final String NO_DETAIL = "1000000002";
    private static final String CLOSED = "1000000003";
    private static final String MATURED = "1000000004";
    private static final String SAVINGS_ACCOUNT = "1000000005";
    private static final String OTHER_USER = "1000000006";
    private static final String PENSION = "1000000007";
    private static final String NO_EXPIRY = "1000000008";
    private static final String UNKNOWN = "1000009999";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SensitiveNumberHasher hasher;
    @MockitoBean private DepositClock clock;

    @BeforeEach
    void fixedDateAndRealHashes() {
        when(clock.today()).thenReturn(LocalDate.of(2026, 9, 9));
        for (int accountId = 1; accountId <= 8; accountId++) {
            jdbc.update("UPDATE account SET account_no_hash = ? WHERE account_id = ?",
                    hasher.hash("100000000" + accountId), accountId);
        }
    }

    @Test
    @WithMockUser(username = "1")
    void detailReturnsContractProductAndMaskedNumberOnly() throws Exception {
        mvc.perform(get(BASE + "/" + RUNNING)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.asOfDate").value("2026-09-09"))
                .andExpect(jsonPath("$.data.dataSource").value("LOCAL_DEMO_DB"))
                .andExpect(jsonPath("$.data.accountId").value(1))
                .andExpect(jsonPath("$.data.accountType").value("DEPOSIT"))
                .andExpect(jsonPath("$.data.accountNoMasked").value("110-***-**0001"))
                .andExpect(jsonPath("$.data.balance").value(10000000))
                .andExpect(jsonPath("$.data.detailsAvailable").value(true))
                .andExpect(jsonPath("$.data.interestRate").value(3.15))
                .andExpect(jsonPath("$.data.contractMonths").value(12))
                .andExpect(jsonPath("$.data.contractDays").value(365))
                .andExpect(jsonPath("$.data.daysUntilMaturity").value(114))
                .andExpect(jsonPath("$.data.matured").value(false))
                .andExpect(jsonPath("$.data.productName").value("KB Star 정기예금"))
                .andExpect(jsonPath("$.data.institutionName").value("KB국민은행"))
                .andExpect(jsonPath("$.data.savingType").value("FIXED_DEPOSIT"));

        String body = mvc.perform(get(BASE + "/" + RUNNING)).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(RUNNING, "accountNoHash", "accountNoEncrypted", "\"accountNo\"");
    }

    @Test
    @WithMockUser(username = "1")
    void detailKeepsAccountsWithoutDetailRowAndReportsPastMaturity() throws Exception {
        // 상세행이 없는 계좌를 404로 감추면 535 목록에는 보이는 계좌가 상세에서 사라진다.
        mvc.perform(get(BASE + "/" + NO_DETAIL)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.detailsAvailable").value(false))
                .andExpect(jsonPath("$.data.interestRate", nullValue()))
                .andExpect(jsonPath("$.data.openingDate", nullValue()))
                .andExpect(jsonPath("$.data.contractMonths", nullValue()))
                .andExpect(jsonPath("$.data.daysUntilMaturity", nullValue()))
                .andExpect(jsonPath("$.data.matured", nullValue()))
                .andExpect(jsonPath("$.data.productId", nullValue()));
        mvc.perform(get(BASE + "/" + MATURED)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"))
                .andExpect(jsonPath("$.data.matured").value(true))
                .andExpect(jsonPath("$.data.daysUntilMaturity").value(-8));
        mvc.perform(get(BASE + "/" + NO_EXPIRY)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.expiryDate", nullValue()))
                .andExpect(jsonPath("$.data.contractDays", nullValue()))
                .andExpect(jsonPath("$.data.matured", nullValue()));
    }

    @Test
    @WithMockUser(username = "1")
    void maturityForecastAppliesContractRateAndWithholdingTax() throws Exception {
        // 10,000,000 × 3.15% × 365/365 = 315,000, 세금 15.4% = 48,510, 세후 266,490
        mvc.perform(get(BASE + "/" + RUNNING + "/maturity-forecast")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETE"))
                .andExpect(jsonPath("$.data.unavailableReasons", hasSize(0)))
                .andExpect(jsonPath("$.data.assumptions", contains(
                        "EXCLUDES_PREFERENTIAL_RATE", "SIMPLE_INTEREST_AT_MATURITY", "TAXED_AT_GENERAL_RATE")))
                .andExpect(jsonPath("$.data.principal").value(10000000))
                .andExpect(jsonPath("$.data.contractDays").value(365))
                .andExpect(jsonPath("$.data.daysUntilMaturity").value(114))
                .andExpect(jsonPath("$.data.matured").value(false))
                .andExpect(jsonPath("$.data.grossInterest").value(315000))
                .andExpect(jsonPath("$.data.tax").value(48510))
                .andExpect(jsonPath("$.data.netInterest").value(266490))
                .andExpect(jsonPath("$.data.estimatedPayoutAmount").value(10266490));

        // 이미 만기가 지났어도 계약 기준 금액은 정해져 있다. 20,000,000 × 3.05% × 730/365 = 1,220,000
        mvc.perform(get(BASE + "/" + MATURED + "/maturity-forecast")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETE"))
                .andExpect(jsonPath("$.data.matured").value(true))
                .andExpect(jsonPath("$.data.contractDays").value(730))
                .andExpect(jsonPath("$.data.daysUntilMaturity").value(-8))
                .andExpect(jsonPath("$.data.grossInterest").value(1220000))
                .andExpect(jsonPath("$.data.tax").value(187880))
                .andExpect(jsonPath("$.data.estimatedPayoutAmount").value(21032120));
    }

    @Test
    @WithMockUser(username = "1")
    void maturityForecastRefusesWhatItCannotCompute() throws Exception {
        mvc.perform(get(BASE + "/" + NO_EXPIRY + "/maturity-forecast")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.unavailableReasons", contains("MISSING_EXPIRY_DATE")))
                .andExpect(jsonPath("$.data.grossInterest", nullValue()))
                .andExpect(jsonPath("$.data.estimatedPayoutAmount", nullValue()));
        mvc.perform(get(BASE + "/" + NO_DETAIL + "/maturity-forecast")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unavailableReasons", contains("MISSING_DEPOSIT_DETAIL")));
        mvc.perform(get(BASE + "/" + CLOSED + "/maturity-forecast")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unavailableReasons",
                        contains("ACCOUNT_CLOSED", "ZERO_PRINCIPAL")));
    }

    @Test
    @WithMockUser(username = "1")
    void earlyTerminationAppliesTieredRateOnElapsedDays() throws Exception {
        // 경과 8개월 → 계약이율의 40% = 1.26%, 예치 251일
        // 10,000,000 × 1.26% × 251/365 = 86,646, 세금 13,343, 세후 73,303
        mvc.perform(get(BASE + "/" + RUNNING + "/early-termination")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETE"))
                .andExpect(jsonPath("$.data.assumptions", contains(
                        "ESTIMATED_EARLY_TERMINATION_RATE", "EXCLUDES_PREFERENTIAL_RATE",
                        "SIMPLE_INTEREST_AT_MATURITY", "TAXED_AT_GENERAL_RATE")))
                .andExpect(jsonPath("$.data.contractRate").value(3.15))
                .andExpect(jsonPath("$.data.elapsedDays").value(251))
                .andExpect(jsonPath("$.data.elapsedMonths").value(8))
                .andExpect(jsonPath("$.data.appliedRate").value(1.26))
                .andExpect(jsonPath("$.data.ratePolicy").value("TIERED_CONTRACT_RATE_RATIO"))
                .andExpect(jsonPath("$.data.rateTier").value("ELAPSED_MONTHS_GTE_6"))
                .andExpect(jsonPath("$.data.grossInterest").value(86646))
                .andExpect(jsonPath("$.data.tax").value(13343))
                .andExpect(jsonPath("$.data.netInterest").value(73303))
                .andExpect(jsonPath("$.data.estimatedPayoutAmount").value(10073303));

        // 만기일이 없어도 중도해지는 계산할 수 있다. 1,000,000 × 1.20% × 192/365 = 6,312
        mvc.perform(get(BASE + "/" + NO_EXPIRY + "/early-termination")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETE"))
                .andExpect(jsonPath("$.data.elapsedDays").value(192))
                .andExpect(jsonPath("$.data.appliedRate").value(1.2))
                .andExpect(jsonPath("$.data.grossInterest").value(6312))
                .andExpect(jsonPath("$.data.estimatedPayoutAmount").value(1005340));
    }

    @Test
    @WithMockUser(username = "1")
    void earlyTerminationBoundariesCoverZeroDaysAndRateFloor() throws Exception {
        // 개설 당일 해지는 예치일수 0이라 이자가 붙지 않는다. 원금만 돌려준다.
        jdbc.update("UPDATE deposit_saving SET opening_date = DATE '2026-09-09' WHERE account_id = 1");
        mvc.perform(get(BASE + "/" + RUNNING + "/early-termination")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETE"))
                .andExpect(jsonPath("$.data.elapsedDays").value(0))
                .andExpect(jsonPath("$.data.elapsedMonths").value(0))
                .andExpect(jsonPath("$.data.rateTier").value("ELAPSED_MONTHS_LT_1"))
                .andExpect(jsonPath("$.data.appliedRate").value(0.1))
                .andExpect(jsonPath("$.data.grossInterest").value(0))
                .andExpect(jsonPath("$.data.tax").value(0))
                .andExpect(jsonPath("$.data.estimatedPayoutAmount").value(10000000));

        // 저금리 계약은 비율을 곱하면 최저이율보다 낮아진다. 관행대로 0.10%를 보장한다.
        jdbc.update("UPDATE deposit_saving SET opening_date = DATE '2025-01-01', interest_rate = 0.1000"
                + " WHERE account_id = 1");
        mvc.perform(get(BASE + "/" + RUNNING + "/early-termination")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rateTier").value("ELAPSED_MONTHS_GTE_11"))
                .andExpect(jsonPath("$.data.appliedRate").value(0.1));
    }

    @Test
    @WithMockUser(username = "1")
    void earlyTerminationRefusesMaturedClosedAndDetaillessDeposits() throws Exception {
        // 만기가 지난 예금은 중도해지 대상이 아니다. 만기 예상 API가 계약 기준 금액을 준다.
        mvc.perform(get(BASE + "/" + MATURED + "/early-termination")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.unavailableReasons", contains("ALREADY_MATURED")))
                .andExpect(jsonPath("$.data.appliedRate", nullValue()))
                .andExpect(jsonPath("$.data.estimatedPayoutAmount", nullValue()));
        mvc.perform(get(BASE + "/" + CLOSED + "/early-termination")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unavailableReasons",
                        contains("ACCOUNT_CLOSED", "ALREADY_MATURED", "ZERO_PRINCIPAL")));
        mvc.perform(get(BASE + "/" + NO_DETAIL + "/early-termination")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unavailableReasons", contains("MISSING_DEPOSIT_DETAIL")));
    }

    @Test
    @WithMockUser(username = "1")
    void amountsOutOfRangeStayNullInsteadOfBecomingZero() throws Exception {
        jdbc.update("UPDATE account SET balance = ? WHERE account_id = 1", Long.MAX_VALUE);
        mvc.perform(get(BASE + "/" + RUNNING + "/maturity-forecast")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.unavailableReasons",
                        contains("CALCULATION_AMOUNT_OUT_OF_RANGE")))
                .andExpect(jsonPath("$.data.grossInterest", nullValue()))
                .andExpect(jsonPath("$.data.estimatedPayoutAmount", nullValue()));
    }

    @Test
    @WithMockUser(username = "1")
    void rateComparisonExcludesOwnProductAndUsesMedianOfProductRepresentatives() throws Exception {
        // 같은 업권 FIXED 비교군에서 보유 상품(KB Star)을 뺀 3.00 / 3.10 / 3.20의 중앙값 3.10
        mvc.perform(get(BASE + "/" + RUNNING + "/rate-comparison")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETE"))
                .andExpect(jsonPath("$.data.comparisonGroup").value("FIXED"))
                .andExpect(jsonPath("$.data.termBand").value("7-12"))
                .andExpect(jsonPath("$.data.industry").value("BANK"))
                .andExpect(jsonPath("$.data.referenceMethod").value("DISCLOSED_BASE_RATE_MEDIAN"))
                .andExpect(jsonPath("$.data.referenceRate").value(3.1))
                .andExpect(jsonPath("$.data.confidence").value("NORMAL"))
                .andExpect(jsonPath("$.data.interestRate").value(3.15))
                .andExpect(jsonPath("$.data.rateDifference").value(0.05))
                .andExpect(jsonPath("$.data.comparisonDirection").value("HIGHER_IS_BETTER"))
                .andExpect(jsonPath("$.data.comparableProductCount").value(3))
                .andExpect(jsonPath("$.data.unmatchedConditions", contains("RATE_TYPE_NOT_MATCHED")))
                .andExpect(jsonPath("$.data.candidates", hasSize(3)))
                .andExpect(jsonPath("$.data.candidates[0].productName").value("쏠편한 정기예금"))
                .andExpect(jsonPath("$.data.candidates[0].representativeRate").value(3.0))
                .andExpect(jsonPath("$.data.candidates[2].productName").value("정기예금"))
                .andExpect(jsonPath("$.data.candidates[2].disclosedPreferredRate").value(3.4))
                .andExpect(jsonPath("$.data.oldestSyncedAt").value("2026-09-01T09:00:00"))
                .andExpect(jsonPath("$.data.latestSyncedAt").value("2026-09-05T09:00:00"))
                // 미일치 조건은 후보마다 다르다. rate_type이 있는 3번 후보만 해당하고 나머지는 비어 있다.
                .andExpect(jsonPath("$.data.candidates[0].unmatchedConditions", hasSize(0)))
                .andExpect(jsonPath("$.data.candidates[1].unmatchedConditions", hasSize(0)))
                .andExpect(jsonPath("$.data.candidates[2].unmatchedConditions",
                        contains("RATE_TYPE_NOT_MATCHED")));

        mvc.perform(get(BASE + "/" + RUNNING + "/rate-comparison").param("topN", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates", hasSize(1)))
                // topN은 표시만 자른다. 기준금리와 표본 수는 전체 후보로 계산한다.
                .andExpect(jsonPath("$.data.comparableProductCount").value(3))
                .andExpect(jsonPath("$.data.referenceRate").value(3.1));
    }

    @Test
    @WithMockUser(username = "1")
    void rateComparisonHonoursRequestedTermAndIndustry() throws Exception {
        // term=24는 13-24 밴드로 옮겨 2.85 / 2.95 두 상품만 남긴다. 표본 2개는 LOW다.
        mvc.perform(get(BASE + "/" + RUNNING + "/rate-comparison").param("term", "24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETE"))
                .andExpect(jsonPath("$.data.termBand").value("13-24"))
                .andExpect(jsonPath("$.data.referenceRate").value(2.9))
                .andExpect(jsonPath("$.data.confidence").value("LOW"))
                .andExpect(jsonPath("$.data.rateDifference").value(0.25))
                .andExpect(jsonPath("$.data.comparableProductCount").value(2))
                .andExpect(jsonPath("$.data.unmatchedConditions", contains("TERM_BAND_MATCH")));

        // 다른 업권으로 비교하면 그 사실을 unmatched로 남긴다. 저축은행 상품은 1개라 표본이 모자란다.
        mvc.perform(get(BASE + "/" + RUNNING + "/rate-comparison").param("industry", "SAVINGS_BANK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PARTIAL"))
                .andExpect(jsonPath("$.data.industry").value("SAVINGS_BANK"))
                .andExpect(jsonPath("$.data.unavailableReasons",
                        contains("INSUFFICIENT_COMPARABLE_PRODUCTS")))
                .andExpect(jsonPath("$.data.referenceRate", nullValue()))
                .andExpect(jsonPath("$.data.rateDifference", nullValue()))
                .andExpect(jsonPath("$.data.comparableProductCount").value(1))
                .andExpect(jsonPath("$.data.unmatchedConditions", contains("OTHER_INDUSTRY_REFERENCE")))
                .andExpect(jsonPath("$.data.candidates", hasSize(1)));

        // 만기 예금도 계약 기간(24개월)으로 비교군을 잡는다. 2.80 / 2.95의 중앙값 2.875
        mvc.perform(get(BASE + "/" + MATURED + "/rate-comparison")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.termBand").value("13-24"))
                .andExpect(jsonPath("$.data.referenceRate").value(2.875))
                .andExpect(jsonPath("$.data.rateDifference").value(0.175));
    }

    @Test
    @WithMockUser(username = "1")
    void rateComparisonNamesTheReasonItCannotCompare() throws Exception {
        mvc.perform(get(BASE + "/" + PENSION + "/rate-comparison")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PARTIAL"))
                .andExpect(jsonPath("$.data.comparisonGroup").value("PENSION"))
                .andExpect(jsonPath("$.data.comparableProductCount").value(0))
                .andExpect(jsonPath("$.data.candidates", hasSize(0)));
        mvc.perform(get(BASE + "/" + CLOSED + "/rate-comparison")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.unavailableReasons", contains("NOT_COMPARABLE_STATUS")));
        mvc.perform(get(BASE + "/" + NO_DETAIL + "/rate-comparison")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unavailableReasons", contains("MISSING_DEPOSIT_DETAIL")));
        // 만기일이 없으면 계약 기간을 모른다. term을 주면 그 기간으로 비교한다.
        mvc.perform(get(BASE + "/" + NO_EXPIRY + "/rate-comparison")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unavailableReasons", contains("MISSING_DEPOSIT_TERM")));
        mvc.perform(get(BASE + "/" + NO_EXPIRY + "/rate-comparison").param("term", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETE"))
                .andExpect(jsonPath("$.data.referenceRate").value(3.1))
                .andExpect(jsonPath("$.data.rateDifference").value(-0.1));

        jdbc.update("UPDATE deposit_saving SET financial_product_id = 7 WHERE account_id = 1");
        mvc.perform(get(BASE + "/" + RUNNING + "/rate-comparison")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unavailableReasons", contains("COMPARISON_GROUP_UNKNOWN")));
        jdbc.update("UPDATE deposit_saving SET financial_product_id = NULL WHERE account_id = 1");
        mvc.perform(get(BASE + "/" + RUNNING + "/rate-comparison")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unavailableReasons", contains("COMPARISON_GROUP_UNKNOWN")));

        jdbc.update("UPDATE deposit_saving SET financial_product_id = 1 WHERE account_id = 1");
        jdbc.update("UPDATE account SET bank_code = 'NO_SECTOR_BANK' WHERE account_id = 1");
        mvc.perform(get(BASE + "/" + RUNNING + "/rate-comparison")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unavailableReasons", contains("INDUSTRY_UNKNOWN")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/rate-comparison", "/early-termination", "/maturity-forecast"})
    void allEndpointsRequireAuthentication(String suffix) throws Exception {
        mvc.perform(get(BASE + "/" + RUNNING + suffix)).andExpect(status().isUnauthorized());
    }

    /**
     * 타인 계좌, 없는 계좌, 예금이 아닌 계좌는 모두 같은 404여야 한다. 셋을 구분하면 응답만으로 타인
     * 계좌번호의 존재를 확인할 수 있게 된다.
     */
    @ParameterizedTest
    @CsvSource({
            OTHER_USER + ",", OTHER_USER + ",/rate-comparison",
            OTHER_USER + ",/early-termination", OTHER_USER + ",/maturity-forecast",
            SAVINGS_ACCOUNT + ",", SAVINGS_ACCOUNT + ",/rate-comparison",
            SAVINGS_ACCOUNT + ",/early-termination", SAVINGS_ACCOUNT + ",/maturity-forecast",
            UNKNOWN + ",", UNKNOWN + ",/rate-comparison",
            UNKNOWN + ",/early-termination", UNKNOWN + ",/maturity-forecast"
    })
    @WithMockUser(username = "1")
    void foreignMissingAndNonDepositAccountsShareOneNotFound(String accountNo, String suffix)
            throws Exception {
        mvc.perform(get(BASE + "/" + accountNo + (suffix == null ? "" : suffix)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    /**
     * 기간을 모르는 것과 아는 기간이 비교 범위 밖인 것을 다른 사유로 구분한다.
     *
     * <p>클라이언트가 "만기일을 채워 달라"와 "비교할 상품이 없다"를 나눠 안내할 수 있어야 한다.
     */
    @Test
    @WithMockUser(username = "1")
    void rateComparisonSeparatesUnknownTermFromOutOfRangeTerm() throws Exception {
        // 만기일이 없어 계약 기간 자체를 모르는 경우.
        mvc.perform(get(BASE + "/" + NO_EXPIRY + "/rate-comparison")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unavailableReasons", contains("MISSING_DEPOSIT_TERM")));

        // 개설일과 만기일이 같은 달이면 계약 기간은 0개월이다. 기간은 알지만 대응 옵션이 없다.
        jdbc.update("UPDATE deposit_saving SET opening_date = DATE '2026-09-01',"
                + " expiry_date = DATE '2026-09-20' WHERE account_id = 1");
        mvc.perform(get(BASE + "/" + RUNNING + "/rate-comparison")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.unavailableReasons",
                        contains("DEPOSIT_TERM_OUT_OF_COMPARABLE_RANGE")))
                .andExpect(jsonPath("$.data.termBand", nullValue()));

        // 비교 상한 120개월을 넘는 계약도 같은 사유다.
        jdbc.update("UPDATE deposit_saving SET opening_date = DATE '2026-01-01',"
                + " expiry_date = DATE '2040-01-01' WHERE account_id = 1");
        mvc.perform(get(BASE + "/" + RUNNING + "/rate-comparison")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unavailableReasons",
                        contains("DEPOSIT_TERM_OUT_OF_COMPARABLE_RANGE")));

        // 범위 밖 계약이어도 요청 term을 주면 그 기간으로 비교한다.
        mvc.perform(get(BASE + "/" + RUNNING + "/rate-comparison").param("term", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETE"))
                .andExpect(jsonPath("$.data.termBand").value("7-12"))
                // 계약 기간이 밴드 밖이므로 모든 후보에 밴드 미일치가 붙는다.
                .andExpect(jsonPath("$.data.candidates[0].unmatchedConditions",
                        contains("TERM_BAND_MATCH")));
    }

    /**
     * 400·404 오류 본문에도 계좌번호 원문이 없어야 한다.
     *
     * <p>Bean Validation의 위반 메시지에 거부된 값이 실리면 형식만 틀린 요청으로도 원문이 되돌아온다.
     * 현재 `@Pattern` 기본 메시지는 정규식만 담지만, 메시지를 바꾸거나 핸들러가 rejectedValue를 넣기
     * 시작하면 조용히 유출로 바뀌는 지점이라 계약으로 고정한다.
     */
    @ParameterizedTest
    @ValueSource(strings = {"12345", "abcdefghij", "1000000001x", UNKNOWN, OTHER_USER, SAVINGS_ACCOUNT})
    @WithMockUser(username = "1")
    void errorBodiesNeverEchoTheSubmittedAccountNumber(String accountNo) throws Exception {
        for (String suffix : new String[] {"", "/rate-comparison", "/early-termination", "/maturity-forecast"}) {
            String body = mvc.perform(get(BASE + "/" + accountNo + suffix))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain(accountNo);
            assertThat(body).doesNotContain(hasher.hash(accountNo));
        }
    }

    /**
     * 예금이 아닌 상품이 연결돼 있으면 상품 정보를 싣지 않는다.
     *
     * <p>{@code deposit_saving}의 FK는 {@code financial_product}를 가리키기만 하고 유형을 제약하지 않아
     * 잘못 연결된 대출 상품명이 예금 상세에 실릴 수 있다.
     */
    @Test
    @WithMockUser(username = "1")
    void detailIgnoresProductsThatAreNotDeposits() throws Exception {
        jdbc.update("UPDATE deposit_saving SET financial_product_id = 8 WHERE account_id = 1");
        mvc.perform(get(BASE + "/" + RUNNING)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.detailsAvailable").value(true))
                .andExpect(jsonPath("$.data.productId", nullValue()))
                .andExpect(jsonPath("$.data.productName", nullValue()))
                .andExpect(jsonPath("$.data.savingType", nullValue()));
        // 금리비교도 예금 상품 모집단에서 찾지 못해 비교군을 만들지 않는다.
        mvc.perform(get(BASE + "/" + RUNNING + "/rate-comparison")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unavailableReasons", contains("COMPARISON_GROUP_UNKNOWN")));
    }

    @Test
    @WithMockUser(username = "2")
    void ownerSeesOnlyTheirOwnDeposit() throws Exception {
        mvc.perform(get(BASE + "/" + OTHER_USER)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").value(6))
                .andExpect(jsonPath("$.data.accountNoMasked").value("110-***-**0006"));
        mvc.perform(get(BASE + "/" + RUNNING)).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @CsvSource(value = {
            "/123456789||", "/abcdefghij||", "/123456789012345678901||",
            "/1000000001/rate-comparison|term|0",
            "/1000000001/rate-comparison|term|121",
            "/1000000001/rate-comparison|term|abc",
            "/1000000001/rate-comparison|term|''",
            "/1000000001/rate-comparison|topN|0",
            "/1000000001/rate-comparison|topN|21",
            "/1000000001/rate-comparison|topN|abc",
            "/1000000001/rate-comparison|topN|''",
            "/1000000001/rate-comparison|industry|''",
            "/1000000001/rate-comparison|industry|bank"
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
