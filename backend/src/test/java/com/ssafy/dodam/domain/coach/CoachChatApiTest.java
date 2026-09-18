package com.ssafy.dodam.domain.coach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.coach.dto.CoachFacts.Fact;
import com.ssafy.dodam.domain.coach.dto.CoachFacts.Section;
import com.ssafy.dodam.domain.coach.service.CoachChatRateLimiter;
import com.ssafy.dodam.domain.coach.service.CoachChatService;
import com.ssafy.dodam.domain.coach.service.CoachChatSession;
import com.ssafy.dodam.domain.coach.service.CoachChatSessionStore;
import com.ssafy.dodam.domain.coach.service.CoachGenerationException;
import com.ssafy.dodam.domain.coach.service.CoachTextGenerator;
import com.ssafy.dodam.domain.coach.service.CoachTextGenerator.ChatMessage;
import com.ssafy.dodam.domain.coach.service.CoachTextGenerator.ChatTurn;
import com.ssafy.dodam.domain.coach.service.CoachTextGenerator.ToolCall;
import com.ssafy.dodam.domain.coach.service.CoachTextGenerator.ToolChoice;
import com.ssafy.dodam.domain.coach.service.CoachToolDispatcher;
import com.ssafy.dodam.domain.coach.service.CoachToolResult;
import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisIssue;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisStatus;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisIssueRepository;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisRepository;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 640 A-3 {@code POST /api/v1/coach/chat}. 외부 모델은 {@link CoachTextGenerator}를, 도구 실행은 {@link CoachToolDispatcher}를
 * 대체한다 — 도구 → 시뮬레이션 변환은 CoachToolDispatcherTest가, 계산은 Simulation 테스트가 맡고, 여기서는 에이전트 루프·
 * 안전장치·세션·응답 계약을 본다. 세션 저장소·예산도 Mock이다(테스트에는 Redis가 없다).
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:coachchat640;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class CoachChatApiTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 18, 12, 0);
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;
    private static final String CHAT = "/api/v1/coach/chat";
    private static final String CONVERSATION = "c_0123456789abcdef";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired DiagnosisRepository diagnoses;
    @Autowired DiagnosisIssueRepository issues;
    @MockitoBean CoachTextGenerator generator;
    @MockitoBean CoachToolDispatcher dispatcher;
    @MockitoBean CoachChatRateLimiter budget;
    @MockitoBean CoachChatSessionStore sessions;

    private Long owner;
    private Long other;
    private Long diagnosisId;
    private Long issueId;

    @BeforeEach
    void setup() {
        given(generator.isAvailable()).willReturn(true);
        given(generator.modelName()).willReturn("gpt-5.4-mini");
        given(budget.tryAcquire(any())).willReturn(true);
        given(sessions.find(any(), any())).willReturn(Optional.empty());

        owner = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "코치")).getId();
        other = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "다른 사용자")).getId();

        Diagnosis diagnosis = Diagnosis.start(owner, null, NOW);
        diagnosis.complete(1, 0, DiagnosisStatus.CAUTION, "v7-cluster-hybrid", null, NOW);
        diagnosisId = diagnoses.save(diagnosis).getDiagnosisId();
        issueId = issues.save(DiagnosisIssue.ofRule(diagnosisId, "SPENDING", "CATEGORY_SPENDING_INCREASE", IssueSeverity.HIGH,
                "카페 지출이 늘었어요", "최근 한 달 카페 지출이 이전 3개월 평균보다 늘었습니다.",
                node("{\"ratio\": 0.45}"), node("{\"ratio\": 0.2}"), null, NOW)).getIssueId();
    }

    private com.fasterxml.jackson.databind.JsonNode node(String raw) {
        try {
            return json.readTree(raw);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    // ---------- 고정 재료 ----------

    private static CoachToolResult combinedOk(String callId) {
        return CoachToolResult.ok(callId, "simulate_combined", 88L, true,
                List.of(new Section("시뮬레이션 결과(서버 계산)", List.of(
                        new Fact("시뮬레이션", "복합 시뮬레이션 (id 88)"),
                        new Fact("현재 예상 달성일(비상금 500만 원)", "2028-02-13"),
                        new Fact("실행 후 예상 달성일(비상금 500만 원)", "2027-10-15"),
                        new Fact("단축(비상금 500만 원)", "121일"),
                        new Fact("만기까지 이자 절감(수수료 제외)", "384,000원")))),
                List.of("달성일·절감액은 서버 시뮬레이션 엔진이 계산한 값이다. 새 숫자를 만들지 말 것."));
    }

    private static ChatTurn text(String content) {
        return new ChatTurn(content, List.of(), "stop");
    }

    private static ChatTurn toolCalls(ToolCall... calls) {
        return new ChatTurn("", List.of(calls), "tool_calls");
    }

    private MvcResult chat(Long userId, String body) throws Exception {
        return mvc.perform(post(CHAT).with(user(userId.toString())).contentType(JSON).content(body)).andReturn();
    }

    @SuppressWarnings("unchecked")
    private List<List<ChatMessage>> capturedMessages(int calls) {
        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(generator, times(calls)).chat(captor.capture(), anyList(), any());
        return captor.getAllValues();
    }

    // ---------- 인증·검증 ----------

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post(CHAT).contentType(JSON).content("{\"message\":\"안녕\"}")).andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsEmptyQuestionsUnknownChipsBadIdentifiersAndLongMessages() throws Exception {
        for (String body : List.of("{}", "{\"message\":\"   \"}", "{\"chipId\":\"CHIP_NOPE\"}",
                "{\"message\":\"안녕\",\"conversationId\":\"../x\"}",
                "{\"message\":\"" + "가".repeat(301) + "\"}",
                "{\"message\":\"안녕\",\"context\":{\"diagnosisId\":" + diagnosisId + "}}")) {
            mvc.perform(post(CHAT).with(user(owner.toString())).contentType(JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(generator, never()).chat(anyList(), anyList(), any());
        verify(dispatcher, never()).dispatch(any(), anyList());
    }

    // ---------- 칩 경로: 1차 호출 없음 ----------

    @Test
    void chipRunsTheMappedToolThenAsksTheModelOnceWithToolChoiceNone() throws Exception {
        given(dispatcher.dispatch(eq(owner), anyList())).willReturn(List.of(combinedOk("chip_combined")));
        given(generator.chat(anyList(), anyList(), any()))
                .willReturn(text("둘 다 하면 비상금 500만 원 목표가 2028-02-13에서 2027-10-15로 121일 빨라지고, 만기까지 이자 384,000원을 아낄 수 있어요."));

        MvcResult result = chat(owner, "{\"chipId\":\"CHIP_COMBINED\"}");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        String body = result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        var data = json.readTree(body).get("data");
        assertThat(data.get("topic").asText()).isEqualTo("CHAT");
        assertThat(data.get("source").asText()).isEqualTo("AI");
        assertThat(data.get("degradedReason").isNull()).isTrue();
        assertThat(data.get("model").asText()).isEqualTo("gpt-5.4-mini");
        assertThat(data.get("conversationId").asText()).matches("c_[0-9a-f]{16}");
        assertThat(data.get("message").asText()).contains("121일");
        assertThat(data.get("facts").get("subject").asText()).isEqualTo("복합 시뮬레이션");
        assertThat(data.get("facts").get("sections").get(0).get("facts").get(3).get("value").asText()).isEqualTo("121일");
        assertThat(data.get("toolResults")).hasSize(1);
        assertThat(data.get("toolResults").get(0).get("tool").asText()).isEqualTo("simulate_combined");
        assertThat(data.get("toolResults").get(0).get("simulationId").asLong()).isEqualTo(88L);
        assertThat(data.get("toolResults").get(0).get("created").asBoolean()).isTrue();
        assertThat(data.get("toolResults").get(0).get("error").isNull()).isTrue();
        // 클릭한 칩은 다음 질문 제안에서 빠진다
        assertThat(data.get("suggestedQuestions")).hasSize(2);
        assertThat(data.get("disclaimer").asText()).contains("금융 자문이 아닙니다");

        // 서버 고정 매핑으로 도구를 실행했다
        ArgumentCaptor<List<ToolCall>> calls = ArgumentCaptor.forClass(List.class);
        verify(dispatcher).dispatch(eq(owner), calls.capture());
        assertThat(calls.getValue()).singleElement().extracting(ToolCall::name).isEqualTo("simulate_combined");
        // 모델 호출은 2차 한 번뿐이고, tool_choice는 none이며, 메시지에는 assistant(tool_calls)+tool 결과가 있다
        ArgumentCaptor<ToolChoice> choice = ArgumentCaptor.forClass(ToolChoice.class);
        verify(generator, times(1)).chat(anyList(), anyList(), choice.capture());
        assertThat(choice.getValue().mode()).isEqualTo(ToolChoice.Mode.NONE);
        List<ChatMessage> messages = capturedMessages(1).get(0);
        assertThat(messages).extracting(ChatMessage::role).containsExactly("system", "user", "assistant", "tool");
        assertThat(messages.get(1).content()).isEqualTo("둘 다 하면?");
        assertThat(messages.get(2).toolCalls()).singleElement().extracting(ToolCall::id).isEqualTo("chip_combined");
        assertThat(messages.get(3).toolCallId()).isEqualTo("chip_combined");
        assertThat(messages.get(3).content()).contains("[시뮬레이션 결과(서버 계산)]").contains("- 단축(비상금 500만 원): 121일")
                .contains("※ 달성일·절감액은");
        // 예산은 호출 1회만큼만 깎였다
        verify(budget, times(1)).tryAcquire(owner);
        // 세션에는 사용자 문장(칩 문구)과 facts만 남는다 — 모델 응답 전문은 없다
        ArgumentCaptor<CoachChatSession> saved = ArgumentCaptor.forClass(CoachChatSession.class);
        verify(sessions).save(eq(owner), saved.capture());
        assertThat(saved.getValue().turnCount()).isEqualTo(1);
        CoachChatSession.Turn turn = saved.getValue().turns().get(0);
        assertThat(turn.userMessage()).isEqualTo("둘 다 하면?");
        assertThat(turn.facts()).contains("단축(비상금 500만 원): 121일").noneMatch(line -> line.contains("빨라지고"));
    }

    @Test
    void chipStillRunsTheToolAndSpeaksFromTheFactsWhenAiIsOff() throws Exception {
        given(generator.isAvailable()).willReturn(false);
        given(dispatcher.dispatch(eq(owner), anyList())).willReturn(List.of(combinedOk("chip_combined")));

        mvc.perform(post(CHAT).with(user(owner.toString())).contentType(JSON).content("{\"chipId\":\"CHIP_COMBINED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("TEMPLATE"))
                .andExpect(jsonPath("$.data.degradedReason").value("AI_DISABLED"))
                .andExpect(jsonPath("$.data.model").isEmpty())
                .andExpect(jsonPath("$.data.message").value(
                        "비상금 500만 원 목표는 현재 2028-02-13에서 실행 후 2027-10-15로 121일 빨라질 것으로 예상돼요. 만기까지 이자를 384,000원 아낄 수 있어요."))
                .andExpect(jsonPath("$.data.toolResults[0].simulationId").value(88));
        verify(generator, never()).chat(anyList(), anyList(), any());
        verify(budget, never()).tryAcquire(any());
    }

    // ---------- 자유 입력: 모델이 도구를 고른다 ----------

    @Test
    void freeTextLetsTheModelPickToolsAndRunsOneRoundTrip() throws Exception {
        ToolCall call = new ToolCall("call_1", "simulate_spending_reduction", "{\"category\":\"CAFE\",\"monthlyReduction\":50000}");
        given(generator.chat(anyList(), anyList(), any()))
                .willReturn(toolCalls(call))
                .willReturn(text("카페를 월 50,000원 줄이면 비상금 500만 원 목표가 121일 빨라져요."));
        given(dispatcher.dispatch(eq(owner), eq(List.of(call)))).willReturn(List.of(combinedOk("call_1")));

        mvc.perform(post(CHAT).with(user(owner.toString())).contentType(JSON)
                        .content("{\"message\":\"카페 5만원 줄이면 목표 언제 돼?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("AI"))
                // 사용자가 말한 5만원을 50,000원으로 되풀이한 것은 지어낸 숫자가 아니다
                .andExpect(jsonPath("$.data.message").value("카페를 월 50,000원 줄이면 비상금 500만 원 목표가 121일 빨라져요."))
                .andExpect(jsonPath("$.data.toolResults[0].simulationId").value(88))
                .andExpect(jsonPath("$.data.suggestedQuestions.length()").value(3));

        // 서비스는 같은 메시지 목록에 덧붙이므로 캡처된 두 목록은 같은 인스턴스다 — 최종 상태로 본다
        List<List<ChatMessage>> calls = capturedMessages(2);
        assertThat(calls.get(1)).extracting(ChatMessage::role).containsExactly("system", "user", "assistant", "tool");
        assertThat(calls.get(1).get(0).content()).contains("도리에게 묻기").doesNotContain("[화면 맥락]");
        assertThat(calls.get(1).get(2).toolCalls()).singleElement().extracting(ToolCall::id).isEqualTo("call_1");
        ArgumentCaptor<ToolChoice> choice = ArgumentCaptor.forClass(ToolChoice.class);
        verify(generator, times(2)).chat(anyList(), anyList(), choice.capture());
        assertThat(choice.getAllValues()).extracting(ToolChoice::mode).containsExactly(ToolChoice.Mode.AUTO, ToolChoice.Mode.NONE);
        // 외부 호출 2회 = 예산 2회
        verify(budget, times(2)).tryAcquire(owner);
    }

    @Test
    void answersWithoutToolsWhenTheModelDoesNotAskForAny() throws Exception {
        given(generator.chat(anyList(), anyList(), any())).willReturn(text("안녕하세요, 저축·소비·대출·목표에 대해 물어봐 주세요."));

        mvc.perform(post(CHAT).with(user(owner.toString())).contentType(JSON).content("{\"message\":\"안녕\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("AI"))
                .andExpect(jsonPath("$.data.facts.sections.length()").value(0))
                .andExpect(jsonPath("$.data.toolResults.length()").value(0));
        verify(dispatcher, never()).dispatch(any(), anyList());
        verify(generator, times(1)).chat(anyList(), anyList(), any());
    }

    @Test
    void toolValidationFailuresAreRelayedNotThrown() throws Exception {
        ToolCall call = new ToolCall("call_1", "simulate_spending_reduction", "{\"category\":\"CAFE\",\"monthlyReduction\":900000}");
        given(generator.chat(anyList(), anyList(), any()))
                .willReturn(toolCalls(call))
                .willReturn(text("카페 월평균 소비보다 큰 금액이라 계산할 수 없었어요. 줄일 금액을 다시 알려 주세요."));
        given(dispatcher.dispatch(eq(owner), anyList())).willReturn(List.of(
                CoachToolResult.error("call_1", "simulate_spending_reduction", "카페 월평균 소비(115,000원)보다 큰 금액이에요.")));

        MvcResult result = chat(owner, "{\"message\":\"카페 90만원 줄이면?\"}");

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        var data = json.readTree(result.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).get("data");
        assertThat(data.get("source").asText()).isEqualTo("AI");
        assertThat(data.get("toolResults").get(0).get("error").asText()).contains("115,000원");
        assertThat(data.get("toolResults").get(0).get("simulationId").isNull()).isTrue();
        // 실패는 facts의 별도 섹션에 사용자용 도구 이름으로 남는다(영문 도구 이름이 화면에 나가지 않는다)
        var failed = data.get("facts").get("sections").get(0);
        assertThat(failed.get("title").asText()).isEqualTo("실행하지 못한 요청");
        assertThat(failed.get("facts").get(0).get("label").asText()).isEqualTo("소비 줄이기 시뮬레이션");
        List<ChatMessage> second = capturedMessages(2).get(1);
        assertThat(second.get(3).content()).isEqualTo("실행 실패: 카페 월평균 소비(115,000원)보다 큰 금액이에요.");
    }

    // ---------- 안전장치 ----------

    @Test
    void blocksAccountNumbersTypedByTheUserBeforeAnyExternalCall() throws Exception {
        mvc.perform(post(CHAT).with(user(owner.toString())).contentType(JSON)
                        .content("{\"message\":\"110-1234-567890 계좌에서 100만원 갚으면?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("TEMPLATE"))
                .andExpect(jsonPath("$.data.degradedReason").value("SENSITIVE_DATA_BLOCKED"))
                .andExpect(jsonPath("$.data.message").value(CoachChatService.SENSITIVE_MESSAGE));
        verify(generator, never()).chat(anyList(), anyList(), any());
        verify(dispatcher, never()).dispatch(any(), anyList());
        // 번호가 든 문장은 세션에도 남기지 않는다
        verify(sessions, never()).save(any(), any());
    }

    @Test
    void fallsBackWhenTheModelInventsANumberTheUserNeverSaid() throws Exception {
        given(dispatcher.dispatch(eq(owner), anyList())).willReturn(List.of(combinedOk("chip_combined")));
        // 384,000 × 12는 아무도 준 적 없는 값이다
        given(generator.chat(anyList(), anyList(), any())).willReturn(text("121일 빨라지고 연 4,608,000원을 아껴요."));

        mvc.perform(post(CHAT).with(user(owner.toString())).contentType(JSON).content("{\"chipId\":\"CHIP_COMBINED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("TEMPLATE"))
                .andExpect(jsonPath("$.data.degradedReason").value("AI_UNVERIFIED_NUMBER"))
                // 템플릿이어도 도구 결과는 그대로 내려간다
                .andExpect(jsonPath("$.data.message").value(org.hamcrest.Matchers.containsString("121일 빨라질 것으로")))
                .andExpect(jsonPath("$.data.toolResults[0].simulationId").value(88));
    }

    @Test
    void stopsAfterOneRoundTripWhenTheModelAsksForToolsAgain() throws Exception {
        ToolCall call = new ToolCall("call_1", "simulate_combined", "{}");
        given(generator.chat(anyList(), anyList(), any()))
                .willReturn(toolCalls(call))
                .willReturn(toolCalls(new ToolCall("call_2", "get_goal_forecast", "{}")));
        given(dispatcher.dispatch(eq(owner), anyList())).willReturn(List.of(combinedOk("call_1")));

        mvc.perform(post(CHAT).with(user(owner.toString())).contentType(JSON).content("{\"message\":\"둘 다 하면?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("TEMPLATE"))
                .andExpect(jsonPath("$.data.degradedReason").value("TOOL_LOOP_EXCEEDED"))
                .andExpect(jsonPath("$.data.toolResults.length()").value(1));
        // 두 번째 tool_calls는 실행하지 않는다
        verify(dispatcher, times(1)).dispatch(any(), anyList());
        verify(generator, times(2)).chat(anyList(), anyList(), any());
    }

    @Test
    void usesTemplateWhenTheCallFailsOrTheBudgetIsGone() throws Exception {
        willThrow(new CoachGenerationException("GMS unreachable")).given(generator).chat(anyList(), anyList(), any());
        mvc.perform(post(CHAT).with(user(owner.toString())).contentType(JSON).content("{\"message\":\"안녕\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("TEMPLATE"))
                .andExpect(jsonPath("$.data.degradedReason").value("AI_UNAVAILABLE"));

        given(budget.tryAcquire(owner)).willReturn(false);
        mvc.perform(post(CHAT).with(user(owner.toString())).contentType(JSON).content("{\"message\":\"안녕\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("TEMPLATE"))
                .andExpect(jsonPath("$.data.degradedReason").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.data.message").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyString())));
        verify(generator, times(1)).chat(anyList(), anyList(), any());
    }

    // ---------- 세션 ----------

    @Test
    void replaysEarlierTurnsAsFactsAndKeepsTheConversationId() throws Exception {
        CoachChatSession earlier = CoachChatSession.start(CONVERSATION)
                .append(new CoachChatSession.Turn("카페를 월 5만원 줄이면?", List.of("단축(비상금 500만 원): 40일")), 6);
        given(sessions.find(owner, CONVERSATION)).willReturn(Optional.of(earlier));
        // 앞선 턴의 40일은 서버가 준 값이므로 이번 문장에서 되풀이해도 된다
        given(generator.chat(anyList(), anyList(), any())).willReturn(text("앞서 본 40일보다 더 빨라지려면 대출 상환도 함께 보면 좋아요."));

        mvc.perform(post(CHAT).with(user(owner.toString())).contentType(JSON)
                        .content("{\"message\":\"더 빨리 모으려면?\",\"conversationId\":\"" + CONVERSATION + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("AI"))
                .andExpect(jsonPath("$.data.conversationId").value(CONVERSATION));

        List<ChatMessage> messages = capturedMessages(1).get(0);
        assertThat(messages).extracting(ChatMessage::role).containsExactly("system", "user", "assistant", "user");
        assertThat(messages.get(1).content()).isEqualTo("카페를 월 5만원 줄이면?");
        assertThat(messages.get(2).content()).isEqualTo(CoachChatService.HISTORY_ASSISTANT_PREFIX + "단축(비상금 500만 원): 40일");
        assertThat(messages.get(3).content()).isEqualTo("더 빨리 모으려면?");
        ArgumentCaptor<CoachChatSession> saved = ArgumentCaptor.forClass(CoachChatSession.class);
        verify(sessions).save(eq(owner), saved.capture());
        assertThat(saved.getValue().conversationId()).isEqualTo(CONVERSATION);
        assertThat(saved.getValue().turnCount()).isEqualTo(2);
    }

    @Test
    void refusesMoreTurnsThanTheSessionAllows() throws Exception {
        // 테스트 설정은 세션당 3턴이다(application-test.yml)
        CoachChatSession full = CoachChatSession.start(CONVERSATION);
        for (int i = 0; i < 3; i++) {
            full = full.append(new CoachChatSession.Turn("질문 " + i, List.of()), 6);
        }
        given(sessions.find(owner, CONVERSATION)).willReturn(Optional.of(full));

        mvc.perform(post(CHAT).with(user(owner.toString())).contentType(JSON)
                        .content("{\"message\":\"하나만 더\",\"conversationId\":\"" + CONVERSATION + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("TEMPLATE"))
                .andExpect(jsonPath("$.data.degradedReason").value("SESSION_TURN_LIMIT"))
                .andExpect(jsonPath("$.data.message").value(CoachChatService.TURN_LIMIT_MESSAGE));
        verify(generator, never()).chat(anyList(), anyList(), any());
        verify(sessions, never()).save(any(), any());
    }

    // ---------- 화면 맥락 ----------

    @Test
    void contextFromTheDiscoveryScreenGoesIntoTheSystemPromptAndMustBeOwn() throws Exception {
        given(generator.chat(anyList(), anyList(), any())).willReturn(text("카페 지출을 조금 줄여 보는 것부터 시작해 보세요."));

        mvc.perform(post(CHAT).with(user(owner.toString())).contentType(JSON)
                        .content("{\"message\":\"이거 어떻게 줄여?\",\"context\":{\"diagnosisId\":" + diagnosisId + ",\"issueId\":" + issueId + "}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("AI"));
        String system = capturedMessages(1).get(0).get(0).content();
        assertThat(system).contains("[화면 맥락]").contains("카페 지출이 늘었어요");

        // 남의 진단 문제는 404 — 3종 코치와 같은 규칙(존재 여부를 드러내지 않는다)
        mvc.perform(post(CHAT).with(user(other.toString())).contentType(JSON)
                        .content("{\"message\":\"이거 어떻게 줄여?\",\"context\":{\"diagnosisId\":" + diagnosisId + ",\"issueId\":" + issueId + "}}"))
                .andExpect(status().isNotFound());
    }
}
