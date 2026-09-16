package com.ssafy.dodam.infrastructure.external.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.coach.service.CoachGenerationException;
import com.ssafy.dodam.domain.coach.service.CoachTextGenerator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
 * <p>응답 형태를 강제하지 않고 평문을 받는다. JSON 스키마를 요구하면 모델이 구조를 맞추는 데 토큰을 쓰고,
 * 우리가 쓰는 것은 문장 하나뿐이라 얻을 것이 없다. 구조가 필요한 값(수치·판정)은 애초에 모델을 거치지 않는다.
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

        CoachGenerationException last = null;
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            try {
                return content(post(body));
            } catch (RetryableGenerationException exception) {
                last = new CoachGenerationException(exception.getMessage(), exception.getCause());
                log.warn("OpenAI call failed (attempt {}/{}): {}", attempt, properties.maxAttempts(), exception.getMessage());
            }
        }
        throw last;
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

    private String content(RawResponse response) {
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
        JsonNode message = parsed.path("choices").path(0).path("message").path("content");
        return message.isTextual() ? message.textValue() : "";
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
