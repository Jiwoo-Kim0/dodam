package com.ssafy.dodam.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.auth.repository.AuthSessionRepository;
import com.ssafy.dodam.domain.user.repository.UserRepository;
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

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @BeforeEach
    void cleanUp() {
        authSessionRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void signupCreatesUserAndReturnsCreated() throws Exception {
        mockMvc.perform(signup(EMAIL, PASSWORD, NAME))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.user.userId").isNumber())
                .andExpect(jsonPath("$.data.user.email").value(EMAIL))
                .andExpect(jsonPath("$.data.user.name").value(NAME))
                .andExpect(jsonPath("$.data.user.role").value("USER"))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());
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

    @Test
    void sessionReturnsCurrentLoginStateForBearerToken() throws Exception {
        mockMvc.perform(signup(EMAIL, PASSWORD, NAME)).andExpect(status().isCreated());
        String accessToken = loginAndReadAccessToken();

        mockMvc.perform(get("/api/v1/auth/session")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
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
        mockMvc.perform(get("/api/v1/auth/session")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void sessionIsExpiredWhenSessionRowIsRevoked() throws Exception {
        mockMvc.perform(signup(EMAIL, PASSWORD, NAME)).andExpect(status().isCreated());
        String accessToken = loginAndReadAccessToken();

        authSessionRepository.findAll().forEach(session -> {
            session.revoke(LocalDateTime.now());
            authSessionRepository.save(session);
        });

        mockMvc.perform(get("/api/v1/auth/session")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
    }

    @Test
    void publicLoginStillWorksWithStaleAuthorizationHeader() throws Exception {
        mockMvc.perform(signup(EMAIL, PASSWORD, NAME)).andExpect(status().isCreated());

        mockMvc.perform(login(EMAIL, PASSWORD)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer stale.garbage.token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    private String loginAndReadAccessToken() throws Exception {
        MvcResult result = mockMvc.perform(login(EMAIL, PASSWORD))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("accessToken").asText();
    }

    private MockHttpServletRequestBuilder signup(
            String email,
            String password,
            String name
    ) throws Exception {
        return post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email,
                        "password", password,
                        "name", name
                )));
    }

    private MockHttpServletRequestBuilder login(
            String email,
            String password
    ) throws Exception {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email,
                        "password", password
                )));
    }
}
