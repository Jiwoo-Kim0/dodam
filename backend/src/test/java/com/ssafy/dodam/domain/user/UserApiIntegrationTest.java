package com.ssafy.dodam.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.dodam.domain.user.entity.Gender;
import com.ssafy.dodam.domain.user.entity.CreditScoreSource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.auth.repository.AuthSessionRepository;
import com.ssafy.dodam.domain.finance.entity.FinanceConnectionStatus;
import com.ssafy.dodam.domain.finance.repository.FinanceConnectionRepository;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.FinancialProfileRepository;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import com.ssafy.dodam.domain.user.repository.UserSettingRepository;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyFinanceClient;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyMember;
import java.util.HashMap;
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
class UserApiIntegrationTest {

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
    private UserSettingRepository userSettingRepository;

    @Autowired
    private FinancialProfileRepository financialProfileRepository;

    @Autowired
    private FinanceConnectionRepository financeConnectionRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @MockitoBean
    private SsafyFinanceClient ssafyFinanceClient;

    private String bearer;

    @BeforeEach
    void setUp() throws Exception {
        authSessionRepository.deleteAll();
        financeConnectionRepository.deleteAll();
        userSettingRepository.deleteAll();
        financialProfileRepository.deleteAll();
        userRepository.deleteAll();

        given(ssafyFinanceClient.createMember(anyString()))
                .willReturn(new SsafyMember(EMAIL, "dodam", "00100", "user-key-1"));

        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", EMAIL, "password", PASSWORD, "name", NAME))))
                .andExpect(status().isCreated());
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", EMAIL, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        bearer = "Bearer " + readData(login).path("accessToken").asText();
    }

    // ---------- S15P21D105-496 내 정보 ----------

    @Test
    void getMeReturnsProfileWithFinanceConnectionStatus() throws Exception {
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value(EMAIL))
                .andExpect(jsonPath("$.data.name").value(NAME))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.financeConnectionStatus").value("CONNECTED"))
                .andExpect(jsonPath("$.data.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty())
                // 629: 가입 때 생년월일·성별을 보내지 않았고 신용등급 조회도 없었으므로 셋 다 null이다
                .andExpect(jsonPath("$.data.birthDate").value(nullValue()))
                .andExpect(jsonPath("$.data.age").value(nullValue()))
                .andExpect(jsonPath("$.data.gender").value(nullValue()))
                .andExpect(jsonPath("$.data.creditRating").value(nullValue()))
                .andExpect(jsonPath("$.data.creditRatingUpdatedAt").value(nullValue()))
                .andExpect(jsonPath("$.data.creditScore").value(nullValue()))
                .andExpect(jsonPath("$.data.creditScoreType").value(nullValue()))
                .andExpect(jsonPath("$.data.creditScoreSource").value(nullValue()))
                .andExpect(jsonPath("$.data.creditScoreModelVersion").value(nullValue()))
                .andExpect(jsonPath("$.data.creditScoreUpdatedAt").value(nullValue()));
    }

    @Test
    void getMeReturnsEstimatedCreditScoreWithSourceMetadata() throws Exception {
        User user = userRepository.findByEmailAndDeletedAtIsNull(EMAIL).orElseThrow();
        LocalDateTime evaluatedAt = LocalDateTime.of(2026, 9, 27, 23, 59, 59);
        user.updateCreditScore(768, CreditScoreSource.SYNTHETIC, "DODAM_CREDIT_V1", evaluatedAt);
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.creditScore").value(768))
                .andExpect(jsonPath("$.data.creditScoreType").value("ESTIMATED"))
                .andExpect(jsonPath("$.data.creditScoreSource").value("SYNTHETIC"))
                .andExpect(jsonPath("$.data.creditScoreModelVersion").value("DODAM_CREDIT_V1"))
                .andExpect(jsonPath("$.data.creditScoreUpdatedAt").value("2026-09-27T23:59:59"));
    }

    // ---------- S15P21D105-629 생년월일·성별·신용등급 ----------

    @Test
    void updateMeSetsBirthDateAndGenderAndComputesAge() throws Exception {
        LocalDate birthDate = LocalDate.now(ZoneId.of("Asia/Seoul")).minusYears(30).minusDays(1);

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("birthDate", birthDate.toString(), "gender", "FEMALE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value(NAME))
                .andExpect(jsonPath("$.data.birthDate").value(birthDate.toString()))
                .andExpect(jsonPath("$.data.age").value(30))
                .andExpect(jsonPath("$.data.gender").value("FEMALE"));

        User user = userRepository.findByEmailAndDeletedAtIsNull(EMAIL).orElseThrow();
        assertThat(user.getBirthDate()).isEqualTo(birthDate);
        assertThat(user.getGender()).isEqualTo(Gender.FEMALE);
        assertThat(user.getName()).isEqualTo(NAME);

        // 성별만 다시 보내면 생년월일은 유지된다
        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("gender", "MALE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.birthDate").value(birthDate.toString()))
                .andExpect(jsonPath("$.data.gender").value("MALE"));
    }

    @Test
    void updateMeRejectsFutureBirthDateAndUnknownGender() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("birthDate", LocalDate.now().plusDays(1).toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("birthDate"));

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("gender", "UNKNOWN"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        // 신용등급은 금융망 값이라 Body로 보내도 무시된다(알 수 없는 필드) — 다른 필드가 없으면 변경 없음으로 처리
        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("creditRating", "A"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_FIELDS_TO_UPDATE"));

        User user = userRepository.findByEmailAndDeletedAtIsNull(EMAIL).orElseThrow();
        assertThat(user.getBirthDate()).isNull();
        assertThat(user.getGender()).isNull();
        assertThat(user.getCreditRating()).isNull();
    }

    @Test
    void getMeWithoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void updateMeChangesName() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "  새이름  "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("새이름"));

        assertThat(userRepository.findByEmailAndDeletedAtIsNull(EMAIL).orElseThrow().getName()).isEqualTo("새이름");
    }

