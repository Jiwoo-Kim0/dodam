package com.ssafy.dodam.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.auth.repository.AuthSessionRepository;
import com.ssafy.dodam.domain.finance.repository.FinanceConnectionRepository;
import com.ssafy.dodam.domain.finance.repository.FinanceSyncRepository;
import com.ssafy.dodam.domain.user.repository.FinancialProfileRepository;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import com.ssafy.dodam.domain.user.repository.UserSettingRepository;
import com.ssafy.dodam.global.crypto.FieldCryptor;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyApiException;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyDemandDepositAccount;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyFinanceClient;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyAccountTransaction;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyMember;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyTransactionHistory;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyUnavailableException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * S15P21D105-501 ACCOUNT 동기화 핸들러. `POST /finance-data/sync`(scope ACCOUNT)로 실행하고 계좌 API로 결과를 본다.
 * 금융망은 Mock이며, 회원가입이 만든 CONNECTED 연결의 userKey가 핸들러까지 전달되는지도 함께 확인한다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Sql(scripts = "/sql/account-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class AccountSyncIntegrationTest {

    private static final String EMAIL = "account-sync@example.com";
    private static final String PASSWORD = "password123";
    private static final String USER_KEY = "user-key-account-sync";

    private static final String MAIN_ACCOUNT_NO = "0011234567890001";
    private static final String SECOND_ACCOUNT_NO = "0041234567890002";
    private static final String SEED_ACCOUNT_NO = "1234567890123456";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSettingRepository userSettingRepository;

    @Autowired
    private FinancialProfileRepository financialProfileRepository;

    @Autowired
    private FinanceConnectionRepository financeConnectionRepository;

    @Autowired
    private FinanceSyncRepository financeSyncRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SensitiveNumberHasher sensitiveNumberHasher;

    @Autowired
    private FieldCryptor fieldCryptor;

    @MockitoBean
    private SsafyFinanceClient ssafyFinanceClient;

    private Long userId;
    private String bearer;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM `transaction`");
        jdbcTemplate.update("DELETE FROM safebox_account");
        jdbcTemplate.update("DELETE FROM account");
        financeSyncRepository.deleteAll();
        authSessionRepository.deleteAll();
        financeConnectionRepository.deleteAll();
        userSettingRepository.deleteAll();
        financialProfileRepository.deleteAll();
        userRepository.deleteAll();

        given(ssafyFinanceClient.createMember(anyString()))
                .willReturn(new SsafyMember(EMAIL, "도담", "00100", USER_KEY));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", EMAIL, "password", PASSWORD, "name", "도담"))))
                .andExpect(status().isCreated());
        userId = userRepository.findByEmailAndDeletedAtIsNull(EMAIL).orElseThrow().getId();
        bearer = login();
    }

    @Test
    void insertsFinanceAccountsWithEncryptedHashedAndMaskedNumbers() throws Exception {
        given(ssafyFinanceClient.inquireDemandDepositAccountList(USER_KEY)).willReturn(List.of(
                financeAccount("001", "한국은행", MAIN_ACCOUNT_NO, "한국은행 수시입출금", 1_500_000L, "20260910"),
                financeAccount("004", "국민은행", SECOND_ACCOUNT_NO, "KB 통장", 0L, "")
        ));

        syncAccounts()
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.results[0].scope").value("ACCOUNT"))
                .andExpect(jsonPath("$.data.results[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.results[0].syncedCount").value(2));
        verify(ssafyFinanceClient).inquireDemandDepositAccountList(USER_KEY);

        mockMvc.perform(get("/api/v1/accounts").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].bankCode").value("004"))
                .andExpect(jsonPath("$.data[0].accountNoMasked").value("004123-**-**0002"))
                .andExpect(jsonPath("$.data[0].balance").value(0))
                .andExpect(jsonPath("$.data[0].lastTransactionDate").doesNotExist())
                .andExpect(jsonPath("$.data[1].bankCode").value("001"))
                .andExpect(jsonPath("$.data[1].accountNoMasked").value("001123-**-**0001"))
                .andExpect(jsonPath("$.data[1].accountName").value("한국은행 수시입출금"))
                .andExpect(jsonPath("$.data[1].accountType").value("DEMAND_DEPOSIT"))
                .andExpect(jsonPath("$.data[1].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data[1].balance").value(1500000))
                .andExpect(jsonPath("$.data[1].currency").value("KRW"))
                .andExpect(jsonPath("$.data[1].lastTransactionDate").value("2026-09-10"))
                .andExpect(jsonPath("$.data[1].sourceType").value("SSAFY"));

        // 원문은 암호문으로만 남고, 해시는 상세 조회 경로와 같은 키로 만들어진다
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT account_no_encrypted, account_no_hash, source_type FROM account WHERE account_no_hash = ?",
                sensitiveNumberHasher.hash(MAIN_ACCOUNT_NO));
        assertThat(fieldCryptor.decrypt((byte[]) row.get("account_no_encrypted"))).isEqualTo(MAIN_ACCOUNT_NO);
        assertThat(row.get("source_type")).isEqualTo("SSAFY");

        mockMvc.perform(get("/api/v1/accounts/" + MAIN_ACCOUNT_NO + "/balance")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(1500000));
    }

    @Test
    void keepsSafeboxAccountNameWhileSyncingBalance() throws Exception {
        given(ssafyFinanceClient.inquireDemandDepositAccountList(USER_KEY)).willReturn(List.of(
                financeAccount("004", "국민은행", MAIN_ACCOUNT_NO, "수시입출금", 0L, "")));
        syncAccounts();
        Long accountId = jdbcTemplate.queryForObject("SELECT account_id FROM account WHERE user_id = ?", Long.class, userId);
        jdbcTemplate.update("UPDATE account SET account_name = '도담 계좌' WHERE account_id = ?", accountId);
        jdbcTemplate.update("INSERT INTO safebox_account (user_id, account_id, account_type, created_at) "
                + "VALUES (?, ?, 'DEMAND_DEPOSIT', ?)", userId, accountId, LocalDateTime.now());

        given(ssafyFinanceClient.inquireDemandDepositAccountList(USER_KEY)).willReturn(List.of(
                financeAccount("004", "국민은행", MAIN_ACCOUNT_NO, "수시입출금", 300_000L, "20260926")));
        syncAccounts();

        assertThat(jdbcTemplate.queryForMap("SELECT account_name, balance FROM account WHERE account_id = ?", accountId))
                .containsEntry("ACCOUNT_NAME", "도담 계좌")
                .containsEntry("BALANCE", 300_000L);
    }

    @Test
    void updatesChangedFinanceAccountsAndLeavesUnchangedOnesAlone() throws Exception {
        given(ssafyFinanceClient.inquireDemandDepositAccountList(USER_KEY)).willReturn(List.of(
                financeAccount("001", "한국은행", MAIN_ACCOUNT_NO, "한국은행 수시입출금", 1_500_000L, "20260910"),
                financeAccount("004", "국민은행", SECOND_ACCOUNT_NO, "KB 통장", 0L, "")
        ));
        syncAccounts().andExpect(jsonPath("$.data.results[0].syncedCount").value(2));
        LocalDateTime firstUpdatedAt = updatedAtOf(SECOND_ACCOUNT_NO);

        given(ssafyFinanceClient.inquireDemandDepositAccountList(USER_KEY)).willReturn(List.of(
                financeAccount("001", "한국은행", MAIN_ACCOUNT_NO, "한국은행 수시입출금", 1_230_000L, "20260911"),
                financeAccount("004", "국민은행", SECOND_ACCOUNT_NO, "KB 통장", 0L, "")
        ));
        syncAccounts().andExpect(jsonPath("$.data.results[0].syncedCount").value(1));

        mockMvc.perform(get("/api/v1/accounts/" + MAIN_ACCOUNT_NO).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.balance").value(1230000))
                .andExpect(jsonPath("$.data.lastTransactionDate").value("2026-09-11"));
        assertThat(updatedAtOf(SECOND_ACCOUNT_NO)).isEqualTo(firstUpdatedAt);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM account", Long.class)).isEqualTo(2L);
    }

    @Test
    void closesFinanceAccountsMissingFromListAndReactivatesWhenTheyReturn() throws Exception {
        given(ssafyFinanceClient.inquireDemandDepositAccountList(USER_KEY)).willReturn(List.of(
                financeAccount("001", "한국은행", MAIN_ACCOUNT_NO, "한국은행 수시입출금", 1_500_000L, "20260910"),
                financeAccount("004", "국민은행", SECOND_ACCOUNT_NO, "KB 통장", 0L, "")
        ));
        syncAccounts();

        given(ssafyFinanceClient.inquireDemandDepositAccountList(USER_KEY)).willReturn(List.of(
                financeAccount("001", "한국은행", MAIN_ACCOUNT_NO, "한국은행 수시입출금", 1_500_000L, "20260910")
        ));
        syncAccounts().andExpect(jsonPath("$.data.results[0].syncedCount").value(1));

        mockMvc.perform(get("/api/v1/accounts/" + SECOND_ACCOUNT_NO).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        given(ssafyFinanceClient.inquireDemandDepositAccountList(USER_KEY)).willReturn(List.of(
                financeAccount("001", "한국은행", MAIN_ACCOUNT_NO, "한국은행 수시입출금", 1_500_000L, "20260910"),
                financeAccount("004", "국민은행", SECOND_ACCOUNT_NO, "KB 통장", 0L, "")
        ));
        syncAccounts().andExpect(jsonPath("$.data.results[0].syncedCount").value(1));

        mockMvc.perform(get("/api/v1/accounts/" + SECOND_ACCOUNT_NO).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void neverOverwritesSeedOrManualAccounts() throws Exception {
        insertSeedAccount(SEED_ACCOUNT_NO, 9_000_000L);
        given(ssafyFinanceClient.inquireDemandDepositAccountList(USER_KEY)).willReturn(List.of(
                financeAccount("004", "국민은행", SEED_ACCOUNT_NO, "금융망 이름", 1L, "20260910")
        ));

        syncAccounts().andExpect(jsonPath("$.data.results[0].syncedCount").value(0));

        mockMvc.perform(get("/api/v1/accounts/" + SEED_ACCOUNT_NO).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountName").value("KB 주거래통장"))
                .andExpect(jsonPath("$.data.balance").value(9000000))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.sourceType").value("SEED"));

        // 목록에서 사라져도 SEED 계좌는 해지 처리하지 않는다
        given(ssafyFinanceClient.inquireDemandDepositAccountList(USER_KEY)).willReturn(List.of());
        syncAccounts().andExpect(jsonPath("$.data.results[0].syncedCount").value(0));
        mockMvc.perform(get("/api/v1/accounts/" + SEED_ACCOUNT_NO).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void emptyFinanceListCompletesWithZero() throws Exception {
        given(ssafyFinanceClient.inquireDemandDepositAccountList(USER_KEY)).willReturn(List.of());

        syncAccounts()
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.results[0].syncedCount").value(0));
    }

    @Test
    void financeApiErrorFailsOnlyAccountScopeWithReadableMessage() throws Exception {
        given(ssafyFinanceClient.inquireDemandDepositAccountList(USER_KEY))
                .willThrow(new SsafyApiException("H1009", "USER_KEY가 유효하지 않습니다."));

        syncAccounts()
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.results[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.results[0].message")
                        .value("금융망 처리 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM account", Long.class)).isZero();
    }

    @Test
    void financeUnavailableFailsAccountScope() throws Exception {
        given(ssafyFinanceClient.inquireDemandDepositAccountList(USER_KEY))
                .willThrow(new SsafyUnavailableException("timeout", null));

        syncAccounts()
                .andExpect(jsonPath("$.data.results[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.results[0].message")
                        .value("금융망에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."));
    }

    @Test
    void malformedFinanceEntryFailsWithoutPartialWrite() throws Exception {
        given(ssafyFinanceClient.inquireDemandDepositAccountList(USER_KEY)).willReturn(List.of(
                financeAccount("001", "한국은행", MAIN_ACCOUNT_NO, "정상 계좌", 1_000L, "20260910"),
                financeAccount("004", "국민은행", SECOND_ACCOUNT_NO, "날짜 깨진 계좌", 1_000L, "2026-09-10")
        ));

        syncAccounts()
                .andExpect(jsonPath("$.data.results[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.results[0].message")
                        .value("금융망에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM account", Long.class)).isZero();
    }

    // ---------- S15P21D105-502 거래내역 적재 ----------

    @Test
    void insertsFinanceTransactionsForSyncedAccountsAndReportsCounts() throws Exception {
        given(ssafyFinanceClient.inquireDemandDepositAccountList(USER_KEY)).willReturn(List.of(
                financeAccount("001", "한국은행", MAIN_ACCOUNT_NO, "한국은행 수시입출금", 1_488_000L, "20260910")
        ));
        given(ssafyFinanceClient.inquireTransactionHistoryList(eq(USER_KEY), eq(MAIN_ACCOUNT_NO), any(), any()))
                .willReturn(new SsafyTransactionHistory(2L, List.of(
                        financeTransaction("1001", "20260910", "123045", "2", 12_000L, 1_488_000L, "스타벅스", ""),
                        financeTransaction("1000", "20260905", "090000", "1", 1_500_000L, 1_500_000L, "급여", "9월 급여")
                )));

        syncAccounts()
                .andExpect(jsonPath("$.data.results[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.results[0].syncedCount").value(3))
                .andExpect(jsonPath("$.data.results[0].message").value("계좌 1건, 거래 2건 반영"));

        mockMvc.perform(get("/api/v1/accounts/" + MAIN_ACCOUNT_NO + "/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].transactionId").value("1001"))
                .andExpect(jsonPath("$.data.content[0].transactionType").value("EXPENSE"))
                .andExpect(jsonPath("$.data.content[0].transactionDate").value("2026-09-10"))
                .andExpect(jsonPath("$.data.content[0].transactionTime").value("12:30:45"))
                .andExpect(jsonPath("$.data.content[0].amount").value(12000))
                .andExpect(jsonPath("$.data.content[0].balanceAfter").value(1488000))
                .andExpect(jsonPath("$.data.content[0].summary").value("스타벅스"))
                .andExpect(jsonPath("$.data.content[0].memo").doesNotExist())
                .andExpect(jsonPath("$.data.content[1].transactionId").value("1000"))
                .andExpect(jsonPath("$.data.content[1].transactionType").value("INCOME"))
                .andExpect(jsonPath("$.data.content[1].memo").value("9월 급여"));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT payment_method, source_type, hidden, category_id FROM `transaction` WHERE transaction_id = '1001'");
        assertThat(row.get("payment_method")).isEqualTo("ACCOUNT");
        assertThat(row.get("source_type")).isEqualTo("SSAFY");
        assertThat(row.get("hidden")).isEqualTo(false);
        assertThat(row.get("category_id")).isNull();
    }

    @Test
    void ownAccountTransfersRemainInAccountHistoryButDisappearFromLedgerAndSpending() throws Exception {
        given(ssafyFinanceClient.inquireDemandDepositAccountList(USER_KEY)).willReturn(List.of(
                financeAccount("001", "한국은행", MAIN_ACCOUNT_NO, "주거래", 900_000L, "20260910"),
                financeAccount("004", "국민은행", SECOND_ACCOUNT_NO, "저축", 100_000L, "20260910")
        ));
        given(ssafyFinanceClient.inquireTransactionHistoryList(eq(USER_KEY), eq(MAIN_ACCOUNT_NO), any(), any()))
                .willReturn(new SsafyTransactionHistory(2L, List.of(
                        financeTransaction("own-out", "20260910", "100000", "2", 100_000L, 900_000L,
                                "이체", "", SECOND_ACCOUNT_NO),
                        financeTransaction("external-out", "20260910", "110000", "2", 20_000L, 880_000L,
                                "송금", "", "9999999999999999")
                )));
        given(ssafyFinanceClient.inquireTransactionHistoryList(eq(USER_KEY), eq(SECOND_ACCOUNT_NO), any(), any()))
                .willReturn(new SsafyTransactionHistory(1L, List.of(
                        financeTransaction("own-in", "20260910", "100001", "1", 100_000L, 100_000L,
                                "이체 입금", "", MAIN_ACCOUNT_NO)
                )));

        syncAccounts().andExpect(jsonPath("$.data.results[0].status").value("COMPLETED"));

        mockMvc.perform(get("/api/v1/accounts/" + MAIN_ACCOUNT_NO + "/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mockMvc.perform(get("/api/v1/accounts/" + SECOND_ACCOUNT_NO + "/transactions")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get("/api/v1/transactions")
                        .param("startDate", "2026-09-01").param("endDate", "2026-09-10")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].transactionId").value("external-out"));
        mockMvc.perform(get("/api/v1/transactions/own-out")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/spending/monthly-summary")
                        .param("yearMonth", "2026-09").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.data.totalSpending").value(20_000));
        mockMvc.perform(get("/api/v1/spending/saving-ratio")
                        .param("yearMonth", "2026-09").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.data.income").value(0))
                .andExpect(jsonPath("$.data.savingTransfer").value(0));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `transaction` WHERE user_id = ? AND internal_transfer = TRUE",
                Long.class, userId)).isEqualTo(2L);
    }

    @Test
    void laterDiscoveryOfOwnedCounterpartyReclassifiesExistingTransactionWithoutOverwritingEdits() throws Exception {
        SsafyAccountTransaction transfer = financeTransaction("late-own", "20260910", "100000", "2",
                100_000L, 900_000L, "이체", "금융망 메모", SECOND_ACCOUNT_NO);
        given(ssafyFinanceClient.inquireDemandDepositAccountList(USER_KEY)).willReturn(List.of(
                financeAccount("001", "한국은행", MAIN_ACCOUNT_NO, "주거래", 900_000L, "20260910")
        ));
        given(ssafyFinanceClient.inquireTransactionHistoryList(eq(USER_KEY), eq(MAIN_ACCOUNT_NO), any(), any()))
                .willReturn(new SsafyTransactionHistory(1L, List.of(transfer)));
        syncAccounts();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT internal_transfer FROM `transaction` WHERE transaction_id = 'late-own'", Boolean.class))
                .isFalse();
        jdbcTemplate.update("UPDATE `transaction` SET memo = '사용자 메모' WHERE transaction_id = 'late-own'");

        given(ssafyFinanceClient.inquireDemandDepositAccountList(USER_KEY)).willReturn(List.of(
                financeAccount("001", "한국은행", MAIN_ACCOUNT_NO, "주거래", 900_000L, "20260910"),
                financeAccount("004", "국민은행", SECOND_ACCOUNT_NO, "저축", 100_000L, "20260910")
        ));
        syncAccounts();

        assertThat(jdbcTemplate.queryForMap(
                "SELECT internal_transfer, memo FROM `transaction` WHERE transaction_id = 'late-own'"))
                .containsEntry("INTERNAL_TRANSFER", true)
                .containsEntry("MEMO", "사용자 메모");
        mockMvc.perform(get("/api/v1/transactions")
                        .param("startDate", "2026-09-01").param("endDate", "2026-09-10")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    void neverOverwritesExistingTransactionsAndOnlyInsertsNewOnes() throws Exception {
        given(ssafyFinanceClient.inquireDemandDepositAccountList(USER_KEY)).willReturn(List.of(
                financeAccount("001", "한국은행", MAIN_ACCOUNT_NO, "한국은행 수시입출금", 1_488_000L, "20260910")
        ));
        given(ssafyFinanceClient.inquireTransactionHistoryList(eq(USER_KEY), eq(MAIN_ACCOUNT_NO), any(), any()))
                .willReturn(new SsafyTransactionHistory(1L, List.of(
                        financeTransaction("1000", "20260905", "090000", "1", 1_500_000L, 1_500_000L, "급여", "9월 급여")
                )));
        syncAccounts().andExpect(jsonPath("$.data.results[0].message").value("계좌 1건, 거래 1건 반영"));

        // 사용자가 메모를 바꾼 뒤 금융망이 같은 거래를 다른 메모로 다시 주더라도 덮어쓰지 않는다
        jdbcTemplate.update("UPDATE `transaction` SET memo = '내가 고친 메모', hidden = TRUE WHERE transaction_id = '1000'");
        given(ssafyFinanceClient.inquireTransactionHistoryList(eq(USER_KEY), eq(MAIN_ACCOUNT_NO), any(), any()))
                .willReturn(new SsafyTransactionHistory(2L, List.of(
                        financeTransaction("1001", "20260910", "123045", "2", 12_000L, 1_488_000L, "스타벅스", ""),
                        financeTransaction("1000", "20260905", "090000", "1", 1_500_000L, 1_500_000L, "급여", "금융망 메모")
                )));
        syncAccounts().andExpect(jsonPath("$.data.results[0].message").value("계좌 0건, 거래 1건 반영"));

        mockMvc.perform(get("/api/v1/accounts/" + MAIN_ACCOUNT_NO + "/transactions/1000")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.memo").value("내가 고친 메모"))
                .andExpect(jsonPath("$.data.hidden").value(true));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `transaction`", Long.class)).isEqualTo(2L);
    }

    @Test
    void skipsTransactionsForSeedAccountsAndFailsWholeScopeOnBrokenTransaction() throws Exception {
        insertSeedAccount(SEED_ACCOUNT_NO, 9_000_000L);
        given(ssafyFinanceClient.inquireDemandDepositAccountList(USER_KEY)).willReturn(List.of(
                financeAccount("004", "국민은행", SEED_ACCOUNT_NO, "금융망 이름", 1L, "20260910")
        ));
        given(ssafyFinanceClient.inquireTransactionHistoryList(eq(USER_KEY), eq(SEED_ACCOUNT_NO), any(), any()))
                .willReturn(new SsafyTransactionHistory(1L, List.of(
                        financeTransaction("2000", "20260905", "090000", "1", 1_000L, 1_000L, "seed 계좌 거래", "")
                )));
        syncAccounts().andExpect(jsonPath("$.data.results[0].message").value("계좌 0건, 거래 0건 반영"));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `transaction`", Long.class)).isZero();

        given(ssafyFinanceClient.inquireDemandDepositAccountList(USER_KEY)).willReturn(List.of(
                financeAccount("001", "한국은행", MAIN_ACCOUNT_NO, "한국은행 수시입출금", 1_000L, "20260910")
        ));
        given(ssafyFinanceClient.inquireTransactionHistoryList(eq(USER_KEY), eq(MAIN_ACCOUNT_NO), any(), any()))
                .willReturn(new SsafyTransactionHistory(1L, List.of(
                        financeTransaction("3000", "20260905", "090000", "9", 1_000L, 1_000L, "알 수 없는 유형", "")
                )));
        syncAccounts()
                .andExpect(jsonPath("$.data.results[0].status").value("FAILED"))
                .andExpect(jsonPath("$.data.results[0].message")
                        .value("금융망에 연결할 수 없습니다. 잠시 후 다시 시도해 주세요."));
        // 계좌도 함께 롤백된다(부분 반영 없음)
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM account WHERE account_no_hash = ?", Long.class,
                sensitiveNumberHasher.hash(MAIN_ACCOUNT_NO))).isZero();
    }

    private static SsafyAccountTransaction financeTransaction(
            String uniqueNo, String date, String time, String type, long amount, long afterBalance,
            String summary, String memo
    ) {
        return financeTransaction(uniqueNo, date, time, type, amount, afterBalance, summary, memo, "");
    }

    private static SsafyAccountTransaction financeTransaction(
            String uniqueNo, String date, String time, String type, long amount, long afterBalance,
            String summary, String memo, String counterpartyAccountNo
    ) {
        return new SsafyAccountTransaction(uniqueNo, date, time, type, type.equals("1") ? "입금" : "출금",
                counterpartyAccountNo,
                amount, afterBalance, summary, memo);
    }

    // ---------- helpers ----------

    private org.springframework.test.web.servlet.ResultActions syncAccounts() throws Exception {
        return mockMvc.perform(post("/api/v1/finance-data/sync")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("scope", "ACCOUNT"))))
                .andExpect(status().isCreated());
    }

    private LocalDateTime updatedAtOf(String accountNo) {
        return jdbcTemplate.queryForObject(
                "SELECT updated_at FROM account WHERE account_no_hash = ?",
                LocalDateTime.class,
                sensitiveNumberHasher.hash(accountNo));
    }

    private void insertSeedAccount(String accountNo, long balance) {
        jdbcTemplate.update(
                """
                INSERT INTO account (
                    account_id, user_id, bank_code, bank_name, account_no_encrypted, account_no_hash,
                    account_no_masked, account_name, account_type, status, balance, last_transaction_date,
                    currency, source_type, created_at, updated_at
                ) VALUES (11, ?, '004', 'KB국민은행', X'00', ?, '123456-**-**3456', 'KB 주거래통장',
                          'DEMAND_DEPOSIT', 'ACTIVE', ?, DATE '2026-09-01', 'KRW', 'SEED',
                          TIMESTAMP '2026-09-07 09:00:00', TIMESTAMP '2026-09-07 09:00:00')
                """,
                userId,
                sensitiveNumberHasher.hash(accountNo),
                balance
        );
    }

    private static SsafyDemandDepositAccount financeAccount(
            String bankCode,
            String bankName,
            String accountNo,
            String accountName,
            long balance,
            String lastTransactionDate
    ) {
        return new SsafyDemandDepositAccount(bankCode, bankName, "도담", accountNo, accountName, "1", "수시입출금",
                "20260901", "20310901", 500_000_000L, 100_000_000L, balance, lastTransactionDate, "KRW");
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", EMAIL, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
