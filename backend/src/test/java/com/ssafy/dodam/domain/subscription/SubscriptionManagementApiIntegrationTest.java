package com.ssafy.dodam.domain.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

/**
 * S15P21D105-514 정기결제 수정·정지/재개·취소.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Sql(scripts = "/sql/subscription-test-data.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
class SubscriptionManagementApiIntegrationTest extends SubscriptionApiTestSupport {

    @BeforeEach
    void seed() {
        seedDefaultSubscriptions();
    }

    // ---------- 수정 ----------

    @Test
    void updatesAmountDayAndEndDate() throws Exception {
        mockMvc.perform(patch("/api/v1/subscriptions/{id}", NETFLIX)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("paymentAmount", 13_500))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentAmount").value(13_500))
                .andExpect(jsonPath("$.data.monthlyEquivalentAmount").value(13_500))
                .andExpect(jsonPath("$.data.nextPaymentDate").value(today.plusDays(20).toString()))
                .andExpect(jsonPath("$.data.updatedAt").isNotEmpty());

        // 결제일을 바꾸면 ACTIVE 정기결제의 다음 결제일을 새 결제일 기준으로 다시 잡는다.
        mockMvc.perform(patch("/api/v1/subscriptions/{id}", NETFLIX)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("paymentDay", 5))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentDay").value(5))
                .andExpect(jsonPath("$.data.nextPaymentDate").value(nextMonthlyOnOrAfterToday(5).toString()));

        mockMvc.perform(patch("/api/v1/subscriptions/{id}", NETFLIX)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("endDate", today.plusDays(90).toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.endDate").value(today.plusDays(90).toString()));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT payment_amount FROM subscription WHERE subscription_id = ?", Long.class, NETFLIX)).isEqualTo(13_500L);
    }

    @Test
    void duplicateUpdateKeepsUpdatedAt() throws Exception {
        mockMvc.perform(patch("/api/v1/subscriptions/{id}", NETFLIX)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("paymentAmount", 17_000, "status", "ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paymentAmount").value(17_000))
                .andExpect(jsonPath("$.data.updatedAt").value(nullValue()));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT updated_at FROM subscription WHERE subscription_id = ?", Timestamp.class, NETFLIX)).isNull();
    }

    @Test
    void rejectsInvalidUpdates() throws Exception {
        mockMvc.perform(patch("/api/v1/subscriptions/{id}", NETFLIX)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_FIELDS_TO_UPDATE"));

        mockMvc.perform(patch("/api/v1/subscriptions/{id}", NETFLIX)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("paymentAmount", 0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        mockMvc.perform(patch("/api/v1/subscriptions/{id}", NETFLIX)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("paymentDay", 32))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        // 종료일은 오늘 이후여야 한다.
        mockMvc.perform(patch("/api/v1/subscriptions/{id}", NETFLIX)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("endDate", today.minusDays(1).toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        // 종료일은 다음 결제일(오늘+20)보다 앞설 수 없다.
        mockMvc.perform(patch("/api/v1/subscriptions/{id}", NETFLIX)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("endDate", today.plusDays(1).toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(patch("/api/v1/subscriptions/{id}", NETFLIX)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "EXPIRED"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(patch("/api/v1/subscriptions/{id}", NETFLIX)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "DONE"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        // 종료 상태는 어떤 필드도 바꿀 수 없다.
        mockMvc.perform(patch("/api/v1/subscriptions/{id}", MILLIE)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("paymentAmount", 1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_NOT_EDITABLE"));

        mockMvc.perform(patch("/api/v1/subscriptions/{id}", WATCHA)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "ACTIVE"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_NOT_EDITABLE"));

        mockMvc.perform(patch("/api/v1/subscriptions/{id}", NETFLIX)
                        .header(HttpHeaders.AUTHORIZATION, otherBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("paymentAmount", 1))))
                .andExpect(status().isNotFound());
    }

    @Test
    void changesStatusThroughUpdateBody() throws Exception {
        mockMvc.perform(patch("/api/v1/subscriptions/{id}", NETFLIX)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "paused"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAUSED"))
                .andExpect(jsonPath("$.data.nextPaymentDate").value(nullValue()));

        // 재개하면 결제일(오늘+20의 일자)에 맞춰 다음 결제일을 다시 잡는다.
        mockMvc.perform(patch("/api/v1/subscriptions/{id}", NETFLIX)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "ACTIVE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.nextPaymentDate").value(today.plusDays(20).toString()));

        mockMvc.perform(patch("/api/v1/subscriptions/{id}", ICLOUD)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "CANCELED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.nextPaymentDate").value(nullValue()))
                .andExpect(jsonPath("$.data.canceledAt").isNotEmpty());
    }

    // ---------- 정지/재개 ----------

    @Test
    void pausesAndResumesWithStatusOrAction() throws Exception {
        mockMvc.perform(patch("/api/v1/subscriptions/{id}/status", ICLOUD)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("action", "pause"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAUSED"))
                .andExpect(jsonPath("$.data.nextPaymentDate").value(nullValue()));

        // 같은 요청을 반복해도 그대로 200(중복 요청 방지).
        mockMvc.perform(patch("/api/v1/subscriptions/{id}/status", ICLOUD)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("action", "PAUSE"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAUSED"));

        mockMvc.perform(patch("/api/v1/subscriptions/{id}/status", ICLOUD)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "active"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.nextPaymentDate").value(today.plusDays(2).toString()));

        // PAUSED로 적재된 정기결제(결제일 3일)를 재개하면 오늘 이후 첫 3일이 다음 결제일이다.
        mockMvc.perform(patch("/api/v1/subscriptions/{id}/status", SPOTIFY)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("action", "resume"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.nextPaymentDate").value(nextMonthlyOnOrAfterToday(3).toString()));
    }

    @Test
    void rejectsInvalidStatusChanges() throws Exception {
        mockMvc.perform(patch("/api/v1/subscriptions/{id}/status", ICLOUD)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(patch("/api/v1/subscriptions/{id}/status", ICLOUD)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("action", "cancel"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(patch("/api/v1/subscriptions/{id}/status", ICLOUD)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("status", "CANCELED"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        Map<String, Object> conflicting = new HashMap<>();
        conflicting.put("status", "ACTIVE");
        conflicting.put("action", "pause");
        mockMvc.perform(patch("/api/v1/subscriptions/{id}/status", ICLOUD)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(conflicting)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        mockMvc.perform(patch("/api/v1/subscriptions/{id}/status", WATCHA)
                        .header(HttpHeaders.AUTHORIZATION, bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("action", "resume"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_NOT_EDITABLE"));

        mockMvc.perform(patch("/api/v1/subscriptions/{id}/status", ICLOUD)
                        .header(HttpHeaders.AUTHORIZATION, otherBearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("action", "pause"))))
                .andExpect(status().isNotFound());
    }

    // ---------- 취소 ----------

    @Test
    void cancelsSubscriptionAndKeepsRow() throws Exception {
        mockMvc.perform(delete("/api/v1/subscriptions/{id}", NETFLIX).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(nullValue()));

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM subscription WHERE subscription_id = ?", String.class, NETFLIX)).isEqualTo("CANCELED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT canceled_at FROM subscription WHERE subscription_id = ?", Timestamp.class, NETFLIX)).isNotNull();

        mockMvc.perform(get("/api/v1/subscriptions/{id}", NETFLIX).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscription.status").value("CANCELED"))
                .andExpect(jsonPath("$.data.subscription.nextPaymentDate").value(nullValue()));

        mockMvc.perform(delete("/api/v1/subscriptions/{id}", NETFLIX).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SUBSCRIPTION_NOT_EDITABLE"));

        mockMvc.perform(delete("/api/v1/subscriptions/{id}", GYM).header(HttpHeaders.AUTHORIZATION, otherBearer))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/v1/subscriptions/{id}", "SUB-NOPE").header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isNotFound());
    }
}
