package com.ssafy.dodam.domain.pattern.repository;

import com.ssafy.dodam.domain.pattern.dto.response.PatternFeatureSummary.SourceFingerprint;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 패턴 분석(API 3)이 쓰는 읽기 전용 JDBC.
 *
 * <p>소비 정의는 API 6과 같다: `transaction_type = EXPENSE`, `spending_type`이 NULL이거나 NON_SPENDING이 아닌 것, 숨기지 않은 거래.
 * 수입은 INCOME 거래 합, 저축성 지출은 NON_SPENDING EXPENSE 합(API 6 saving-ratio의 `savingTransfer`와 같은 정의)이다.
 * 재량 소비(discretionary)는 외식·카페·쇼핑·뷰티·문화·여행·택시 카테고리 소비다. 대출상환은 `LOAN_REPAYMENT` 카테고리다.
 *
 * <p>군집 학습용 모집단 조회({@link #readPopulationMonthly})는 사용자별·월별 집계값만 돌려준다. 거래 원문·식별정보는 읽지 않는다.
 * 애플리케이션은 이 집계로 비율 특징만 만들고 사용자 ID는 표본 수 계산에만 쓴다(기획안 14-6 "익명·집계 통계로만 수행").
 */
@Repository
public class JdbcPatternFeatureReader {

    public static final List<String> DISCRETIONARY_CATEGORY_CODES =
            List.of("FOOD", "CAFE", "SHOPPING", "BEAUTY", "CULTURE", "TRAVEL", "TAXI");
    public static final String LOAN_REPAYMENT_CATEGORY_CODE = "LOAN_REPAYMENT";

    private static final String IS_SPENDING = """
            (entry.transaction_type = 'EXPENSE'
              AND (entry.spending_type IS NULL OR entry.spending_type <> 'NON_SPENDING'))
            """;

    private static final String MONTHLY_SELECT = """
            SELECT YEAR(entry.transaction_date) AS y, MONTH(entry.transaction_date) AS m,
                   COALESCE(SUM(CASE WHEN entry.transaction_type = 'INCOME' THEN entry.amount ELSE 0 END), 0) AS income,
                   COALESCE(SUM(CASE WHEN """ + IS_SPENDING + """
                       THEN entry.amount ELSE 0 END), 0) AS spending,
                   COALESCE(SUM(CASE WHEN """ + IS_SPENDING + """
                       AND entry.spending_type = 'FIXED' THEN entry.amount ELSE 0 END), 0) AS fixed_spending,
                   COALESCE(SUM(CASE WHEN """ + IS_SPENDING + """
                       AND entry.spending_type = 'VARIABLE' THEN entry.amount ELSE 0 END), 0) AS variable_spending,
                   COALESCE(SUM(CASE WHEN """ + IS_SPENDING + """
                       AND category.code IN (:discretionaryCodes) THEN entry.amount ELSE 0 END), 0) AS discretionary,
                   COALESCE(SUM(CASE WHEN entry.transaction_type = 'EXPENSE' AND entry.spending_type = 'NON_SPENDING'
                       THEN entry.amount ELSE 0 END), 0) AS saving_transfer,
                   COALESCE(SUM(CASE WHEN """ + IS_SPENDING + """
                       AND category.code = :loanCode THEN entry.amount ELSE 0 END), 0) AS loan_repayment,
                   COUNT(*) AS cnt
            """;

    private static final String USER_MONTHLY_SQL = MONTHLY_SELECT + """
            FROM `transaction` entry
            LEFT JOIN transaction_category category ON category.category_id = entry.category_id
            WHERE entry.user_id = :userId
              AND entry.hidden = FALSE
              AND entry.internal_transfer = FALSE
              AND entry.transaction_date BETWEEN :startDate AND :endDate
            GROUP BY YEAR(entry.transaction_date), MONTH(entry.transaction_date)
            ORDER BY y, m
            """;

    private static final String POPULATION_MONTHLY_SQL = MONTHLY_SELECT.replaceFirst(
            "SELECT ", "SELECT entry.user_id AS uid, ") + """
            FROM `transaction` entry
            JOIN `user` owner ON owner.user_id = entry.user_id AND owner.deleted_at IS NULL
            LEFT JOIN transaction_category category ON category.category_id = entry.category_id
            WHERE entry.hidden = FALSE
              AND entry.internal_transfer = FALSE
              AND entry.transaction_date BETWEEN :startDate AND :endDate
            GROUP BY entry.user_id, YEAR(entry.transaction_date), MONTH(entry.transaction_date)
            ORDER BY uid, y, m
            """;

    private static final String USER_CATEGORY_SQL = """
            SELECT category.code, category.name, COALESCE(SUM(entry.amount), 0) AS total, COUNT(*) AS cnt
            FROM `transaction` entry
            LEFT JOIN transaction_category category ON category.category_id = entry.category_id
            WHERE entry.user_id = :userId
              AND entry.hidden = FALSE
              AND entry.internal_transfer = FALSE
              AND """ + IS_SPENDING + """
              AND entry.transaction_date BETWEEN :startDate AND :endDate
            GROUP BY category.code, category.name
            ORDER BY total DESC, category.code
            """;

    // (700) 재사용 판정용 지문. 숨김 필터를 걸지 않아 숨김 전환도 updated_at으로 잡힌다. 원문·식별정보는 읽지 않는다.
    private static final String SOURCE_FINGERPRINT_SQL = """
            SELECT COUNT(*) AS cnt, COALESCE(SUM(amount), 0) AS amount_sum,
                   MAX(created_at) AS last_created_at, MAX(updated_at) AS last_updated_at
            FROM `transaction`
            WHERE user_id = :userId
              AND internal_transfer = FALSE
              AND transaction_date BETWEEN :startDate AND :endDate
            """;

    private static final String LIQUIDITY_SQL = """
            SELECT COALESCE(SUM(CASE WHEN account_type = 'DEMAND_DEPOSIT' AND status = 'ACTIVE' THEN balance ELSE 0 END), 0)
                       AS liquid_balance,
                   COUNT(*) AS account_count
            FROM account
            WHERE user_id = :userId
            """;

    private static final String PROFILE_INCOME_SQL = """
            SELECT monthly_income FROM financial_profile WHERE user_id = :userId
            """;

    private static final String DEMOGRAPHICS_SQL = """
            SELECT user_id, birth_date, gender
            FROM `user`
            WHERE deleted_at IS NULL AND (birth_date IS NOT NULL OR gender IS NOT NULL)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcPatternFeatureReader(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 한 사용자의 월별 집계(거래가 있는 달만, 오래된 순). */
    public List<MonthlyPatternRow> readUserMonthly(Long userId, LocalDate startDate, LocalDate endDate) {
        return jdbcTemplate.query(USER_MONTHLY_SQL, window(startDate, endDate).addValue("userId", userId),
                (rs, i) -> mapMonthly(userId, rs.getInt("y"), rs.getInt("m"), rs));
    }

    /** 삭제되지 않은 전체 사용자의 월별 집계. 군집 모델 학습 입력이다. */
    public List<MonthlyPatternRow> readPopulationMonthly(LocalDate startDate, LocalDate endDate) {
        return jdbcTemplate.query(POPULATION_MONTHLY_SQL, window(startDate, endDate),
                (rs, i) -> mapMonthly(rs.getLong("uid"), rs.getInt("y"), rs.getInt("m"), rs));
    }

    public List<CategoryPatternRow> readUserCategories(Long userId, LocalDate startDate, LocalDate endDate) {
        return jdbcTemplate.query(USER_CATEGORY_SQL, window(startDate, endDate).addValue("userId", userId),
                (rs, i) -> new CategoryPatternRow(rs.getString("code"), rs.getString("name"), rs.getLong("total"), rs.getLong("cnt")));
    }

    /** 분석 기간 안 사용자 거래의 지문(700). 같은 날 완료 분석을 재사용해도 되는지 판정하는 데 쓴다. */
    public SourceFingerprint readSourceFingerprint(Long userId, LocalDate startDate, LocalDate endDate) {
        return jdbcTemplate.queryForObject(SOURCE_FINGERPRINT_SQL, window(startDate, endDate).addValue("userId", userId),
                (rs, i) -> new SourceFingerprint(rs.getLong("cnt"), rs.getLong("amount_sum"),
                        toLocalDateTime(rs.getTimestamp("last_created_at")), toLocalDateTime(rs.getTimestamp("last_updated_at"))));
    }

    public LiquidityRow readLiquidity(Long userId) {
        return jdbcTemplate.queryForObject(LIQUIDITY_SQL, new MapSqlParameterSource("userId", userId),
                (rs, i) -> new LiquidityRow(rs.getLong("liquid_balance"), rs.getLong("account_count")));
    }

    /**
     * 삭제되지 않은 사용자의 생년월일·성별(632). 인프로세스 학습이 동년배 집단 통계를 만들 때 쓴다. 둘 다 없는 사용자는 빼고 돌려준다.
     * Python 학습 스크립트의 DEMOGRAPHICS_SQL과 같은 조회다.
     */
    public Map<Long, Demographics> readDemographics() {
        Map<Long, Demographics> result = new java.util.HashMap<>();
        jdbcTemplate.query(DEMOGRAPHICS_SQL, new MapSqlParameterSource(), rs -> {
            java.sql.Date birth = rs.getDate("birth_date");
            result.put(rs.getLong("user_id"), new Demographics(birth == null ? null : birth.toLocalDate(), rs.getString("gender")));
        });
        return result;
    }

    /** 금융 기본정보의 월 소득. 없거나 0이면 null. */
    public Long readProfileMonthlyIncome(Long userId) {
        List<Long> rows = jdbcTemplate.query(PROFILE_INCOME_SQL, new MapSqlParameterSource("userId", userId),
                (rs, i) -> rs.getObject("monthly_income", Long.class));
        if (rows.isEmpty()) {
            return null;
        }
        Long income = rows.get(0);
        return income == null || income <= 0 ? null : income;
    }

    private static MonthlyPatternRow mapMonthly(Long userId, int year, int month, java.sql.ResultSet rs)
            throws java.sql.SQLException {
        return new MonthlyPatternRow(
                userId, YearMonth.of(year, month),
                rs.getLong("income"), rs.getLong("spending"), rs.getLong("fixed_spending"), rs.getLong("variable_spending"),
                rs.getLong("discretionary"), rs.getLong("saving_transfer"), rs.getLong("loan_repayment"), rs.getLong("cnt"));
    }

    private static MapSqlParameterSource window(LocalDate startDate, LocalDate endDate) {
        return new MapSqlParameterSource()
                .addValue("startDate", Objects.requireNonNull(startDate))
                .addValue("endDate", Objects.requireNonNull(endDate))
                .addValue("discretionaryCodes", DISCRETIONARY_CATEGORY_CODES)
                .addValue("loanCode", LOAN_REPAYMENT_CATEGORY_CODE);
    }

    /** 사용자·월별 집계 한 행. 금액 단위는 원. */
    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    public record MonthlyPatternRow(
            Long userId,
            YearMonth yearMonth,
            long income,
            long spending,
            long fixedSpending,
            long variableSpending,
            long discretionary,
            long savingTransfer,
            long loanRepayment,
            long transactionCount
    ) {
    }

    /** 632 동년배 비교용 인구통계. 값이 없는 항목은 null. */
    public record Demographics(LocalDate birthDate, String gender) {
    }

    public record CategoryPatternRow(String code, String name, long total, long count) {
    }

    /** 입출금 활성 계좌 잔액 합과 사용자 계좌 수(유형·상태 무관). 계좌 수가 0이면 유동성을 판정하지 않는다. */
    public record LiquidityRow(long liquidBalance, long accountCount) {
    }
}
