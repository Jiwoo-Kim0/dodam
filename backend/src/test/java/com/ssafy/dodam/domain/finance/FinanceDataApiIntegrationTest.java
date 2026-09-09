package com.ssafy.dodam.domain.finance;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.ssafy.dodam.domain.finance.repository.FinanceConnectionRepository;
import com.ssafy.dodam.domain.user.repository.FinancialProfileRepository;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import com.ssafy.dodam.domain.user.repository.UserSettingRepository;
import com.ssafy.dodam.global.crypto.FieldCryptor;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyApiException;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyFinanceClient;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyMember;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyUnavailableException;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
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
    private FieldCryptor fieldCryptor;

    @MockitoBean
    private SsafyFinanceClient ssafyFinanceClient;

    private Long userId;
    private String bearer;

    @BeforeEach
    void setUp() throws Exception {
        authSessionRepository.deleteAll();
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
