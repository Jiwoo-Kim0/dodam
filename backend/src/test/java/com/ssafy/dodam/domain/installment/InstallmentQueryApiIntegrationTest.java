package com.ssafy.dodam.domain.installment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_CLASS;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
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
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:installment-query;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@Sql(scripts = {"/sql/card-query-test-data.sql", "/sql/installment-query-test-schema.sql"},
        executionPhase = BEFORE_TEST_CLASS)
class InstallmentQueryApiIntegrationTest {

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        jdbc.update("DELETE FROM installment");
        jdbc.update("DELETE FROM deposit_saving WHERE account_id IN (31, 32, 33)");
        jdbc.update("DELETE FROM account WHERE account_id IN (31, 32, 33)");
        insert(1, 1, "ACTIVE", today, 2, 120000);
        insert(2, 1, "COMPLETED", null, 0, 50000);
        insert(3, 1, "CANCELED", null, 2, 80000);
        insert(4, 1, "OVERDUE", today.minusDays(1), 2, 90000);
        insert(5, 3, "ACTIVE", today, 3, 999999);
    }

    @Test
    @WithMockUser(username = "1")
    void listsOnlyOwnedRowsWithStablePaginationAndFilters() throws Exception {
        mvc.perform(get("/api/v1/installments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.content[0].installmentId").value(4));
        mvc.perform(get("/api/v1/installments").param("size", "1").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].installmentId").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(4));
        mvc.perform(get("/api/v1/installments").param("cardId", "1").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].monthlyAmount").value(120000))
                .andExpect(jsonPath("$.data.content[0].remainingPrincipal").value(nullValue()));
    }

    @Test
    @WithMockUser(username = "1")
    void detailPreservesNullableFieldsWithoutExposingSensitiveNumbers() throws Exception {
        mvc.perform(get("/api/v1/installments/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.nextPaymentDate").value(nullValue()))
                .andExpect(jsonPath("$.data.feeRate").value(nullValue()))
                .andExpect(jsonPath("$.data.firstPaymentDate").exists())
                .andExpect(jsonPath("$.data.cardNoHash").doesNotExist())
                .andExpect(jsonPath("$.data.cardNoEncrypted").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/installments/5", "/api/v1/installments/999",
            "/api/v1/installments?cardId=3", "/api/v1/installments?cardId=999"})
    @WithMockUser(username = "1")
    void hidesOtherUserResourcesAndUnknownResources(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "1")
    void returnsEmptyPageForOwnedCardAndOutOfRangePage() throws Exception {
        mvc.perform(get("/api/v1/installments").param("cardId", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/v1/installments").param("page", "2147483647").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)))
                .andExpect(jsonPath("$.data.totalElements").value(4));
    }

    @Test
    @WithMockUser(username = "1")
    void forecastAndScheduleShareAmountsCountsAndExclusions() throws Exception {
        var forecast = mvc.perform(get("/api/v1/installments/forecast").param("months", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.complete").value(false))
                .andExpect(jsonPath("$.data.excludedInstallmentCount").value(1))
                .andExpect(jsonPath("$.data.exclusions[0].reason").value("OVERDUE"))
                .andExpect(jsonPath("$.data.months", hasSize(3)))
                .andExpect(jsonPath("$.data.months[0].scheduledAmount").value(120000))
                .andExpect(jsonPath("$.data.months[1].installmentCount").value(1))
                .andExpect(jsonPath("$.data.months[2].scheduledAmount").value(0))
                .andReturn().getResponse().getContentAsString();
        var schedule = mvc.perform(get("/api/v1/installments/monthly-schedule")
                        .param("endMonth", YearMonth.from(today).plusMonths(2).toString()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(objectMapper.readTree(forecast)).isEqualTo(objectMapper.readTree(schedule));
        mvc.perform(get("/api/v1/installments/forecast"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.months", hasSize(6)));
    }

    @Test
    @WithMockUser(username = "1")
    void reportsMissingAndInconsistentDatesWithoutCountingTheirAmounts() throws Exception {
        jdbc.update("UPDATE installment SET next_payment_date = NULL WHERE installment_id = 1");
        insert(6, 1, "ACTIVE", today.plusDays(1), 1, 100);
        jdbc.update("UPDATE installment SET first_payment_date = ? WHERE installment_id = 6", today.plusDays(2));
        mvc.perform(get("/api/v1/installments/forecast"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.complete").value(false))
                .andExpect(jsonPath("$.data.excludedInstallmentCount").value(3))
                .andExpect(jsonPath("$.data.exclusions[1].reason").value("MISSING_NEXT_PAYMENT_DATE"))
                .andExpect(jsonPath("$.data.exclusions[2].reason").value("INCONSISTENT_PAYMENT_DATES"))
                .andExpect(jsonPath("$.data.months[0].scheduledAmount").value(0));
    }

    @Test
    @WithMockUser(username = "9")
    void emptyUserHasCompleteZeroScheduleAndEmptyList() throws Exception {
        mvc.perform(get("/api/v1/installments/forecast").param("months", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.complete").value(true))
                .andExpect(jsonPath("$.data.excludedInstallmentCount").value(0))
                .andExpect(jsonPath("$.data.months", hasSize(1)))
                .andExpect(jsonPath("$.data.months[0].scheduledAmount").value(0));
        mvc.perform(get("/api/v1/installments"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "1")
    void burdenDividesRemainingScheduleByProfileLessSavingsAndReportsBasis() throws Exception {
        jdbc.update("DELETE FROM installment WHERE installment_id = 4");
        insertSavings(31, "ACTIVE", 100000L);
        insertSavings(32, "INACTIVE", 999999L);
        insertSavings(33, "ACTIVE", null);

        // 1,000,000 − 300,000 − 100,000(정상 적금만) − 0 = 600,000; 120,000 / 600,000 = 20.00%
        mvc.perform(get("/api/v1/installments/burden"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearMonth").value(YearMonth.from(today).toString()))
                .andExpect(jsonPath("$.data.periodStart").value(today.toString()))
                .andExpect(jsonPath("$.data.periodEnd").value(YearMonth.from(today).atEndOfMonth().toString()))
                .andExpect(jsonPath("$.data.scheduledAmount").value(120000))
                .andExpect(jsonPath("$.data.installmentCount").value(1))
                .andExpect(jsonPath("$.data.complete").value(true))
                .andExpect(jsonPath("$.data.availableAmount").value(600000))
                .andExpect(jsonPath("$.data.availableAmountBasis.monthlyIncome").value(1000000))
                .andExpect(jsonPath("$.data.availableAmountBasis.fixedExpense").value(300000))
                .andExpect(jsonPath("$.data.availableAmountBasis.monthlySaving").value(100000))
                .andExpect(jsonPath("$.data.availableAmountBasis.monthlyLoanRepayment").value(0))
                .andExpect(jsonPath("$.data.availableAmountBasis.notes[0]").value("SAVING_MONTHLY_AMOUNT_MISSING"))
                .andExpect(jsonPath("$.data.burdenRatePercent").value(20.0))
                .andExpect(jsonPath("$.data.unavailableReason").value(nullValue()));

        // 다음 달은 1회차만 남아 같은 금액, 그다음 달은 0원이라 0.00%
        mvc.perform(get("/api/v1/installments/burden")
                        .param("yearMonth", YearMonth.from(today).plusMonths(1).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.periodStart")
                        .value(YearMonth.from(today).plusMonths(1).atDay(1).toString()))
                .andExpect(jsonPath("$.data.burdenRatePercent").value(20.0));
        mvc.perform(get("/api/v1/installments/burden")
                        .param("yearMonth", YearMonth.from(today).plusMonths(2).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scheduledAmount").value(0))
                .andExpect(jsonPath("$.data.burdenRatePercent").value(0.0));
    }

    @Test
    @WithMockUser(username = "1")
    void burdenReportsOneReasonInPriorityOrderWithoutRate() throws Exception {
        // 연체 할부(4)가 남아 있으면 일정 불완전이 먼저다.
        mvc.perform(get("/api/v1/installments/burden"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.complete").value(false))
                .andExpect(jsonPath("$.data.exclusions[0].reason").value("OVERDUE"))
                .andExpect(jsonPath("$.data.availableAmount").value(700000))
                .andExpect(jsonPath("$.data.burdenRatePercent").value(nullValue()))
                .andExpect(jsonPath("$.data.unavailableReason").value("INCOMPLETE_SCHEDULE"));

        jdbc.update("DELETE FROM installment WHERE installment_id = 4");
        jdbc.update("UPDATE financial_profile SET fixed_expense = 1000000 WHERE user_id = 1");
        try {
            mvc.perform(get("/api/v1/installments/burden"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.complete").value(true))
                    .andExpect(jsonPath("$.data.availableAmount").value(0))
                    .andExpect(jsonPath("$.data.burdenRatePercent").value(nullValue()))
                    .andExpect(jsonPath("$.data.unavailableReason").value("NON_POSITIVE_AVAILABLE_AMOUNT"));
        } finally {
            jdbc.update("UPDATE financial_profile SET fixed_expense = 300000 WHERE user_id = 1");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"2", "9"})
    void burdenWithoutIncomeHasNoAvailableAmount(String userId) throws Exception {
        // 2는 프로필 값이 모두 NULL, 9는 프로필 행 자체가 없다. 2의 정상 할부(5)는 예정액에 잡히지만 비율은 못 낸다.
        mvc.perform(get("/api/v1/installments/burden").with(user(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.complete").value(true))
                .andExpect(jsonPath("$.data.availableAmount").value(nullValue()))
                .andExpect(jsonPath("$.data.availableAmountBasis.monthlyIncome").value(nullValue()))
                .andExpect(jsonPath("$.data.availableAmountBasis.notes[0]").value("INCOME_UNKNOWN"))
                .andExpect(jsonPath("$.data.availableAmountBasis.notes[1]").value("FIXED_EXPENSE_UNKNOWN"))
                .andExpect(jsonPath("$.data.burdenRatePercent").value(nullValue()))
                .andExpect(jsonPath("$.data.unavailableReason").value("AVAILABLE_AMOUNT_UNAVAILABLE"));
    }

    @Test
    @WithMockUser(username = "1")
    void burdenRejectsPastMalformedBlankAndOutOfWindowMonths() throws Exception {
        YearMonth month = YearMonth.from(today);
        for (String value : new String[]{"", " ", "2026-13", "0000-01", "202609", month.minusMonths(1).toString(),
                month.plusMonths(12).toString()}) {
            mvc.perform(get("/api/v1/installments/burden").param("yearMonth", value))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        mvc.perform(get("/api/v1/installments/burden").param("yearMonth", month.plusMonths(11).toString()))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/installments", "/api/v1/installments/1",
            "/api/v1/installments/monthly-schedule", "/api/v1/installments/forecast",
            "/api/v1/installments/burden"})
    void rejectsUnauthenticatedRequests(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @ParameterizedTest
    @CsvSource({
            "/api/v1/installments, page, -1",
            "/api/v1/installments, size, 0",
            "/api/v1/installments, size, 101",
            "/api/v1/installments, cardId, 0",
            "/api/v1/installments/forecast, months, 0",
            "/api/v1/installments/forecast, months, 13"
    })
    @WithMockUser(username = "1")
    void numericConstraintsReturnValidationError(String path, String key, String value) throws Exception {
        mvc.perform(get(path).param(key, value)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @ParameterizedTest
    @CsvSource({
            "/api/v1/installments, page, ''",
            "/api/v1/installments, size, ''",
            "/api/v1/installments, cardId, ''",
            "/api/v1/installments, status, ''",
            "/api/v1/installments, status, UNKNOWN",
            "/api/v1/installments, page, abc",
            "/api/v1/installments, page, 2147483648",
            "/api/v1/installments, cardId, 9223372036854775808",
            "/api/v1/installments/forecast, months, ''",
            "/api/v1/installments/monthly-schedule, startMonth, ''",
            "/api/v1/installments/monthly-schedule, endMonth, ''",
            "/api/v1/installments/monthly-schedule, startMonth, 0000-01",
            "/api/v1/installments/monthly-schedule, endMonth, 2026-13"
    })
    @WithMockUser(username = "1")
    void emptyAndMalformedInputsReturnInvalidRequest(String path, String key, String value) throws Exception {
        mvc.perform(get(path).param(key, value)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @WithMockUser(username = "1")
    void rejectsWhitespacePastReversedAndOversizedRanges() throws Exception {
        for (String key : new String[]{"page", "size", "cardId", "status"}) {
            mvc.perform(get("/api/v1/installments").param(key, " "))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        mvc.perform(get("/api/v1/installments/forecast").param("months", " "))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        YearMonth month = YearMonth.from(today);
        String[][] ranges = {{month.minusMonths(1).toString(), month.toString()},
                {month.plusMonths(1).toString(), month.toString()},
                {month.toString(), month.plusMonths(12).toString()}};
        for (String[] range : ranges) {
            mvc.perform(get("/api/v1/installments/monthly-schedule")
                            .param("startMonth", range[0]).param("endMonth", range[1]))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        mvc.perform(get("/api/v1/installments/0")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/v1/installments/not-an-id")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    /** 사용자 1의 적금 계좌. {@code monthlyAmount}가 null이면 상세행 없이 계좌만 만들어 약정액 미상 상태를 만든다. */
    private void insertSavings(long accountId, String status, Long monthlyAmount) {
        jdbc.update("""
                INSERT INTO account (account_id, user_id, bank_code, bank_name, account_no_encrypted,
                    account_no_hash, account_no_masked, account_name, account_type, status, balance,
                    created_at, updated_at)
                VALUES (?, 1, '004', 'KB국민은행', X'00', ?, '***', '테스트 적금', 'SAVINGS', ?, 0,
                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, accountId, String.valueOf(accountId).repeat(64).substring(0, 64), status);
        if (monthlyAmount != null) {
            jdbc.update("""
                    INSERT INTO deposit_saving (account_id, user_id, account_type, interest_rate,
                        monthly_amount, payment_day, opening_date, created_at)
                    VALUES (?, 1, 'SAVINGS', 3.0000, ?, 10, ?, CURRENT_TIMESTAMP)
                    """, accountId, monthlyAmount, today.minusMonths(1));
        }
    }

    private void insert(long id, long card, String status, LocalDate next, int remaining, long amount) {
        jdbc.update("""
                INSERT INTO installment (installment_id, card_id, merchant_name, total_amount,
                    months, remaining_principal, remaining_months, monthly_amount, fee_rate,
                    purchase_date, first_payment_date, next_payment_date, status, created_at)
                VALUES (?, ?, ?, ?, 6, NULL, ?, ?, NULL, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                """, id, card, "테스트 상점", amount * 6, remaining, amount,
                today.minusMonths(6), today.minusMonths(5), next, status);
    }
}
