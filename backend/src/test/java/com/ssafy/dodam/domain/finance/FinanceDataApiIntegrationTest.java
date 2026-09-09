package com.ssafy.dodam.domain.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.auth.repository.AuthSessionRepository;
import com.ssafy.dodam.domain.finance.entity.FinanceConnection;
import com.ssafy.dodam.domain.finance.entity.FinanceConnectionStatus;
import com.ssafy.dodam.domain.finance.entity.FinanceSync;
import com.ssafy.dodam.domain.finance.entity.FinanceSyncScope;
import com.ssafy.dodam.domain.finance.entity.FinanceSyncStatus;
import com.ssafy.dodam.domain.finance.repository.FinanceConnectionRepository;
import com.ssafy.dodam.domain.finance.repository.FinanceSyncRepository;
import com.ssafy.dodam.domain.finance.service.FinanceSyncScopeHandler;
import com.ssafy.dodam.domain.finance.service.FinanceSyncScopeResult;
import com.ssafy.dodam.domain.finance.service.FinanceSyncService;
import com.ssafy.dodam.domain.user.repository.FinancialProfileRepository;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import com.ssafy.dodam.domain.user.repository.UserSettingRepository;
import com.ssafy.dodam.global.crypto.FieldCryptor;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyApiException;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyCreditRating;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyCreditRatingCriterion;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Import(FinanceDataApiIntegrationTest.SyncHandlerStubs.class)
@Sql(scripts = "/sql/finance-summary-test-schema.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class FinanceDataApiIntegrationTest {

    private static final String EMAIL = "dodam@example.com";
    private static final String PASSWORD = "password123";
    private static final String NAME = "도담";
    private static final String USER_KEY = "cf1d49ba-user-key";
    private static final String NEW_USER_KEY = "cf1d49ba-user-key-reissued";

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
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private FinanceSyncRepository financeSyncRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FieldCryptor fieldCryptor;

    @MockitoBean
    private SsafyFinanceClient ssafyFinanceClient;

    private Long userId;
    private String bearer;

    @BeforeEach
    void setUp() throws Exception {
        authSessionRepository.deleteAll();
        financeSyncRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM card");
        jdbcTemplate.update("DELETE FROM loan");
        jdbcTemplate.update("DELETE FROM account");
        financeConnectionRepository.deleteAll();
        userSettingRepository.deleteAll();
        financialProfileRepository.deleteAll();
        userRepository.deleteAll();

        given(ssafyFinanceClient.createMember(anyString())).willReturn(member(USER_KEY));
        given(ssafyFinanceClient.searchMember(anyString())).willReturn(member(USER_KEY));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", EMAIL, "password", PASSWORD, "name", NAME))))
                .andExpect(status().isCreated());
        userId = userRepository.findByEmailAndDeletedAtIsNull(EMAIL).orElseThrow().getId();
        bearer = login();
    }

    // ---------- S15P21D105-498 연동 상태 조회 ----------

    @Test
    void getConnectionReturnsConnectedStateAfterSignup() throws Exception {
        mockMvc.perform(get("/api/v1/finance-data/connection").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONNECTED"))
                .andExpect(jsonPath("$.data.memberEmailMasked").value("do***@example.com"))
                .andExpect(jsonPath("$.data.connectedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.disconnectedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.lastSyncedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.userKey").doesNotExist())
                .andExpect(jsonPath("$.data.externalUserKeyHash").doesNotExist());
    }

    @Test
    void getConnectionIsPendingWhenNoConnectionRowExists() throws Exception {
        financeConnectionRepository.deleteAll();

        mockMvc.perform(get("/api/v1/finance-data/connection").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.memberEmailMasked").value(nullValue()));
    }

    @Test
    void financeDataEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/finance-data/connection"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mockMvc.perform(post("/api/v1/finance-data/connection"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/v1/finance-data/member"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- S15P21D105-498 금융망 사용자 등록 ----------

    @Test
    void connectIsRejectedWhenAlreadyConnectedWithoutCallingFinanceNetwork() throws Exception {
        mockMvc.perform(post("/api/v1/finance-data/connection").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FINANCE_ALREADY_CONNECTED"));

        // 회원가입 때 1회 호출된 것 외에 추가 호출이 없어야 한다
        verify(ssafyFinanceClient).createMember(EMAIL);
        verify(ssafyFinanceClient, never()).searchMember(anyString());
    }

    @Test
    void connectRecoversFailedConnectionFromSignup() throws Exception {
        markConnectionFailed();
        given(ssafyFinanceClient.createMember(anyString())).willReturn(member(NEW_USER_KEY));

        mockMvc.perform(post("/api/v1/finance-data/connection").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CONNECTED"))
                .andExpect(jsonPath("$.data.memberEmailMasked").value("do***@example.com"))
                .andExpect(jsonPath("$.data.connectedAt").isNotEmpty());

        FinanceConnection connection = financeConnectionRepository.findByUserId(userId).orElseThrow();
        assertThat(connection.getStatus()).isEqualTo(FinanceConnectionStatus.CONNECTED);
        assertThat(fieldCryptor.decrypt(connection.getExternalUserKeyEncrypted())).isEqualTo(NEW_USER_KEY);
        assertThat(connection.getExternalUserKeyHash()).isEqualTo(fieldCryptor.hash(NEW_USER_KEY));
        assertThat(financeConnectionRepository.count()).isEqualTo(1);
    }

    @Test
    void connectCreatesConnectionRowWhenNoneExists() throws Exception {
        financeConnectionRepository.deleteAll();

        mockMvc.perform(post("/api/v1/finance-data/connection").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CONNECTED"));

        FinanceConnection connection = financeConnectionRepository.findByUserId(userId).orElseThrow();
        assertThat(connection.getMemberEmailHash()).isEqualTo(fieldCryptor.hash(EMAIL));
        assertThat(fieldCryptor.decrypt(connection.getExternalUserKeyEncrypted())).isEqualTo(USER_KEY);
    }

    @Test
    void connectReusesMemberWhenFinanceNetworkAlreadyHasOurUser() throws Exception {
        markConnectionFailed();
        given(ssafyFinanceClient.createMember(anyString()))
                .willThrow(new SsafyApiException("E4002", "이미 존재하는 ID입니다."));
        given(ssafyFinanceClient.searchMember(anyString())).willReturn(member(USER_KEY));

        mockMvc.perform(post("/api/v1/finance-data/connection").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CONNECTED"));
    }

    @Test
    void connectIsRejectedWhenEmailBelongsToAnotherService() throws Exception {
        markConnectionFailed();
        given(ssafyFinanceClient.createMember(anyString()))
                .willThrow(new SsafyApiException("E4002", "이미 존재하는 ID입니다."));
        given(ssafyFinanceClient.searchMember(anyString()))
                .willThrow(new SsafyApiException("E4003", "존재하지 않는 ID입니다."));

        mockMvc.perform(post("/api/v1/finance-data/connection").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FINANCE_EMAIL_ALREADY_REGISTERED"));

        assertThat(connectionStatus()).isEqualTo(FinanceConnectionStatus.FAILED);
    }

    @Test
    void connectReportsUnavailableWhenFinanceNetworkIsDownAndKeepsState() throws Exception {
        markConnectionFailed();
        given(ssafyFinanceClient.createMember(anyString()))
                .willThrow(new SsafyUnavailableException("timeout", null));

        mockMvc.perform(post("/api/v1/finance-data/connection").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FINANCE_API_UNAVAILABLE"));

        assertThat(connectionStatus()).isEqualTo(FinanceConnectionStatus.FAILED);
    }

    @Test
    void connectReportsGatewayErrorOnUnexpectedFinanceNetworkResponse() throws Exception {
        markConnectionFailed();
        given(ssafyFinanceClient.createMember(anyString()))
                .willThrow(new SsafyApiException("E4004", "유효하지 않은 API KEY입니다."));

        mockMvc.perform(post("/api/v1/finance-data/connection").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("FINANCE_API_ERROR"));
    }

    // ---------- S15P21D105-498 금융망 사용자 조회 ----------

    @Test
    void getMemberReturnsFinanceNetworkAccountWithoutUserKey() throws Exception {
        mockMvc.perform(get("/api/v1/finance-data/member").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value(EMAIL))
                .andExpect(jsonPath("$.data.userName").value("dodam"))
                .andExpect(jsonPath("$.data.institutionCode").value("00100"))
                .andExpect(jsonPath("$.data.connectedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.userKey").doesNotExist());

        // 저장된 이메일 원문을 복호화해 조회해야 한다
        verify(ssafyFinanceClient).searchMember(EMAIL);
    }

    @Test
    void getMemberRequiresConnectedState() throws Exception {
        markConnectionFailed();

        mockMvc.perform(get("/api/v1/finance-data/member").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FINANCE_NOT_CONNECTED"));

        verify(ssafyFinanceClient, never()).searchMember(anyString());
    }

    @Test
    void getMemberTranslatesFinanceNetworkErrors() throws Exception {
        given(ssafyFinanceClient.searchMember(anyString()))
                .willThrow(new SsafyApiException("E4003", "존재하지 않는 ID입니다."));
        mockMvc.perform(get("/api/v1/finance-data/member").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("FINANCE_API_ERROR"));

        // 이미 예외를 던지도록 stubbing된 mock은 given(mock.call()) 형태로 다시 stubbing할 수 없다
        willThrow(new SsafyUnavailableException("timeout", null)).given(ssafyFinanceClient).searchMember(anyString());
        mockMvc.perform(get("/api/v1/finance-data/member").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FINANCE_API_UNAVAILABLE"));
    }

    // ---------- S15P21D105-498 연동 해제 ----------

    @Test
    void disconnectMarksConnectionDisconnectedAndIsVisibleOnUserProfile() throws Exception {
        mockMvc.perform(delete("/api/v1/finance-data/connection").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISCONNECTED"))
                .andExpect(jsonPath("$.data.disconnectedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.financeConnectionStatus").value("DISCONNECTED"));
    }

    @Test
    void disconnectTwiceIsRejected() throws Exception {
        mockMvc.perform(delete("/api/v1/finance-data/connection").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/v1/finance-data/connection").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FINANCE_NOT_CONNECTED"));
    }

    @Test
    void disconnectWithoutConnectionRowIsNotFound() throws Exception {
        financeConnectionRepository.deleteAll();

        mockMvc.perform(delete("/api/v1/finance-data/connection").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FINANCE_CONNECTION_NOT_FOUND"));
    }

    @Test
    void reconnectAfterDisconnectRestoresConnectedStateAndKeepsDisconnectHistory() throws Exception {
        mockMvc.perform(delete("/api/v1/finance-data/connection").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
        given(ssafyFinanceClient.createMember(anyString()))
                .willThrow(new SsafyApiException("E4002", "이미 존재하는 ID입니다."));
        given(ssafyFinanceClient.searchMember(anyString())).willReturn(member(USER_KEY));

        mockMvc.perform(post("/api/v1/finance-data/connection").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("CONNECTED"))
                .andExpect(jsonPath("$.data.connectedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.disconnectedAt").isNotEmpty());

        mockMvc.perform(get("/api/v1/finance-data/member").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());
    }

    // ---------- S15P21D105-499 금융 데이터 동기화 ----------

    @Test
    void syncAllRunsEveryScopeAndReportsPerScopeResults() throws Exception {
        mockMvc.perform(post("/api/v1/finance-data/sync")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.syncId").isNumber())
                .andExpect(jsonPath("$.data.scope").value("ALL"))
                // LOAN 스텁이 실패하므로 전체는 FAILED, 나머지 3개는 처리됨 → 75%
                .andExpect(jsonPath("$.data.status").value("FAILED"))
                .andExpect(jsonPath("$.data.progress").value(75))
                .andExpect(jsonPath("$.data.errorMessage").value(containsString("LOAN")))
                .andExpect(jsonPath("$.data.startedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.completedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.results", hasSize(4)))
                .andExpect(jsonPath("$.data.results[0].scope").value("ACCOUNT"))
                .andExpect(jsonPath("$.data.results[0].status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.results[0].syncedCount").value(3))
                .andExpect(jsonPath("$.data.results[1].scope").value("CARD"))
                .andExpect(jsonPath("$.data.results[1].status").value("SKIPPED"))
                .andExpect(jsonPath("$.data.results[2].scope").value("LOAN"))
                .andExpect(jsonPath("$.data.results[2].status").value("FAILED"))
                .andExpect(jsonPath("$.data.results[2].message").value("동기화 처리 중 오류가 발생했습니다."))
                .andExpect(jsonPath("$.data.results[3].scope").value("SAVING"))
                .andExpect(jsonPath("$.data.results[3].status").value("SKIPPED"));

        // ACCOUNT가 실제로 반영됐으므로 last_synced_at은 갱신된다
        assertThat(financeConnectionRepository.findByUserId(userId).orElseThrow().getLastSyncedAt()).isNotNull();
        assertThat(financeSyncRepository.count()).isEqualTo(1);
    }

    @Test
    void syncSingleScopeCompletesAndIsReportedAsLatest() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/finance-data/sync")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("scope", "account"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.scope").value("ACCOUNT"))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.progress").value(100))
                .andExpect(jsonPath("$.data.errorMessage").value(nullValue()))
                .andExpect(jsonPath("$.data.results", hasSize(1)))
                .andReturn();
        long syncId = readData(result).path("syncId").asLong();

        mockMvc.perform(get("/api/v1/finance-data/sync/status").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inProgress").value(false))
                .andExpect(jsonPath("$.data.sync.syncId").value(syncId))
                .andExpect(jsonPath("$.data.sync.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.sync.results").value(nullValue()));

        mockMvc.perform(get("/api/v1/finance-data/sync/latest").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastSyncedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.lastCompletedSync.syncId").value(syncId));

        mockMvc.perform(get("/api/v1/finance-data/connection").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.data.lastSyncedAt").isNotEmpty());
    }

    @Test
    void syncWithOnlySkippedScopesDoesNotUpdateLastSyncedAt() throws Exception {
        mockMvc.perform(post("/api/v1/finance-data/sync")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("scope", "SAVING"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.results[0].status").value("SKIPPED"));

        mockMvc.perform(get("/api/v1/finance-data/sync/latest").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.data.lastSyncedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.lastCompletedSync.scope").value("SAVING"));
    }

    @Test
    void syncWithoutBodyDefaultsToAllScopes() throws Exception {
        mockMvc.perform(post("/api/v1/finance-data/sync").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.scope").value("ALL"))
                .andExpect(jsonPath("$.data.results", hasSize(4)));
    }

    @Test
    void syncRejectsUnknownScope() throws Exception {
        mockMvc.perform(post("/api/v1/finance-data/sync")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("scope", "CRYPTO"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        assertThat(financeSyncRepository.count()).isZero();
    }

    @Test
    void syncRequiresConnectedFinanceNetwork() throws Exception {
        markConnectionFailed();

        mockMvc.perform(post("/api/v1/finance-data/sync").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FINANCE_NOT_CONNECTED"));

        assertThat(financeSyncRepository.count()).isZero();
    }

    @Test
    void syncIsRejectedWhileAnotherSyncIsRunning() throws Exception {
        financeSyncRepository.save(FinanceSync.start(userId, FinanceSyncScope.ALL, LocalDateTime.now()));

        mockMvc.perform(post("/api/v1/finance-data/sync").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FINANCE_SYNC_IN_PROGRESS"));

        mockMvc.perform(get("/api/v1/finance-data/sync/status").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.data.inProgress").value(true))
                .andExpect(jsonPath("$.data.sync.status").value("RUNNING"));

        assertThat(financeSyncRepository.count()).isEqualTo(1);
    }

    @Test
    void staleRunningSyncIsExpiredAndNewSyncStarts() throws Exception {
        FinanceSync stale = financeSyncRepository.save(FinanceSync.start(
                userId, FinanceSyncScope.ALL, LocalDateTime.now().minus(FinanceSyncService.STALE_AFTER).minusMinutes(1)
        ));

        mockMvc.perform(get("/api/v1/finance-data/sync/status").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(jsonPath("$.data.inProgress").value(false));

        mockMvc.perform(post("/api/v1/finance-data/sync")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("scope", "ACCOUNT"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));

        FinanceSync expired = financeSyncRepository.findById(stale.getId()).orElseThrow();
        assertThat(expired.getStatus()).isEqualTo(FinanceSyncStatus.FAILED);
        assertThat(expired.getErrorMessage()).contains("만료");
        assertThat(financeSyncRepository.count()).isEqualTo(2);
    }

    @Test
    void disconnectIsRejectedWhileSyncIsRunning() throws Exception {
        financeSyncRepository.save(FinanceSync.start(userId, FinanceSyncScope.ALL, LocalDateTime.now()));

        mockMvc.perform(delete("/api/v1/finance-data/connection").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FINANCE_SYNC_IN_PROGRESS"));

        assertThat(connectionStatus()).isEqualTo(FinanceConnectionStatus.CONNECTED);
    }

    @Test
    void syncStatusAndLatestAreEmptyWithoutHistory() throws Exception {
        mockMvc.perform(get("/api/v1/finance-data/sync/status").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.inProgress").value(false))
                .andExpect(jsonPath("$.data.sync").value(nullValue()));

        mockMvc.perform(get("/api/v1/finance-data/sync/latest").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lastSyncedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.lastCompletedSync").value(nullValue()));
    }

    @Test
    void syncHistoryIsIsolatedPerUser() throws Exception {
        mockMvc.perform(post("/api/v1/finance-data/sync").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isCreated());

        given(ssafyFinanceClient.createMember(anyString()))
                .willReturn(new SsafyMember("other@example.com", "other", "00100", "other-key"));
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "other@example.com", "password", PASSWORD, "name", "타인"))))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", "other@example.com", "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        String otherBearer = "Bearer " + readData(login).path("accessToken").asText();

        mockMvc.perform(get("/api/v1/finance-data/sync/status").header(HttpHeaders.AUTHORIZATION, otherBearer))
                .andExpect(jsonPath("$.data.sync").value(nullValue()));
    }

    /**
     * 동기화 확장점 검증용 스텁. ACCOUNT는 3건 성공, LOAN은 예외, CARD·SAVING은 미등록(SKIPPED).
     */
    @TestConfiguration
    static class SyncHandlerStubs {

        @Bean
        FinanceSyncScopeHandler accountSyncHandlerStub() {
            return new FinanceSyncScopeHandler() {
                @Override
                public FinanceSyncScope scope() {
                    return FinanceSyncScope.ACCOUNT;
                }

                @Override
                public FinanceSyncScopeResult sync(Long userId, String userKey) {
                    assertThat(userKey).isNotBlank();
                    return FinanceSyncScopeResult.completed(FinanceSyncScope.ACCOUNT, 3);
                }
            };
        }

        @Bean
        FinanceSyncScopeHandler loanSyncHandlerStub() {
            return new FinanceSyncScopeHandler() {
                @Override
                public FinanceSyncScope scope() {
                    return FinanceSyncScope.LOAN;
                }

                @Override
                public FinanceSyncScopeResult sync(Long userId, String userKey) {
                    throw new IllegalStateException("loan sync stub failure");
                }
            };
        }
    }

    // ---------- S15P21D105-500 금융 데이터 통합 요약 ----------

    @Test
    void summaryAggregatesOwnAccountsCardsAndLoansOnly() throws Exception {
        long otherUserId = userId + 1000;
        insertAccount(userId, "DEMAND_DEPOSIT", "ACTIVE", 1_500_000);
        insertAccount(userId, "DEMAND_DEPOSIT", "INACTIVE", 200_000);
        insertAccount(userId, "DEPOSIT", "ACTIVE", 10_000_000);
        insertAccount(userId, "SAVINGS", "ACTIVE", 3_000_000);
        insertAccount(userId, "MINUS", "ACTIVE", -800_000);
        insertAccount(userId, "DEMAND_DEPOSIT", "CLOSED", 999_999);
        insertAccount(otherUserId, "DEMAND_DEPOSIT", "ACTIVE", 77_000_000);
        insertCard(userId, "ACTIVE", 3_000_000);
        insertCard(userId, "ACTIVE", 2_000_000);
        insertCard(userId, "CANCELED", 9_000_000);
        insertCard(otherUserId, "ACTIVE", 5_000_000);
        insertLoan(userId, "ACTIVE", 20_000_000);
        insertLoan(userId, "OVERDUE", 5_000_000);
        insertLoan(userId, "COMPLETED", 0);
        insertLoan(otherUserId, "ACTIVE", 30_000_000);

        mockMvc.perform(get("/api/v1/finance-data/summary").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connection.status").value("CONNECTED"))
                .andExpect(jsonPath("$.data.connection.lastSyncedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.accounts.count").value(5))
                .andExpect(jsonPath("$.data.accounts.demandDepositBalance").value(1_700_000))
                .andExpect(jsonPath("$.data.accounts.depositBalance").value(10_000_000))
                .andExpect(jsonPath("$.data.accounts.savingsBalance").value(3_000_000))
                .andExpect(jsonPath("$.data.accounts.minusAccountCount").value(1))
                .andExpect(jsonPath("$.data.accounts.minusUsedAmount").value(800_000))
                .andExpect(jsonPath("$.data.cards.activeCount").value(2))
                .andExpect(jsonPath("$.data.cards.totalCount").value(3))
                .andExpect(jsonPath("$.data.cards.totalCreditLimit").value(5_000_000))
                .andExpect(jsonPath("$.data.loans.outstandingCount").value(2))
                .andExpect(jsonPath("$.data.loans.overdueCount").value(1))
                .andExpect(jsonPath("$.data.loans.totalBalance").value(25_000_000))
                .andExpect(jsonPath("$.data.totals.cashAssets").value(14_700_000))
                .andExpect(jsonPath("$.data.totals.liabilities").value(25_800_000))
                .andExpect(jsonPath("$.data.totals.netAssets").value(-11_100_000));
    }

    @Test
    void summaryReturnsZerosWithoutFinancialDataAndWorksWhenDisconnected() throws Exception {
        mockMvc.perform(delete("/api/v1/finance-data/connection").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/finance-data/summary").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.connection.status").value("DISCONNECTED"))
                .andExpect(jsonPath("$.data.accounts.count").value(0))
                .andExpect(jsonPath("$.data.cards.totalCount").value(0))
                .andExpect(jsonPath("$.data.loans.outstandingCount").value(0))
                .andExpect(jsonPath("$.data.totals.netAssets").value(0));
    }

    // ---------- S15P21D105-500 신용등급 조회 ----------

    @Test
    void creditRatingIsFetchedWithStoredUserKey() throws Exception {
        given(ssafyFinanceClient.inquireMyCreditRating(anyString()))
                .willReturn(new SsafyCreditRating("B", 3_500_000L, 12_000_000L, 15_500_000L));

        mockMvc.perform(get("/api/v1/finance-data/credit-rating").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ratingName").value("B"))
                .andExpect(jsonPath("$.data.creditScore").doesNotExist())
                .andExpect(jsonPath("$.data.demandDepositAssetValue").value(3_500_000))
                .andExpect(jsonPath("$.data.depositSavingsAssetValue").value(12_000_000))
                .andExpect(jsonPath("$.data.totalAssetValue").value(15_500_000))
                .andExpect(jsonPath("$.data.inquiredAt").isNotEmpty());

        // 저장된 userKey 암호문을 복호화해 그대로 넘겨야 한다
        verify(ssafyFinanceClient).inquireMyCreditRating(USER_KEY);
    }

    @Test
    void creditRatingCriteriaKeepsFinanceNetworkOrderAndDoesNotNeedConnection() throws Exception {
        given(ssafyFinanceClient.inquireAssetBasedCreditRatingList()).willReturn(List.of(
                new SsafyCreditRatingCriterion("RT-0fa85f6425e811ea4", "A", 100_000_000L, 2_000_000_000L),
                new SsafyCreditRatingCriterion("RT-a335h7aa7f3x74ag5", "E", 0L, 29_999_999L)
        ));
        // 앱 단위 API이므로 금융망 연결이 FAILED여도 조회된다
        markConnectionFailed();

        mockMvc.perform(get("/api/v1/finance-data/credit-rating/criteria").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.criteria", hasSize(2)))
                .andExpect(jsonPath("$.data.criteria[0].ratingUniqueNo").value("RT-0fa85f6425e811ea4"))
                .andExpect(jsonPath("$.data.criteria[0].ratingName").value("A"))
                .andExpect(jsonPath("$.data.criteria[0].minAssetValue").value(100_000_000))
                .andExpect(jsonPath("$.data.criteria[0].maxAssetValue").value(2_000_000_000L))
                .andExpect(jsonPath("$.data.criteria[1].ratingName").value("E"))
                .andExpect(jsonPath("$.data.criteria[1].minAssetValue").value(0))
                .andExpect(jsonPath("$.data.inquiredAt").isNotEmpty());

        verify(ssafyFinanceClient).inquireAssetBasedCreditRatingList();
    }

    @Test
    void myCreditRatingRequiresConnectedStateWithoutCallingFinanceNetwork() throws Exception {
        markConnectionFailed();

        mockMvc.perform(get("/api/v1/finance-data/credit-rating").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FINANCE_NOT_CONNECTED"));

        verify(ssafyFinanceClient, never()).inquireMyCreditRating(anyString());
    }

    @Test
    void creditRatingTranslatesFinanceNetworkFailures() throws Exception {
        given(ssafyFinanceClient.inquireMyCreditRating(anyString()))
                .willThrow(new SsafyApiException("H1009", "USER_KEY가 유효하지 않습니다."));
        mockMvc.perform(get("/api/v1/finance-data/credit-rating").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("FINANCE_API_ERROR"));

        given(ssafyFinanceClient.inquireAssetBasedCreditRatingList())
                .willThrow(new SsafyUnavailableException("timeout", null));
        mockMvc.perform(get("/api/v1/finance-data/credit-rating/criteria").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("FINANCE_API_UNAVAILABLE"));
    }

    // ---------- summary fixtures ----------

    private void insertAccount(long ownerId, String type, String status, long balance) {
        jdbcTemplate.update("""
                INSERT INTO account (user_id, bank_code, bank_name, account_no_encrypted, account_no_hash,
                                     account_no_masked, account_name, account_type, status, balance, created_at, updated_at)
                VALUES (?, '001', '한국은행', X'00', ?, '110-***-1234', '테스트 계좌', ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, ownerId, uniqueHash(), type, status, balance);
    }

    private void insertCard(long ownerId, String status, long creditLimit) {
        jdbcTemplate.update("""
                INSERT INTO card (user_id, withdrawal_account_id, withdrawal_account_type, card_no_encrypted, card_no_hash,
                                  card_no_masked, issuer_code, issuer_name, card_name, status, credit_limit, created_at)
                VALUES (?, 1, 'DEMAND_DEPOSIT', X'00', ?, '1234-****-****-5678', 'KB', '국민카드', '테스트 카드', ?, ?, CURRENT_TIMESTAMP)
                """, ownerId, uniqueHash(), status, creditLimit);
    }

    private void insertLoan(long ownerId, String status, long balance) {
        jdbcTemplate.update("""
                INSERT INTO loan (user_id, withdrawal_account_id, withdrawal_account_type, institution_code, institution_name,
                                  account_no_encrypted, account_no_hash, account_no_masked, account_name, loan_type,
                                  repayment_type, status, original_principal, balance, interest_rate, created_at)
                VALUES (?, 1, 'DEMAND_DEPOSIT', '004', '국민은행', X'00', ?, '9999-***-0001', '테스트 대출', 'CREDIT',
                        'EQUAL_PAYMENT', ?, 50000000, ?, 4.5000, CURRENT_TIMESTAMP)
                """, ownerId, uniqueHash(), status, balance);
    }

    private static String uniqueHash() {
        return String.format("%064x", java.math.BigInteger.valueOf(System.nanoTime() + (long) (Math.random() * 1_000_000)));
    }

    // ---------- helpers ----------

    private static SsafyMember member(String userKey) {
        return new SsafyMember(EMAIL, "dodam", "00100", userKey);
    }

    private FinanceConnectionStatus connectionStatus() {
        return financeConnectionRepository.findByUserId(userId).orElseThrow().getStatus();
    }

    /**
     * 회원가입 시 금융망 장애로 FAILED가 된 상태를 재현한다.
     */
    private void markConnectionFailed() {
        FinanceConnection connection = financeConnectionRepository.findByUserId(userId).orElseThrow();
        financeConnectionRepository.delete(connection);
        financeConnectionRepository.flush();
        financeConnectionRepository.save(FinanceConnection.failed(
                userId,
                fieldCryptor.encrypt(EMAIL),
                fieldCryptor.hash(EMAIL),
                FieldCryptor.maskEmail(EMAIL)
        ));
    }

    private String login() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", EMAIL, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + readData(result).path("accessToken").asText();
    }

    private JsonNode readData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
