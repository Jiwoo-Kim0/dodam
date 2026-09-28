package com.ssafy.dodam.domain.product.repository;

import com.ssafy.dodam.domain.product.model.MonthlySpendingProfile;
import com.ssafy.dodam.domain.product.model.SpendingSegment;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCardRecommendationSpendingReader implements CardRecommendationSpendingReader {

    private static final String MONTHLY_SPENDING_SQL = """
            SELECT
                COALESCE(category.code, 'UNCATEGORIZED') AS category_code,
                spend.payment_method,
                SUM(spend.amount) AS total_amount
            FROM `transaction` spend
            LEFT JOIN transaction_category category
                ON category.category_id = spend.category_id
            WHERE spend.user_id = :userId
              AND spend.transaction_date >= :startDate
              AND spend.transaction_date < :endDate
              AND spend.transaction_type = 'EXPENSE'
              AND spend.hidden = 0
              AND COALESCE(
                    spend.spending_type,
                    category.default_spending_type,
                    'VARIABLE'
                  ) <> 'NON_SPENDING'
            GROUP BY COALESCE(category.code, 'UNCATEGORIZED'), spend.payment_method
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcCardRecommendationSpendingReader(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public MonthlySpendingProfile read(Long userId, YearMonth yearMonth) {
        Map<String, Object> parameters = Map.of(
                "userId", userId,
                "startDate", yearMonth.atDay(1),
                "endDate", yearMonth.plusMonths(1).atDay(1)
        );
        List<SpendingSegment> segments = jdbcTemplate.query(
                MONTHLY_SPENDING_SQL,
                parameters,
                (resultSet, rowNumber) -> new SpendingSegment(
                        resultSet.getString("category_code"),
                        resultSet.getString("payment_method"),
                        resultSet.getLong("total_amount")
                )
        );
        return MonthlySpendingProfile.from(segments);
    }
}
