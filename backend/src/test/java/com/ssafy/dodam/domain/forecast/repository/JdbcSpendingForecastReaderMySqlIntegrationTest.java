package com.ssafy.dodam.domain.forecast.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.dodam.domain.forecast.service.SpendingForecastCalculator.MonthlyPoint;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * 월별 변동 소비 조회(706)를 MySQL 8.0에서 실측한다. H2(MySQL 모드)는 같은 SQL을 다르게 해석할 수 있어
 * {@code SpendingForecastApiTest}의 H2 회귀만으로는 덮이지 않는다.
 *
 * <p>확인 대상은 셋이다. {@code YEAR()}·{@code MONTH()}를 별칭으로 뽑아 그 별칭으로 {@code GROUP BY}하는 형태,
 * {@code COALESCE(거래.spending_type, 카테고리.default_spending_type, 'VARIABLE')} 해석 규칙, 그리고 달 경계가
 * 포함 구간인지다.
 *
 * <p><b>이 테스트는 기존 행을 지우지 않는다.</b> 같은 계층의 {@code JdbcGoalPaceReaderMySqlIntegrationTest}는
 * {@code DELETE FROM `transaction`}으로 시작하지만, 706의 검증용 DB에는 24개월 seed 139만 행이 들어 있어 그 방식을
 * 쓸 수 없다. 조회가 {@code user_id}로 좁혀지므로 자기 사용자만 만들어 쓰면 충분하고, 클래스에 {@code @Transactional}을
 * 걸어 검증이 끝나면 넣은 행이 롤백된다. 덕분에 빈 스키마에서도, seed가 적재된 DB에서도 같은 결과가 나온다.
 *
 * <p><b>seed 데이터로는 {@code COALESCE} 분기가 타지 않는다.</b> 적재된 139만 행의 {@code spending_type} NULL이
 * 0건, {@code category_id} NULL이 0건이라 실제 데이터는 첫 인자에서 끝난다. 규칙을 보려면 이 테스트처럼 행을 넣어야 한다.
 *
 * <p>실행 방법: 24개월 seed가 적재된 MySQL 8.0을 띄우고
 * {@code SPENDING_FORECAST_MYSQL_TEST_URL=jdbc:mysql://127.0.0.1:<port>/dodam_706_test}를 설정한다. 빈 스키마를 쓸
 * 때는 마이그레이션이 버전 순서대로 적용되지 않는다 — V6(transaction)이 V11(card)을 참조하므로 <b>V6을 V11 뒤로 옮겨</b>
 * 미리 올려 두고 Flyway는 끈다. 실제 운영 자격증명은 쓰지 않는다.
 */
@EnabledIfEnvironmentVariable(
        named = "SPENDING_FORECAST_MYSQL_TEST_URL",
        matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/dodam_706_test(?:\\?.*)?"
)
@ActiveProfiles("test")
@SpringBootTest(properties = {
        "logging.level.org.springframework.jdbc=INFO",
        "logging.level.org.hibernate.SQL=INFO"
})
@Transactional
class JdbcSpendingForecastReaderMySqlIntegrationTest {

    private static final YearMonth FROM = YearMonth.of(2023, 1);
    private static final YearMonth TO = YearMonth.of(2023, 12);

    @Autowired JdbcSpendingForecastReader reader;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;

    private Long userId;

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("SPENDING_FORECAST_MYSQL_TEST_URL"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> "");
        // 스키마는 컨테이너 준비 단계에서 올린다. V6(transaction)이 V11(card)을 참조해 버전 순서대로 적용할 수 없다.
        registry.add("spring.flyway.enabled", () -> "false");
        // validate가 아니라 none이다. seed를 적재해 둔 검증 DB는 스키마가 그 시점에 멈춰 있어 이후 마이그레이션이
        // 더한 컬럼에서 검증이 깨진다(실측: monthly_report.metrics_summary 없음). 이 테스트가 보는 것은 조회 SQL이고
        // 리더는 raw JDBC라 엔티티 매핑이 필요 없다. 쓰는 테이블은 transaction·transaction_category·user 셋뿐이다.
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
    }

    @BeforeEach
    void setUp() {
        // 사용자를 새로 만들지 않고 이미 있는 행 하나를 빌린다. seed를 적재해 둔 DB는 스키마가 적재 시점에 멈춰 있어
        // 현재 User 엔티티로 INSERT하면 그 뒤에 추가된 컬럼에서 깨진다(실측: Unknown column 'credit_score', 680 V42).
        // 조회 구간이 seed 창(2024-09~2026-09) 밖인 2023년이라 빌린 사용자의 기존 거래는 결과에 섞이지 않는다.
        // 빈 스키마에서는 빌릴 행이 없으므로 그때만 만든다 — 그 경우 엔티티와 스키마가 어긋날 일도 없다.
        userId = jdbc.queryForList("SELECT user_id FROM `user` ORDER BY user_id LIMIT 1", Long.class).stream()
                .findFirst()
                .orElseGet(() -> users.saveAndFlush(
                        User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "예측 조회")).getId());

        // 빌린 사용자라도 2023년은 비어 있어야 기대값이 성립한다. 아니면 여기서 먼저 깨진다.
        assertThat(reader.readMonthlyVariableSpending(userId, FROM, TO)).isEmpty();
    }

    @Test
    @DisplayName("YEAR/MONTH 별칭 GROUP BY가 달별로 묶이고 거래가 없는 달은 행이 없다")
    void groupsByCalendarMonthAndSkipsEmptyMonths() {
        expense(LocalDate.of(2023, 3, 5), 100_000, "VARIABLE", null);
        expense(LocalDate.of(2023, 3, 20), 50_000, "VARIABLE", null);
        // 2023-04은 비운다
        expense(LocalDate.of(2023, 5, 9), 70_000, "VARIABLE", null);

        List<MonthlyPoint> points = reader.readMonthlyVariableSpending(userId, FROM, TO);

        assertThat(points).containsExactly(
                new MonthlyPoint(YearMonth.of(2023, 3), 150_000L),
                new MonthlyPoint(YearMonth.of(2023, 5), 70_000L));
    }

    @Test
    @DisplayName("spending_type이 NULL이면 카테고리 기본값으로, 카테고리도 없으면 VARIABLE로 해석한다")
    void resolvesSpendingTypeByCoalesce() {
        Long variableCategory = categoryWithDefault("VARIABLE");
        Long fixedCategory = categoryWithDefault("FIXED");

        expense(LocalDate.of(2023, 7, 3), 11_000, null, variableCategory);
        expense(LocalDate.of(2023, 7, 4), 44_000, null, null);
        expense(LocalDate.of(2023, 7, 5), 22_000, null, fixedCategory);
        expense(LocalDate.of(2023, 7, 6), 33_000, "NON_SPENDING", variableCategory);
        expense(LocalDate.of(2023, 7, 7), 55_000, "FIXED", variableCategory);
        // 지출이 아닌 행은 금액이 커도 들어오면 안 된다
        income(LocalDate.of(2023, 7, 8), 3_000_000);

        List<MonthlyPoint> points = reader.readMonthlyVariableSpending(userId, FROM, TO);

        assertThat(points).containsExactly(new MonthlyPoint(YearMonth.of(2023, 7), 55_000L));
    }

    @Test
    @DisplayName("조회 구간의 첫날과 마지막 날을 포함한다")
    void boundsAreInclusive() {
        expense(FROM.atDay(1), 10_000, "VARIABLE", null);
        expense(TO.atEndOfMonth(), 20_000, "VARIABLE", null);
        expense(FROM.minusMonths(1).atEndOfMonth(), 90_000, "VARIABLE", null);
        expense(TO.plusMonths(1).atDay(1), 90_000, "VARIABLE", null);

        List<MonthlyPoint> points = reader.readMonthlyVariableSpending(userId, FROM, TO);

        assertThat(points).containsExactly(
                new MonthlyPoint(FROM, 10_000L),
                new MonthlyPoint(TO, 20_000L));
    }

    // ---------- helpers ----------

    private void income(LocalDate date, long amount) {
        transaction(date, "INCOME", amount, null, null);
    }

    private void expense(LocalDate date, long amount, String spendingType, Long categoryId) {
        transaction(date, "EXPENSE", amount, spendingType, categoryId);
    }

    private void transaction(LocalDate date, String type, long amount, String spendingType, Long categoryId) {
        jdbc.update("""
                INSERT INTO `transaction` (transaction_id, user_id, account_id, card_id, category_id, transaction_date,
                    transaction_time, transaction_type, amount, balance_after, merchant_name, payment_method, spending_type,
                    summary, memo, hidden, source_type, created_at, updated_at)
                VALUES (?, ?, NULL, NULL, ?, ?, NULL, ?, ?, NULL, NULL, 'CARD', ?, 'test', NULL, FALSE, 'SEED', ?, NULL)
                """, "TEST-706-" + UUID.randomUUID(), userId, categoryId, date, type, amount, spendingType,
                LocalDateTime.now());
    }

    /** seed가 적재된 DB에는 이미 있고, 빈 스키마에서는 만들어 쓴다. 어느 쪽이든 같은 규칙을 본다 */
    private Long categoryWithDefault(String defaultSpendingType) {
        List<Long> found = jdbc.queryForList(
                "SELECT category_id FROM transaction_category WHERE default_spending_type = ? ORDER BY category_id LIMIT 1",
                Long.class, defaultSpendingType);
        if (!found.isEmpty()) {
            return found.get(0);
        }
        String code = "TEST_706_" + defaultSpendingType;
        jdbc.update("INSERT INTO transaction_category (code, name, default_spending_type, active) VALUES (?, ?, ?, TRUE)",
                code, code, defaultSpendingType);
        return jdbc.queryForObject("SELECT category_id FROM transaction_category WHERE code = ?", Long.class, code);
    }
}
