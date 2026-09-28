package com.ssafy.dodam.domain.depositsaving;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.dodam.domain.depositsaving.service.DepositSavingClock;
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

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:deposit535;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@Sql("/sql/deposit-saving-test-data.sql")
class DepositSavingApiIntegrationTest {
    private static final String BASE = "/api/v1/deposit-savings";
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private DepositSavingClock clock;

    @BeforeEach
    void fixedDate() {
        when(clock.today()).thenReturn(LocalDate.of(2026, 9, 9));
    }

    @Test
    @WithMockUser(username = "1")
    void listAndSummaryUseSameOwnedAccountsAndPreserveMissingDetails() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.content", hasSize(4)))
                .andExpect(jsonPath("$.data.content[0].accountId").value(1))
                .andExpect(jsonPath("$.data.content[3].detailsAvailable").value(false))
                .andExpect(jsonPath("$.data.content[3].interestRate", nullValue()));
        mvc.perform(get(BASE + "/summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(4))
                .andExpect(jsonPath("$.data.totalBalance").value(1850000))
                .andExpect(jsonPath("$.data.depositBalance").value(1250000))
                .andExpect(jsonPath("$.data.savingsBalance").value(600000));
        String body = mvc.perform(get(BASE)).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("accountNoHash", "accountNoEncrypted", "\"accountNo\"");
    }

    @Test
    @WithMockUser(username = "1")
    void filtersAndPaginationDoNotChangeTotals() throws Exception {
        mvc.perform(get(BASE).param("type", "SAVINGS").param("status", "CLOSED"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].accountId").value(3));
        mvc.perform(get(BASE).param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].accountId").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(4));
        mvc.perform(get(BASE).param("page", "99").param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "1")
    void maturitiesExcludeTodayAndIncludeUpperBound() throws Exception {
        mvc.perform(get(BASE + "/maturities")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].accountId").value(2))
                .andExpect(jsonPath("$.data.content[0].daysUntilMaturity").value(1))
                .andExpect(jsonPath("$.data.content[1].accountId").value(1))
                .andExpect(jsonPath("$.data.content[1].daysUntilMaturity").value(30));
        mvc.perform(get(BASE + "/maturities").param("withinDays", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1));
        jdbc.update("UPDATE deposit_saving SET expiry_date = DATE '2026-10-10' WHERE account_id = 1");
        mvc.perform(get(BASE + "/maturities")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @WithMockUser(username = "1")
    void historicalRateIncludesClosedAccountsAndOnlyPaidInstallments() throws Exception {
        mvc.perform(get(BASE + "/rate").param("yearMonth", "2026-08"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.paidAmount").value(600000))
                .andExpect(jsonPath("$.data.rate").value(20.00))
                .andExpect(jsonPath("$.data.periodEnd").value("2026-08-31"))
                .andExpect(jsonPath("$.data.reason", nullValue()))
                .andExpect(jsonPath("$.data.calculationBasis").value("SAVINGS_PAID_TO_PROFILE_INCOME"));
    }

    @Test
    @WithMockUser(username = "1")
    void maturityNullLeapDayAndTieOrdering() throws Exception {
        when(clock.today()).thenReturn(LocalDate.of(2024, 2, 28));
        jdbc.update("UPDATE deposit_saving SET expiry_date = NULL WHERE account_id = 4");
        jdbc.update("UPDATE deposit_saving SET expiry_date = DATE '2024-02-29' WHERE account_id IN (1, 2)");
        mvc.perform(get(BASE + "/maturities").param("withinDays", "1").param("size", "1").param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].accountId").value(2))
                .andExpect(jsonPath("$.data.content[0].daysUntilMaturity").value(1));
        when(clock.today()).thenReturn(LocalDate.of(2026, 12, 31));
        jdbc.update("UPDATE deposit_saving SET expiry_date = DATE '2027-12-31' WHERE account_id = 1");
        mvc.perform(get(BASE + "/maturities").param("withinDays", "365"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].daysUntilMaturity").value(365));
    }

    @Test
    @WithMockUser(username = "1")
    void defaultMonthAndCurrentMonthCutoffFollowTheInjectedDate() throws Exception {
        mvc.perform(get(BASE + "/rate")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearMonth").value("2026-09"))
                .andExpect(jsonPath("$.data.asOfDate").value("2026-09-09"))
                .andExpect(jsonPath("$.data.periodEnd").value("2026-09-09"))
                .andExpect(jsonPath("$.data.paidAmount").value(100000))
                .andExpect(jsonPath("$.data.rate").value(3.33));
        when(clock.today()).thenReturn(LocalDate.of(2026, 8, 31));
        mvc.perform(get(BASE + "/rate")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearMonth").value("2026-08"))
                .andExpect(jsonPath("$.data.paidAmount").value(600000));
        mvc.perform(get(BASE + "/rate").param("yearMonth", "2026-09"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "1")
    void rateDistinguishesZeroPaidFromMissingAndZeroIncome() throws Exception {
        mvc.perform(get(BASE + "/rate").param("yearMonth", "2026-07"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rate").value(0.0));
        jdbc.update("UPDATE financial_profile SET monthly_income = 0 WHERE user_id = 1");
        mvc.perform(get(BASE + "/rate")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.rate", nullValue()))
                .andExpect(jsonPath("$.data.reason").value("ZERO_INCOME"));
        jdbc.update("UPDATE financial_profile SET monthly_income = NULL WHERE user_id = 1");
        mvc.perform(get(BASE + "/rate")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reason").value("INCOME_NOT_REGISTERED"));
        jdbc.update("DELETE FROM financial_profile WHERE user_id = 1");
        mvc.perform(get(BASE + "/rate")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monthlyIncome", nullValue()))
                .andExpect(jsonPath("$.data.reason").value("INCOME_NOT_REGISTERED"));
    }

    @Test
    @WithMockUser(username = "1")
    void overflowRemainsNullAndRatesOverOneHundredAreNotClamped() throws Exception {
        jdbc.update("UPDATE financial_profile SET monthly_income = 100000 WHERE user_id = 1");
        mvc.perform(get(BASE + "/rate").param("yearMonth", "2026-08"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.rate").value(600.0));
        jdbc.update("UPDATE savings_payment SET amount = ? WHERE deposit_saving_id = 2 AND status = 'PAID'",
                Long.MAX_VALUE);
        mvc.perform(get(BASE + "/rate").param("yearMonth", "2026-08"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.paidAmount", nullValue()))
                .andExpect(jsonPath("$.data.rate", nullValue()))
                .andExpect(jsonPath("$.data.reason").value("AMOUNT_OUT_OF_RANGE"));
        jdbc.update("UPDATE account SET balance = ? WHERE account_id = 1", Long.MAX_VALUE);
        mvc.perform(get(BASE + "/summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalBalance", nullValue()))
                .andExpect(jsonPath("$.data.depositBalance", nullValue()))
                .andExpect(jsonPath("$.data.savingsBalance").value(600000));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "/summary", "/maturities", "/rate"})
    void allEndpointsRequireAuthentication(String path) throws Exception {
        mvc.perform(get(BASE + path)).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "2")
    void secondUserCannotSeeFirstUserInAnyAggregate() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].accountId").value(6));
        mvc.perform(get(BASE + "/summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalBalance").value(9000000));
        mvc.perform(get(BASE + "/maturities")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get(BASE + "/rate").param("yearMonth", "2026-08"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.paidAmount").value(9000000));
    }

    @Test
    @WithMockUser(username = "99")
    void noAccountsProduceEmptyListsAndZeroSummary() throws Exception {
        mvc.perform(get(BASE)).andExpect(status().isOk()).andExpect(jsonPath("$.data.content", hasSize(0)));
        mvc.perform(get(BASE + "/summary")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(0))
                .andExpect(jsonPath("$.data.totalBalance").value(0));
        mvc.perform(get(BASE + "/maturities")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)));
        mvc.perform(get(BASE + "/rate")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paidAmount").value(0))
                .andExpect(jsonPath("$.data.rate", nullValue()));
    }

    @ParameterizedTest
    @CsvSource(value = {
            "|type|LOAN", "|type|''", "|status|EXPIRED", "|status|''",
            "|page|-1", "|size|0", "|size|101", "|page|abc",
            "/maturities|withinDays|0", "/maturities|withinDays|366", "/maturities|withinDays|abc",
            "/rate|yearMonth|2026-13", "/rate|yearMonth|2026-9", "/rate|yearMonth|2026-10",
            "/rate|yearMonth|''"
    }, delimiter = '|')
    @WithMockUser(username = "1")
    void invalidInputsReturnBadRequest(String path, String parameter, String value) throws Exception {
        mvc.perform(get(BASE + (path == null ? "" : path)).param(parameter, value))
                .andExpect(status().isBadRequest());
    }
}
