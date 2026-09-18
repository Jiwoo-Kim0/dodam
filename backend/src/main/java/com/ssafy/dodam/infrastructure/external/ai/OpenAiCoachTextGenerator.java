package com.ssafy.dodam.infrastructure.external.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.coach.service.CoachGenerationException;
import com.ssafy.dodam.domain.coach.service.CoachTextGenerator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * OpenAI Chat Completions 호출. {@link CoachTextGenerator}의 유일한 구현이다.
 *
 * <p>{@link #generate}는 응답 형태를 강제하지 않고 평문을 받는다. JSON 스키마를 요구하면 모델이 구조를 맞추는 데 토큰을 쓰고,
 * 우리가 쓰는 것은 문장 하나뿐이라 얻을 것이 없다. 구조가 필요한 값(수치·판정)은 애초에 모델을 거치지 않는다.
 *
 * <p>(640 A-1) {@link #chat}은 OpenAI tool calling 형식({@code tools[].function}, {@code tool_choice}, 응답 {@code message.tool_calls[]})을
 * 그대로 보내고 받는다. SSAFY GMS 중계가 이 필드를 손대지 않고 통과시키는 것은 637에서 실측했다(ai-coach.md §8-3). 도구 실행과
 * 숫자 계산은 여기 없다 — 모델이 고른 도구 이름·인자 문자열을 도메인에 넘기는 것까지가 이 클래스의 일이다.
 *
 * <p><b>실패는 전부 {@link CoachGenerationException}으로 모은다.</b> 호출부가 공급자별 예외를 알 필요가 없고,
 * 어떤 실패든 결론이 "템플릿으로 대체"로 같기 때문이다. 구분이 필요한 것은 재시도 여부뿐이다:
 * <ul>
 *   <li>재시도 <b>한다</b> — 연결 실패·타임아웃, 429(레이트 리밋), 5xx. 다시 하면 될 수도 있는 실패다</li>
 *   <li>재시도 <b>하지 않는다</b> — 401·403(키 문제), 400(요청 문제). 같은 요청을 다시 보내도 같은 답이고,
 *       사용자를 기다리게 할 뿐이다</li>
 * </ul>
 * 재시도 간 대기를 넣지 않는다. 사용자가 화면에서 기다리는 동기 요청이라 대기를 넣으면 실패 경로의
 * 체감 지연이 타임아웃의 두 배가 된다. 대기가 필요할 만큼 레이트 리밋이 잦아지면 비동기로 바꿀 일이다.
 *
 * <p>로그에 프롬프트·응답 본문을 남기지 않는다. 프롬프트에는 회원의 재무 수치가 들어 있고, 실패 응답에는
 * 요청 일부가 되비쳐 오는 경우가 있다.
 */
@Component
public class OpenAiCoachTextGenerator implements CoachTextGenerator {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCoachTextGenerator.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final AiCoachProperties properties;

    @Autowired
    public OpenAiCoachTextGenerator(AiCoachProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, defaultBuilder(properties));
    }

    OpenAiCoachTextGenerator(AiCoachProperties properties, ObjectMapper objectMapper, RestClient.Builder builder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = builder.build();
    }

    private static RestClient.Builder defaultBuilder(AiCoachProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder().requestFactory(requestFactory);
    }

    @Override
    public boolean isAvailable() {
        return properties.isConfigured();
    }

    @Override
    public String modelName() {
        return properties.model();
    }

    @Override
    public String generate(String systemPrompt, String userPrompt) {
        if (!properties.isConfigured()) {
            throw new CoachGenerationException("AI coach is not configured");
        }
        // 필드 구성을 설정에서 받는다. 상한 필드 이름이 모델 계열마다 다르고(max_tokens /
        // max_completion_tokens), temperature를 받지 않는 모델도 있어 모델 교체가 코드 변경이 되면 안 된다.
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put(properties.maxTokensParameter(), properties.maxTokens());
        if (properties.temperature() != null) {
            body.put("temperature", properties.temperature());
        }
        body.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)));

        JsonNode message = callWithRetry(body);
        return textOf(message.path("content"));
    }

    @Override
    public ChatTurn chat(List<ChatMessage> messages, List<ToolDefinition> tools, ToolChoice toolChoice) {
        if (!properties.isConfigured()) {
            throw new CoachGenerationException("AI coach is not configured");
        }
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("메시지가 없는 대화는 보낼 수 없습니다.");
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.model());
        body.put(properties.maxTokensParameter(), properties.maxTokens());
        if (properties.temperature() != null) {
            body.put("temperature", properties.temperature());
        }
        body.put("messages", messages.stream().map(OpenAiCoachTextGenerator::toOpenAiMessage).toList());
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools.stream().map(OpenAiCoachTextGenerator::toOpenAiTool).toList());
            body.put("tool_choice", toOpenAiToolChoice(toolChoice == null ? ToolChoice.auto() : toolChoice));
        }
        JsonNode message = callWithRetry(body);
        List<ToolCall> toolCalls = new ArrayList<>();
        for (JsonNode call : message.path("tool_calls")) {
            JsonNode function = call.path("function");
            String id = textOf(call.path("id"));
            String name = textOf(function.path("name"));
            if (id.isEmpty() || name.isEmpty()) {
                // 이름이나 id가 없는 호출은 실행할 수도, 결과를 되돌려 보낼 수도 없다
                continue;
            }
            toolCalls.add(new ToolCall(id, name, textOf(function.path("arguments"))));
        }
        String finishReason = message.path("finish_reason").isTextual() ? message.path("finish_reason").textValue() : null;
        return new ChatTurn(textOf(message.path("content")), toolCalls, finishReason);
    }

    /** 재시도 정책은 {@link #generate}·{@link #chat} 공통이다. 반환은 {@code choices[0].message}에 {@code finish_reason}을 덧붙인 노드다. */
    private JsonNode callWithRetry(Map<String, Object> body) {
        CoachGenerationException last = null;
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            try {
                return message(post(body));
            } catch (RetryableGenerationException exception) {
                last = new CoachGenerationException(exception.getMessage(), exception.getCause());
                log.warn("OpenAI call failed (attempt {}/{}): {}", attempt, properties.maxAttempts(), exception.getMessage());
            }
        }
        throw last;
    }

    private static Map<String, Object> toOpenAiMessage(ChatMessage message) {
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("role", message.role());
        // OpenAI는 assistant의 tool_calls 메시지에서 content가 null인 것을 허용하지만, 그 외 역할은 문자열이어야 한다
        node.put("content", message.content() == null && message.toolCalls().isEmpty() ? "" : message.content());
        if (!message.toolCalls().isEmpty()) {
            List<Map<String, Object>> calls = new ArrayList<>();
            for (ToolCall call : message.toolCalls()) {
                Map<String, Object> function = new LinkedHashMap<>();
                function.put("name", call.name());
                function.put("arguments", call.argumentsJson() == null ? "{}" : call.argumentsJson());
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", call.id());
                entry.put("type", "function");
                entry.put("function", function);
                calls.add(entry);
            }
            node.put("tool_calls", calls);
        }
        if (message.toolCallId() != null) {
            node.put("tool_call_id", message.toolCallId());
        }
        return node;
    }

    private static Map<String, Object> toOpenAiTool(ToolDefinition tool) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", tool.name());
        if (tool.description() != null) {
            function.put("description", tool.description());
        }
        if (tool.parameters() != null) {
            function.put("parameters", tool.parameters());
        }
        Map<String, Object> node = new LinkedHashMap<>();
        node.put("type", "function");
        node.put("function", function);
        return node;
    }

    private static Object toOpenAiToolChoice(ToolChoice choice) {
        return switch (choice.mode()) {
            case AUTO -> "auto";
            case REQUIRED -> "required";
            case NONE -> "none";
            case FUNCTION -> Map.of("type", "function", "function", Map.of("name", choice.functionName()));
        };
    }

    private static String textOf(JsonNode node) {
        return node != null && node.isTextual() ? node.textValue() : "";
    }

    private RawResponse post(Map<String, Object> body) {
        try {
            return restClient.post()
                    .uri(properties.chatCompletionsUrl())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .exchange((request, response) -> new RawResponse(
                            response.getStatusCode().value(),
                            new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8)));
        } catch (ResourceAccessException exception) {
            throw new RetryableGenerationException("OpenAI unreachable: " + exception.getMessage(), exception);
        } catch (RestClientException exception) {
            throw new RetryableGenerationException("OpenAI call failed: " + exception.getMessage(), exception);
        }
    }

    private JsonNode message(RawResponse response) {
        if (response.status() == 429 || response.status() >= 500) {
            throw new RetryableGenerationException("OpenAI responded with status " + response.status());
        }
        if (response.status() < 200 || response.status() >= 300) {
            // 키 만료·권한·요청 형식 문제. 재시도해도 같으므로 한 번에 포기한다.
            throw new CoachGenerationException("OpenAI rejected the request with status " + response.status());
        }
        JsonNode parsed;
        try {
            parsed = objectMapper.readTree(response.body());
        } catch (IOException exception) {
            throw new RetryableGenerationException("OpenAI response is not JSON", exception);
        }
        JsonNode choice = parsed.path("choices").path(0);
        JsonNode message = choice.path("message");
        if (!message.isObject()) {
            // 본문이 비면 예외가 아니라 빈 메시지다. 빈 문장 판정은 CoachService가 한다(기존 계약).
            return objectMapper.createObjectNode();
        }
        ((ObjectNode) message).set("finish_reason", choice.path("finish_reason"));
        return message;
    }

    /** 재시도 가치가 있는 실패. 루프 밖으로 나가지 않으며 최종적으로 {@link CoachGenerationException}이 된다. */
    private static class RetryableGenerationException extends RuntimeException {
        RetryableGenerationException(String message) {
            super(message);
        }

        RetryableGenerationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private record RawResponse(int status, String body) {
    }
}
