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
        AiCoachProperties properties = new AiCoachProperties(
                true, apiKey, null, null, null, null, null, maxAttempts, null, null);
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

    @Test
    void contentTypeIsJson() {
        OpenAiCoachTextGenerator client = client("sk-test", 1);
        server.expect(requestTo(URL))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"content\":\"ok\"}}]}", MediaType.APPLICATION_JSON));

        assertThat(client.generate("규칙", "사실")).isEqualTo("ok");
        server.verify();
    }
}
