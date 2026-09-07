package com.ssafy.dodam.domain.product;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Sql(
        scripts = {
                "/sql/product-query-test-data.sql",
                "/sql/product-comparison-recommendation-test-data.sql"
        },
        executionPhase = BEFORE_TEST_CLASS
)
class ProductComparisonRecommendationApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser
    void comparesDepositAndSavingsInRequestedOrder() throws Exception {
        mockMvc.perform(post("/api/v1/products/deposit-savings/compare")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[201,101]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products", hasSize(2)))
                .andExpect(jsonPath("$.data.products[0].productId").value(201))
                .andExpect(jsonPath("$.data.products[0].productType").value("SAVINGS"))
                .andExpect(jsonPath("$.data.products[1].productId").value(101))
                .andExpect(jsonPath("$.data.products[1].productType").value("DEPOSIT"))
                .andExpect(jsonPath("$.data.products[1].options[0].term").value(12));
    }

    @Test
    @WithMockUser
    void comparesDifferentLoanTypes() throws Exception {
        mockMvc.perform(post("/api/v1/products/loans/compare")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[302,301]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products", hasSize(2)))
                .andExpect(jsonPath("$.data.products[0].loanType").value("MORTGAGE"))
                .andExpect(jsonPath("$.data.products[1].loanType").value("PERSONAL_CREDIT"));
    }

    @Test
    @WithMockUser
    void comparesCardsWithBenefits() throws Exception {
        mockMvc.perform(post("/api/v1/products/cards/compare")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[402,401]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.products", hasSize(2)))
                .andExpect(jsonPath("$.data.products[0].productId").value(402))
                .andExpect(jsonPath("$.data.products[0].maxBenefitLimit").value(0))
                .andExpect(jsonPath("$.data.products[1].benefits[0].category")
                        .value("TRANSPORT"));
    }

    @Test
    @WithMockUser
    void validatesComparisonRequestAndProductTypes() throws Exception {
        mockMvc.perform(post("/api/v1/products/cards/compare")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[401]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(post("/api/v1/products/cards/compare")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[401,401]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/v1/products/cards/compare")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[401,101]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(post("/api/v1/products/cards/compare")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[401,999]}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "1")
    void recommendsCardsFromVisibleExpenseTransactions() throws Exception {
        mockMvc.perform(get("/api/v1/products/cards/recommended")
                        .param("yearMonth", "2026-09")
                        .param("topN", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearMonth").value("2026-09"))
                .andExpect(jsonPath("$.data.totalSpending").value(150000))
                .andExpect(jsonPath("$.data.dataAvailable").value(true))
                .andExpect(jsonPath("$.data.recommendations", hasSize(5)))
                .andExpect(jsonPath("$.data.recommendations[0].rank").value(1))
                .andExpect(jsonPath("$.data.recommendations[0].productId").value(405))
                .andExpect(jsonPath("$.data.recommendations[0].estimatedMonthlyBenefit")
                        .value(6000))
                .andExpect(jsonPath("$.data.recommendations[0].matchedBenefits", hasSize(2)))
                .andExpect(jsonPath("$.data.recommendations[1].productId").value(403))
                .andExpect(jsonPath("$.data.recommendations[1].estimatedMonthlyBenefit")
                        .value(3000))
                .andExpect(jsonPath("$.data.recommendations[2].productId").value(402))
                .andExpect(jsonPath("$.data.recommendations[2].estimatedMonthlyBenefit")
                        .value(1500))
                .andExpect(jsonPath("$.data.recommendations[3].productId").value(404))
                .andExpect(jsonPath("$.data.recommendations[3].matchedBenefits[0].calculable")
                        .value(false))
                .andExpect(jsonPath("$.data.recommendations[4].productId").value(401))
                .andExpect(jsonPath("$.data.recommendations[4].estimatedPerformanceReachable")
                        .value(false))
                .andExpect(jsonPath("$.data.recommendations[4].performanceShortfall")
                        .value(150000))
                .andExpect(jsonPath("$.data.recommendations[4].potentialMonthlyBenefit")
                        .value(10000));
    }

    @Test
    @WithMockUser(username = "2")
    void returnsEmptyRecommendationsWithoutSpendingData() throws Exception {
        mockMvc.perform(get("/api/v1/products/cards/recommended")
                        .param("yearMonth", "2026-09"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalSpending").value(0))
                .andExpect(jsonPath("$.data.dataAvailable").value(false))
                .andExpect(jsonPath("$.data.recommendations", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "1")
    void validatesRecommendationParameters() throws Exception {
        mockMvc.perform(get("/api/v1/products/cards/recommended")
                        .param("yearMonth", "2026-13"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/products/cards/recommended")
                        .param("topN", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void comparisonAndRecommendationRequireAuthentication() throws Exception {
        mockMvc.perform(post("/api/v1/products/cards/compare")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productIds\":[401,402]}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/products/cards/recommended")
                        .param("yearMonth", "2026-09"))
                .andExpect(status().isUnauthorized());
    }
}
