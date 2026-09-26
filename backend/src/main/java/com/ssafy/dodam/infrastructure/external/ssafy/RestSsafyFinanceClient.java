package com.ssafy.dodam.infrastructure.external.ssafy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
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
 *   거래 API의 Header 검증 오류(H1000~H1010)도 같은 형태이며, 정상 코드는 H0000 하나다.
 */
@Component
public class RestSsafyFinanceClient implements SsafyFinanceClient {

    private static final Logger log = LoggerFactory.getLogger(RestSsafyFinanceClient.class);

    static final String MEMBER_CREATE_PATH = "/ssafy/api/v1/member/";
    static final String MEMBER_SEARCH_PATH = "/ssafy/api/v1/member/search";
    private static final String DEPOSIT_BASE = "/ssafy/api/v1/edu/demandDeposit/";
    static final String DEMAND_DEPOSIT_ACCOUNT_LIST_PATH =
            "/ssafy/api/v1/edu/demandDeposit/inquireDemandDepositAccountList";
    static final String API_INQUIRE_DEMAND_DEPOSIT_ACCOUNT_LIST = "inquireDemandDepositAccountList";
    static final String TRANSACTION_HISTORY_LIST_PATH =
            "/ssafy/api/v1/edu/demandDeposit/inquireTransactionHistoryList";
    static final String API_INQUIRE_TRANSACTION_HISTORY_LIST = "inquireTransactionHistoryList";
    /** 2.4 거래내역 조회의 transactionType: 입금·출금 모두. */
    static final String TRANSACTION_TYPE_ALL = "A";
    static final String ORDER_BY_DESC = "DESC";
    private static final DateTimeFormatter FINANCE_DATE = DateTimeFormatter.BASIC_ISO_DATE;
    static final String CREDIT_RATING_PATH = "/ssafy/api/v1/edu/loan/inquireMyCreditRating";
    static final String CREDIT_RATING_LIST_PATH = "/ssafy/api/v1/edu/loan/inquireAssetBasedCreditRatingList";
    static final String API_INQUIRE_MY_CREDIT_RATING = "inquireMyCreditRating";
    static final String API_INQUIRE_CREDIT_RATING_LIST = "inquireAssetBasedCreditRatingList";

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
    public List<SsafyDemandDepositProduct> inquireDemandDepositList() {
        return inquireList(DEPOSIT_BASE + "inquireDemandDepositList", "inquireDemandDepositList", null,
                SsafyDemandDepositProduct.class);
    }

