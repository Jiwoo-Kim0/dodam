package com.ssafy.dodam.domain.transaction.repository;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 소비 집계(S15P21D105-506). "소비"의 정의는 한 곳에서만 바꾼다({@link #SPENDING_WHERE}).
 *
 * <ul>
 *   <li>`transaction_type = EXPENSE`만 소비다. INCOME·TRANSFER는 제외한다.</li>
 *   <li>`spending_type = NON_SPENDING`(저축·이체 성격의 카테고리)은 제외한다. NULL(미분류)은 소비로 본다.</li>
 *   <li>숨긴 거래(`hidden = 1`)는 제외한다. 카드·계좌 집계와 같은 규칙이다.</li>
 *   <li>계좌·카드·수동 거래를 가리지 않는다. 사용자 원장 전체가 대상이다.</li>
 * </ul>
 * 거래가 없는 날·달·카테고리는 행이 없으므로 빈 값 채우기는 서비스가 한다.
 */
@Repository
public class JdbcSpendingReader {

    static final String SPENDING_WHERE = """
            WHERE entry.user_id = :userId
              AND entry.hidden = FALSE
              AND entry.transaction_type = 'EXPENSE'
              AND (entry.spending_type IS NULL OR entry.spending_type <> 'NON_SPENDING')
              AND entry.transaction_date BETWEEN :startDate AND :endDate
            """;

    private static final String MONTHLY_SQL = """
            SELECT YEAR(entry.transaction_date) AS y, MONTH(entry.transaction_date) AS m,
                   COALESCE(SUM(entry.amount), 0) AS total, COUNT(*) AS cnt
            FROM `transaction` entry
            """ + SPENDING_WHERE + """
            GROUP BY YEAR(entry.transaction_date), MONTH(entry.transaction_date)
            ORDER BY y, m
            """;

    private static final String DAILY_SQL = """
            SELECT entry.transaction_date AS d, COALESCE(SUM(entry.amount), 0) AS total, COUNT(*) AS cnt
            FROM `transaction` entry
            """ + SPENDING_WHERE + """
            GROUP BY entry.transaction_date
            ORDER BY d
            """;

    private static final String CATEGORY_SQL = """
            SELECT entry.category_id, category.code, category.name,
                   COALESCE(SUM(entry.amount), 0) AS total, COUNT(*) AS cnt
            FROM `transaction` entry
            LEFT JOIN transaction_category category ON category.category_id = entry.category_id
            """ + SPENDING_WHERE + """
            GROUP BY entry.category_id, category.code, category.name
            ORDER BY total DESC, entry.category_id
            """;

    private static final String CATEGORY_MONTHLY_SQL = """
            SELECT YEAR(entry.transaction_date) AS y, MONTH(entry.transaction_date) AS m,
                   entry.category_id, category.code, category.name,
                   COALESCE(SUM(entry.amount), 0) AS total, COUNT(*) AS cnt
            FROM `transaction` entry
            LEFT JOIN transaction_category category ON category.category_id = entry.category_id
            """ + SPENDING_WHERE + """
            GROUP BY YEAR(entry.transaction_date), MONTH(entry.transaction_date),
                     entry.category_id, category.code, category.name
            ORDER BY y, m, total DESC, entry.category_id
            """;

    private static final String PAYMENT_METHOD_SQL = """
            SELECT entry.payment_method, COALESCE(SUM(entry.amount), 0) AS total, COUNT(*) AS cnt
            FROM `transaction` entry
            """ + SPENDING_WHERE + """
            GROUP BY entry.payment_method
            ORDER BY total DESC, entry.payment_method
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcSpendingReader(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<MonthlySpendingRow> readMonthly(Long userId, LocalDate startDate, LocalDate endDate) {
        return jdbcTemplate.query(MONTHLY_SQL, params(userId, startDate, endDate), (rs, i) -> new MonthlySpendingRow(
                YearMonth.of(rs.getInt("y"), rs.getInt("m")), rs.getLong("total"), rs.getLong("cnt")));
    }

    public List<DailySpendingRow> readDaily(Long userId, LocalDate startDate, LocalDate endDate) {
        return jdbcTemplate.query(DAILY_SQL, params(userId, startDate, endDate), (rs, i) -> new DailySpendingRow(
                rs.getObject("d", LocalDate.class), rs.getLong("total"), rs.getLong("cnt")));
    }

    public List<CategorySpendingRow> readByCategory(Long userId, LocalDate startDate, LocalDate endDate) {
        return jdbcTemplate.query(CATEGORY_SQL, params(userId, startDate, endDate), (rs, i) -> new CategorySpendingRow(
                null, rs.getObject("category_id", Long.class), rs.getString("code"), rs.getString("name"),
                rs.getLong("total"), rs.getLong("cnt")));
    }

    public List<CategorySpendingRow> readByMonthAndCategory(Long userId, LocalDate startDate, LocalDate endDate) {
        return jdbcTemplate.query(CATEGORY_MONTHLY_SQL, params(userId, startDate, endDate), (rs, i) -> new CategorySpendingRow(
                YearMonth.of(rs.getInt("y"), rs.getInt("m")), rs.getObject("category_id", Long.class),
                rs.getString("code"), rs.getString("name"), rs.getLong("total"), rs.getLong("cnt")));
    }

    public List<PaymentMethodSpendingRow> readByPaymentMethod(Long userId, LocalDate startDate, LocalDate endDate) {
        return jdbcTemplate.query(PAYMENT_METHOD_SQL, params(userId, startDate, endDate), (rs, i) -> new PaymentMethodSpendingRow(
                rs.getString("payment_method"), rs.getLong("total"), rs.getLong("cnt")));
    }

    private static MapSqlParameterSource params(Long userId, LocalDate startDate, LocalDate endDate) {
        return new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("startDate", startDate)
                .addValue("endDate", endDate);
    }

    public record MonthlySpendingRow(YearMonth yearMonth, long total, long count) {
    }

    public record DailySpendingRow(LocalDate date, long total, long count) {
    }

    /**
     * @param yearMonth  월별 집계일 때만 있음
     * @param categoryId 미분류면 null(code·name도 null)
     */
    public record CategorySpendingRow(YearMonth yearMonth, Long categoryId, String code, String name, long total, long count) {
    }

    /**
     * @param paymentMethod 저장값 그대로. 없으면 null
     */
    public record PaymentMethodSpendingRow(String paymentMethod, long total, long count) {
    }
}
