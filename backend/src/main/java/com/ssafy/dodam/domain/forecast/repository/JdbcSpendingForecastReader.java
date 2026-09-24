package com.ssafy.dodam.domain.forecast.repository;

import com.ssafy.dodam.domain.forecast.service.SpendingForecastCalculator.MonthlyPoint;
import java.time.YearMonth;
import java.util.List;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 예측 입력용 월별 변동 소비 조회(S15P21D105-706).
 *
 * <p><b>왜 {@code JdbcPatternFeatureReader.readUserMonthly()}를 쓰지 않는가.</b> 그 쿼리의 {@code variable_spending}은
 * {@code spending_type = 'VARIABLE'} 명시값만 센다. 반면 V6의 {@code chk_transaction_spending_type}은 NULL을 허용하고
 * 패턴 쪽 {@code IS_SPENDING}은 NULL을 소비로 세므로, 명시값만 세면 NULL 잔여만큼 과소 집계된다. 그 쿼리에 해석 규칙을 넣으면
 * {@code fixedExpenseShare}·{@code discretionaryShare}가 같이 움직여 K-Means 특징과 저장된 {@code pattern_analysis}로
 * 번지는데, 700의 무효화는 거래 지문 비교라 <b>SQL 규칙 변경을 감지하지 못한다.</b> 그래서 패턴 쪽은 그대로 두고 예측 전용으로 둔다.
 *
 * <p>해석 규칙은 {@code COALESCE(거래.spending_type, 카테고리.default_spending_type, 'VARIABLE')}이다. 카테고리가
 * {@code HOUSING}·{@code INSURANCE}인데 거래의 값이 NULL이면 기본값이 {@code FIXED}라 예측 대상에서 빠진다. 무조건
 * {@code VARIABLE}로 두는 것보다 이 편이 맞다. 카테고리가 없는 거래는 LEFT JOIN으로 남겨 두고 {@code VARIABLE}로 본다.
 *
 * <p>거래가 없는 달은 <b>행이 없다.</b> 0으로 채우지 않는다. 0을 관측치로 넣으면 수준이 끌려 내려간다.
 */
@Repository
public class JdbcSpendingForecastReader {

    private static final String MONTHLY_VARIABLE_SQL = """
            SELECT YEAR(entry.transaction_date) AS y,
                   MONTH(entry.transaction_date) AS m,
                   SUM(entry.amount) AS variable_spending
            FROM `transaction` entry
            LEFT JOIN transaction_category category ON category.category_id = entry.category_id
            WHERE entry.user_id = :userId
              AND entry.transaction_date BETWEEN :startDate AND :endDate
              AND entry.transaction_type = 'EXPENSE'
              AND COALESCE(entry.spending_type, category.default_spending_type, 'VARIABLE') = 'VARIABLE'
            GROUP BY y, m
            ORDER BY y, m
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcSpendingForecastReader(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * @param from 포함하는 첫 달
     * @param to   포함하는 마지막 달
     * @return 거래가 있는 달만, 오래된 순
     */
    public List<MonthlyPoint> readMonthlyVariableSpending(Long userId, YearMonth from, YearMonth to) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("userId", userId)
                .addValue("startDate", from.atDay(1))
                .addValue("endDate", to.atEndOfMonth());
        return jdbcTemplate.query(MONTHLY_VARIABLE_SQL, params,
                (rs, i) -> new MonthlyPoint(YearMonth.of(rs.getInt("y"), rs.getInt("m")),
                        rs.getLong("variable_spending")));
    }
}
