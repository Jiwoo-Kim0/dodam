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
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyFinanceClient;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyMember;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyUnavailableException;
import java.time.LocalDateTime;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Import(FinanceDataApiIntegrationTest.SyncHandlerStubs.class)
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
    private FieldCryptor fieldCryptor;

    @MockitoBean
    private SsafyFinanceClient ssafyFinanceClient;

    private Long userId;
    private String bearer;

    @BeforeEach
    void setUp() throws Exception {
        authSessionRepository.deleteAll();
        financeSyncRepository.deleteAll();
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
