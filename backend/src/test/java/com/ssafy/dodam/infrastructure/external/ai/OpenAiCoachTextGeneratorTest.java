package com.ssafy.dodam.infrastructure.external.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withUnauthorizedRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.coach.service.CoachGenerationException;
import com.ssafy.dodam.domain.coach.service.CoachTextGenerator.ChatMessage;
import com.ssafy.dodam.domain.coach.service.CoachTextGenerator.ChatTurn;
import com.ssafy.dodam.domain.coach.service.CoachTextGenerator.ToolCall;
import com.ssafy.dodam.domain.coach.service.CoachTextGenerator.ToolChoice;
import com.ssafy.dodam.domain.coach.service.CoachTextGenerator.ToolDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 외부 호출 실패 처리. 어떤 실패든 {@link CoachGenerationException} 하나로 모이고, 재시도 여부만 갈린다. */
class OpenAiCoachTextGeneratorTest {

    private static final String URL = "https://api.openai.com/v1/chat/completions";

    private MockRestServiceServer server;

    private OpenAiCoachTextGenerator client(String apiKey, int maxAttempts) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return client(apiKey, maxAttempts, 0.2, null);
    }

    private OpenAiCoachTextGenerator client(String apiKey, int maxAttempts, Double temperature, String maxTokensParameter) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        AiCoachProperties properties = new AiCoachProperties(
                true, apiKey, null, null, null, temperature, null, maxTokensParameter, maxAttempts, null, null);
        return new OpenAiCoachTextGenerator(properties, new ObjectMapper(), builder);
    }

    @Test
    void sendsPromptsAndReturnsContent() {
        OpenAiCoachTextGenerator client = client("sk-test", 2);
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer sk-test"))
                .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value("사실"))
                .andRespond(withSuccess("""
                        {"choices": [{"message": {"role": "assistant", "content": "설명입니다."}}]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.generate("규칙", "사실")).isEqualTo("설명입니다.");
        assertThat(client.modelName()).isEqualTo("gpt-4o-mini");
        server.verify();
    }

    @Test
    void doesNotRetryWhenTheKeyIsRejected() {
        OpenAiCoachTextGenerator client = client("sk-bad", 3);
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withUnauthorizedRequest());

        assertThatThrownBy(() -> client.generate("규칙", "사실"))
                .isInstanceOf(CoachGenerationException.class)
                .hasMessageContaining("401");
        server.verify();
    }

    @Test
    void retriesServerErrorsUpToMaxAttempts() {
        OpenAiCoachTextGenerator client = client("sk-test", 2);
        server.expect(ExpectedCount.times(2), requestTo(URL)).andRespond(withServerError());

        assertThatThrownBy(() -> client.generate("규칙", "사실")).isInstanceOf(CoachGenerationException.class);
        server.verify();
    }

    @Test
    void retriesRateLimitThenSucceeds() {
        OpenAiCoachTextGenerator client = client("sk-test", 2);
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withSuccess("""
                {"choices": [{"message": {"content": "설명입니다."}}]}
                """, MediaType.APPLICATION_JSON));

        assertThat(client.generate("규칙", "사실")).isEqualTo("설명입니다.");
        server.verify();
    }

    @Test
    void treatsUnreadableOrEmptyBodiesAsFailureAndEmptyText() {
        OpenAiCoachTextGenerator client = client("sk-test", 1);
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withSuccess("not json", MediaType.TEXT_PLAIN));
        assertThatThrownBy(() -> client.generate("규칙", "사실")).isInstanceOf(CoachGenerationException.class);

        OpenAiCoachTextGenerator other = client("sk-test", 1);
        server.expect(ExpectedCount.once(), requestTo(URL))
                .andRespond(withSuccess("{\"choices\": []}", MediaType.APPLICATION_JSON));
        // 본문이 비면 예외가 아니라 빈 문자열이다. 빈 문장 판정은 CoachService가 한다.
        assertThat(other.generate("규칙", "사실")).isEmpty();
    }

    @Test
    void isUnavailableWithoutAnApiKey() {
        OpenAiCoachTextGenerator client = client("   ", 2);

        assertThat(client.isAvailable()).isFalse();
        assertThatThrownBy(() -> client.generate("규칙", "사실")).isInstanceOf(CoachGenerationException.class);
    }

    /**
     * 모델 교체가 코드 변경이 되면 안 된다. 상한 필드 이름과 temperature 포함 여부를 설정으로 받는다
     * — 계열이 다른 모델은 {@code max_tokens}를 거부하거나 temperature를 받지 않는다.
     */
    @Test
    void adaptsRequestBodyToTheConfiguredModelFamily() {
        OpenAiCoachTextGenerator client = client("sk-test", 1, null, "max_completion_tokens");
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.max_completion_tokens").value(600))
                .andExpect(jsonPath("$.max_tokens").doesNotExist())
                // temperature를 비우면 본문에서 아예 빠진다
                .andExpect(jsonPath("$.temperature").doesNotExist())
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", MediaType.APPLICATION_JSON));

        assertThat(client.generate("규칙", "사실")).isEqualTo("ok");
        server.verify();
    }

    @Test
    void sendsTemperatureAndMaxTokensByDefault() {
        OpenAiCoachTextGenerator client = client("sk-test", 1);
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.temperature").value(0.2))
                .andExpect(jsonPath("$.max_tokens").value(600))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", MediaType.APPLICATION_JSON));

        assertThat(client.generate("규칙", "사실")).isEqualTo("ok");
        server.verify();
    }

    @Test
    void contentTypeIsJson() {
        OpenAiCoachTextGenerator client = client("sk-test", 1);
        server.expect(requestTo(URL))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", MediaType.APPLICATION_JSON));

        assertThat(client.generate("규칙", "사실")).isEqualTo("ok");
        server.verify();
    }

    // ---------- 640 A-1: tool calling 대화 ----------

    private static ToolDefinition spendingTool(ObjectMapper json) {
        var parameters = json.createObjectNode();
        parameters.put("type", "object");
        var properties = parameters.putObject("properties");
        properties.putObject("category").put("type", "string").put("description", "카테고리 코드").putArray("enum").add("CAFE").add("FOOD");
        properties.putObject("monthlyReduction").put("type", "integer").put("description", "월 감소액(원)");
        parameters.putArray("required").add("monthlyReduction");
        return new ToolDefinition("simulate_spending_reduction", "카테고리 소비를 줄였을 때 목표 영향", parameters);
    }

    @Test
    void chatSendsToolsInOpenAiShapeAndParsesToolCalls() {
        ObjectMapper json = new ObjectMapper();
        OpenAiCoachTextGenerator client = client("sk-test", 1);
        server.expect(requestTo(URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.model").value("gpt-4o-mini"))
                .andExpect(jsonPath("$.max_tokens").value(600))
                .andExpect(jsonPath("$.messages.length()").value(2))
                .andExpect(jsonPath("$.messages[0].role").value("system"))
                .andExpect(jsonPath("$.messages[1].content").value("카페 5만원 줄이면?"))
                .andExpect(jsonPath("$.tools[0].type").value("function"))
                .andExpect(jsonPath("$.tools[0].function.name").value("simulate_spending_reduction"))
                .andExpect(jsonPath("$.tools[0].function.parameters.properties.category.enum[0]").value("CAFE"))
                .andExpect(jsonPath("$.tool_choice").value("required"))
                .andRespond(withSuccess("""
                        {"choices": [{"finish_reason": "tool_calls", "message": {"role": "assistant", "content": null,
                          "tool_calls": [
                            {"id": "call_1", "type": "function", "function": {"name": "simulate_spending_reduction",
                              "arguments": "{\\"category\\":\\"CAFE\\",\\"monthlyReduction\\":50000}"}},
                            {"id": "", "type": "function", "function": {"name": "broken"}}
                          ]}}]}
                        """, MediaType.APPLICATION_JSON));

        ChatTurn turn = client.chat(List.of(ChatMessage.system("도리"), ChatMessage.user("카페 5만원 줄이면?")),
                List.of(spendingTool(json)), ToolChoice.required());

        assertThat(turn.hasToolCalls()).isTrue();
        assertThat(turn.content()).isEmpty();
        assertThat(turn.finishReason()).isEqualTo("tool_calls");
        // id나 이름이 없는 호출은 실행도 회신도 못 하므로 버린다
        assertThat(turn.toolCalls()).hasSize(1);
        ToolCall call = turn.toolCalls().get(0);
        assertThat(call.id()).isEqualTo("call_1");
        assertThat(call.name()).isEqualTo("simulate_spending_reduction");
        assertThat(call.argumentsJson()).isEqualTo("{\"category\":\"CAFE\",\"monthlyReduction\":50000}");
        server.verify();
    }

    @Test
    void chatReplaysAssistantToolCallsAndToolResultsInTheSecondTurn() {
        ObjectMapper json = new ObjectMapper();
        OpenAiCoachTextGenerator client = client("sk-test", 1);
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.messages[2].role").value("assistant"))
                .andExpect(jsonPath("$.messages[2].content").isEmpty())
                .andExpect(jsonPath("$.messages[2].tool_calls[0].id").value("call_1"))
                .andExpect(jsonPath("$.messages[2].tool_calls[0].type").value("function"))
                .andExpect(jsonPath("$.messages[2].tool_calls[0].function.name").value("simulate_spending_reduction"))
                .andExpect(jsonPath("$.messages[2].tool_calls[0].function.arguments").value("{\"monthlyReduction\":50000}"))
                .andExpect(jsonPath("$.messages[3].role").value("tool"))
                .andExpect(jsonPath("$.messages[3].tool_call_id").value("call_1"))
                .andExpect(jsonPath("$.messages[3].content").value("{\"advancedDays\":104}"))
                .andExpect(jsonPath("$.tool_choice").value("auto"))
                .andRespond(withSuccess("""
                        {"choices": [{"finish_reason": "stop", "message": {"role": "assistant", "content": "104일 빨라져요."}}]}
                        """, MediaType.APPLICATION_JSON));

        ToolCall previous = new ToolCall("call_1", "simulate_spending_reduction", "{\"monthlyReduction\":50000}");
        ChatTurn turn = client.chat(List.of(
                        ChatMessage.system("도리"), ChatMessage.user("카페 5만원 줄이면?"),
                        ChatMessage.assistant(null, List.of(previous)), ChatMessage.tool("call_1", "{\"advancedDays\":104}")),
                List.of(spendingTool(json)), null);

        assertThat(turn.hasToolCalls()).isFalse();
        assertThat(turn.content()).isEqualTo("104일 빨라져요.");
        assertThat(turn.finishReason()).isEqualTo("stop");
        server.verify();
    }

    @Test
    void chatWithoutToolsIsPlainChatAndForcedFunctionChoiceIsAnObject() {
        ObjectMapper json = new ObjectMapper();
        OpenAiCoachTextGenerator client = client("sk-test", 1);
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.tools").doesNotExist())
                .andExpect(jsonPath("$.tool_choice").doesNotExist())
                .andRespond(withSuccess("{\"choices\": [{\"message\": {\"content\": \"안녕하세요.\"}}]}", MediaType.APPLICATION_JSON));
        assertThat(client.chat(List.of(ChatMessage.user("안녕")), List.of(), null).content()).isEqualTo("안녕하세요.");
        server.verify();

        OpenAiCoachTextGenerator forced = client("sk-test", 1);
        server.expect(requestTo(URL))
                .andExpect(jsonPath("$.tool_choice.type").value("function"))
                .andExpect(jsonPath("$.tool_choice.function.name").value("simulate_spending_reduction"))
                .andRespond(withSuccess("{\"choices\": []}", MediaType.APPLICATION_JSON));
        ChatTurn empty = forced.chat(List.of(ChatMessage.user("카페")), List.of(spendingTool(json)),
                ToolChoice.function("simulate_spending_reduction"));
        // 빈 응답은 예외가 아니라 빈 턴이다(generate와 같은 계약)
        assertThat(empty.content()).isEmpty();
        assertThat(empty.toolCalls()).isEmpty();
        server.verify();
    }

    @Test
    void chatFollowsTheSameFailureContractAsGenerate() {
        OpenAiCoachTextGenerator client = client("sk-test", 1);
        server.expect(ExpectedCount.once(), requestTo(URL)).andRespond(withServerError());
        assertThatThrownBy(() -> client.chat(List.of(ChatMessage.user("카페")), List.of(), null))
                .isInstanceOf(CoachGenerationException.class);
        server.verify();

        OpenAiCoachTextGenerator unconfigured = client("   ", 1);
        assertThatThrownBy(() -> unconfigured.chat(List.of(ChatMessage.user("카페")), List.of(), null))
                .isInstanceOf(CoachGenerationException.class);
        assertThatThrownBy(() -> client.chat(List.of(), List.of(), null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ChatMessage.tool(" ", "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ToolChoice.function(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
