package com.ssafy.dodam.domain.card.repository;

import com.ssafy.dodam.domain.product.model.MonthlySpendingProfile;
import com.ssafy.dodam.domain.product.model.SpendingSegment;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcCardAnalyticsReader implements CardAnalyticsReader {

    private static final String VISIBLE_SPENDING_FILTER = """
              AND spend.transaction_type = 'EXPENSE'
              AND spend.hidden = 0
              AND COALESCE(
                    spend.spending_type,
                    category.default_spending_type,
                    'VARIABLE'
                  ) <> 'NON_SPENDING'
            """;

    private static final String MONTHLY_SQL = """
            SELECT COALESCE(SUM(spend.amount), 0) AS total_amount,
                   COUNT(*) AS transaction_count
            FROM `transaction` spend
            LEFT JOIN transaction_category category ON category.category_id = spend.category_id
            WHERE spend.user_id = :userId
              AND spend.card_id = :cardId
              AND spend.transaction_date >= :startDate
              AND spend.transaction_date < :endDate
            """ + VISIBLE_SPENDING_FILTER;

    private static final String CATEGORY_SQL = """
            SELECT COALESCE(category.code, 'UNCATEGORIZED') AS category_code,
                   COALESCE(category.name, '미분류') AS category_name,
                   SUM(spend.amount) AS total_amount,
                   COUNT(*) AS transaction_count
            FROM `transaction` spend
            LEFT JOIN transaction_category category ON category.category_id = spend.category_id
            WHERE spend.user_id = :userId
              AND spend.card_id = :cardId
              AND spend.transaction_date >= :startDate
              AND spend.transaction_date < :endDate
            """ + VISIBLE_SPENDING_FILTER + """
            GROUP BY COALESCE(category.code, 'UNCATEGORIZED'),
                     COALESCE(category.name, '미분류')
            ORDER BY total_amount DESC, category_code ASC
            """;

    private static final String SPENDING_PROFILE_SQL = """
            SELECT COALESCE(category.code, 'UNCATEGORIZED') AS category_code,
                   spend.payment_method,
                   SUM(spend.amount) AS total_amount
            FROM `transaction` spend
            LEFT JOIN transaction_category category ON category.category_id = spend.category_id
            WHERE spend.user_id = :userId
              AND spend.card_id = :cardId
              AND spend.transaction_date >= :startDate
              AND spend.transaction_date < :endDate
            """ + VISIBLE_SPENDING_FILTER + """
            GROUP BY COALESCE(category.code, 'UNCATEGORIZED'), spend.payment_method
            """;

    private static final String LOW_USAGE_SQL = """
            SELECT card.card_id,
                   card.card_no_masked,
                   card.card_name,
                   card.issuer_name,
                   card.annual_fee,
                   COALESCE(SUM(CASE WHEN spend.transaction_id IS NOT NULL AND
                       COALESCE(spend.spending_type, category.default_spending_type, 'VARIABLE')
                           <> 'NON_SPENDING'
                       THEN spend.amount ELSE 0 END), 0) AS total_amount,
                   COUNT(CASE WHEN spend.transaction_id IS NOT NULL AND
                       COALESCE(spend.spending_type, category.default_spending_type, 'VARIABLE')
                           <> 'NON_SPENDING'
                       THEN 1 END) AS transaction_count
            FROM card
            LEFT JOIN `transaction` spend
              ON spend.user_id = card.user_id
             AND spend.card_id = card.card_id
             AND spend.transaction_date BETWEEN :startDate AND :endDate
             AND spend.transaction_type = 'EXPENSE'
             AND spend.hidden = 0
            LEFT JOIN transaction_category category ON category.category_id = spend.category_id
            WHERE card.user_id = :userId
              AND card.status = 'ACTIVE'
            GROUP BY card.card_id, card.card_no_masked, card.card_name,
                     card.issuer_name, card.annual_fee
            HAVING COALESCE(SUM(CASE WHEN spend.transaction_id IS NOT NULL AND
                       COALESCE(spend.spending_type, category.default_spending_type, 'VARIABLE')
                           <> 'NON_SPENDING'
                       THEN spend.amount ELSE 0 END), 0) <= :thresholdAmount
            ORDER BY total_amount ASC, card.annual_fee DESC, card.card_id ASC
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcCardAnalyticsReader(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public CardMonthlyAggregateRow readMonthly(Long userId, Long cardId, YearMonth yearMonth) {
        return jdbcTemplate.queryForObject(
                MONTHLY_SQL,
                monthParameters(userId, cardId, yearMonth),
                (resultSet, rowNumber) -> new CardMonthlyAggregateRow(
                        resultSet.getLong("total_amount"),
                        resultSet.getLong("transaction_count")
                )
        );
    }

    @Override
    public List<CardCategoryAggregateRow> readCategories(
            Long userId,
            Long cardId,
            YearMonth yearMonth
    ) {
        return jdbcTemplate.query(
                CATEGORY_SQL,
                monthParameters(userId, cardId, yearMonth),
                (resultSet, rowNumber) -> new CardCategoryAggregateRow(
                        resultSet.getString("category_code"),
                        resultSet.getString("category_name"),
                        resultSet.getLong("total_amount"),
                        resultSet.getLong("transaction_count")
                )
        );
    }

    @Override
    public MonthlySpendingProfile readSpendingProfile(
            Long userId,
            Long cardId,
            YearMonth yearMonth
    ) {
        List<SpendingSegment> segments = jdbcTemplate.query(
                SPENDING_PROFILE_SQL,
                monthParameters(userId, cardId, yearMonth),
                (resultSet, rowNumber) -> new SpendingSegment(
                        resultSet.getString("category_code"),
                        resultSet.getString("payment_method"),
                        resultSet.getLong("total_amount")
                )
        );
        return MonthlySpendingProfile.from(segments);
    }

    @Override
    public List<LowUsageCardRow> readLowUsageCards(
            Long userId,
            LocalDate startDate,
            LocalDate endDate,
            long thresholdAmount
    ) {
        return jdbcTemplate.query(
                LOW_USAGE_SQL,
                Map.of(
                        "userId", userId,
                        "startDate", startDate,
                        "endDate", endDate,
                        "thresholdAmount", thresholdAmount
                ),
                (resultSet, rowNumber) -> new LowUsageCardRow(
                        resultSet.getLong("card_id"),
                        resultSet.getString("card_no_masked"),
                        resultSet.getString("card_name"),
                        resultSet.getString("issuer_name"),
                        resultSet.getObject("annual_fee", Long.class),
                        resultSet.getLong("total_amount"),
                        resultSet.getLong("transaction_count")
                )
        );
    }

    private static Map<String, Object> monthParameters(
            Long userId,
            Long cardId,
            YearMonth yearMonth
    ) {
        return Map.of(
                "userId", userId,
                "cardId", cardId,
                "startDate", yearMonth.atDay(1),
                "endDate", yearMonth.plusMonths(1).atDay(1)
        );
    }
}
