package com.ssafy.dodam.domain.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyMember;
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
