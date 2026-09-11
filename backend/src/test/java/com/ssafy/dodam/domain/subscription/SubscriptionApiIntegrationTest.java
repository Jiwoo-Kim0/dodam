package com.ssafy.dodam.domain.subscription;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

/**
 * S15P21D105-512 정기결제 목록·상세·이력·요약.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Sql(scripts = "/sql/subscription-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class SubscriptionApiIntegrationTest extends SubscriptionApiTestSupport {

    @BeforeEach
    void seed() {
        seedDefaultSubscriptions();
        seedNetflixHistory();
    }

    // ---------- 목록 ----------

    @Test
    void listsSubscriptionsOrderedByStatusThenNextPaymentDate() throws Exception {
        mockMvc.perform(get("/api/v1/subscriptions").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(6))
                .andExpect(jsonPath("$.data.content", hasSize(6)))
                .andExpect(jsonPath("$.data.content[0].subscriptionId").value(ICLOUD))
                .andExpect(jsonPath("$.data.content[0].daysUntilNextPayment").value(2))
                .andExpect(jsonPath("$.data.content[0].cardId").value(nullValue()))
                .andExpect(jsonPath("$.data.content[0].cardName").value(nullValue()))
                .andExpect(jsonPath("$.data.content[1].subscriptionId").value(NETFLIX))
                .andExpect(jsonPath("$.data.content[1].name").value("넷플릭스"))
                .andExpect(jsonPath("$.data.content[1].category").value("VIDEO"))
                .andExpect(jsonPath("$.data.content[1].categoryLabel").value("영상"))
                .andExpect(jsonPath("$.data.content[1].paymentAmount").value(17_000))
                .andExpect(jsonPath("$.data.content[1].monthlyEquivalentAmount").value(17_000))
                .andExpect(jsonPath("$.data.content[1].billingCycle").value("MONTHLY"))
                .andExpect(jsonPath("$.data.content[1].nextPaymentDate").value(today.plusDays(20).toString()))
                .andExpect(jsonPath("$.data.content[1].daysUntilNextPayment").value(20))
                .andExpect(jsonPath("$.data.content[1].cardId").value(cardId))
                .andExpect(jsonPath("$.data.content[1].cardName").value("도담 체크"))
                .andExpect(jsonPath("$.data.content[1].cardNoMasked").value("5***-****-****-1234"))
                .andExpect(jsonPath("$.data.content[1].status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.content[2].subscriptionId").value(GYM))
                .andExpect(jsonPath("$.data.content[2].monthlyEquivalentAmount").value(25_000))
                .andExpect(jsonPath("$.data.content[2].paymentDay").value(nullValue()))
                .andExpect(jsonPath("$.data.content[3].subscriptionId").value(SPOTIFY))
                .andExpect(jsonPath("$.data.content[3].status").value("PAUSED"))
                .andExpect(jsonPath("$.data.content[3].nextPaymentDate").value(nullValue()))
                .andExpect(jsonPath("$.data.content[3].daysUntilNextPayment").value(nullValue()))
                .andExpect(jsonPath("$.data.content[4].subscriptionId").value(WATCHA))
                .andExpect(jsonPath("$.data.content[4].status").value("EXPIRED"))
                .andExpect(jsonPath("$.data.content[4].endDate").value(today.minusDays(30).toString()))
                .andExpect(jsonPath("$.data.content[5].subscriptionId").value(MILLIE))
                .andExpect(jsonPath("$.data.content[5].status").value("CANCELED"))
                .andExpect(jsonPath("$.data.content[5].canceledAt").isNotEmpty());
    }

    @Test
    void filtersAndPagesSubscriptions() throws Exception {
        mockMvc.perform(get("/api/v1/subscriptions").param("status", "active").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[*].status").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("ACTIVE"))));

        mockMvc.perform(get("/api/v1/subscriptions").param("category", "video").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].subscriptionId").value(NETFLIX))
                .andExpect(jsonPath("$.data.content[1].subscriptionId").value(WATCHA));

        mockMvc.perform(get("/api/v1/subscriptions").param("page", "1").param("size", "4").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.last").value(true));

        mockMvc.perform(get("/api/v1/subscriptions").param("status", "DONE").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/v1/subscriptions").param("size", "101").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/subscriptions").header(HttpHeaders.AUTHORIZATION, otherBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].subscriptionId").value(OTHER_NETFLIX));

        mockMvc.perform(get("/api/v1/subscriptions"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 상세 ----------

    @Test
    void getsDetailWithPaymentStatsAndHidesOthers() throws Exception {
        mockMvc.perform(get("/api/v1/subscriptions/{id}", NETFLIX).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscription.subscriptionId").value(NETFLIX))
                .andExpect(jsonPath("$.data.subscription.cardName").value("도담 체크"))
                .andExpect(jsonPath("$.data.payments.paymentCount").value(7))
                .andExpect(jsonPath("$.data.payments.successCount").value(6))
                .andExpect(jsonPath("$.data.payments.failedCount").value(1))
                .andExpect(jsonPath("$.data.payments.totalPaidAmount").value(102_000))
                .andExpect(jsonPath("$.data.payments.averagePaidAmount").value(17_000))
                .andExpect(jsonPath("$.data.payments.firstPaymentDate").value(today.minusDays(400).toString()))
                .andExpect(jsonPath("$.data.payments.lastPaymentDate").value(today.minusDays(10).toString()))
                .andExpect(jsonPath("$.data.payments.lastResultCode").value("SUCCESS"));

        mockMvc.perform(get("/api/v1/subscriptions/{id}", SPOTIFY).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.payments.paymentCount").value(0))
                .andExpect(jsonPath("$.data.payments.averagePaidAmount").value(nullValue()))
                .andExpect(jsonPath("$.data.payments.lastPaymentDate").value(nullValue()));

        mockMvc.perform(get("/api/v1/subscriptions/{id}", NETFLIX).header(HttpHeaders.AUTHORIZATION, otherBearer))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/subscriptions/{id}", "SUB-NOPE").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/subscriptions/{id}", "bad id!").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---------- 이력 ----------

    @Test
    void listsHistoryWithinQueryPeriodPolicy() throws Exception {
        mockMvc.perform(get("/api/v1/subscriptions/{id}/history", NETFLIX).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.content[0].paymentDate").value(today.minusDays(10).toString()))
                .andExpect(jsonPath("$.data.content[0].success").value(true))
                .andExpect(jsonPath("$.data.content[2].paymentDate").value(today.minusDays(69).toString()))
                .andExpect(jsonPath("$.data.content[2].retryCount").value(1))
                .andExpect(jsonPath("$.data.content[3].paymentDate").value(today.minusDays(70).toString()))
                .andExpect(jsonPath("$.data.content[3].resultCode").value("FAILED"))
                .andExpect(jsonPath("$.data.content[3].success").value(false))
                .andExpect(jsonPath("$.data.content[3].resultMessage").value("한도 초과"));

        mockMvc.perform(get("/api/v1/subscriptions/{id}/history", NETFLIX)
                        .param("startDate", today.minusMonths(6).toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(6))
                .andExpect(jsonPath("$.data.content[5].paymentDate").value(today.minusDays(130).toString()))
                .andExpect(jsonPath("$.data.content[5].resultCode").value(nullValue()))
                .andExpect(jsonPath("$.data.content[5].success").value(true));

        mockMvc.perform(get("/api/v1/subscriptions/{id}/history", NETFLIX)
                        .param("startDate", today.minusMonths(2).toString())
                        .param("endDate", today.minusMonths(3).toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/v1/subscriptions/{id}/history", NETFLIX)
                        .param("startDate", today.minusMonths(14).toString())
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/v1/subscriptions/{id}/history", NETFLIX).header(HttpHeaders.AUTHORIZATION, otherBearer))
                .andExpect(status().isNotFound());
    }

    // ---------- 요약 ----------

    @Test
    void summarizesActiveSubscriptions() throws Exception {
        mockMvc.perform(get("/api/v1/subscriptions/summary").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(6))
                .andExpect(jsonPath("$.data.activeCount").value(3))
                .andExpect(jsonPath("$.data.pausedCount").value(1))
                .andExpect(jsonPath("$.data.expiredCount").value(1))
                .andExpect(jsonPath("$.data.canceledCount").value(1))
                .andExpect(jsonPath("$.data.monthlyTotalAmount").value(43_100))
                .andExpect(jsonPath("$.data.yearlyTotalAmount").value(517_200))
                .andExpect(jsonPath("$.data.nextPaymentDate").value(today.plusDays(2).toString()))
                .andExpect(jsonPath("$.data.upcomingWithin7Days.count").value(1))
                .andExpect(jsonPath("$.data.upcomingWithin7Days.amount").value(1_100))
                .andExpect(jsonPath("$.data.upcomingWithin30Days.count").value(2))
                .andExpect(jsonPath("$.data.upcomingWithin30Days.amount").value(18_100))
                .andExpect(jsonPath("$.data.byCategory", hasSize(3)))
                .andExpect(jsonPath("$.data.byCategory[0].category").value("FITNESS"))
                .andExpect(jsonPath("$.data.byCategory[0].categoryLabel").value("운동·건강"))
                .andExpect(jsonPath("$.data.byCategory[0].monthlyAmount").value(25_000))
                .andExpect(jsonPath("$.data.byCategory[1].category").value("VIDEO"))
                .andExpect(jsonPath("$.data.byCategory[1].count").value(1))
                .andExpect(jsonPath("$.data.byCategory[2].category").value("CLOUD"));

        mockMvc.perform(get("/api/v1/subscriptions/summary").header(HttpHeaders.AUTHORIZATION, otherBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCount").value(1))
                .andExpect(jsonPath("$.data.monthlyTotalAmount").value(17_000))
                .andExpect(jsonPath("$.data.nextPaymentDate").value(today.plusDays(5).toString()));
    }
}
