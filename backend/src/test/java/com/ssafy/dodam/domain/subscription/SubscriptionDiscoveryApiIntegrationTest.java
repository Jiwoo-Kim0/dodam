package com.ssafy.dodam.domain.subscription;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.dodam.domain.subscription.entity.SubscriptionCategory;
import com.ssafy.dodam.domain.subscription.service.SubscriptionServiceCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

/**
 * S15P21D105-513 미등록 반복결제 탐지·서비스 카탈로그·카테고리.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Sql(scripts = "/sql/subscription-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class SubscriptionDiscoveryApiIntegrationTest extends SubscriptionApiTestSupport {

    private static final long SUBSCRIPTION_CATEGORY = 15L;
    private static final long HOUSING_CATEGORY = 12L;
    private static final long INSURANCE_CATEGORY = 14L;
    private static final long CAFE_CATEGORY = 2L;
    private static final long INCOME_CATEGORY = 19L;

    @Autowired
    private SubscriptionServiceCatalog catalog;

    @BeforeEach
    void seed() {
        seedDefaultSubscriptions();

        // 쿠팡와우 7,890원: 30일 간격 4회 → MONTHLY 후보. 등록된 정기결제가 없다.
        for (int i = 0; i < 4; i++) {
            insertTransaction("TX-WOW-" + i, userId, cardId, SUBSCRIPTION_CATEGORY, today.minusDays(5 + 30L * i),
                    "EXPENSE", 7_890, "쿠팡와우", "CARD", false);
        }
        // 넷플릭스 17,000원: 3회 반복이지만 ACTIVE 정기결제로 이미 등록돼 있어 후보에서 뺀다.
        for (int i = 0; i < 3; i++) {
            insertTransaction("TX-NF-" + i, userId, cardId, SUBSCRIPTION_CATEGORY, today.minusDays(12 + 30L * i),
                    "EXPENSE", 17_000, "넷플릭스", "CARD", false);
        }
        // 밀리의서재 9,900원: 정기결제는 CANCELED인데 결제가 계속되므로 다시 후보로 보인다.
        for (int i = 0; i < 3; i++) {
            insertTransaction("TX-ML-" + i, userId, null, SUBSCRIPTION_CATEGORY, today.minusDays(20 + 30L * i),
                    "EXPENSE", 9_900, "밀리의서재", "ACCOUNT", false);
        }
        // 스타벅스: 금액이 들쭉날쭉하고, 같은 4,500원 2건도 42일 간격이라 어느 주기에도 들지 않는다 → minOccurrences=2에서도 후보 아님.
        insertTransaction("TX-SB-0", userId, cardId, CAFE_CATEGORY, today.minusDays(3), "EXPENSE", 4_500, "스타벅스", "CARD", false);
        insertTransaction("TX-SB-1", userId, cardId, CAFE_CATEGORY, today.minusDays(9), "EXPENSE", 5_100, "스타벅스", "CARD", false);
        insertTransaction("TX-SB-2", userId, cardId, CAFE_CATEGORY, today.minusDays(45), "EXPENSE", 4_500, "스타벅스", "CARD", false);
        // 유튜브 프리미엄 14,900원: 2회 → 기본(3회)에서는 빠지고 minOccurrences=2에서 나온다.
        insertTransaction("TX-YT-0", userId, cardId, null, today.minusDays(3), "EXPENSE", 14_900, "유튜브 프리미엄", "CARD", false);
        insertTransaction("TX-YT-1", userId, cardId, null, today.minusDays(33), "EXPENSE", 14_900, "유튜브 프리미엄", "CARD", false);
        // 숨긴 거래는 보지 않는다.
        for (int i = 0; i < 3; i++) {
            insertTransaction("TX-GYM-" + i, userId, cardId, null, today.minusDays(7 + 30L * i),
                    "EXPENSE", 50_000, "헬스장", "CARD", true);
        }
        // 고정비: 매달 같은 날 같은 금액 같은 이름이라 주기 조건은 완벽히 맞지만 구독이 아니다(시연 seed 4b 블록과 같은 모양).
        // 카테고리가 SUBSCRIPTION이 아니고 카탈로그에도 없으므로 후보에서 뺀다.
        for (int i = 0; i < 3; i++) {
            insertTransaction("TX-INS-" + i, userId, null, INSURANCE_CATEGORY, today.minusDays(8 + 30L * i),
                    "EXPENSE", 105_000, "도담 보험", "ACCOUNT", false);
            insertTransaction("TX-HOUSE-" + i, userId, null, HOUSING_CATEGORY, today.minusDays(11 + 30L * i),
                    "EXPENSE", 455_000, "도담 주거 관리사무소", "ACCOUNT", false);
        }
        // 미분류(카테고리 null) + 카탈로그 밖 이름도 반복만으로는 후보가 아니다.
        for (int i = 0; i < 3; i++) {
            insertTransaction("TX-ACADEMY-" + i, userId, cardId, null, today.minusDays(15 + 30L * i),
                    "EXPENSE", 200_000, "도담 어학원", "CARD", false);
        }
        // 수입은 보지 않는다.
        for (int i = 0; i < 3; i++) {
            insertTransaction("TX-INC-" + i, userId, null, INCOME_CATEGORY, today.minusDays(1 + 30L * i),
                    "INCOME", 3_000_000, "도담컴퍼니", "ACCOUNT", false);
        }
        // 타인의 반복결제는 본인 결과에 나오지 않는다.
        for (int i = 0; i < 3; i++) {
            insertTransaction("TX-OTHER-" + i, otherUserId, otherCardId, SUBSCRIPTION_CATEGORY, today.minusDays(2 + 30L * i),
                    "EXPENSE", 9_500, "티빙", "CARD", false);
        }
    }

    // ---------- 후보 탐지 ----------

    @Test
    void detectsUnregisteredRecurringPayments() throws Exception {
        mockMvc.perform(get("/api/v1/subscriptions/candidates").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].merchantName").value("쿠팡와우"))
                .andExpect(jsonPath("$.data[0].amount").value(7_890))
                .andExpect(jsonPath("$.data[0].occurrences").value(4))
                .andExpect(jsonPath("$.data[0].firstPaymentDate").value(today.minusDays(95).toString()))
                .andExpect(jsonPath("$.data[0].lastPaymentDate").value(today.minusDays(5).toString()))
                .andExpect(jsonPath("$.data[0].averageIntervalDays").value(30.0))
                .andExpect(jsonPath("$.data[0].estimatedBillingCycle").value("MONTHLY"))
                .andExpect(jsonPath("$.data[0].suggestedPaymentDay").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data[0].suggestedPaymentDay").value(lessThanOrEqualTo(31)))
                .andExpect(jsonPath("$.data[0].expectedNextPaymentDate").value(greaterThanOrEqualTo(today.toString())))
                .andExpect(jsonPath("$.data[0].cardId").value(cardId))
                .andExpect(jsonPath("$.data[0].categoryCode").value("SUBSCRIPTION"))
                .andExpect(jsonPath("$.data[0].suggestedCategory").value("SHOPPING"))
                .andExpect(jsonPath("$.data[0].matchedServiceCode").value("COUPANG_WOW"))
                .andExpect(jsonPath("$.data[0].transactionIds", hasSize(4)))
                .andExpect(jsonPath("$.data[0].transactionIds[0]").value("TX-WOW-0"))
                .andExpect(jsonPath("$.data[1].merchantName").value("밀리의서재"))
                .andExpect(jsonPath("$.data[1].occurrences").value(3))
                .andExpect(jsonPath("$.data[1].cardId").value(nullValue()))
                .andExpect(jsonPath("$.data[1].matchedServiceCode").value("MILLIE"))
                .andExpect(jsonPath("$.data[1].suggestedCategory").value("BOOK"));
    }

    @Test
    void excludesRegularFixedCostsThatAreNotSubscriptions() throws Exception {
        // minOccurrences=2까지 낮춰도 보험·관리비·학원비는 나오지 않는다. 카테고리가 없어도 카탈로그와 맞는 유튜브 프리미엄은 남는다.
        mockMvc.perform(get("/api/v1/subscriptions/candidates").param("minOccurrences", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].merchantName",
                        containsInAnyOrder("쿠팡와우", "밀리의서재", "유튜브 프리미엄")))
                .andExpect(jsonPath("$.data[*].merchantName",
                        not(hasItems("도담 보험", "도담 주거 관리사무소", "도담 어학원"))));
    }

    @Test
    void honoursMinOccurrencesAndLookbackParameters() throws Exception {
        mockMvc.perform(get("/api/v1/subscriptions/candidates").param("minOccurrences", "2")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[2].merchantName").value("유튜브 프리미엄"))
                .andExpect(jsonPath("$.data[2].occurrences").value(2))
                .andExpect(jsonPath("$.data[2].matchedServiceCode").value("YOUTUBE_PREMIUM"))
                .andExpect(jsonPath("$.data[2].categoryCode").value(nullValue()));

        mockMvc.perform(get("/api/v1/subscriptions/candidates").param("lookbackMonths", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));

        mockMvc.perform(get("/api/v1/subscriptions/candidates").param("minOccurrences", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/subscriptions/candidates").param("lookbackMonths", "13")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/subscriptions/candidates").header(HttpHeaders.AUTHORIZATION, otherBearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].merchantName").value("티빙"))
                .andExpect(jsonPath("$.data[0].matchedServiceCode").value("TVING"));
    }

    // ---------- 서비스 카탈로그 ----------

    @Test
    void listsServiceCatalogWithCategoryFilter() throws Exception {
        int total = catalog.entries().size();
        int videoCount = catalog.entries("VIDEO").size();

        mockMvc.perform(get("/api/v1/subscriptions/services").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(total))
                .andExpect(jsonPath("$.data.content", hasSize(Math.min(20, total))))
                .andExpect(jsonPath("$.data.content[0].serviceCode").value("NETFLIX"))
                .andExpect(jsonPath("$.data.content[0].name").value("넷플릭스"))
                .andExpect(jsonPath("$.data.content[0].category").value("VIDEO"))
                .andExpect(jsonPath("$.data.content[0].categoryLabel").value("영상"))
                .andExpect(jsonPath("$.data.content[0].typicalMonthlyAmount").value(17_000))
                .andExpect(jsonPath("$.data.content[0].iconKey").value("netflix"));

        mockMvc.perform(get("/api/v1/subscriptions/services").param("category", "video").param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(videoCount))
                .andExpect(jsonPath("$.data.content", hasSize(videoCount)));

        mockMvc.perform(get("/api/v1/subscriptions/services").param("category", "UNKNOWN")
                        .header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mockMvc.perform(get("/api/v1/subscriptions/services").param("size", "0").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/subscriptions/services"))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 카테고리 ----------

    @Test
    void listsCategoriesWithOwnSubscriptionCounts() throws Exception {
        mockMvc.perform(get("/api/v1/subscriptions/categories").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(SubscriptionCategory.values().length)))
                .andExpect(jsonPath("$.data[0].code").value("VIDEO"))
                .andExpect(jsonPath("$.data[0].label").value("영상"))
                // 넷플릭스(ACTIVE) + 왓챠(EXPIRED). CANCELED는 세지 않는다.
                .andExpect(jsonPath("$.data[0].subscriptionCount").value(2))
                .andExpect(jsonPath("$.data[1].code").value("MUSIC"))
                .andExpect(jsonPath("$.data[1].subscriptionCount").value(1))
                .andExpect(jsonPath("$.data[2].code").value("BOOK"))
                .andExpect(jsonPath("$.data[2].subscriptionCount").value(0))
                .andExpect(jsonPath("$.data[3].code").value("CLOUD"))
                .andExpect(jsonPath("$.data[3].subscriptionCount").value(1));
    }
}
