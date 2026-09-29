package com.ssafy.dodam.domain.transaction.repository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 소비 분석(S15P21D105-507)이 쓰는 읽기 전용 JDBC. 소비 정의는 {@link JdbcSpendingReader#SPENDING_WHERE}를 그대로 쓴다.
 * 소비목표(`goal` + `spending_goal`)는 Goal API(508~511) 전이라 엔티티 없이 여기서 읽는다.
 */
@Repository
public class JdbcSpendingAnalysisReader {

    private static final String SPENDING_TYPE_MONTHLY_SQL = """
            SELECT YEAR(entry.transaction_date) AS y, MONTH(entry.transaction_date) AS m,
                   COALESCE(SUM(entry.amount), 0) AS total, COUNT(*) AS cnt
            FROM `transaction` entry
            """ + JdbcSpendingReader.SPENDING_WHERE + """
              AND entry.spending_type = :spendingType
            GROUP BY YEAR(entry.transaction_date), MONTH(entry.transaction_date)
            ORDER BY y, m
            """;

    private static final String SPENDING_TYPE_CATEGORY_SQL = """
            SELECT entry.category_id, category.code, category.name,
                   COALESCE(SUM(entry.amount), 0) AS total, COUNT(*) AS cnt
            FROM `transaction` entry
            LEFT JOIN transaction_category category ON category.category_id = entry.category_id
            """ + JdbcSpendingReader.SPENDING_WHERE + """
              AND entry.spending_type = :spendingType
            GROUP BY entry.category_id, category.code, category.name
            ORDER BY total DESC, entry.category_id
            """;

    private static final String SPENDING_TOTAL_SQL = """
            SELECT COALESCE(SUM(entry.amount), 0)
            FROM `transaction` entry
            """ + JdbcSpendingReader.SPENDING_WHERE;

    // 수입: INCOME 거래 합. 소비 정의와 달리 spending_type을 보지 않는다.
    private static final String INCOME_TOTAL_SQL = """
            SELECT COALESCE(SUM(entry.amount), 0)
            FROM `transaction` entry
            WHERE entry.user_id = :userId
              AND entry.hidden = FALSE
              AND entry.internal_transfer = FALSE
              AND entry.transaction_type = 'INCOME'
              AND entry.transaction_date BETWEEN :startDate AND :endDate
            """;

    // 저축성 지출: 소비에서 제외한 NON_SPENDING 지출(저축·투자 이체 등).
    private static final String SAVING_TRANSFER_TOTAL_SQL = """
            SELECT COALESCE(SUM(entry.amount), 0)
            FROM `transaction` entry
            WHERE entry.user_id = :userId
              AND entry.hidden = FALSE
              AND entry.internal_transfer = FALSE
              AND entry.transaction_type = 'EXPENSE'
              AND entry.spending_type = 'NON_SPENDING'
              AND entry.transaction_date BETWEEN :startDate AND :endDate
            """;

    private static final String SPENDING_GOALS_SQL = """
            SELECT g.goal_id, g.name, g.status, sg.monthly_budget
            FROM goal g
            JOIN spending_goal sg ON sg.goal_id = g.goal_id
            WHERE g.user_id = :userId
              AND g.type = 'SPENDING'
              AND g.deleted_at IS NULL
            ORDER BY g.goal_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcSpendingAnalysisReader(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<JdbcSpendingReader.MonthlySpendingRow> readMonthlyBySpendingType(
            Long userId, String spendingType, LocalDate startDate, LocalDate endDate
    ) {
        return jdbcTemplate.query(SPENDING_TYPE_MONTHLY_SQL,
                params(userId, startDate, endDate).addValue("spendingType", spendingType),
                (rs, i) -> new JdbcSpendingReader.MonthlySpendingRow(
                        YearMonth.of(rs.getInt("y"), rs.getInt("m")), rs.getLong("total"), rs.getLong("cnt")));
    }

    public List<JdbcSpendingReader.CategorySpendingRow> readCategoriesBySpendingType(
            Long userId, String spendingType, LocalDate startDate, LocalDate endDate
    ) {
        return jdbcTemplate.query(SPENDING_TYPE_CATEGORY_SQL,
                params(userId, startDate, endDate).addValue("spendingType", spendingType),
                (rs, i) -> new JdbcSpendingReader.CategorySpendingRow(
                        null, rs.getObject("category_id", Long.class), rs.getString("code"), rs.getString("name"),
                        rs.getLong("total"), rs.getLong("cnt")));
    }

    public long readSpendingTotal(Long userId, LocalDate startDate, LocalDate endDate) {
        return single(SPENDING_TOTAL_SQL, userId, startDate, endDate);
    }

    public long readIncomeTotal(Long userId, LocalDate startDate, LocalDate endDate) {
        return single(INCOME_TOTAL_SQL, userId, startDate, endDate);
    }

    public long readSavingTransferTotal(Long userId, LocalDate startDate, LocalDate endDate) {
        return single(SAVING_TRANSFER_TOTAL_SQL, userId, startDate, endDate);
    }

    /**
     * 삭제되지 않은 소비 목표 전부(상태 무관). 상태 필터는 서비스가 한다.
     */
    public List<SpendingGoalRow> readSpendingGoals(Long userId) {
        return jdbcTemplate.query(SPENDING_GOALS_SQL, new MapSqlParameterSource("userId", userId),
                (rs, i) -> new SpendingGoalRow(
                        rs.getLong("goal_id"), rs.getString("name"), rs.getString("status"), rs.getLong("monthly_budget")));
    }

    private long single(String sql, Long userId, LocalDate startDate, LocalDate endDate) {
        return Objects.requireNonNullElse(
                jdbcTemplate.queryForObject(sql, params(userId, startDate, endDate), Long.class), 0L);
    }

    private static MapSqlParameterSource params(Long userId, LocalDate startDate, LocalDate endDate) {
        return new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("startDate", startDate)
                .addValue("endDate", endDate);
    }

    public record SpendingGoalRow(Long goalId, String name, String status, long monthlyBudget) {
    }
}
