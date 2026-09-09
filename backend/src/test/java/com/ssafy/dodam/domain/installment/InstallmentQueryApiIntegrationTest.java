package com.ssafy.dodam.domain.installment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_CLASS;
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

    @ParameterizedTest
    @ValueSource(strings = {"/api/v1/installments", "/api/v1/installments/1",
            "/api/v1/installments/monthly-schedule", "/api/v1/installments/forecast"})
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
