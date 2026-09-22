package com.ssafy.dodam.domain.depositsaving;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
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
 * {@code GET /api/v1/deposit-savings/{accountId}/maturity-forecast} (S15P21D105-678).
 *
 * <p>기준일은 {@link DepositSavingClock}만 대체한다. 예금·적금 서비스의 자체 Clock은 이 경로에서 쓰이지 않아야
 * 하므로(기준일 한 번 읽기) 그 둘을 고정하지 않은 채로 기대값이 맞는지 본다.
 *
 * <p>금액 단정은 계산식 손검산이다. 예금 1: 10,000,000 × 3.0% × 365/365 = 300,000, 세액 46,200, 수령 10,253,800.
 * 적금 2(678 시연 목돈적금과 같은 계약): 78회 납입 + 예정 6회 = 84,000,000, 회차별 실제 일수 가중 단리 3.25% 세전
 * 9,669,506, 세액 1,489,103, 세후 8,180,403, 수령 92,180,403.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:depositsavingforecast678;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@Sql("/sql/deposit-saving-forecast-test-data.sql")
class DepositSavingForecastApiIntegrationTest {
    private static final String BASE = "/api/v1/deposit-savings";
    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private DepositSavingClock clock;

    @BeforeEach
    void fixedDate() {
        when(clock.today()).thenReturn(LocalDate.of(2026, 9, 21));
    }

    @Test
    @WithMockUser(username = "1")
    void depositForecastUsesContractDaysAndGeneralTax() throws Exception {
        mvc.perform(get(BASE + "/1/maturity-forecast")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.asOfDate").value("2026-09-21"))
                .andExpect(jsonPath("$.data.accountId").value(1))
                .andExpect(jsonPath("$.data.accountName").value("예금"))
                .andExpect(jsonPath("$.data.accountNoMasked").value("***-01"))
                .andExpect(jsonPath("$.data.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.data.status").value("COMPLETE"))
                .andExpect(jsonPath("$.data.reasons", empty()))
                .andExpect(jsonPath("$.data.principal").value(10000000))
                .andExpect(jsonPath("$.data.interestRate").value(3.0))
                .andExpect(jsonPath("$.data.expiryDate").value("2027-01-01"))
                .andExpect(jsonPath("$.data.daysUntilMaturity").value(102))
                .andExpect(jsonPath("$.data.expectedPrincipal").value(10000000))
                .andExpect(jsonPath("$.data.expectedInterest").value(300000))
                .andExpect(jsonPath("$.data.tax").value(46200))
                .andExpect(jsonPath("$.data.expectedNetInterest").value(253800))
                .andExpect(jsonPath("$.data.expectedAmount").value(10253800));
    }

    @Test
    @WithMockUser(username = "1")
    void savingsForecastAddsRemainingInstallmentsUntilMaturity() throws Exception {
        mvc.perform(get(BASE + "/2/maturity-forecast")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("SAVINGS"))
                .andExpect(jsonPath("$.data.status").value("COMPLETE"))
                .andExpect(jsonPath("$.data.principal").value(78000000))
                .andExpect(jsonPath("$.data.interestRate").value(3.25))
                .andExpect(jsonPath("$.data.openingDate").value("2020-04-15"))
                .andExpect(jsonPath("$.data.expiryDate").value("2027-04-15"))
                .andExpect(jsonPath("$.data.daysUntilMaturity").value(206))
                .andExpect(jsonPath("$.data.expectedPrincipal").value(84000000))
                .andExpect(jsonPath("$.data.expectedInterest").value(9669506))
                .andExpect(jsonPath("$.data.tax").value(1489103))
                .andExpect(jsonPath("$.data.expectedNetInterest").value(8180403))
                .andExpect(jsonPath("$.data.expectedAmount").value(92180403));
        String body = mvc.perform(get(BASE + "/2/maturity-forecast")).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("accountNoHash", "accountNoEncrypted", "\"accountNo\"");
    }

    @Test
    @WithMockUser(username = "1")
    void unavailableForecastsCarryTheDomainReasonAndNoAmounts() throws Exception {
        // 만기일 없는 청약형 적금: 0원이 아니라 계산 불가로 답한다.
        mvc.perform(get(BASE + "/3/maturity-forecast")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("SAVINGS"))
                .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.reasons", contains("INCOMPLETE_SAVINGS_TERMS")))
                .andExpect(jsonPath("$.data.principal").value(200000))
                .andExpect(jsonPath("$.data.expiryDate", nullValue()))
                .andExpect(jsonPath("$.data.daysUntilMaturity", nullValue()))
                .andExpect(jsonPath("$.data.expectedPrincipal", nullValue()))
                .andExpect(jsonPath("$.data.expectedAmount", nullValue()));
        mvc.perform(get(BASE + "/4/maturity-forecast")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.reasons", contains("MISSING_DEPOSIT_DETAIL")))
                .andExpect(jsonPath("$.data.expectedPrincipal", nullValue()))
                .andExpect(jsonPath("$.data.expectedAmount", nullValue()));
    }

    @Test
    @WithMockUser(username = "1")
    void balanceMismatchWithPaymentHistoryIsReportedNotHidden() throws Exception {
        jdbc.update("UPDATE account SET balance = 77000000 WHERE account_id = 2");
        mvc.perform(get(BASE + "/2/maturity-forecast")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.reasons", contains("PAYMENT_HISTORY_BALANCE_MISMATCH")))
                .andExpect(jsonPath("$.data.principal").value(77000000));
    }

    @ParameterizedTest
    @WithMockUser(username = "1")
    @ValueSource(strings = {"5", "6", "9999"})
    void demandDepositOtherUsersAndUnknownAccountsAreAllNotFound(String accountId) throws Exception {
        mvc.perform(get(BASE + "/" + accountId + "/maturity-forecast")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "2")
    void ownerOfTheOtherDepositCanReadItByTheSameId() throws Exception {
        mvc.perform(get(BASE + "/6/maturity-forecast")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").value(6))
                .andExpect(jsonPath("$.data.status").value("COMPLETE"));
        mvc.perform(get(BASE + "/1/maturity-forecast")).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @WithMockUser(username = "1")
    @ValueSource(strings = {"0", "-1", "abc"})
    void invalidAccountIdIsBadRequest(String accountId) throws Exception {
        mvc.perform(get(BASE + "/" + accountId + "/maturity-forecast")).andExpect(status().isBadRequest());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get(BASE + "/1/maturity-forecast")).andExpect(status().isUnauthorized());
    }
}