    @Test
    void updateMeRejectsEmptyBodyAndBlankName() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_FIELDS_TO_UPDATE"));

        mockMvc.perform(patch("/api/v1/users/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void withdrawSoftDeletesUserRevokesSessionsAndDisconnectsFinance() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(nullValue()));

        User user = userRepository.findAll().get(0);
        assertThat(user.isDeleted()).isTrue();
        assertThat(user.hasPassword()).isFalse();
        assertThat(authSessionRepository.findAllByUserIdAndRevokedAtIsNull(user.getId())).isEmpty();
        assertThat(financeConnectionRepository.findByUserId(user.getId()).orElseThrow().getStatus())
                .isEqualTo(FinanceConnectionStatus.DISCONNECTED);

        // 탈퇴 후에는 기존 Access Token으로도, 재로그인으로도 접근 불가
        mockMvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SESSION_EXPIRED"));
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", EMAIL, "password", PASSWORD))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    // ---------- S15P21D105-497 사용자 설정 ----------

    @Test
    void getSettingsReturnsDefaultsCreatedAtSignup() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/settings").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notificationEnabled").value(true))
                .andExpect(jsonPath("$.data.dailyBriefingEnabled").value(true))
                .andExpect(jsonPath("$.data.monthlyReportEnabled").value(true))
                .andExpect(jsonPath("$.data.dailyBriefingTime").value(nullValue()))
                .andExpect(jsonPath("$.data.timezone").value("Asia/Seoul"));
    }

    @Test
    void updateSettingsAppliesOnlyProvidedFields() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("dailyBriefingEnabled", false);
        body.put("dailyBriefingTime", "08:30");
        body.put("timezone", "UTC");

        mockMvc.perform(patch("/api/v1/users/me/settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notificationEnabled").value(true))
                .andExpect(jsonPath("$.data.dailyBriefingEnabled").value(false))
                .andExpect(jsonPath("$.data.dailyBriefingTime").value("08:30"))
                .andExpect(jsonPath("$.data.timezone").value("UTC"));
    }

    @Test
    void updateSettingsValidatesTimeFormatAndTimezone() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("dailyBriefingTime", "8:30"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'dailyBriefingTime')]").exists());

        mockMvc.perform(patch("/api/v1/users/me/settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("timezone", "Mars/Olympus"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TIMEZONE"));

        mockMvc.perform(patch("/api/v1/users/me/settings")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_FIELDS_TO_UPDATE"));
    }

    // ---------- S15P21D105-497 금융 기본정보 ----------

    @Test
    void financialProfileStartsEmptyAndAcceptsPartialUpdates() throws Exception {
        mockMvc.perform(get("/api/v1/users/me/financial-profile").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monthlyIncome").value(nullValue()))
                .andExpect(jsonPath("$.data.payday").value(nullValue()));

        mockMvc.perform(patch("/api/v1/users/me/financial-profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("monthlyIncome", 3_200_000, "payday", 25))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monthlyIncome").value(3_200_000))
                .andExpect(jsonPath("$.data.payday").value(25))
                .andExpect(jsonPath("$.data.fixedExpense").value(nullValue()));

        mockMvc.perform(patch("/api/v1/users/me/financial-profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("fixedExpense", 1_100_000))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.monthlyIncome").value(3_200_000))
                .andExpect(jsonPath("$.data.fixedExpense").value(1_100_000));
    }

    @Test
    void financialProfileRejectsValuesOutsideErdChecks() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/financial-profile")
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("monthlyIncome", -1, "payday", 32))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[?(@.field == 'monthlyIncome')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'payday')]").exists());
    }

    // ---------- helpers ----------

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private JsonNode readData(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }
}
