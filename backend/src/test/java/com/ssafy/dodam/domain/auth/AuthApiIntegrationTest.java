package com.ssafy.dodam.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
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
import java.time.LocalDateTime;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class AuthApiIntegrationTest {

    private static final String EMAIL = "dodam@example.com";
    private static final String PASSWORD = "password123";
    private static final String NAME = "도담";
    private static final String USER_KEY_PREFIX = "cf1d49ba-user-key-for-";

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

    @BeforeEach
    void setUp() {
        authSessionRepository.deleteAll();
        financeConnectionRepository.deleteAll();
        userSettingRepository.deleteAll();
        financialProfileRepository.deleteAll();
        userRepository.deleteAll();

        given(ssafyFinanceClient.createMember(anyString()))
                .willAnswer(invocation -> member(invocation.getArgument(0)));
    }

    // ---------- S15P21D105-494 회원가입 ----------

    @Test
    void signupCreatesUserWithDefaultsAndConnectedFinanceAccount() throws Exception {
        mockMvc.perform(signup(EMAIL, PASSWORD, NAME))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.userId").isNumber())
                .andExpect(jsonPath("$.data.user.email").value(EMAIL))
                .andExpect(jsonPath("$.data.user.name").value(NAME))
                .andExpect(jsonPath("$.data.user.role").value("USER"))
                .andExpect(jsonPath("$.data.financeConnectionStatus").value("CONNECTED"))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        Long userId = userRepository.findByEmailAndDeletedAtIsNull(EMAIL).orElseThrow().getId();
        assertThat(userSettingRepository.findById(userId)).isPresent();
        assertThat(financialProfileRepository.findById(userId)).isPresent();

        FinanceConnection connection = financeConnectionRepository.findByUserId(userId).orElseThrow();
        assertThat(connection.getStatus()).isEqualTo(FinanceConnectionStatus.CONNECTED);
        assertThat(fieldCryptor.decrypt(connection.getExternalUserKeyEncrypted())).isEqualTo(userKeyFor(EMAIL));
        assertThat(connection.getExternalUserKeyHash()).isEqualTo(fieldCryptor.hash(userKeyFor(EMAIL)));
        assertThat(connection.getMemberEmailMasked()).isEqualTo("do***@example.com");
    }

    @Test
    void signupReusesExistingFinanceMemberWhenAlreadyRegisteredByUs() throws Exception {
        given(ssafyFinanceClient.createMember(anyString()))
                .willThrow(new SsafyApiException("E4002", "이미 존재하는 ID입니다."));
        given(ssafyFinanceClient.searchMember(anyString()))
                .willAnswer(invocation -> member(invocation.getArgument(0)));

        mockMvc.perform(signup(EMAIL, PASSWORD, NAME))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.financeConnectionStatus").value("CONNECTED"));
    }

    @Test
    void signupIsRejectedWhenEmailBelongsToAnotherServiceOnFinanceNetwork() throws Exception {
        given(ssafyFinanceClient.createMember(anyString()))
                .willThrow(new SsafyApiException("E4002", "이미 존재하는 ID입니다."));
        given(ssafyFinanceClient.searchMember(anyString()))
                .willThrow(new SsafyApiException("E4003", "존재하지 않는 ID입니다."));

        mockMvc.perform(signup(EMAIL, PASSWORD, NAME))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FINANCE_EMAIL_ALREADY_REGISTERED"));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    void signupSucceedsWithFailedConnectionWhenFinanceNetworkIsDown() throws Exception {
        given(ssafyFinanceClient.createMember(anyString()))
                .willThrow(new SsafyUnavailableException("timeout", null));

        mockMvc.perform(signup(EMAIL, PASSWORD, NAME))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.financeConnectionStatus").value("FAILED"));

        Long userId = userRepository.findByEmailAndDeletedAtIsNull(EMAIL).orElseThrow().getId();
        FinanceConnection connection = financeConnectionRepository.findByUserId(userId).orElseThrow();
        assertThat(connection.getExternalUserKeyEncrypted()).isNull();
        assertThat(connection.getMemberEmailHash()).isEqualTo(fieldCryptor.hash(EMAIL));
    }

    @Test
    void signupNormalizesEmailCaseAndRejectsDuplicate() throws Exception {
        mockMvc.perform(signup(EMAIL, PASSWORD, NAME))
                .andExpect(status().isCreated());

        mockMvc.perform(signup("DODAM@Example.com", PASSWORD, "다른이름"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void signupRejectsInvalidInputWithFieldErrors() throws Exception {
        mockMvc.perform(signup("not-an-email", "short", ""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists());
    }

    // ---------- S15P21D105-494 로그인 ----------

    @Test
    void loginReturnsTokensAndCreatesSession() throws Exception {
        mockMvc.perform(signup(EMAIL, PASSWORD, NAME)).andExpect(status().isCreated());

        mockMvc.perform(login(EMAIL, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.accessTokenExpiresAt").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshTokenExpiresAt").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value(EMAIL));

        assertThat(authSessionRepository.count()).isEqualTo(1);
    }

    @Test
    void loginDoesNotRevealWhetherEmailExists() throws Exception {
        mockMvc.perform(signup(EMAIL, PASSWORD, NAME)).andExpect(status().isCreated());

        mockMvc.perform(login(EMAIL, "wrongpass1"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mockMvc.perform(login("nobody@example.com", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    // ---------- S15P21D105-494 세션 조회 ----------

    @Test
    void sessionReturnsCurrentLoginStateForBearerToken() throws Exception {
        mockMvc.perform(signup(EMAIL, PASSWORD, NAME)).andExpect(status().isCreated());
        JsonNode tokens = loginAndReadTokens();

        mockMvc.perform(get("/api/v1/auth/session").header(HttpHeaders.AUTHORIZATION, bearer(tokens)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.email").value(EMAIL))
                .andExpect(jsonPath("$.data.user.role").value("USER"))
                .andExpect(jsonPath("$.data.sessionId").isNumber())
                .andExpect(jsonPath("$.data.sessionCreatedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.sessionExpiresAt").isNotEmpty())
                .andExpect(jsonPath("$.data.lastAccessedAt", not(nullValue())));
    }

    @Test
    void sessionWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/session"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void sessionWithMalformedTokenReturnsInvalidTokenCode() throws Exception {
        mockMvc.perform(get("/api/v1/auth/session").header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void sessionIsExpiredWhenSessionRowIsRevoked() throws Exception {
        mockMvc.perform(signup(EMAIL, PASSWORD, NAME)).andExpect(status().isCreated());
        JsonNode tokens = loginAndReadTokens();

        authSessionRepository.findAll().forEach(session -> {
            session.revoke(LocalDateTime.now());
            authSessionRepository.save(session);
        });

        mockMvc.perform(get("/api/v1/auth/session").header(HttpHeaders.AUTHORIZATION, bearer(tokens)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    @Test
    void publicLoginStillWorksWithStaleAuthorizationHeader() throws Exception {
        mockMvc.perform(signup(EMAIL, PASSWORD, NAME)).andExpect(status().isCreated());

        mockMvc.perform(login(EMAIL, PASSWORD).header(HttpHeaders.AUTHORIZATION, "Bearer stale.garbage.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    // ---------- S15P21D105-495 토큰 갱신 ----------

    @Test
    void refreshRotatesRefreshTokenAndKeepsSession() throws Exception {
        mockMvc.perform(signup(EMAIL, PASSWORD, NAME)).andExpect(status().isCreated());
        JsonNode tokens = loginAndReadTokens();
        String oldRefreshToken = tokens.path("refreshToken").asText();

        MvcResult result = mockMvc.perform(refresh(oldRefreshToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value(EMAIL))
                .andReturn();
        String newRefreshToken = readData(result).path("refreshToken").asText();

        assertThat(newRefreshToken).isNotEqualTo(oldRefreshToken);
        assertThat(authSessionRepository.count()).isEqualTo(1);

        // 이전 Refresh Token은 회전 직후 무효
        mockMvc.perform(refresh(oldRefreshToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // 새 Refresh Token은 계속 사용 가능
        mockMvc.perform(refresh(newRefreshToken))
                .andExpect(status().isOk());
    }

    @Test
    void refreshWithUnknownTokenIsUnauthorized() throws Exception {
        mockMvc.perform(refresh("definitely-not-issued"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    void refreshAfterLogoutReportsSessionExpired() throws Exception {
        mockMvc.perform(signup(EMAIL, PASSWORD, NAME)).andExpect(status().isCreated());
        JsonNode tokens = loginAndReadTokens();

        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(tokens)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(nullValue()));

        mockMvc.perform(refresh(tokens.path("refreshToken").asText()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    // ---------- S15P21D105-495 로그아웃 · 세션 삭제 ----------

    @Test
    void logoutRevokesOnlyCurrentSessionAndIsIdempotent() throws Exception {
        mockMvc.perform(signup(EMAIL, PASSWORD, NAME)).andExpect(status().isCreated());
        JsonNode first = loginAndReadTokens();
        JsonNode second = loginAndReadTokens();

        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(first)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/logout").header(HttpHeaders.AUTHORIZATION, bearer(first)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/session").header(HttpHeaders.AUTHORIZATION, bearer(first)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        mockMvc.perform(get("/api/v1/auth/session").header(HttpHeaders.AUTHORIZATION, bearer(second)))
                .andExpect(status().isOk());
    }

    @Test
    void deleteSessionWithoutIdRevokesAllActiveSessions() throws Exception {
        mockMvc.perform(signup(EMAIL, PASSWORD, NAME)).andExpect(status().isCreated());
        JsonNode first = loginAndReadTokens();
        JsonNode second = loginAndReadTokens();

        mockMvc.perform(delete("/api/v1/auth/session").header(HttpHeaders.AUTHORIZATION, bearer(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revokedCount").value(2));

        mockMvc.perform(get("/api/v1/auth/session").header(HttpHeaders.AUTHORIZATION, bearer(second)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    @Test
    void deleteSessionByIdChecksOwnership() throws Exception {
        mockMvc.perform(signup(EMAIL, PASSWORD, NAME)).andExpect(status().isCreated());
        mockMvc.perform(signup("other@example.com", PASSWORD, "타인")).andExpect(status().isCreated());
        JsonNode mine = loginAndReadTokens();
        JsonNode theirs = loginAndReadTokens("other@example.com");
        long theirSessionId = readSessionId(theirs);
        long mySessionId = readSessionId(mine);

        mockMvc.perform(delete("/api/v1/auth/session")
                        .param("sessionId", String.valueOf(theirSessionId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mine)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_FOUND"));

        mockMvc.perform(delete("/api/v1/auth/session")
                        .param("sessionId", String.valueOf(mySessionId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mine)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revokedCount").value(1));

        mockMvc.perform(get("/api/v1/auth/session").header(HttpHeaders.AUTHORIZATION, bearer(theirs)))
                .andExpect(status().isOk());
    }

    // ---------- helpers ----------

    /**
     * 금융망은 사용자마다 다른 userKey를 주므로 이메일별로 결정적인 키를 만든다.
     * Mockito가 stubbing 중 빈 문자열로 호출하기도 하므로 '@'가 없어도 동작해야 한다.
     */
    private static SsafyMember member(String email) {
        int at = email.indexOf('@');
        String userName = at > 0 ? email.substring(0, at) : email;
        return new SsafyMember(email, userName, "00100", userKeyFor(email));
    }

    private static String userKeyFor(String email) {
        return USER_KEY_PREFIX + email;
    }

    private JsonNode loginAndReadTokens() throws Exception {
        return loginAndReadTokens(EMAIL);
    }

    private JsonNode loginAndReadTokens(String email) throws Exception {
        MvcResult result = mockMvc.perform(login(email, PASSWORD))
                .andExpect(status().isOk())
                .andReturn();
        return readData(result);
    }

    private long readSessionId(JsonNode tokens) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/auth/session").header(HttpHeaders.AUTHORIZATION, bearer(tokens)))
                .andExpect(status().isOk())
                .andReturn();
        return readData(result).path("sessionId").asLong();
    }

    private JsonNode readData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private static String bearer(JsonNode tokens) {
        return "Bearer " + tokens.path("accessToken").asText();
    }

    private MockHttpServletRequestBuilder signup(String email, String password, String name) throws Exception {
        return post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email,
                        "password", password,
                        "name", name
                )));
    }

    private MockHttpServletRequestBuilder login(String email, String password) throws Exception {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email,
                        "password", password
                )));
    }

    private MockHttpServletRequestBuilder refresh(String refreshToken) throws Exception {
        return post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken)));
    }
}
