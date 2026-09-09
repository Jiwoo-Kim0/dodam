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
                         "REC":{"ratingName":"B+","demandDepositAssetValue":"3500000",
                                "depositSavingsAssetValue":"12000000","totalAssetValue":"15500000","creditScore":"720"}}
                        """, MediaType.APPLICATION_JSON));

        SsafyCreditRating rating = client.inquireMyCreditRating("user-key-1");

        assertThat(rating.ratingName()).isEqualTo("B+");
        assertThat(rating.demandDepositAssetValue()).isEqualTo(3_500_000L);
        assertThat(rating.depositSavingsAssetValue()).isEqualTo(12_000_000L);
        assertThat(rating.totalAssetValue()).isEqualTo(15_500_000L);
        assertThat(rating.creditScore()).isEqualTo(720);
        server.verify();
    }

    @Test
    void creditRatingListParsesRecArrayInOrder() {
        server.expect(requestTo(BASE_URL + "/ssafy/api/v1/edu/loan/inquireCreditRatingList"))
                .andExpect(jsonPath("$.Header.apiName").value("inquireCreditRatingList"))
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"H0000","responseMessage":"정상처리 되었습니다."},
                         "REC":[{"ratingName":"A+","demandDepositAssetValue":"100000000","ratingPercent":"5%"},
                                {"ratingName":"A","demandDepositAssetValue":"50000000","ratingPercent":"10%"}]}
                        """, MediaType.APPLICATION_JSON));

        List<SsafyCreditRatingCriterion> criteria = client.inquireCreditRatingList("user-key-1");

        assertThat(criteria).hasSize(2);
        assertThat(criteria.get(0).ratingName()).isEqualTo("A+");
        assertThat(criteria.get(0).demandDepositAssetValue()).isEqualTo(100_000_000L);
        assertThat(criteria.get(0).ratingPercent()).isEqualTo("5%");
        assertThat(criteria.get(1).ratingName()).isEqualTo("A");
    }

    @Test
    void transactionErrorResponseBecomesApiException() {
        server.expect(requestTo(BASE_URL + "/ssafy/api/v1/edu/loan/inquireMyCreditRating"))
                .andRespond(withBadRequest()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"responseCode\":\"A1003\",\"responseMessage\":\"유효하지 않은 userKey입니다.\"}"));

        assertThatThrownBy(() -> client.inquireMyCreditRating("bad-key"))
                .isInstanceOf(SsafyApiException.class)
                .extracting("responseCode").isEqualTo("A1003");
    }

    @Test
    void successStatusWithNonHeaderCodeIsTreatedAsApiError() {
        server.expect(requestTo(BASE_URL + "/ssafy/api/v1/edu/loan/inquireMyCreditRating"))
                .andRespond(withSuccess("""
                        {"Header":{"responseCode":"A1004","responseMessage":"기관거래고유번호가 중복되었습니다."}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.inquireMyCreditRating("user-key-1"))
                .isInstanceOf(SsafyApiException.class)
                .extracting("responseCode").isEqualTo("A1004");
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
        server.expect(requestTo(BASE_URL + "/ssafy/api/v1/edu/loan/inquireCreditRatingList"))
                .andRespond(withBadRequest().body("<html>gateway error</html>"));

        assertThatThrownBy(() -> client.inquireCreditRatingList("user-key-1"))
                .isInstanceOf(SsafyUnavailableException.class);
    }
}
