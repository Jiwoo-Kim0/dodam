package com.ssafy.dodam.domain.card.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCardBenefitReader implements CardBenefitReader {

    private static final String SQL = """
            SELECT benefits
            FROM financial_product
            WHERE product_id = :productId
              AND product_type = 'CARD'
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcCardBenefitReader(
            NamedParameterJdbcTemplate jdbcTemplate,
            ObjectMapper objectMapper
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<JsonNode> readBenefits(Long financialProductId) {
        if (financialProductId == null) {
            return Optional.empty();
        }
        List<String> values = jdbcTemplate.query(
                SQL,
                Map.of("productId", financialProductId),
                (resultSet, rowNumber) -> resultSet.getString("benefits")
        );
        if (values.isEmpty() || values.get(0) == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readTree(values.get(0)));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("카드 혜택 데이터를 해석할 수 없습니다.", exception);
        }
    }
}
