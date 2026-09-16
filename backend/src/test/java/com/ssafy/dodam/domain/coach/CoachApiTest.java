package com.ssafy.dodam.domain.coach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.coach.service.CoachAnswerCache;
import com.ssafy.dodam.domain.coach.service.CoachGenerationException;
import com.ssafy.dodam.domain.coach.service.CoachTextGenerator;
import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisIssue;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisStatus;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisIssueRepository;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisRepository;
import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import com.ssafy.dodam.domain.recommendation.entity.RecommendationReason;
import com.ssafy.dodam.domain.recommendation.repository.RecommendationReasonRepository;
import com.ssafy.dodam.domain.recommendation.repository.RecommendationRepository;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API 21 3종(S15P21D105-589). 외부 AI는 {@link CoachTextGenerator}를 대체해 검증한다 — 테스트가 실제
 * OpenAI를 호출하면 비용이 들고, 실패 경로(타임아웃·지어낸 숫자)는 실제 호출로는 재현할 수 없다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:coach589;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class CoachApiTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 16, 12, 0);
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String KEY = "a".repeat(64);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired DiagnosisRepository diagnoses;
    @Autowired DiagnosisIssueRepository issues;
    @Autowired RecommendationRepository recommendations;
    @Autowired RecommendationReasonRepository reasons;
    @MockitoBean CoachTextGenerator generator;
    @MockitoBean CoachAnswerCache cache;

    private Long owner;
    private Long other;
    private Long diagnosisId;
    private Long issueId;
    private Long recommendationId;

    @BeforeEach
    void setup() {
        given(generator.isAvailable()).willReturn(false);
        given(generator.modelName()).willReturn("gpt-5.4-mini");

        owner = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "코치")).getId();
        other = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "다른 사용자")).getId();

        Diagnosis diagnosis = Diagnosis.start(owner, null, NOW);
        diagnosis.complete(2, 1, DiagnosisStatus.CAUTION, "diagnosis-v1", null, NOW);
        diagnosisId = diagnoses.save(diagnosis).getDiagnosisId();

        issueId = issues.save(DiagnosisIssue.ofRule(diagnosisId, "SPENDING", "HIGH_DINING", IssueSeverity.HIGH,
                "외식 지출이 많습니다", "최근 3개월 외식 지출이 기준을 넘습니다.",
                node("{\"ratio\": 0.32, \"targetId\": 77}"),
                node("{\"ratio\": 0.2}"),
                node("{\"ruleKey\": \"SPENDING_HIGH\", \"accountNo\": \"1234567890\"}"), NOW)).getIssueId();
        issues.save(DiagnosisIssue.ofRule(diagnosisId, "DEBT", "LOW_RISK", IssueSeverity.LOW,
                "대출 부담은 낮습니다", "대출 상환 비중이 기준 이하입니다.", null, null, null, NOW));

        Recommendation recommendation = Recommendation.pending(owner, diagnosisId, null, "SPENDING", "CANCEL_SUBSCRIPTION",
                "쓰지 않는 구독을 정리하세요", "최근 3개월 사용 기록이 없는 구독이 있습니다.", 1,
                new Recommendation.Effect(12_000L, null, null, null, null, null, null, null), KEY, NOW);
        recommendationId = recommendations.save(recommendation).getRecommendationId();
        reasons.save(RecommendationReason.of(recommendationId, "UNUSED_SUBSCRIPTION",
                "3개월 동안 사용 기록이 없습니다.", node("{\"monthlyFee\": 12000, \"targetId\": 3}")));
    }

    private com.fasterxml.jackson.databind.JsonNode node(String raw) {
        try {
            return json.readTree(raw);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    // ---------- 인증·소유권 ----------

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post("/api/v1/coach/financial-summary").contentType(JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/coach/inefficiency-explanation").contentType(JSON)
                        .content("{\"diagnosisId\":" + diagnosisId + ",\"issueId\":" + issueId + "}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/coach/recommendation-explanation").contentType(JSON)
                        .content("{\"recommendationId\":" + recommendationId + "}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void otherUsersResourcesAreNotFound() throws Exception {
        // 남의 것과 없는 것을 같게 응답한다 — 구분하면 ID를 훑어 남의 진단 존재 여부를 알 수 있다
        mvc.perform(post("/api/v1/coach/financial-summary").with(user(other.toString())).contentType(JSON)
                        .content("{\"diagnosisId\":" + diagnosisId + "}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(post("/api/v1/coach/inefficiency-explanation").with(user(other.toString())).contentType(JSON)
                        .content("{\"diagnosisId\":" + diagnosisId + ",\"issueId\":" + issueId + "}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/coach/recommendation-explanation").with(user(other.toString())).contentType(JSON)
                        .content("{\"recommendationId\":" + recommendationId + "}"))
                .andExpect(status().isNotFound());
        // 완료 진단이 하나도 없는 사용자의 요약도 404다
        mvc.perform(post("/api/v1/coach/financial-summary").with(user(other.toString())).contentType(JSON).content("{}"))
                .andExpect(status().isNotFound());
        verify(generator, never()).generate(any(), any());
    }

    @Test
    void validatesRequestBody() throws Exception {
        for (String body : List.of("{}", "{\"diagnosisId\":" + diagnosisId + "}", "{\"issueId\":0}")) {
            mvc.perform(post("/api/v1/coach/inefficiency-explanation").with(user(owner.toString()))
                            .contentType(JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/coach/recommendation-explanation").with(user(owner.toString()))
                        .contentType(JSON).content("{\"recommendationId\":-1}"))
                .andExpect(status().isBadRequest());
        // 요약은 본문 자체가 없어도 된다(명세: diagnosisId?)
        mvc.perform(post("/api/v1/coach/financial-summary").with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.facts.sections[0].title").value("진단 개요"));
    }

    // ---------- 서버 계산 결과만 전달 ----------

    @Test
    void summaryCarriesServerComputedValuesOnly() throws Exception {
        String body = mvc.perform(post("/api/v1/coach/financial-summary").with(user(owner.toString()))
                        .contentType(JSON).content("{\"diagnosisId\":" + diagnosisId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topic").value("FINANCIAL_SUMMARY"))
                .andExpect(jsonPath("$.data.facts.sections[0].facts[2].label").value("종합 상태(서버 판정)"))
                .andExpect(jsonPath("$.data.facts.sections[0].facts[2].value").value("CAUTION"))
                .andExpect(jsonPath("$.data.facts.sections[0].facts[3].value").value("2건"))
                .andExpect(jsonPath("$.data.disclaimer").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        // 군집 결과가 없는 사용자이므로 또래 비교를 만들지 말라는 지시가 들어간다
        assertThat(body).contains("저장된 소비패턴 군집 결과가 없다");
        // 주요 문제는 심각도 순이다
        assertThat(json.readTree(body).at("/data/facts/sections/3/facts/0/label").asText()).isEqualTo("HIGH / SPENDING");
    }

    @Test
    void inefficiencyExcludesInternalIdentifiersFromFacts() throws Exception {
        String body = mvc.perform(post("/api/v1/coach/inefficiency-explanation").with(user(owner.toString()))
                        .contentType(JSON).content("{\"diagnosisId\":" + diagnosisId + ",\"issueId\":" + issueId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subject").value("외식 지출이 많습니다"))
                .andExpect(jsonPath("$.data.facts.sections[0].facts[2].value").value("HIGH"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("ruleKey").contains("0.32").contains("0.2");
        // evidence·currentValue에 들어 있던 내부 식별자와 계좌번호는 프롬프트 입력에 실리지 않는다
        assertThat(body).doesNotContain("targetId").doesNotContain("accountNo").doesNotContain("1234567890");
    }

    @Test
    void recommendationCarriesStoredEffectAndReasons() throws Exception {
        String body = mvc.perform(post("/api/v1/coach/recommendation-explanation").with(user(owner.toString()))
                        .contentType(JSON).content("{\"recommendationId\":" + recommendationId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.topic").value("RECOMMENDATION_EXPLANATION"))
                .andExpect(jsonPath("$.data.subject").value("쓰지 않는 구독을 정리하세요"))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("12,000원").contains("UNUSED_SUBSCRIPTION");
        // 연 절감액은 서버가 계산하지 않았다 → 사실에도 없고, 추정하지 말라는 지시가 들어간다
        assertThat(body).doesNotContain("연 절감액");
        assertThat(body).doesNotContain("targetId");
    }

    // ---------- 외부 호출 결과별 응답 ----------

    @Test
    void usesTemplateWhenAiIsOff() throws Exception {
        mvc.perform(post("/api/v1/coach/financial-summary").with(user(owner.toString())).contentType(JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("TEMPLATE"))
                .andExpect(jsonPath("$.data.degradedReason").value("AI_DISABLED"))
                .andExpect(jsonPath("$.data.model").isEmpty())
                .andExpect(jsonPath("$.data.message").isNotEmpty())
                .andExpect(jsonPath("$.data.highlights").isNotEmpty());
        verify(generator, never()).generate(any(), any());
    }

    @Test
    void usesAiAnswerWhenEveryNumberCameFromTheServer() throws Exception {
        given(generator.isAvailable()).willReturn(true);
        given(generator.generate(any(), any())).willReturn("매달 12,000원을 아낄 수 있는 개선안입니다.\n\n사용 기록을 확인해 보세요.");

        mvc.perform(post("/api/v1/coach/recommendation-explanation").with(user(owner.toString()))
                        .contentType(JSON).content("{\"recommendationId\":" + recommendationId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("AI"))
                .andExpect(jsonPath("$.data.degradedReason").isEmpty())
                .andExpect(jsonPath("$.data.model").value("gpt-5.4-mini"))
                .andExpect(jsonPath("$.data.message").value("매달 12,000원을 아낄 수 있는 개선안입니다.\n\n사용 기록을 확인해 보세요."))
                // 수치는 AI 경로에서도 서버가 만든다
                .andExpect(jsonPath("$.data.highlights[0]").value("영역: SPENDING"));
    }

    @Test
    void fallsBackToTemplateWhenTheCallFails() throws Exception {
        given(generator.isAvailable()).willReturn(true);
        willThrow(new CoachGenerationException("OpenAI unreachable")).given(generator).generate(any(), any());

        mvc.perform(post("/api/v1/coach/recommendation-explanation").with(user(owner.toString()))
                        .contentType(JSON).content("{\"recommendationId\":" + recommendationId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("TEMPLATE"))
                .andExpect(jsonPath("$.data.degradedReason").value("AI_UNAVAILABLE"))
                // 실패해도 수치는 그대로 내려간다 — FE는 화면을 그릴 수 있다
                .andExpect(jsonPath("$.data.facts.sections[1].facts[0].value").value("12,000원"));
    }

    @Test
    void fallsBackWhenTheModelInventsANumber() throws Exception {
        given(generator.isAvailable()).willReturn(true);
        // 12,000 × 12 = 144,000. 서버는 연 절감액을 계산하지 않았으므로 모델이 만들어 낸 값이다.
        given(generator.generate(any(), any())).willReturn("매달 12,000원, 연 144,000원을 아낄 수 있습니다.");

        mvc.perform(post("/api/v1/coach/recommendation-explanation").with(user(owner.toString()))
                        .contentType(JSON).content("{\"recommendationId\":" + recommendationId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("TEMPLATE"))
                .andExpect(jsonPath("$.data.degradedReason").value("AI_UNVERIFIED_NUMBER"));
    }

    // ---------- 응답 캐시 ----------

    @Test
    void cachesSuccessfulAiAnswersOnly() throws Exception {
        given(generator.isAvailable()).willReturn(true);
        given(generator.generate(any(), any())).willReturn("매달 12,000원을 아낄 수 있는 개선안입니다.");

        mvc.perform(post("/api/v1/coach/recommendation-explanation").with(user(owner.toString()))
                        .contentType(JSON).content("{\"recommendationId\":" + recommendationId + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.source").value("AI"));
        verify(cache).put(any(), eq(new CoachAnswerCache.Entry("매달 12,000원을 아낄 수 있는 개선안입니다.", "gpt-5.4-mini")));

        // 템플릿은 저장하지 않는다. 저장하면 공급자가 복구된 뒤에도 TTL 내내 템플릿만 나간다.
        willThrow(new CoachGenerationException("OpenAI unreachable")).given(generator).generate(any(), any());
        mvc.perform(post("/api/v1/coach/financial-summary").with(user(owner.toString())).contentType(JSON).content("{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.source").value("TEMPLATE"));
        // put이 여전히 1회 = 템플릿 응답은 저장되지 않았다
        verify(cache, times(1)).put(any(), any());
    }

    @Test
    void servesCachedAnswerWithoutCallingTheModel() throws Exception {
        given(generator.isAvailable()).willReturn(true);
        given(cache.find(any())).willReturn(
                Optional.of(new CoachAnswerCache.Entry("저장해 둔 설명입니다.", "gpt-5.4-mini")));

        mvc.perform(post("/api/v1/coach/financial-summary").with(user(owner.toString())).contentType(JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("AI"))
                .andExpect(jsonPath("$.data.degradedReason").isEmpty())
                .andExpect(jsonPath("$.data.model").value("gpt-5.4-mini"))
                .andExpect(jsonPath("$.data.message").value("저장해 둔 설명입니다."))
                // 수치는 캐시가 아니라 매번 DB에서 다시 만든다
                .andExpect(jsonPath("$.data.facts.sections[0].facts[3].value").value("2건"));
        verify(generator, never()).generate(any(), any());
    }

    @Test
    void cachedAnswerIsUsedEvenWhenTheProviderIsDown() throws Exception {
        // AI가 꺼져 있어도 이미 받아 둔 문장이 있으면 템플릿보다 그쪽이 낫다
        given(generator.isAvailable()).willReturn(false);
        given(cache.find(any())).willReturn(
                Optional.of(new CoachAnswerCache.Entry("저장해 둔 설명입니다.", "gpt-5.4-mini")));

        mvc.perform(post("/api/v1/coach/financial-summary").with(user(owner.toString())).contentType(JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("AI"))
                .andExpect(jsonPath("$.data.message").value("저장해 둔 설명입니다."));
    }

    @Test
    void fallsBackWhenTheModelReturnsNothing() throws Exception {
        given(generator.isAvailable()).willReturn(true);
        given(generator.generate(any(), any())).willReturn("   ");

        mvc.perform(post("/api/v1/coach/financial-summary").with(user(owner.toString())).contentType(JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("TEMPLATE"))
                .andExpect(jsonPath("$.data.degradedReason").value("AI_EMPTY_RESPONSE"));
    }
}