    @Override
    public SsafyDemandDepositProduct createDemandDeposit(String bankCode, String name, String description) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("bankCode", bankCode);
        body.put("accountTypeName", name);
        body.put("accountTypeDescription", description);
        String path = DEPOSIT_BASE + "createDemandDeposit";
        return convert(extractRec(post(path, "createDemandDeposit", null, body), path, false),
                SsafyDemandDepositProduct.class, path);
    }

    @Override
    public SsafyCreatedDemandDepositAccount createDemandDepositAccount(String userKey, String typeNo) {
        String path = DEPOSIT_BASE + "createDemandDepositAccount";
        return convert(extractRec(post(path, "createDemandDepositAccount", userKey,
                Map.of("accountTypeUniqueNo", typeNo)), path, false), SsafyCreatedDemandDepositAccount.class, path);
    }

    @Override
    public SsafyDepositResult depositToAccount(String userKey, String accountNo, long amount, String summary) {
        String path = DEPOSIT_BASE + "updateDemandDepositAccountDeposit";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountNo", accountNo);
        body.put("transactionBalance", amount);
        body.put("transactionSummary", summary);
        return convert(extractRec(post(path, "updateDemandDepositAccountDeposit", userKey, body), path, false),
                SsafyDepositResult.class, path);
    }

    @Override
    public List<SsafyTransferEntry> transfer(String userKey, String withdrawalNo, String depositNo,
            long amount, String withdrawalSummary, String depositSummary) {
        String path = DEPOSIT_BASE + "updateDemandDepositAccountTransfer";
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("withdrawalAccountNo", withdrawalNo);
        body.put("depositAccountNo", depositNo);
        body.put("transactionBalance", amount);
        body.put("withdrawalTransactionSummary", withdrawalSummary);
        body.put("depositTransactionSummary", depositSummary);
        JsonNode rec = extractRec(post(path, "updateDemandDepositAccountTransfer", userKey, body), path, false);
        if (!rec.isArray() || rec.size() != 2) {
            throw new SsafyUnavailableException("SSAFY transfer returned an invalid REC", null);
        }
        try {
            return objectMapper.readerForListOf(SsafyTransferEntry.class).readValue(rec);
        } catch (IOException exception) {
            throw new SsafyUnavailableException("SSAFY transfer REC could not be parsed", exception);
        }
    }

    @Override
    public SsafyAccountBalance inquireAccountBalance(String userKey, String accountNo) {
        String path = DEPOSIT_BASE + "inquireDemandDepositAccountBalance";
        return convert(extractRec(post(path, "inquireDemandDepositAccountBalance", userKey,
                Map.of("accountNo", accountNo)), path, false), SsafyAccountBalance.class, path);
    }

    /**
     * 2.4 수시입출금 계좌 목록 조회. 계좌가 없는 사용자는 REC가 빈 배열(또는 생략)로 오므로 빈 목록으로 해석한다.
     */
    @Override
    public List<SsafyDemandDepositAccount> inquireDemandDepositAccountList(String userKey) {
        return inquireList(
                DEMAND_DEPOSIT_ACCOUNT_LIST_PATH,
                API_INQUIRE_DEMAND_DEPOSIT_ACCOUNT_LIST,
                userKey,
                SsafyDemandDepositAccount.class
        );
    }

    /**
     * 2.4 계좌 거래내역 조회. 입금·출금을 모두 최신순으로 받는다. REC는 {totalCount, list}이며 거래가 없으면 list가 비어 있다.
     */
    @Override
    public SsafyTransactionHistory inquireTransactionHistoryList(
            String userKey,
            String accountNo,
            LocalDate startDate,
            LocalDate endDate
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("accountNo", accountNo);
        body.put("startDate", startDate.format(FINANCE_DATE));
        body.put("endDate", endDate.format(FINANCE_DATE));
        body.put("transactionType", TRANSACTION_TYPE_ALL);
        body.put("orderByType", ORDER_BY_DESC);
        JsonNode rec = extractRec(
                post(TRANSACTION_HISTORY_LIST_PATH, API_INQUIRE_TRANSACTION_HISTORY_LIST, userKey, body),
                TRANSACTION_HISTORY_LIST_PATH,
                true
        );
        if (rec == null) {
            return SsafyTransactionHistory.empty();
        }
        SsafyTransactionHistory history = convert(rec, SsafyTransactionHistory.class, TRANSACTION_HISTORY_LIST_PATH);
        return history.list() == null ? SsafyTransactionHistory.empty() : history;
    }

    @Override
    public SsafyCreditRating inquireMyCreditRating(String userKey) {
        JsonNode rec = inquire(CREDIT_RATING_PATH, API_INQUIRE_MY_CREDIT_RATING, userKey);
        return convert(rec, SsafyCreditRating.class, CREDIT_RATING_PATH);
    }

    /**
     * 앱 단위 API — 문서에 "Header: userKey 제외"로 명시되어 있어 userKey 없이 보낸다.
     */
    @Override
    public List<SsafyCreditRatingCriterion> inquireAssetBasedCreditRatingList() {
        JsonNode rec = inquire(CREDIT_RATING_LIST_PATH, API_INQUIRE_CREDIT_RATING_LIST, null);
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
     * Header만 있는 조회 요청을 보내고 REC 노드를 돌려준다. REC가 없으면 예외다.
     */
    private JsonNode inquire(String path, String apiName, String userKey) {
        JsonNode rec = extractRec(post(path, apiName, userKey, Map.of()), path, false);
        if (rec == null) {
            throw new SsafyUnavailableException("SSAFY finance API returned no REC for " + path, null);
        }
        return rec;
    }

    /**
     * 목록 API용. REC가 없거나 null이면 빈 목록, 배열이 아니면 해석 실패로 본다.
     */
    private <T> List<T> inquireList(String path, String apiName, String userKey, Class<T> elementType) {
        JsonNode rec = extractRec(post(path, apiName, userKey, Map.of()), path, true);
        if (rec == null) {
            return List.of();
        }
        if (!rec.isArray()) {
            throw new SsafyUnavailableException("SSAFY finance API returned non-array REC for " + path, null);
        }
        try {
            return objectMapper.readerForListOf(elementType).readValue(rec);
        } catch (IOException exception) {
            throw new SsafyUnavailableException("SSAFY finance API REC could not be parsed for " + path, exception);
        }
    }

    /**
     * @param extraBody Header 외에 Body에 실을 항목(계좌번호·기간 등). 순서를 유지해 문서 예시와 같은 형태로 보낸다.
     */
    private String post(String path, String apiName, String userKey, Map<String, Object> extraBody) {
        SsafyRequestHeader header = headerFactory.create(apiName, userKey);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("Header", header);
        body.putAll(extraBody);
        try {
            return restClient.post()
                    .uri(path)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException exception) {
            throw toApiException(exception);
        } catch (ResourceAccessException exception) {
            throw new SsafyUnavailableException("SSAFY finance API unreachable: " + path, exception);
        }
    }

    /**
     * @param allowMissingRec true면 REC가 없거나 null일 때 null을 돌려준다(목록 API의 "결과 없음")
     */
    private JsonNode extractRec(String body, String path, boolean allowMissingRec) {
        if (body == null || body.isBlank()) {
            throw new SsafyUnavailableException("SSAFY finance API returned empty body for " + path, null);
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (JsonProcessingException exception) {
            throw new SsafyUnavailableException("SSAFY finance API returned unparseable body for " + path, exception);
        }
        // 정상 처리는 H0000 하나뿐이다 (H1000~H1010은 Header 검증 오류). 2xx로 내려온 오류 코드도 방어적으로 처리한다.
        String responseCode = root.path("Header").path("responseCode").asText(null);
        if (responseCode != null && !SsafyErrorCode.SUCCESS.code().equals(responseCode)) {
            throw new SsafyApiException(responseCode, root.path("Header").path("responseMessage").asText(""));
        }
        JsonNode rec = root.path("REC");
        if (rec.isMissingNode() || rec.isNull()) {
            if (allowMissingRec) {
                return null;
            }
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
        log.warn("SSAFY finance API returned HTTP {} with unparseable body", exception.getStatusCode());
        return new SsafyUnavailableException(
                "SSAFY finance API returned HTTP " + exception.getStatusCode(), exception
        );
    }
}
