package com.ssafy.dodam.domain.product;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Sql(scripts = "/sql/product-query-test-data.sql", executionPhase = BEFORE_TEST_CLASS)
class ProductQueryApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser
    void getsCompaniesWithNameFilterAndPaginationMetadata() throws Exception {
        mockMvc.perform(get("/api/v1/products/companies")
                        .param("name", "국민"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.content[0].companyCode").value("KB_KOOKMIN"))
                .andExpect(jsonPath("$.data.content[1].companyCode").value("KB_CARD"))
                .andExpect(jsonPath("$.data.page").value(0))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(1))
                .andExpect(jsonPath("$.data.last").value(true))
                .andExpect(jsonPath("$.message").value(nullValue()));
    }

    @Test
    @WithMockUser
    void getsDepositListUsingCompanyTermAndRateFilters() throws Exception {
        mockMvc.perform(get("/api/v1/products/deposits")
                        .param("companyId", "1")
                        .param("term", "12")
                        .param("minRate", "3.5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].productId").value(101))
                .andExpect(jsonPath("$.data.content[0].maxRate").value(3.6));
    }

    @Test
    @WithMockUser
    void getsDepositDetailWithOptions() throws Exception {
        mockMvc.perform(get("/api/v1/products/deposits/101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productName").value("KB Star 정기예금"))
                .andExpect(jsonPath("$.data.company.companyCode").value("KB_KOOKMIN"))
                .andExpect(jsonPath("$.data.options", hasSize(1)))
                .andExpect(jsonPath("$.data.options[0].term").value(12))
                .andExpect(jsonPath("$.data.options[0].preferredRate").value(3.6));
    }

    @Test
    @WithMockUser
    void getsSavingsUsingSavingTypeFilter() throws Exception {
        mockMvc.perform(get("/api/v1/products/savings")
                        .param("savingType", "flexible")
                        .param("term", "12"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].productId").value(201))
                .andExpect(jsonPath("$.data.content[0].savingType").value("FLEXIBLE"));
    }

    @Test
    @WithMockUser
    void getsSavingsLoanAndCardDetails() throws Exception {
        mockMvc.perform(get("/api/v1/products/savings/201"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productName").value("KB 자유적금"))
                .andExpect(jsonPath("$.data.savingType").value("FLEXIBLE"))
                .andExpect(jsonPath("$.data.options[0].limitAmount").value(3000000));

        mockMvc.perform(get("/api/v1/products/loans/301"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productName").value("KB 직장인 신용대출"))
                .andExpect(jsonPath("$.data.loanType").value("PERSONAL_CREDIT"))
                .andExpect(jsonPath("$.data.maxLimit").value(100000000))
                .andExpect(jsonPath("$.data.options[0].repaymentType").value("MATURITY"));

        mockMvc.perform(get("/api/v1/products/cards/401"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.productName").value("KB 생활카드"))
                .andExpect(jsonPath("$.data.baselinePerformance").value(300000))
                .andExpect(jsonPath("$.data.benefits[0].description").value("교통 할인"));
    }

    @Test
    @WithMockUser
    void separatesPersonalMortgageAndJeonseLoanCatalogs() throws Exception {
        mockMvc.perform(get("/api/v1/products/loans/personal")
                        .param("rateType", "fixed")
                        .param("minRate", "3.0")
                        .param("maxRate", "6.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].productId").value(301));

        mockMvc.perform(get("/api/v1/products/loans/mortgage")
                        .param("mortgageType", "mortgage")
                        .param("repaymentType", "equal_payment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].productId").value(302));

        mockMvc.perform(get("/api/v1/products/loans/jeonse")
                        .param("repaymentType", "bullet"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].productId").value(303));
    }

    @Test
    @WithMockUser
    void getsCardsUsingIssuerAndBenefitCategoryFilters() throws Exception {
        mockMvc.perform(get("/api/v1/products/cards")
                        .param("issuerCode", "kb_card")
                        .param("category", "transport"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].productId").value(401))
                .andExpect(jsonPath("$.data.content[0].benefits", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].benefits[0].category")
                        .value("TRANSPORT"));
    }

    @Test
    @WithMockUser
    void returnsNotFoundWhenProductTypeDoesNotMatchDetailPath() throws Exception {
        mockMvc.perform(get("/api/v1/products/deposits/301"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @WithMockUser
    void rejectsInvalidPaginationAndRateRange() throws Exception {
        mockMvc.perform(get("/api/v1/products/companies")
                        .param("page", "-1")
                        .param("size", "101"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(get("/api/v1/products/loans/personal")
                        .param("minRate", "7.0")
                        .param("maxRate", "3.0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(get("/api/v1/products/cards")
                        .param("category", "TRANSPORT%"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void productCatalogRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/products/companies"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
