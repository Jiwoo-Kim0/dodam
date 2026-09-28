package com.ssafy.dodam.infrastructure.external.ai;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI 코치가 쓰는 생성형 AI 접속 설정({@code app.ai}).
 *
 * <p>{@code api-key}가 비어 있으면 호출하지 않고 템플릿 응답으로 간다(기동은 정상). 키가 없다고 앱이 죽으면
 * 로컬·테스트에서 진단·추천 API까지 함께 막히는데, 코치는 그 데이터의 표현 계층일 뿐이라 그럴 이유가 없다.
 *
 * @param enabled       false면 키가 있어도 호출하지 않는다. 비용을 끊고 싶을 때의 스위치
 * @param apiKey        {@code OPENAI_API_KEY}. 로그·응답 어디에도 실리지 않는다
 * @param baseUrl       공급자 호스트
 * @param chatPath      Chat Completions 경로
 * @param model         사용할 모델. {@code max_tokens} 파라미터를 받는 모델이어야 한다
 * @param temperature   낮게 둔다. 같은 사실에서 매번 다른 문장이 나오면 검증도 재현도 어렵다.
 *                      <b>비우면 본문에서 아예 뺀다</b> — 이 값을 받지 않는 모델이 있다
 * @param maxTokens     응답 상한. 프롬프트가 400자 이내를 요구하므로 넉넉한 값이다
 * @param maxTokensParameter 응답 상한을 싣는 필드 이름. 모델 계열마다 다르다
 *                      ({@code max_tokens} / {@code max_completion_tokens}). 모델 교체를 코드 변경 없이
 *                      하기 위한 설정이며, 이름이 틀리면 공급자가 400으로 거부한다
 * @param maxAttempts   429·5xx·네트워크 오류일 때의 총 시도 횟수(1이면 재시도 없음)
 * @param connectTimeout 연결 타임아웃
 * @param readTimeout    응답 타임아웃. 사용자가 기다리는 동기 요청이라 길게 두지 않는다
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiCoachProperties(
        Boolean enabled,
        String apiKey,
        String baseUrl,
        String chatPath,
        String model,
        Double temperature,
        Integer maxTokens,
        String maxTokensParameter,
        Integer maxAttempts,
        Duration connectTimeout,
        Duration readTimeout
) {

    public AiCoachProperties {
        enabled = enabled == null || enabled;
        apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey.trim();
        baseUrl = blankTo(baseUrl, "https://api.openai.com");
        chatPath = blankTo(chatPath, "/v1/chat/completions");
        model = blankTo(model, "gpt-4o-mini");
        // temperature는 기본값을 넣지 않는다. null이면 요청 본문에서 빼며, 값을 받지 않는 모델도 그대로 동작한다.
        maxTokens = maxTokens == null || maxTokens <= 0 ? 600 : maxTokens;
        maxTokensParameter = blankTo(maxTokensParameter, "max_tokens");
        maxAttempts = maxAttempts == null || maxAttempts < 1 ? 2 : maxAttempts;
        connectTimeout = connectTimeout == null ? Duration.ofSeconds(3) : connectTimeout;
        readTimeout = readTimeout == null ? Duration.ofSeconds(12) : readTimeout;
    }

    /** 실제 호출이 가능한 상태인지. 키가 없으면 켜져 있어도 호출하지 않는다. */
    public boolean isConfigured() {
        return enabled && apiKey != null;
    }

    public String chatCompletionsUrl() {
        return baseUrl + chatPath;
    }

    private static String blankTo(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
