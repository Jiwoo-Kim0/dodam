package com.ssafy.dodam.domain.coach.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.coach.service.CoachTextGenerator.ToolCall;
import com.ssafy.dodam.domain.coach.service.CoachTextGenerator.ToolDefinition;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 640 A-2: 도구 스키마는 enum·description으로 고정되고, 사용자 식별자·계좌번호 인자는 없다. 칩은 고정 매핑이다. */
class CoachToolRegistryTest {

    private final ObjectMapper json = new ObjectMapper();
    private final CoachToolRegistry registry = new CoachToolRegistry(json);

    @Test
    void exposesEightToolsWithoutIdentifierArguments() {
        List<ToolDefinition> tools = registry.definitions();
        assertThat(tools).extracting(ToolDefinition::name).containsExactly(
                "simulate_spending_reduction", "simulate_saving_change", "simulate_loan_prepayment", "simulate_loan_refinance",
                "simulate_combined", "get_goal_forecast", "get_latest_diagnosis", "get_spending_forecast");
        for (ToolDefinition tool : tools) {
            assertThat(tool.description()).isNotBlank();
            assertThat(tool.parameters().get("type").asText()).isEqualTo("object");
            String schema = tool.parameters().toString();
            assertThat(schema).doesNotContain("userId").doesNotContain("accountNo").doesNotContain("loanId").doesNotContain("goalId");
        }
        assertThat(registry.supports("simulate_combined")).isTrue();
        assertThat(registry.supports("delete_everything")).isFalse();
    }

    @Test
    void categoryArgumentIsAnEnumOfSpendingCodesAndAmountsAreRequired() {
        ToolDefinition spending = registry.definitions().get(0);
        JsonNode category = spending.parameters().at("/properties/category");
        assertThat(category.get("enum")).extracting(JsonNode::asText).contains("CAFE", "FOOD", "TAXI").doesNotContain("SAVING", "INCOME");
        assertThat(category.get("description").asText()).contains("CAFE");
        assertThat(spending.parameters().get("required")).extracting(JsonNode::asText).containsExactly("monthlyReduction");

        ToolDefinition combined = registry.definitions().get(4);
        JsonNode item = combined.parameters().at("/properties/scenarios/items");
        assertThat(item.at("/properties/type/enum")).extracting(JsonNode::asText)
                .containsExactly("SPENDING", "SAVING", "LOAN_PREPAYMENT", "LOAN_REFINANCE");
        assertThat(combined.parameters().at("/properties/scenarios/minItems").asInt()).isEqualTo(2);
    }

    @Test
    void chipsMapToFixedToolCallsWithoutTheModel() {
        List<ToolCall> cafe = registry.chip("CHIP_SPENDING_CAFE_50K").orElseThrow();
        assertThat(cafe).hasSize(1);
        assertThat(cafe.get(0).name()).isEqualTo("simulate_spending_reduction");
        assertThat(cafe.get(0).argumentsJson()).isEqualTo("{\"category\":\"CAFE\",\"monthlyReduction\":50000}");

        List<ToolCall> prepay = registry.chip("chip_loan_prepay_1m").orElseThrow();
        assertThat(prepay.get(0).name()).isEqualTo("simulate_loan_prepayment");
        assertThat(prepay.get(0).argumentsJson()).contains("1000000");

        List<ToolCall> combined = registry.chip("CHIP_COMBINED").orElseThrow();
        assertThat(combined.get(0).name()).isEqualTo("simulate_combined");
        assertThat(combined.get(0).argumentsJson()).contains("\"type\":\"SPENDING\"").contains("\"type\":\"LOAN_PREPAYMENT\"");
        assertThat(combined.get(0).id()).startsWith("chip_");

        assertThat(registry.chip("CHIP_NOPE")).isEmpty();
        assertThat(registry.chip(null)).isEmpty();
        assertThat(CoachToolRegistry.chipLabels()).hasSize(3);
        assertThat(CoachToolRegistry.MAX_TOOL_CALLS_PER_TURN).isEqualTo(3);
    }
}
