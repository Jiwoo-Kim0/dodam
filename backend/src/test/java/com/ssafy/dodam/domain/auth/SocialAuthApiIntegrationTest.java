package com.ssafy.dodam.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.auth.entity.UserOauthAccount;
import com.ssafy.dodam.domain.auth.repository.AuthSessionRepository;
import com.ssafy.dodam.domain.auth.repository.UserOauthAccountRepository;
import com.ssafy.dodam.domain.auth.social.SocialIdentity;
import com.ssafy.dodam.domain.auth.social.SocialIdentityClient;
import com.ssafy.dodam.domain.auth.social.SocialIdentityClients;
import com.ssafy.dodam.domain.auth.social.SocialProvider;
import com.ssafy.dodam.domain.finance.entity.FinanceConnectionStatus;
import com.ssafy.dodam.domain.finance.repository.FinanceConnectionRepository;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.FinancialProfileRepository;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import com.ssafy.dodam.domain.user.repository.UserSettingRepository;
import com.ssafy.dodam.global.crypto.FieldCryptor;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyFinanceClient;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyMember;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 소셜 로그인 API(POST /api/v1/auth/social/{provider}) 통합 테스트. 공급자 검증기는 Mock으로 대체하고 그 뒤의 회원 생성·연결·세션 발급을 본다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class SocialAuthApiIntegrationTest {

    private static final String KAKAO_TOKEN = "kakao-access-token";
    private static final String GOOGLE_TOKEN = "google-id-token";

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
    private UserOauthAccountRepository oauthAccountRepository;

    @Autowired
    private FieldCryptor fieldCryptor;

    @MockitoBean
    private SocialIdentityClients socialIdentityClients;

    @MockitoBean
    private SsafyFinanceClient ssafyFinanceClient;

    private SocialIdentityClient kakao;
    private SocialIdentityClient google;

    @BeforeEach
    void setUp() {
        oauthAccountRepository.deleteAll();
        authSessionRepository.deleteAll();
        financeConnectionRepository.deleteAll();
        userSettingRepository.deleteAll();
        financialProfileRepository.deleteAll();
        userRepository.deleteAll();

        kakao = mock(SocialIdentityClient.class);
        google = mock(SocialIdentityClient.class);
        given(socialIdentityClients.get(SocialProvider.KAKAO)).willReturn(kakao);
        given(socialIdentityClients.get(SocialProvider.GOOGLE)).willReturn(google);
        given(ssafyFinanceClient.createMember(anyString()))
                .willAnswer(invocation -> {
                    String email = invocation.getArgument(0);
                    return new SsafyMember(email, "user", "00100", "user-key-for-" + email);
                });
    }

    // ---------- 신규 회원 ----------

    @Test
    void firstKakaoLoginWithoutEmailCreatesSocialUserWithSyntheticEmail() throws Exception {
        given(kakao.verify(KAKAO_TOKEN)).willReturn(new SocialIdentity(SocialProvider.KAKAO, "1001", null, false, "도담이"));

        MvcResult result = mockMvc.perform(socialLogin("kakao", KAKAO_TOKEN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.name").value("도담이"))
                .andExpect(jsonPath("$.data.user.role").value("USER"))
                .andReturn();

        JsonNode user = objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("user");
        long userId = user.path("userId").asLong();
        String email = user.path("email").asText();
        assertThat(email).startsWith("kakao.").endsWith("@social.dodam.invalid");

        User saved = userRepository.findById(userId).orElseThrow();
        assertThat(saved.hasPassword()).isFalse();
        assertThat(userSettingRepository.findById(userId)).isPresent();
        assertThat(financialProfileRepository.findById(userId)).isPresent();
        // 대체 이메일은 금융망에 등록하지 않으므로 연결은 FAILED로 남는다.
        assertThat(financeConnectionRepository.findById(userId).orElseThrow().getStatus())
                .isEqualTo(FinanceConnectionStatus.FAILED);

        UserOauthAccount link = oauthAccountRepository
                .findByProviderAndProviderUserIdHash(SocialProvider.KAKAO, fieldCryptor.hash("1001"))
                .orElseThrow();
        assertThat(link.getUserId()).isEqualTo(userId);
        assertThat(link.getProviderEmailHash()).isNull();
        assertThat(authSessionRepository.findAllByUserIdAndRevokedAtIsNull(userId)).hasSize(1);
    }

    @Test
    void googleLoginWithVerifiedEmailCreatesUserWithThatEmailAndFinanceConnection() throws Exception {
        given(google.verify(GOOGLE_TOKEN))
                .willReturn(new SocialIdentity(SocialProvider.GOOGLE, "sub-1", "Dodam.G@Example.com", true, "구글 도담"));

        MvcResult result = mockMvc.perform(socialLogin("google", GOOGLE_TOKEN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.email").value("dodam.g@example.com"))
                .andExpect(jsonPath("$.data.user.name").value("구글 도담"))
                .andReturn();

        long userId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("user").path("userId").asLong();
        assertThat(financeConnectionRepository.findById(userId).orElseThrow().getStatus())
                .isEqualTo(FinanceConnectionStatus.CONNECTED);
        UserOauthAccount link = oauthAccountRepository
                .findByProviderAndProviderUserIdHash(SocialProvider.GOOGLE, fieldCryptor.hash("sub-1"))
                .orElseThrow();
        assertThat(link.getProviderEmailHash()).isEqualTo(fieldCryptor.hash("dodam.g@example.com"));
        assertThat(link.getProviderEmailMasked()).isEqualTo("do***@example.com");
        assertThat(fieldCryptor.decrypt(link.getProviderEmailEncrypted())).isEqualTo("dodam.g@example.com");
    }

    @Test
    void unverifiedProviderEmailIsNotUsedAsAccountEmail() throws Exception {
        given(kakao.verify(KAKAO_TOKEN))
                .willReturn(new SocialIdentity(SocialProvider.KAKAO, "1002", "someone@example.com", false, null));

        mockMvc.perform(socialLogin("kakao", KAKAO_TOKEN))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.user.name").value("도담 회원"))
                .andExpect(jsonPath("$.data.user.email").value(org.hamcrest.Matchers.endsWith("@social.dodam.invalid")));

        // 확인되지 않은 이메일도 공급자 이메일로는 저장된다(마스킹·해시·암호문).
        UserOauthAccount link = oauthAccountRepository
                .findByProviderAndProviderUserIdHash(SocialProvider.KAKAO, fieldCryptor.hash("1002"))
                .orElseThrow();
        assertThat(link.getProviderEmailMasked()).isEqualTo("so***@example.com");
    }

    // ---------- 재로그인·연결 ----------

    @Test
    void secondLoginWithSameSocialAccountReturns200AndSameUser() throws Exception {
        given(kakao.verify(KAKAO_TOKEN)).willReturn(new SocialIdentity(SocialProvider.KAKAO, "1001", null, false, "도담이"));

        long first = userIdOf(mockMvc.perform(socialLogin("kakao", KAKAO_TOKEN)).andExpect(status().isCreated()).andReturn());
        long second = userIdOf(mockMvc.perform(socialLogin("kakao", KAKAO_TOKEN)).andExpect(status().isOk()).andReturn());

        assertThat(second).isEqualTo(first);
        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(oauthAccountRepository.count()).isEqualTo(1);
        assertThat(authSessionRepository.findAllByUserIdAndRevokedAtIsNull(first)).hasSize(2);
    }

    @Test
    void verifiedEmailMatchingLocalUserLinksSocialAccountToThatUser() throws Exception {
        long localUserId = signupLocal("dodam@example.com", "password123", "도담");
        given(google.verify(GOOGLE_TOKEN))
                .willReturn(new SocialIdentity(SocialProvider.GOOGLE, "sub-1", "dodam@example.com", true, "구글 이름"));

        long linked = userIdOf(mockMvc.perform(socialLogin("google", GOOGLE_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.name").value("도담"))
                .andReturn());

        assertThat(linked).isEqualTo(localUserId);
        assertThat(oauthAccountRepository.existsByUserIdAndProvider(localUserId, SocialProvider.GOOGLE)).isTrue();
        // 기존 비밀번호 로그인도 그대로 된다.
        mockMvc.perform(passwordLogin("dodam@example.com", "password123")).andExpect(status().isOk());
    }

    @Test
    void secondGoogleAccountWithSameVerifiedEmailIsRejectedWhenProviderAlreadyLinked() throws Exception {
        long localUserId = signupLocal("dodam@example.com", "password123", "도담");
        given(google.verify("token-a"))
                .willReturn(new SocialIdentity(SocialProvider.GOOGLE, "sub-a", "dodam@example.com", true, null));
        given(google.verify("token-b"))
                .willReturn(new SocialIdentity(SocialProvider.GOOGLE, "sub-b", "dodam@example.com", true, null));

        mockMvc.perform(socialLogin("google", "token-a")).andExpect(status().isOk());
        mockMvc.perform(socialLogin("google", "token-b"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SOCIAL_ACCOUNT_ALREADY_LINKED"));

        assertThat(oauthAccountRepository.findAllByUserIdOrderByIdAsc(localUserId)).hasSize(1);
    }

    @Test
    void differentProvidersCanLinkToTheSameUser() throws Exception {
        given(google.verify(GOOGLE_TOKEN))
                .willReturn(new SocialIdentity(SocialProvider.GOOGLE, "sub-1", "dodam@example.com", true, "도담"));
        given(kakao.verify(KAKAO_TOKEN))
                .willReturn(new SocialIdentity(SocialProvider.KAKAO, "1001", "dodam@example.com", true, "도담"));

        long byGoogle = userIdOf(mockMvc.perform(socialLogin("google", GOOGLE_TOKEN)).andExpect(status().isCreated()).andReturn());
        long byKakao = userIdOf(mockMvc.perform(socialLogin("kakao", KAKAO_TOKEN)).andExpect(status().isOk()).andReturn());

        assertThat(byKakao).isEqualTo(byGoogle);
        assertThat(oauthAccountRepository.findAllByUserIdOrderByIdAsc(byGoogle)).hasSize(2);
    }

    // ---------- 실패 ----------

    @Test
    void socialOnlyUserCannotLoginWithPassword() throws Exception {
        given(google.verify(GOOGLE_TOKEN))
                .willReturn(new SocialIdentity(SocialProvider.GOOGLE, "sub-1", "dodam@example.com", true, "도담"));
        mockMvc.perform(socialLogin("google", GOOGLE_TOKEN)).andExpect(status().isCreated());

        mockMvc.perform(passwordLogin("dodam@example.com", "anything123"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void withdrawnLinkedUserIsForbidden() throws Exception {
        given(kakao.verify(KAKAO_TOKEN)).willReturn(new SocialIdentity(SocialProvider.KAKAO, "1001", null, false, "도담이"));
        long userId = userIdOf(mockMvc.perform(socialLogin("kakao", KAKAO_TOKEN)).andExpect(status().isCreated()).andReturn());

        User user = userRepository.findById(userId).orElseThrow();
        user.withdraw(LocalDateTime.now());
        userRepository.save(user);

        mockMvc.perform(socialLogin("kakao", KAKAO_TOKEN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("USER_WITHDRAWN"));
    }

    @Test
    void invalidProviderTokenIs401() throws Exception {
        given(kakao.verify("bad")).willThrow(new BusinessException(ErrorCode.SOCIAL_TOKEN_INVALID));

        mockMvc.perform(socialLogin("kakao", "bad"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SOCIAL_TOKEN_INVALID"));
        assertThat(userRepository.count()).isZero();
    }

    @Test
    void unknownProviderIs400() throws Exception {
        mockMvc.perform(socialLogin("naver", "token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SOCIAL_PROVIDER_NOT_SUPPORTED"));
    }

    @Test
    void blankTokenIs400() throws Exception {
        mockMvc.perform(socialLogin("kakao", " "))
                .andExpect(status().isBadRequest());
    }

    @Test
    void providerPathIsCaseInsensitive() throws Exception {
        given(kakao.verify(KAKAO_TOKEN)).willReturn(new SocialIdentity(SocialProvider.KAKAO, "1001", null, false, null));

        mockMvc.perform(socialLogin("KaKaO", KAKAO_TOKEN)).andExpect(status().isCreated());
    }

    // ---------- helpers ----------

    private long signupLocal(String email, String password, String name) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password, "name", name))))
                .andExpect(status().isCreated());
        return userRepository.findByEmailAndDeletedAtIsNull(email).orElseThrow().getId();
    }

    private MockHttpServletRequestBuilder socialLogin(String provider, String token) throws Exception {
        return post("/api/v1/auth/social/" + provider)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("token", token)));
    }

    private MockHttpServletRequestBuilder passwordLogin(String email, String password) throws Exception {
        return post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("email", email, "password", password)));
    }

    private long userIdOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("user").path("userId").asLong();
    }
}
