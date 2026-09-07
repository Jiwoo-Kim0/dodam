package com.ssafy.dodam.infrastructure.external.ssafy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * 금융망 사용자 계정 API (2.2.1 생성, 2.2.2 조회) 구현.
 * 이 두 API는 공통 Header 없이 apiKey·userId만 Body로 보낸다.
 * 오류는 HTTP 4xx Body의 {"responseCode":"E4002","responseMessage":"..."}로 온다.
 */
@Component
public class RestSsafyFinanceClient implements SsafyFinanceClient {

    private static final Logger log = LoggerFactory.getLogger(RestSsafyFinanceClient.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    public RestSsafyFinanceClient(SsafyFinanceProperties properties, ObjectMapper objectMapper) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = objectMapper;
        this.apiKey = properties.apiKey();
    }

    @Override
    public SsafyMember createMember(String email) {
        return post("/ssafy/api/v1/member/", email);
    }

    @Override
    public SsafyMember searchMember(String email) {
        return post("/ssafy/api/v1/member/search", email);
    }

    private SsafyMember post(String path, String email) {
        try {
            SsafyMember member = restClient.post()
                    .uri(path)
                    .body(Map.of("apiKey", apiKey, "userId", email))
                    .retrieve()
                    .body(SsafyMember.class);
            if (member == null || member.userKey() == null || member.userKey().isBlank()) {
                throw new SsafyUnavailableException("SSAFY finance API returned no userKey for " + path, null);
            }
            return member;
        } catch (RestClientResponseException exception) {
            throw toApiException(exception);
        } catch (ResourceAccessException exception) {
            throw new SsafyUnavailableException("SSAFY finance API unreachable: " + path, exception);
        }
    }

    private RuntimeException toApiException(RestClientResponseException exception) {
        String body = exception.getResponseBodyAsString();
        try {
            JsonNode node = objectMapper.readTree(body);
            String code = node.path("responseCode").asText(null);
            if (code != null) {
                return new SsafyApiException(code, node.path("responseMessage").asText(""));
            }
        } catch (IOException | RuntimeException ignored) {
            // 아래에서 원문 그대로 로그로 남긴다
        }
        log.warn("SSAFY finance API returned HTTP {} with unparseable body: {}", exception.getStatusCode(), body);
        return new SsafyUnavailableException(
                "SSAFY finance API returned HTTP " + exception.getStatusCode(), exception
        );
    }
}
