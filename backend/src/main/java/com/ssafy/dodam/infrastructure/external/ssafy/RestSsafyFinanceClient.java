package com.ssafy.dodam.infrastructure.external.ssafy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * SSAFY 교육용 금융망 OPEN API 구현.
 *
 * - 사용자 계정 API(2.2.1 생성, 2.2.2 조회)는 공통 Header 없이 apiKey·userId만 Body로 보낸다.
 * - 거래 API는 {"Header": {...}} 형태의 공통 Header를 보내고 {"Header": {...}, "REC": ...}로 받는다.
 *   REC는 API에 따라 객체 또는 배열이다.
 * - 오류는 HTTP 4xx Body의 {"responseCode":"E4002","responseMessage":"..."}로 온다.
 */
@Component
public class RestSsafyFinanceClient implements SsafyFinanceClient {

    private static final Logger log = LoggerFactory.getLogger(RestSsafyFinanceClient.class);

    static final String MEMBER_CREATE_PATH = "/ssafy/api/v1/member/";
    static final String MEMBER_SEARCH_PATH = "/ssafy/api/v1/member/search";
    static final String CREDIT_RATING_PATH = "/ssafy/api/v1/edu/loan/inquireMyCreditRating";
    static final String CREDIT_RATING_LIST_PATH = "/ssafy/api/v1/edu/loan/inquireCreditRatingList";
    static final String API_INQUIRE_MY_CREDIT_RATING = "inquireMyCreditRating";
    static final String API_INQUIRE_CREDIT_RATING_LIST = "inquireCreditRatingList";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final SsafyHeaderFactory headerFactory;
    private final String apiKey;

    @Autowired
    public RestSsafyFinanceClient(
            SsafyFinanceProperties properties,
            ObjectMapper objectMapper,
            SsafyHeaderFactory headerFactory
    ) {
        this(properties, objectMapper, headerFactory, defaultBuilder(properties));
    }

    RestSsafyFinanceClient(
            SsafyFinanceProperties properties,
            ObjectMapper objectMapper,
            SsafyHeaderFactory headerFactory,
            RestClient.Builder builder
    ) {
        this.restClient = builder
                .baseUrl(properties.baseUrl())
                .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.objectMapper = objectMapper;
        this.headerFactory = headerFactory;
        this.apiKey = properties.apiKey();
    }

    private static RestClient.Builder defaultBuilder(SsafyFinanceProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        return RestClient.builder().requestFactory(requestFactory);
    }

    // ---------- 사용자 계정 API (Header 없음) ----------

    @Override
    public SsafyMember createMember(String email) {
        return postMember(MEMBER_CREATE_PATH, email);
    }

    @Override
    public SsafyMember searchMember(String email) {
        return postMember(MEMBER_SEARCH_PATH, email);
    }

    private SsafyMember postMember(String path, String email) {
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

    // ---------- 거래 API (공통 Header) ----------

    @Override
    public SsafyCreditRating inquireMyCreditRating(String userKey) {
        JsonNode rec = inquire(CREDIT_RATING_PATH, API_INQUIRE_MY_CREDIT_RATING, userKey);
        return convert(rec, SsafyCreditRating.class, CREDIT_RATING_PATH);
    }

    @Override
    public List<SsafyCreditRatingCriterion> inquireCreditRatingList(String userKey) {
        JsonNode rec = inquire(CREDIT_RATING_LIST_PATH, API_INQUIRE_CREDIT_RATING_LIST, userKey);
        if (!rec.isArray()) {
            throw new SsafyUnavailableException("SSAFY finance API returned non-array REC for " + CREDIT_RATING_LIST_PATH, null);
        }
        try {
            return objectMapper.readerForListOf(SsafyCreditRatingCriterion.class).readValue(rec);
        } catch (IOException exception) {
            throw new SsafyUnavailableException("SSAFY finance API REC could not be parsed for " + CREDIT_RATING_LIST_PATH, exception);
        }
    }

    /**
     * Header만 있는 조회 요청을 보내고 REC 노드를 돌려준다.
     */
    private JsonNode inquire(String path, String apiName, String userKey) {
        SsafyRequestHeader header = headerFactory.create(apiName, userKey);
        try {
            String body = restClient.post()
                    .uri(path)
                    .body(Map.of("Header", header))
                    .retrieve()
                    .body(String.class);
            return extractRec(body, path);
        } catch (RestClientResponseException exception) {
            throw toApiException(exception);
        } catch (ResourceAccessException exception) {
            throw new SsafyUnavailableException("SSAFY finance API unreachable: " + path, exception);
        }
    }

    private JsonNode extractRec(String body, String path) {
        if (body == null || body.isBlank()) {
            throw new SsafyUnavailableException("SSAFY finance API returned empty body for " + path, null);
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException exception) {
            throw new SsafyUnavailableException("SSAFY finance API returned unparseable body for " + path, exception);
        }
        // 정상 처리는 H0000. 2xx로 내려온 오류 코드도 방어적으로 처리한다.
        String responseCode = root.path("Header").path("responseCode").asText(null);
        if (responseCode != null && !responseCode.startsWith("H")) {
            throw new SsafyApiException(responseCode, root.path("Header").path("responseMessage").asText(""));
        }
        JsonNode rec = root.path("REC");
        if (rec.isMissingNode() || rec.isNull()) {
            throw new SsafyUnavailableException("SSAFY finance API returned no REC for " + path, null);
        }
        return rec;
    }

    private <T> T convert(JsonNode rec, Class<T> type, String path) {
        try {
            return objectMapper.treeToValue(rec, type);
        } catch (JsonProcessingException exception) {
            throw new SsafyUnavailableException("SSAFY finance API REC could not be parsed for " + path, exception);
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
