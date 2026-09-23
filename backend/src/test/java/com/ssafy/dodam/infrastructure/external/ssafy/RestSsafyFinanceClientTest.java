package com.ssafy.dodam.infrastructure.external.ssafy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * 금융망 거래 API 요청 형식(공통 Header)과 응답(REC) 해석을 검증한다. 실제 네트워크는 타지 않는다.
 */
class RestSsafyFinanceClientTest {

    private static final String BASE_URL = "https://finopenapi.ssafy.io";
    private static final SsafyFinanceProperties PROPERTIES = new SsafyFinanceProperties(
            BASE_URL, "test-api-key", Duration.ofSeconds(3), Duration.ofSeconds(5)
    );

    private MockRestServiceServer server;
    private RestSsafyFinanceClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        Clock fixed = Clock.fixed(Instant.parse("2026-09-09T09:30:45Z"), ZoneId.of("Asia/Seoul"));
        client = new RestSsafyFinanceClient(
                PROPERTIES, new ObjectMapper(), new SsafyHeaderFactory(PROPERTIES, fixed), builder
        );
    }

    @Test
    void createMemberPostsApiKeyAndEmailWithoutHeader() {
        server.expect(requestTo(BASE_URL + "/ssafy/api/v1/member/"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.apiKey").value("test-api-key"))
                .andExpect(jsonPath("$.userId").value("dodam@example.com"))
                .andExpect(jsonPath("$.Header").doesNotExist())
                .andRespond(withSuccess("""
                        {"userId":"dodam@example.com","userName":"dodam","institutionCode":"00100",
                         "userKey":"user-key-1","created":"2026-09-09T18:30:45","modified":"2026-09-09T18:30:45"}
                        """, MediaType.APPLICATION_JSON));

        SsafyMember member = client.createMember("dodam@example.com");

        assertThat(member.userKey()).isEqualTo("user-key-1");
        server.verify();
    }

    @Test
    void memberErrorResponseBecomesApiException() {
        server.expect(requestTo(BASE_URL + "/ssafy/api/v1/member/"))
                .andRespond(withBadRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"responseCode\":\"E4002\",\"responseMessage\":\"이미 존재하는 ID입니다.\"}"));

        assertThatThrownBy(() -> client.createMember("dodam@example.com"))
                .isInstanceOf(SsafyApiException.class)
                .satisfies(exception -> assertThat(((SsafyApiException) exception).is(SsafyErrorCode.MEMBER_ALREADY_EXISTS)).isTrue());
    }

    @Test
    void creditRatingInquirySendsCommonHeaderAndParsesRec() {
        server.expect(requestTo(BASE_URL + "/ssafy/api/v1/edu/loan/inquireMyCreditRating"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.Header.apiName").value("inquireMyCreditRating"))
                .andExpect(jsonPath("$.Header.apiServiceCode").value("inquireMyCreditRating"))
                .andExpect(jsonPath("$.Header.transmissionDate").value("20260909"))
                .andExpect(jsonPath("$.Header.transmissionTime").value("183045"))
                .andExpect(jsonPath("$.Header.institutionCode").value("00100"))
                .andExpect(jsonPath("$.Header.fintechAppNo").value("001"))
                .andExpect(jsonPath("$.Header.institutionTransactionUniqueNo")
                        .value(Matchers.matchesRegex("20260909183045\\d{6}")))
                .andExpect(jsonPath("$.Header.apiKey").value("test-api-key"))
                .andExpect(jsonPath("$.Header.userKey").value("user-key-1"))
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000","responseMessage":"정상처리 되었습니다.",
                                   "apiName":"inquireMyCreditRating","institutionTransactionUniqueNo":"20260909183045123456"},
                         "REC":{"ratingName":"A","demandDepositAssetValue":"1089427976",
                                "depositSavingsAssetValue":"0","totalAssetValue":"1089427976"}}
                        """, MediaType.APPLICATION_JSON));

        SsafyCreditRating rating = client.inquireMyCreditRating("user-key-1");

        // API 문서 2.7.4 응답 예시 그대로: Long 항목이 문자열로 내려온다
        assertThat(rating.ratingName()).isEqualTo("A");
        assertThat(rating.demandDepositAssetValue()).isEqualTo(1_089_427_976L);
        assertThat(rating.depositSavingsAssetValue()).isZero();
        assertThat(rating.totalAssetValue()).isEqualTo(1_089_427_976L);
        server.verify();
    }

    @Test
    void creditRatingCriteriaIsAppLevelCallWithoutUserKeyAndParsesRecArrayInOrder() {
        server.expect(requestTo(BASE_URL + "/ssafy/api/v1/edu/loan/inquireAssetBasedCreditRatingList"))
                .andExpect(jsonPath("$.Header.apiName").value("inquireAssetBasedCreditRatingList"))
                .andExpect(jsonPath("$.Header.apiServiceCode").value("inquireAssetBasedCreditRatingList"))
                .andExpect(jsonPath("$.Header.apiKey").value("test-api-key"))
                // 문서 2.7.1: "Header 공통 — userKey 제외". 키 자체가 없어야 한다
                .andExpect(jsonPath("$.Header.userKey").doesNotExist())
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000","responseMessage":"정상처리 되었습니다."},
                         "REC":[{"ratingUniqueNo":"RT-0fa85f6425e811ea4","ratingName":"A","minAssetValue":"100000000","maxAssetValue":"2000000000"},
                                {"ratingUniqueNo":"RT-2gwxr5125e640552a","ratingName":"B","minAssetValue":"80000000","maxAssetValue":"99999999"},
                                {"ratingUniqueNo":"RT-a335h7aa7f3x74ag5","ratingName":"E","minAssetValue":"0","maxAssetValue":"29999999"}]}
                        """, MediaType.APPLICATION_JSON));

        List<SsafyCreditRatingCriterion> criteria = client.inquireAssetBasedCreditRatingList();

        assertThat(criteria).hasSize(3);
        assertThat(criteria.get(0).ratingUniqueNo()).isEqualTo("RT-0fa85f6425e811ea4");
        assertThat(criteria.get(0).ratingName()).isEqualTo("A");
        assertThat(criteria.get(0).minAssetValue()).isEqualTo(100_000_000L);
        assertThat(criteria.get(0).maxAssetValue()).isEqualTo(2_000_000_000L);
        assertThat(criteria.get(2).ratingName()).isEqualTo("E");
        assertThat(criteria.get(2).minAssetValue()).isZero();
        server.verify();
    }

    @Test
    void demandDepositAccountListSendsUserKeyAndParsesRecArray() {
        server.expect(requestTo(BASE_URL + "/ssafy/api/v1/edu/demandDeposit/inquireDemandDepositAccountList"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.Header.apiName").value("inquireDemandDepositAccountList"))
                .andExpect(jsonPath("$.Header.apiServiceCode").value("inquireDemandDepositAccountList"))
                .andExpect(jsonPath("$.Header.apiKey").value("test-api-key"))
                .andExpect(jsonPath("$.Header.userKey").value("user-key-1"))
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000","responseMessage":"정상처리 되었습니다."},
                         "REC":[{"bankCode":"001","bankName":"한국은행","userName":"도담","accountNo":"0011234567890001",
                                 "accountName":"한국은행 수시입출금 상품","accountTypeCode":"1","accountTypeName":"수시입출금",
                                 "accountCreatedDate":"20260901","accountExpiryDate":"20310901",
                                 "dailyTransferLimit":"500000000","oneTimeTransferLimit":"100000000",
                                 "accountBalance":"1500000","lastTransactionDate":"20260910","currency":"KRW"},
                                {"bankCode":"004","bankName":"국민은행","userName":"도담","accountNo":"0041234567890002",
                                 "accountName":"KB 통장","accountTypeCode":"1","accountTypeName":"수시입출금",
                                 "accountCreatedDate":"20260902","accountExpiryDate":"20310902",
                                 "dailyTransferLimit":"500000000","oneTimeTransferLimit":"100000000",
                                 "accountBalance":"0","lastTransactionDate":"","currency":"KRW"}]}
                        """, MediaType.APPLICATION_JSON));

        List<SsafyDemandDepositAccount> accounts = client.inquireDemandDepositAccountList("user-key-1");

        assertThat(accounts).hasSize(2);
        assertThat(accounts.get(0).accountNo()).isEqualTo("0011234567890001");
        assertThat(accounts.get(0).accountBalance()).isEqualTo(1_500_000L);
        assertThat(accounts.get(0).lastTransactionDate()).isEqualTo("20260910");
        assertThat(accounts.get(1).accountBalance()).isZero();
        assertThat(accounts.get(1).lastTransactionDate()).isEmpty();
        server.verify();
    }

    @Test
    void demandDepositProductAndAccountMethodsUseExpectedHeadersAndRecShapes() {
        String base = BASE_URL + "/ssafy/api/v1/edu/demandDeposit/";
        server.expect(requestTo(base + "inquireDemandDepositList"))
                .andExpect(jsonPath("$.Header.userKey").doesNotExist())
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000"},"REC":[{"accountTypeUniqueNo":"TYPE-1","bankCode":"004"}]}
                        """, MediaType.APPLICATION_JSON));
        assertThat(client.inquireDemandDepositList()).singleElement()
                .extracting(SsafyDemandDepositProduct::accountTypeUniqueNo).isEqualTo("TYPE-1");

        server.verify();
        server.reset();

        server.expect(requestTo(base + "createDemandDeposit"))
                .andExpect(jsonPath("$.bankCode").value("004"))
                .andExpect(jsonPath("$.accountTypeName").value("목표통장"))
                .andExpect(jsonPath("$.Header.userKey").doesNotExist())
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000"},"REC":{"accountTypeUniqueNo":"TYPE-2"}}
                        """, MediaType.APPLICATION_JSON));
        assertThat(client.createDemandDeposit("004", "목표통장", "설명").accountTypeUniqueNo()).isEqualTo("TYPE-2");

        server.verify();
        server.reset();

        server.expect(requestTo(base + "createDemandDepositAccount"))
                .andExpect(jsonPath("$.Header.userKey").value("user-key-1"))
                .andExpect(jsonPath("$.accountTypeUniqueNo").value("TYPE-2"))
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000"},"REC":{"accountNo":"1234567890123456","accountBalance":"0"}}
                        """, MediaType.APPLICATION_JSON));
        assertThat(client.createDemandDepositAccount("user-key-1", "TYPE-2").accountNo())
                .isEqualTo("1234567890123456");
        server.verify();
    }

    @Test
    void depositTransferAndBalanceMethodsParseRecAndKeepAccountNumbersOutOfErrors() {
        String base = BASE_URL + "/ssafy/api/v1/edu/demandDeposit/";
        server.expect(requestTo(base + "updateDemandDepositAccountDeposit"))
                .andExpect(jsonPath("$.transactionBalance").value(1000))
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000"},"REC":{"transactionUniqueNo":"D-1","transactionBalance":"1000"}}
                        """, MediaType.APPLICATION_JSON));
        assertThat(client.depositToAccount("user-key-1", "1234567890123456", 1000, "준비")
                .transactionUniqueNo()).isEqualTo("D-1");

        server.verify();
        server.reset();

        server.expect(requestTo(base + "updateDemandDepositAccountTransfer"))
                .andExpect(jsonPath("$.withdrawalAccountNo").value("1234567890123456"))
                .andExpect(jsonPath("$.depositAccountNo").value("1111222233334444"))
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000"},"REC":[
                          {"transactionUniqueNo":"OUT-1","transactionType":"2","transactionBalance":"1000"},
                          {"transactionUniqueNo":"IN-1","transactionType":"1","transactionBalance":"1000"}]}
                        """, MediaType.APPLICATION_JSON));
        assertThat(client.transfer("user-key-1", "1234567890123456", "1111222233334444",
                1000, "출금", "입금")).extracting(SsafyTransferEntry::transactionUniqueNo)
                .containsExactly("OUT-1", "IN-1");

        server.verify();
        server.reset();

        server.expect(requestTo(base + "inquireDemandDepositAccountBalance"))
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000"},"REC":{"accountNo":"1234567890123456","accountBalance":"2000"}}
                        """, MediaType.APPLICATION_JSON));
        assertThat(client.inquireAccountBalance("user-key-1", "1234567890123456").accountBalance()).isEqualTo(2000);
        server.verify();
    }

    @Test
    void financeErrorMessageDoesNotRetainAccountNumber() {
        server.expect(requestTo(BASE_URL + "/ssafy/api/v1/edu/demandDeposit/updateDemandDepositAccountTransfer"))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                        .body("{\"responseCode\":\"A1014\",\"responseMessage\":\"1234567890123456 잔액 부족\"}"));
        assertThatThrownBy(() -> client.transfer("user-key-1", "1234567890123456", "1111222233334444",
                1000, "출금", "입금"))
                .isInstanceOf(SsafyApiException.class)
                .hasMessageNotContaining("1234567890123456");
        server.verify();
    }

    @Test
    void demandDepositAccountListWithoutRecIsEmpty() {
        server.expect(requestTo(BASE_URL + "/ssafy/api/v1/edu/demandDeposit/inquireDemandDepositAccountList"))
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000","responseMessage":"정상처리 되었습니다."}}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.inquireDemandDepositAccountList("user-key-1")).isEmpty();

        server.reset();
        server.expect(requestTo(BASE_URL + "/ssafy/api/v1/edu/demandDeposit/inquireDemandDepositAccountList"))
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000","responseMessage":"정상처리 되었습니다."},"REC":[]}
                        """, MediaType.APPLICATION_JSON));

        assertThat(client.inquireDemandDepositAccountList("user-key-1")).isEmpty();
    }

    @Test
    void demandDepositAccountListWithObjectRecIsUnavailable() {
        server.expect(requestTo(BASE_URL + "/ssafy/api/v1/edu/demandDeposit/inquireDemandDepositAccountList"))
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000"},"REC":{"bankCode":"001"}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.inquireDemandDepositAccountList("user-key-1"))
                .isInstanceOf(SsafyUnavailableException.class);
    }

    @Test
    void transactionHistoryListSendsAccountAndPeriodInBodyAndParsesList() {
        server.expect(requestTo(BASE_URL + "/ssafy/api/v1/edu/demandDeposit/inquireTransactionHistoryList"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.Header.apiName").value("inquireTransactionHistoryList"))
                .andExpect(jsonPath("$.Header.apiServiceCode").value("inquireTransactionHistoryList"))
                .andExpect(jsonPath("$.Header.userKey").value("user-key-1"))
                .andExpect(jsonPath("$.accountNo").value("0011234567890001"))
                .andExpect(jsonPath("$.startDate").value("20250912"))
                .andExpect(jsonPath("$.endDate").value("20260911"))
                .andExpect(jsonPath("$.transactionType").value("A"))
                .andExpect(jsonPath("$.orderByType").value("DESC"))
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000","responseMessage":"정상처리 되었습니다."},
                         "REC":{"totalCount":"2",
                                "list":[{"transactionUniqueNo":"1001","transactionDate":"20260910","transactionTime":"123045",
                                         "transactionType":"2","transactionTypeName":"출금","transactionAccountNo":"",
                                         "transactionBalance":"12000","transactionAfterBalance":"1488000",
                                         "transactionSummary":"스타벅스","transactionMemo":""},
                                        {"transactionUniqueNo":"1000","transactionDate":"20260905","transactionTime":"090000",
                                         "transactionType":"1","transactionTypeName":"입금","transactionAccountNo":"0041111111111111",
                                         "transactionBalance":"1500000","transactionAfterBalance":"1500000",
                                         "transactionSummary":"급여","transactionMemo":"9월 급여"}]}}
                        """, MediaType.APPLICATION_JSON));

        SsafyTransactionHistory history = client.inquireTransactionHistoryList(
                "user-key-1", "0011234567890001", LocalDate.of(2025, 9, 12), LocalDate.of(2026, 9, 11));

        assertThat(history.totalCount()).isEqualTo(2L);
        assertThat(history.list()).hasSize(2);
        assertThat(history.list().get(0).transactionUniqueNo()).isEqualTo("1001");
        assertThat(history.list().get(0).transactionType()).isEqualTo("2");
        assertThat(history.list().get(0).transactionBalance()).isEqualTo(12_000L);
        assertThat(history.list().get(0).transactionAfterBalance()).isEqualTo(1_488_000L);
        assertThat(history.list().get(1).transactionMemo()).isEqualTo("9월 급여");
        server.verify();
    }

    @Test
    void transactionHistoryListWithoutRecOrListIsEmpty() {
        server.expect(requestTo(BASE_URL + "/ssafy/api/v1/edu/demandDeposit/inquireTransactionHistoryList"))
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000"}}
                        """, MediaType.APPLICATION_JSON));
        assertThat(client.inquireTransactionHistoryList("user-key-1", "0011234567890001",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 11)).list()).isEmpty();

        server.reset();
        server.expect(requestTo(BASE_URL + "/ssafy/api/v1/edu/demandDeposit/inquireTransactionHistoryList"))
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000"},"REC":{"totalCount":"0","list":[]}}
                        """, MediaType.APPLICATION_JSON));
        assertThat(client.inquireTransactionHistoryList("user-key-1", "0011234567890001",
                LocalDate.of(2026, 1, 1), LocalDate.of(2026, 9, 11)).list()).isEmpty();
    }

    @Test
    void transactionErrorResponseBecomesApiException() {
        server.expect(requestTo(BASE_URL + "/ssafy/api/v1/edu/loan/inquireMyCreditRating"))
                .andRespond(withBadRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"responseCode\":\"H1009\",\"responseMessage\":\"USER_KEY가 유효하지 않습니다.\"}"));

        assertThatThrownBy(() -> client.inquireMyCreditRating("bad-key"))
                .isInstanceOf(SsafyApiException.class)
                .satisfies(exception -> assertThat(((SsafyApiException) exception).is(SsafyErrorCode.INVALID_USER_KEY)).isTrue());
    }

    @Test
    void successStatusWithHeaderErrorCodeIsTreatedAsApiError() {
        // Header 검증 오류 코드도 H로 시작한다(H1000~H1010). 정상은 H0000 하나뿐이어야 한다
        server.expect(requestTo(BASE_URL + "/ssafy/api/v1/edu/loan/inquireMyCreditRating"))
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H1007","responseMessage":"기관거래고유번호가 중복된 값입니다."}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.inquireMyCreditRating("user-key-1"))
                .isInstanceOf(SsafyApiException.class)
                .satisfies(exception -> assertThat(((SsafyApiException) exception).is(SsafyErrorCode.DUPLICATE_TRANSACTION_NO)).isTrue());
    }

    @Test
    void missingRecIsUnavailable() {
        server.expect(requestTo(BASE_URL + "/ssafy/api/v1/edu/loan/inquireMyCreditRating"))
                .andRespond(withSuccess("{\"Header\":{\"responseCode\":\"H0000\"}}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.inquireMyCreditRating("user-key-1"))
                .isInstanceOf(SsafyUnavailableException.class);
    }

    @Test
    void unparseableErrorBodyIsUnavailable() {
        server.expect(requestTo(BASE_URL + "/ssafy/api/v1/edu/loan/inquireAssetBasedCreditRatingList"))
                .andRespond(withBadRequest().body("<html>gateway error</html>"));

        assertThatThrownBy(() -> client.inquireAssetBasedCreditRatingList())
                .isInstanceOf(SsafyUnavailableException.class);
    }
}
