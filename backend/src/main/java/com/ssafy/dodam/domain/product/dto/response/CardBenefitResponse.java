package com.ssafy.dodam.domain.product.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

public record CardBenefitResponse(
        String category,
        BigDecimal discountRate,
        String description,
        Long limitAmount
) {

    public static List<CardBenefitResponse> from(JsonNode benefits) {
        if (benefits == null || !benefits.isArray()) {
            return List.of();
        }

        List<CardBenefitResponse> responses = new ArrayList<>();
        for (JsonNode benefit : benefits) {
            responses.add(new CardBenefitResponse(
                    textOrNull(benefit.get("category")),
                    decimalOrNull(benefit.get("discountRate")),
                    textOrNull(benefit.get("description")),
                    longOrNull(benefit.get("limitAmount"))
            ));
        }
        return List.copyOf(responses);
    }

    private static String textOrNull(JsonNode value) {
        return value == null || value.isNull() ? null : value.asText();
    }

    private static BigDecimal decimalOrNull(JsonNode value) {
        return value == null || value.isNull() ? null : value.decimalValue();
    }

    private static Long longOrNull(JsonNode value) {
        return value == null || value.isNull() ? null : value.longValue();
    }
}
