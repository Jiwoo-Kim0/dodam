package com.ssafy.dodam.domain.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Sql(scripts = "/sql/card-query-test-data.sql", executionPhase = BEFORE_TEST_CLASS)
class CardQueryApiIntegrationTest {

    private static final String OWNED_CARD_NO = "1111222233334444";
    private static final String SUSPENDED_CARD_NO = "5555666677778888";
    private static final String OTHER_USER_CARD_NO = "9999000011112222";
    private static final String UNKNOWN_CARD_NO = "4444333322221111";
    private static final String OWNED_ACCOUNT_NO = "1234567890123456";
    private static final String ALTERNATIVE_ACCOUNT_NO = "1101234567890123";
    private static final String INACTIVE_ACCOUNT_NO = "2222222233333333";
    private static final String OTHER_USER_ACCOUNT_NO = "1100000000000021";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SensitiveNumberHasher sensitiveNumberHasher;

    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        updateCardHash(1L, OWNED_CARD_NO);
        updateCardHash(2L, SUSPENDED_CARD_NO);
        updateCardHash(3L, OTHER_USER_CARD_NO);
        updateAccountHash(11L, OWNED_ACCOUNT_NO);
        updateAccountHash(13L, ALTERNATIVE_ACCOUNT_NO);
        updateAccountHash(14L, INACTIVE_ACCOUNT_NO);
        updateAccountHash(12L, OTHER_USER_ACCOUNT_NO);
        jdbcTemplate.update("DELETE FROM card WHERE card_id = 4");
        jdbcTemplate.update(
                "UPDATE card SET withdrawal_account_id = 11, withdrawal_day = 25, "
                        + "updated_at = TIMESTAMP '2026-09-01 10:00:00' WHERE card_id = 1"
        );

        jdbcTemplate.update("DELETE FROM `transaction`");
        insertTransaction("TX-C1-001", 1L, 1L, 1L, today, LocalTime.of(12, 30),
                12000, "스타벅스 강남", "카페 결제", false);
        insertTransaction("TX-C1-002", 1L, 1L, 2L, today.minusDays(3), LocalTime.of(9, 5),
                1500, "서울교통공사", "지하철", false);
        // 2개월 전으로 둔다. 40일 전은 오늘 날짜에 따라 전월에 들어가고, 그러면 실적 기준월 소비가
        // 0이라는 혜택 테스트의 전제가 매달 10일경부터 깨진다.
        insertTransaction("TX-C1-003", 1L, 1L, null, today.minusMonths(2), null,
                32000, "이마트 성수", "장보기", false);
        insertTransaction("TX-C1-004", 1L, 1L, null, today.minusDays(200), LocalTime.of(18, 0),
                50000, "올리브영", "화장품", false);
        insertTransaction("TX-C1-005", 1L, 1L, 1L, today.minusDays(5), LocalTime.of(20, 0),
                99000, "숨긴 거래", "숨김", true);
        insertTransaction("TX-C2-001", 1L, 2L, 1L, today.minusDays(5), LocalTime.of(11, 0),
                7000, "다른 카드 가맹점", "다른 카드", false);
        insertTransaction("TX-U2-001", 2L, 3L, 1L, today.minusDays(5), LocalTime.of(13, 0),
                3000, "타인 카드 가맹점", "타인 거래", false);
    }

    @Test
    @WithMockUser(username = "1")
    void getsOwnedCardsOrderedByStatusAndName() throws Exception {
        mockMvc.perform(get("/api/v1/cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].cardNoMasked").value("1111-22**-****-4444"))
                .andExpect(jsonPath("$.data[0].cardName").value("KB 톡톡카드"))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[0].creditLimit").value(5000000))
                .andExpect(jsonPath("$.data[0].withdrawalDay").value(25))
                .andExpect(jsonPath("$.data[0].withdrawalAccount.bankName").value("KB국민은행"))
                .andExpect(jsonPath("$.data[0].withdrawalAccount.accountNoMasked")
                        .value("123456-**-**7890"))
                .andExpect(jsonPath("$.data[1].status").value("SUSPENDED"))
                .andExpect(jsonPath("$.data[1].annualFee").value(nullValue()));
    }

    @Test
    @WithMockUser(username = "1")
    void filtersOwnedCardsByIssuerCodeAndStatus() throws Exception {
        mockMvc.perform(get("/api/v1/cards").param("issuerCode", "kb_card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].issuerName").value("KB국민카드"));

        mockMvc.perform(get("/api/v1/cards").param("status", "suspended"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].cardName").value("신한 Deep Dream"));

        mockMvc.perform(get("/api/v1/cards").param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @WithMockUser(username = "1")
    void getsCardSummaryAggregatedByIssuer() throws Exception {
        mockMvc.perform(get("/api/v1/cards/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCardCount").value(2))
                .andExpect(jsonPath("$.data.activeCardCount").value(1))
                .andExpect(jsonPath("$.data.totalCreditLimit").value(8000000))
                .andExpect(jsonPath("$.data.totalAnnualFee").value(30000))
                .andExpect(jsonPath("$.data.issuers", hasSize(2)))
                .andExpect(jsonPath("$.data.issuers[0].issuerCode").value("KB_CARD"))
                .andExpect(jsonPath("$.data.issuers[0].cardCount").value(1))
                .andExpect(jsonPath("$.data.issuers[0].totalCreditLimit").value(5000000));
    }

    @Test
    @WithMockUser(username = "1")
    void getsCardDetailByCardNumber() throws Exception {
        mockMvc.perform(get("/api/v1/cards/{cardNo}", OWNED_CARD_NO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cardNoMasked").value("1111-22**-****-4444"))
                .andExpect(jsonPath("$.data.cardName").value("KB 톡톡카드"))
                .andExpect(jsonPath("$.data.financialProductId").value(402))
                .andExpect(jsonPath("$.data.baselinePerformance").value(300000))
                .andExpect(jsonPath("$.data.maxBenefitLimit").value(20000))
                .andExpect(jsonPath("$.data.withdrawalAccount.accountType").value("DEMAND_DEPOSIT"));
    }

    @Test
    @WithMockUser(username = "1")
    void hidesCardsOwnedByOtherUsers() throws Exception {
        mockMvc.perform(get("/api/v1/cards/{cardNo}", OTHER_USER_CARD_NO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/cards/{cardNo}", UNKNOWN_CARD_NO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/cards/{cardNo}/transactions", OTHER_USER_CARD_NO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/cards/{cardNo}/payment-schedule", OTHER_USER_CARD_NO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/cards/{cardNo}/billing-statements", OTHER_USER_CARD_NO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/cards/{cardNo}/limit", OTHER_USER_CARD_NO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "1")
    void getsCardTransactionsWithinDefaultPeriod() throws Exception {
        mockMvc.perform(get("/api/v1/cards/{cardNo}/transactions", OWNED_CARD_NO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(3)))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[0].transactionId").value("TX-C1-001"))
                .andExpect(jsonPath("$.data.content[0].amount").value(12000))
                .andExpect(jsonPath("$.data.content[0].merchantName").value("스타벅스 강남"))
                .andExpect(jsonPath("$.data.content[0].categoryCode").value("CAFE"))
                .andExpect(jsonPath("$.data.content[0].categoryName").value("카페"))
                .andExpect(jsonPath("$.data.content[1].transactionId").value("TX-C1-002"))
                .andExpect(jsonPath("$.data.content[2].transactionId").value("TX-C1-003"))
                .andExpect(jsonPath("$.data.content[2].transactionTime").value(nullValue()))
                .andExpect(jsonPath("$.data.content[2].categoryCode").value(nullValue()));
    }

    @Test
    @WithMockUser(username = "1")
    void getsCardTransactionsWithExplicitPeriodAndPaging() throws Exception {
        mockMvc.perform(get("/api/v1/cards/{cardNo}/transactions", OWNED_CARD_NO)
                        .param("startDate", today.minusDays(210).toString())
                        .param("endDate", today.toString())
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.last").value(false));

        mockMvc.perform(get("/api/v1/cards/{cardNo}/transactions", OWNED_CARD_NO)
                        .param("startDate", today.minusDays(210).toString())
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[1].transactionId").value("TX-C1-004"))
                .andExpect(jsonPath("$.data.last").value(true));
    }

    @Test
    @WithMockUser(username = "1")
    void appliesQueryPeriodPolicyToCardTransactions() throws Exception {
        mockMvc.perform(get("/api/v1/cards/{cardNo}/transactions", OWNED_CARD_NO)
                        .param("startDate", today.toString())
                        .param("endDate", today.minusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/v1/cards/{cardNo}/transactions", OWNED_CARD_NO)
                        .param("endDate", today.plusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/v1/cards/{cardNo}/transactions", OWNED_CARD_NO)
                        .param("startDate", today.minusMonths(12).minusDays(1).toString())
                        .param("endDate", today.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @WithMockUser(username = "1")
    void validatesCardNumberAndPagingParameters() throws Exception {
        mockMvc.perform(get("/api/v1/cards/{cardNo}", "12ab"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/cards/{cardNo}/transactions", OWNED_CARD_NO)
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/cards/{cardNo}/transactions", OWNED_CARD_NO)
                        .param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/cards/{cardNo}/transactions", OWNED_CARD_NO)
                        .param("startDate", "2026-13-01"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @WithMockUser(username = "1")
    void getsNextPaymentScheduleOrNullWhenNoneExists() throws Exception {
        mockMvc.perform(get("/api/v1/cards/{cardNo}/payment-schedule", OWNED_CARD_NO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cardNoMasked")
                        .value("1111-22**-****-4444"))
                .andExpect(jsonPath("$.data.schedule.billingMonth").value("2026-09"))
                .andExpect(jsonPath("$.data.schedule.billingDate").value("2026-09-25"))
                .andExpect(jsonPath("$.data.schedule.scheduledAmount").value(350000))
                .andExpect(jsonPath("$.data.schedule.status").value("SCHEDULED"));

        mockMvc.perform(get("/api/v1/cards/{cardNo}/payment-schedule", SUSPENDED_CARD_NO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cardNoMasked")
                        .value("5555-66**-****-8888"))
                .andExpect(jsonPath("$.data.schedule").value(nullValue()));
    }

    @Test
    @WithMockUser(username = "1")
    void getsBillingStatementsWithinMonthPeriodInDescendingOrder() throws Exception {
        mockMvc.perform(get("/api/v1/cards/{cardNo}/billing-statements", OWNED_CARD_NO)
                        .param("startMonth", "2026-06")
                        .param("endMonth", "2026-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(4)))
                .andExpect(jsonPath("$.data[0].billingMonth").value("2026-09"))
                .andExpect(jsonPath("$.data[0].billingDate").value("2026-09-25"))
                .andExpect(jsonPath("$.data[0].totalBalance").value(350000))
                .andExpect(jsonPath("$.data[0].status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data[0].paymentDate").value(nullValue()))
                .andExpect(jsonPath("$.data[1].billingMonth").value("2026-08"))
                .andExpect(jsonPath("$.data[1].paymentDate").value("2026-08-25"))
                .andExpect(jsonPath("$.data[3].billingMonth").value("2026-06"));

        mockMvc.perform(get("/api/v1/cards/{cardNo}/billing-statements", SUSPENDED_CARD_NO)
                        .param("startMonth", "2026-06")
                        .param("endMonth", "2026-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "1")
    void validatesBillingStatementMonthPeriod() throws Exception {
        YearMonth currentMonth = YearMonth.from(today);

        mockMvc.perform(get("/api/v1/cards/{cardNo}/billing-statements", OWNED_CARD_NO)
                        .param("startMonth", "2026-13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/v1/cards/{cardNo}/billing-statements", OWNED_CARD_NO)
                        .param("startMonth", "2026-09")
                        .param("endMonth", "2026-08"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/v1/cards/{cardNo}/billing-statements", OWNED_CARD_NO)
                        .param("endMonth", currentMonth.plusMonths(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/v1/cards/{cardNo}/billing-statements", OWNED_CARD_NO)
                        .param("startMonth", currentMonth.minusMonths(12).toString())
                        .param("endMonth", currentMonth.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @WithMockUser(username = "1")
    void getsStoredCardLimitWithoutDerivingAvailableAmount() throws Exception {
        mockMvc.perform(get("/api/v1/cards/{cardNo}/limit", OWNED_CARD_NO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cardNoMasked")
                        .value("1111-22**-****-4444"))
                .andExpect(jsonPath("$.data.creditLimit").value(5000000))
                .andExpect(jsonPath("$.data.usedAmount").doesNotExist())
                .andExpect(jsonPath("$.data.availableLimit").doesNotExist());
    }

    @Test
    @WithMockUser(username = "1")
    void getsMonthlySummaryAndCategoriesFromVisibleSpending() throws Exception {
        String currentMonth = YearMonth.from(today).toString();
        insertExcludedTransaction("TX-C1-INCOME", "INCOME", "VARIABLE", 500000);
        insertExcludedTransaction("TX-C1-NON-SPENDING", "EXPENSE", "NON_SPENDING", 80000);

        mockMvc.perform(get("/api/v1/cards/{cardNo}/monthly-summary", OWNED_CARD_NO)
                        .param("yearMonth", currentMonth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cardNoMasked").value("1111-22**-****-4444"))
                .andExpect(jsonPath("$.data.yearMonth").value(currentMonth))
                .andExpect(jsonPath("$.data.totalAmount").value(13500))
                .andExpect(jsonPath("$.data.transactionCount").value(2));

        mockMvc.perform(get("/api/v1/cards/{cardNo}/categories", OWNED_CARD_NO)
                        .param("yearMonth", currentMonth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalAmount").value(13500))
                .andExpect(jsonPath("$.data.categories", hasSize(2)))
                .andExpect(jsonPath("$.data.categories[0].categoryCode").value("CAFE"))
                .andExpect(jsonPath("$.data.categories[0].amount").value(12000))
                .andExpect(jsonPath("$.data.categories[0].ratio").value(88.89))
                .andExpect(jsonPath("$.data.categories[1].categoryCode")
                        .value("PUBLIC_TRANSPORT"));
    }

    @Test
    @WithMockUser(username = "1")
    void validatesHistoricalMonthAndHidesOtherUsersCardAnalytics() throws Exception {
        mockMvc.perform(get("/api/v1/cards/{cardNo}/monthly-summary", OWNED_CARD_NO)
                        .param("yearMonth", "2026-13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/v1/cards/{cardNo}/categories", OTHER_USER_CARD_NO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "1")
    void getsLowUsageActiveCardsIncludingCardsWithNoTransactions() throws Exception {
        jdbcTemplate.update(
                """
                INSERT INTO card (
                    card_id, user_id, withdrawal_account_id, withdrawal_account_type,
                    financial_product_id, card_no_encrypted, card_no_hash, card_no_masked,
                    issuer_code, issuer_name, card_name, status, credit_limit,
                    baseline_performance, max_benefit_limit, annual_fee, expiry_month,
                    withdrawal_day, created_at, updated_at
                ) VALUES (4, 1, 11, 'DEMAND_DEPOSIT', NULL, X'00', REPEAT('4', 64),
                    '3333-44**-****-5555', 'HANA_CARD', '하나카드', '미사용 카드',
                    'ACTIVE', 1000000, NULL, NULL, 20000, '2029-12', 15,
                    TIMESTAMP '2026-04-01 09:00:00', NULL)
                """
        );

        mockMvc.perform(get("/api/v1/cards/low-usage")
                        .param("thresholdAmount", "50000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lookbackMonths").value(3))
                .andExpect(jsonPath("$.data.thresholdAmount").value(50000))
                .andExpect(jsonPath("$.data.cards", hasSize(2)))
                .andExpect(jsonPath("$.data.cards[0].cardName").value("미사용 카드"))
                .andExpect(jsonPath("$.data.cards[0].totalUsageAmount").value(0))
                .andExpect(jsonPath("$.data.cards[0].transactionCount").value(0))
                .andExpect(jsonPath("$.data.cards[1].cardName").value("KB 톡톡카드"))
                .andExpect(jsonPath("$.data.cards[1].totalUsageAmount").value(45500));

        mockMvc.perform(get("/api/v1/cards/low-usage")
                        .param("lookbackMonths", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @WithMockUser(username = "1")
    void getsOwnedPaymentSchedulesAndMonthlyBurden() throws Exception {
        mockMvc.perform(get("/api/v1/cards/payment-schedules")
                        .param("startDate", "2026-09-01")
                        .param("endDate", "2026-10-31"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalScheduledAmount").value(760000))
                .andExpect(jsonPath("$.data.schedules", hasSize(2)))
                .andExpect(jsonPath("$.data.schedules[0].billingDate").value("2026-09-25"))
                .andExpect(jsonPath("$.data.schedules[1].billingDate").value("2026-10-25"));

        mockMvc.perform(get("/api/v1/cards/payment-burden")
                        .param("yearMonth", "2026-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalBillingAmount").value(350000))
                .andExpect(jsonPath("$.data.monthlyIncome").value(1000000))
                .andExpect(jsonPath("$.data.burdenRate").value(35.0));

        mockMvc.perform(get("/api/v1/cards/payment-burden")
                        .param("yearMonth", "2026-06"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalBillingAmount").value(250000))
                .andExpect(jsonPath("$.data.burdenRate").value(25.0));
    }

    @Test
    @WithMockUser(username = "2")
    void returnsNullBurdenRateWhenMonthlyIncomeIsMissing() throws Exception {
        mockMvc.perform(get("/api/v1/cards/payment-burden")
                        .param("yearMonth", "2026-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalBillingAmount").value(90000))
                .andExpect(jsonPath("$.data.monthlyIncome").value(nullValue()))
                .andExpect(jsonPath("$.data.burdenRate").value(nullValue()));
    }

    @Test
    @WithMockUser(username = "1")
    void getsBenefitsAndEstimatedBenefitUsage() throws Exception {
        String currentMonth = YearMonth.from(today).toString();

        mockMvc.perform(get("/api/v1/cards/{cardNo}/benefits", OWNED_CARD_NO)
                        .param("category", "cafe"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.benefitDataAvailable").value(true))
                .andExpect(jsonPath("$.data.benefits", hasSize(1)))
                .andExpect(jsonPath("$.data.benefits[0].category").value("CAFE"))
                .andExpect(jsonPath("$.data.benefits[0].discountRate").value(10.0));

        mockMvc.perform(get("/api/v1/cards/{cardNo}/benefit-usage", OWNED_CARD_NO)
                        .param("yearMonth", currentMonth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSpending").value(13500))
                .andExpect(jsonPath("$.data.matchedSpending").value(13500))
                .andExpect(jsonPath("$.data.coverageRate").value(100.0))
                .andExpect(jsonPath("$.data.performanceMet").value(false))
                .andExpect(jsonPath("$.data.performanceShortfall").value(300000))
                .andExpect(jsonPath("$.data.estimatedBenefit").value(0));
    }

    @Test
    @WithMockUser(username = "1")
    void basesBenefitEligibilityOnPreviousMonthSpending() throws Exception {
        YearMonth usageMonth = YearMonth.from(today);
        jdbcTemplate.update("DELETE FROM `transaction`");
        insertTransaction("TX-USAGE-MONTH", 1L, 1L, 1L, usageMonth.atDay(10), null,
                400000, "요청 월 카페", "요청 월 소비", false);

        mockMvc.perform(get("/api/v1/cards/{cardNo}/benefit-usage", OWNED_CARD_NO)
                        .param("yearMonth", usageMonth.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSpending").value(400000))
                .andExpect(jsonPath("$.data.performanceMet").value(false))
                .andExpect(jsonPath("$.data.performanceShortfall").value(300000))
                .andExpect(jsonPath("$.data.estimatedBenefit").value(0));

        insertTransaction("TX-PREVIOUS-MONTH", 1L, 1L, 1L,
                usageMonth.minusMonths(1).atDay(10), null,
                300000, "전월 카페", "전월 실적", false);

        mockMvc.perform(get("/api/v1/cards/{cardNo}/benefit-usage", OWNED_CARD_NO)
                        .param("yearMonth", usageMonth.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSpending").value(400000))
                .andExpect(jsonPath("$.data.performanceMet").value(true))
                .andExpect(jsonPath("$.data.performanceShortfall").value(0))
                .andExpect(jsonPath("$.data.estimatedBenefit").value(5000));
    }

    @Test
    @WithMockUser(username = "1")
    void readsDecemberPerformanceForJanuaryBenefitUsage() throws Exception {
        YearMonth january = YearMonth.of(today.getYear() - 1, 1);
        jdbcTemplate.update("DELETE FROM `transaction`");
        insertTransaction("TX-JANUARY-USAGE", 1L, 1L, 1L, january.atDay(10), null,
                100000, "1월 카페", "1월 소비", false);
        insertTransaction("TX-DECEMBER-PERFORMANCE", 1L, 1L, 1L,
                january.minusMonths(1).atDay(10), null,
                300000, "12월 카페", "전년도 실적", false);

        mockMvc.perform(get("/api/v1/cards/{cardNo}/benefit-usage", OWNED_CARD_NO)
                        .param("yearMonth", january.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearMonth").value(january.toString()))
                .andExpect(jsonPath("$.data.totalSpending").value(100000))
                .andExpect(jsonPath("$.data.performanceMet").value(true))
                .andExpect(jsonPath("$.data.performanceShortfall").value(0))
                .andExpect(jsonPath("$.data.estimatedBenefit").value(5000));
    }

    @Test
    @WithMockUser(username = "1")
    void distinguishesUnavailableBenefitDataFromNoCalculatedBenefit() throws Exception {
        mockMvc.perform(get("/api/v1/cards/{cardNo}/benefit-usage", SUSPENDED_CARD_NO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.benefitDataAvailable").value(false))
                .andExpect(jsonPath("$.data.estimatedBenefit").value(nullValue()))
                .andExpect(jsonPath("$.data.coverageRate").value(nullValue()));
    }

    @Test
    @WithMockUser(username = "1")
    void updatesCardPaymentSettingsPartiallyAndIdempotently() throws Exception {
        mockMvc.perform(patch("/api/v1/cards/{cardNo}/payment-settings", OWNED_CARD_NO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"withdrawalAccountNo":"1101234567890123","withdrawalDay":20}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.withdrawalDay").value(20))
                .andExpect(jsonPath("$.data.withdrawalAccount.accountNoMasked")
                        .value("110-***-****34"));

        mockMvc.perform(get("/api/v1/cards/{cardNo}", OWNED_CARD_NO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.withdrawalAccount.accountNoMasked")
                        .value("110-***-****34"));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT withdrawal_account_id FROM card WHERE card_id = 1",
                Long.class
        )).isEqualTo(13L);
        LocalDateTime firstUpdatedAt = jdbcTemplate.queryForObject(
                "SELECT updated_at FROM card WHERE card_id = 1",
                LocalDateTime.class
        );

        mockMvc.perform(patch("/api/v1/cards/{cardNo}/payment-settings", OWNED_CARD_NO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"withdrawalDay\":20}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.withdrawalDay").value(20));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT updated_at FROM card WHERE card_id = 1",
                LocalDateTime.class
        )).isEqualTo(firstUpdatedAt);
    }

    @Test
    @WithMockUser(username = "1")
    void validatesCardPaymentSettingsOwnershipStateAndFields() throws Exception {
        mockMvc.perform(patch("/api/v1/cards/{cardNo}/payment-settings", OWNED_CARD_NO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_FIELDS_TO_UPDATE"));

        mockMvc.perform(patch("/api/v1/cards/{cardNo}/payment-settings", OWNED_CARD_NO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"withdrawalAccountNo\":\"1100000000000021\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(patch("/api/v1/cards/{cardNo}/payment-settings", OWNED_CARD_NO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"withdrawalAccountNo\":\"2222222233333333\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/cards/{cardNo}/payment-settings", SUSPENDED_CARD_NO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"withdrawalDay\":10}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @WithMockUser(username = "2")
    void scopesCardsToTheAuthenticatedUser() throws Exception {
        mockMvc.perform(get("/api/v1/cards"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].cardName").value("KB 마이핏카드"));

        mockMvc.perform(get("/api/v1/cards/{cardNo}/transactions", OTHER_USER_CARD_NO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].transactionId").value("TX-U2-001"));
    }

    @Test
    void cardApisRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/cards"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/cards/summary"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/cards/{cardNo}", OWNED_CARD_NO))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/cards/{cardNo}/transactions", OWNED_CARD_NO))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/cards/{cardNo}/payment-schedule", OWNED_CARD_NO))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/cards/{cardNo}/billing-statements", OWNED_CARD_NO))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/cards/{cardNo}/limit", OWNED_CARD_NO))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/cards/payment-schedules"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/cards/{cardNo}/benefits", OWNED_CARD_NO))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(patch("/api/v1/cards/{cardNo}/payment-settings", OWNED_CARD_NO)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    private void updateCardHash(Long cardId, String cardNo) {
        jdbcTemplate.update(
                "UPDATE card SET card_no_hash = ? WHERE card_id = ?",
                sensitiveNumberHasher.hash(cardNo),
                cardId
        );
    }

    private void updateAccountHash(Long accountId, String accountNo) {
        jdbcTemplate.update(
                "UPDATE account SET account_no_hash = ? WHERE account_id = ?",
                sensitiveNumberHasher.hash(accountNo),
                accountId
        );
    }

    private void insertTransaction(
            String transactionId,
            Long userId,
            Long cardId,
            Long categoryId,
            LocalDate transactionDate,
            LocalTime transactionTime,
            long amount,
            String merchantName,
            String summary,
            boolean hidden
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO `transaction` (
                    transaction_id, user_id, account_id, card_id, category_id,
                    transaction_date, transaction_time, transaction_type, amount,
                    merchant_name, payment_method, spending_type, summary, memo,
                    hidden, source_type
                ) VALUES (?, ?, NULL, ?, ?, ?, ?, 'EXPENSE', ?, ?, 'CARD', 'VARIABLE',
                          ?, NULL, ?, 'SEED')
                """,
                transactionId,
                userId,
                cardId,
                categoryId,
                transactionDate,
                transactionTime,
                amount,
                merchantName,
                summary,
                hidden
        );
    }

    private void insertExcludedTransaction(
            String transactionId,
            String transactionType,
            String spendingType,
            long amount
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO `transaction` (
                    transaction_id, user_id, account_id, card_id, category_id,
                    transaction_date, transaction_time, transaction_type, amount,
                    merchant_name, payment_method, spending_type, summary, memo,
                    hidden, source_type
                ) VALUES (?, 1, NULL, 1, NULL, ?, NULL, ?, ?, '제외 거래', 'CARD', ?,
                          '제외', NULL, FALSE, 'SEED')
                """,
                transactionId,
                today,
                transactionType,
                amount,
                spendingType
        );
    }
}
