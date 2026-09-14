package com.ssafy.dodam.domain.pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.pattern.dto.request.AnalyzePatternRequest;
import com.ssafy.dodam.domain.pattern.dto.response.AnalyzePatternResult;
import com.ssafy.dodam.domain.pattern.service.ClusterModelProvider;
import com.ssafy.dodam.domain.pattern.service.PatternAnalysisRunner;
import com.ssafy.dodam.domain.pattern.service.PatternClock;
import com.ssafy.dodam.domain.pattern.service.PatternQueryService;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 로컬 일회용 MySQL에서만 실행한다. 일반 test는 외부 DB 없이 이 테스트를 건너뛴다.
 *
 * <p>H2는 Flyway를 끄고 엔티티로 스키마를 만들기 때문에 V31 DDL과 매핑이 어긋나도 통과한다. 이 테스트가 확인하는 것은
 * (1) V31이 적용된 스키마가 `ddl-auto: validate`를 통과하는가(DECIMAL(10,4) ↔ BigDecimal, JSON ↔ JsonNode),
 * (2) 실제 MySQL 집계 SQL(YEAR/MONTH·CASE·IN 목록)이 동작하는가, (3) JSON 컬럼이 문자열 리터럴이 아닌 객체로 저장돼 `JSON_EXTRACT`가 통하는가다.
 *
 * <p>실행 방법: V1~V31이 적용된 `dodam_516_test` 스키마를 가진 MySQL 8.0을 띄우고(빈 DB는 V6이 V11의 card를 참조하므로 README 순서로 손 적용)
 * `PATTERN_MYSQL_TEST_URL=jdbc:mysql://127.0.0.1:<port>/dodam_516_test`, `PATTERN_MYSQL_TEST_PASSWORD`를 설정한다. Flyway는 끄고 validate만 한다.
 */
@EnabledIfEnvironmentVariable(
        named = "PATTERN_MYSQL_TEST_URL",
        matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/dodam_516_test(?:\\?.*)?"
)
@ActiveProfiles("test")
@SpringBootTest
class PatternMySqlIntegrationTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 10, 0);
    private static final long USER_BASE = 516_000L;
    private static final List<YearMonth> WINDOW = List.of(
            YearMonth.of(2026, 3), YearMonth.of(2026, 4), YearMonth.of(2026, 5),
            YearMonth.of(2026, 6), YearMonth.of(2026, 7), YearMonth.of(2026, 8));

    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired PatternAnalysisRunner runner;
    @Autowired PatternQueryService query;
    @Autowired ClusterModelProvider modelProvider;
    @MockitoBean PatternClock clock;

    private final Map<String, Long> categories = new HashMap<>();
    private int sequence;

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("PATTERN_MYSQL_TEST_URL"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("PATTERN_MYSQL_TEST_PASSWORD", ""));
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        // PATTERN_MYSQL_TEST_ARTIFACT=file:/path/spending-kmeans-v1.json 이면 Python 학습 아티팩트 경로를 함께 검증한다.
        registry.add("app.pattern.model-location", () -> System.getenv().getOrDefault("PATTERN_MYSQL_TEST_ARTIFACT", ""));
    }

    private static boolean artifactEnabled() {
        String location = System.getenv("PATTERN_MYSQL_TEST_ARTIFACT");
        return location != null && !location.isBlank();
    }

    @BeforeEach
    void setUp() {
        given(clock.now()).willReturn(NOW);
        given(clock.today()).willReturn(NOW.toLocalDate());
        cleanUp();
        modelProvider.invalidate();
        jdbc.query("SELECT category_id, code FROM transaction_category", rs -> {
            categories.put(rs.getString("code"), rs.getLong("category_id"));
        });
        assertThat(categories).containsKeys("INCOME", "SAVING", "HOUSING", "FOOD", "GROCERY", "SHOPPING", "LOAN_REPAYMENT");
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    void 실제_Flyway_스키마에서_분석을_실행하고_JSON을_객체로_저장한다() {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'pattern_analysis'
                  AND column_name IN ('feature_summary_json', 'cluster_stats_json') AND data_type = 'json'
                """, Integer.class)).isEqualTo(2);

        long owner = USER_BASE;
        insertUser(owner, "패턴 본인");
        seedOwner(owner);
        insertAccount(owner, 6_000_000);
        for (int i = 1; i <= 40; i++) {
            long userId = USER_BASE + i;
            insertUser(userId, "모집단 " + i);
            seedPopulationMember(userId, i);
        }

        AnalyzePatternResult result = runner.run(owner, new AnalyzePatternRequest(null, "HYBRID"));

        assertThat(result.cached()).isFalse();
        assertThat(result.analysis().status()).isEqualTo("COMPLETED");
        assertThat(result.analysis().analysisMode()).isEqualTo("HYBRID");
        assertThat(result.analysis().metrics().debtBurden()).isEqualTo(0.1);
        assertThat(result.analysis().metrics().fixedExpenseRatio()).isEqualTo(0.5556);
        assertThat(result.analysis().liquidityLevel()).isEqualTo("HIGH");
        assertThat(result.analysis().clusterProfile()).isNotNull();
        assertThat(result.analysis().clusterProfile().clusterLabel()).isEqualTo("저축 중심형");
        if (artifactEnabled()) {
            assertThat(result.analysis().clusterProfile().modelSource()).isEqualTo("ARTIFACT");
            assertThat(result.analysis().clusterProfile().trainedBy()).startsWith("python-sklearn");
            assertThat(result.analysis().featureSummary().populationSize()).isGreaterThanOrEqualTo(41);
        } else {
            assertThat(result.analysis().clusterProfile().modelSource()).isEqualTo("IN_PROCESS");
            assertThat(result.analysis().featureSummary().populationSize()).isEqualTo(41);
        }

        Long id = result.analysis().analysisId();
        assertThat(jdbc.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(cluster_stats_json, '$.clusterLabel')) FROM pattern_analysis WHERE analysis_id = ?",
                String.class, id)).isEqualTo("저축 중심형");
        assertThat(jdbc.queryForObject(
                "SELECT JSON_UNQUOTE(JSON_EXTRACT(feature_summary_json, '$.windowStart')) FROM pattern_analysis WHERE analysis_id = ?",
                String.class, id)).isEqualTo("2026-03");
        assertThat(jdbc.queryForObject(
                "SELECT JSON_LENGTH(feature_summary_json, '$.months') FROM pattern_analysis WHERE analysis_id = ?", Integer.class, id))
                .isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT debt_burden FROM pattern_analysis WHERE analysis_id = ?", String.class, id))
                .isEqualTo("0.1000");

        assertThat(runner.run(owner, new AnalyzePatternRequest(false, "HYBRID")).cached()).isTrue();
        assertThat(query.getLatest(owner).clusterProfile().sampleSize()).isGreaterThanOrEqualTo(10);
        assertThat(query.getSpendingPattern(owner, 6).totalSpending()).isEqualTo(1_800_000L * 6);
        assertThat(query.getCashflowPattern(owner, null).liquidity().level()).isEqualTo("HIGH");
        assertThat(query.getPriorityProfile(owner).source()).isEqualTo("PATTERN_ANALYSIS");
    }

    // ---------- fixture ----------

    private void seedOwner(long userId) {
        for (YearMonth ym : WINDOW) {
            tx(userId, ym.atDay(25), "INCOME", "INCOME", "NON_SPENDING", 3_000_000);
            tx(userId, ym.atDay(5), "EXPENSE", "HOUSING", "FIXED", 700_000);
            tx(userId, ym.atDay(20), "EXPENSE", "LOAN_REPAYMENT", "FIXED", 300_000);
            tx(userId, ym.atDay(12), "EXPENSE", "FOOD", "VARIABLE", 400_000);
            tx(userId, ym.atDay(15), "EXPENSE", "GROCERY", "VARIABLE", 400_000);
            tx(userId, ym.atDay(26), "EXPENSE", "SAVING", "NON_SPENDING", 1_000_000);
        }
    }

    /** 성향 4종(저축·소비·변동·균형)을 10명씩 돌려 넣는다. */
    private void seedPopulationMember(long userId, int index) {
        int type = index % 4;
        long jitter = (index / 4) * 5_000L;
        for (int m = 0; m < WINDOW.size(); m++) {
            YearMonth ym = WINDOW.get(m);
            tx(userId, ym.atDay(25), "INCOME", "INCOME", "NON_SPENDING", 3_000_000);
            switch (type) {
                case 0 -> {
                    tx(userId, ym.atDay(5), "EXPENSE", "HOUSING", "FIXED", 700_000);
                    tx(userId, ym.atDay(12), "EXPENSE", "FOOD", "VARIABLE", 400_000 + jitter);
                    tx(userId, ym.atDay(15), "EXPENSE", "GROCERY", "VARIABLE", 400_000);
                    tx(userId, ym.atDay(26), "EXPENSE", "SAVING", "NON_SPENDING", 1_000_000);
                }
                case 1 -> {
                    tx(userId, ym.atDay(5), "EXPENSE", "HOUSING", "FIXED", 700_000);
                    tx(userId, ym.atDay(12), "EXPENSE", "FOOD", "VARIABLE", 1_000_000 + jitter);
                    tx(userId, ym.atDay(15), "EXPENSE", "SHOPPING", "VARIABLE", 1_000_000);
                }
                case 2 -> {
                    tx(userId, ym.atDay(5), "EXPENSE", "HOUSING", "FIXED", 700_000);
                    tx(userId, ym.atDay(12), "EXPENSE", "FOOD", "VARIABLE", (m % 2 == 0 ? 500_000 : 1_900_000) + jitter);
                    tx(userId, ym.atDay(26), "EXPENSE", "SAVING", "NON_SPENDING", 200_000);
                }
                default -> {
                    tx(userId, ym.atDay(5), "EXPENSE", "HOUSING", "FIXED", 900_000);
                    tx(userId, ym.atDay(12), "EXPENSE", "FOOD", "VARIABLE", 500_000 + jitter);
                    tx(userId, ym.atDay(15), "EXPENSE", "GROCERY", "VARIABLE", 600_000);
                    tx(userId, ym.atDay(26), "EXPENSE", "SAVING", "NON_SPENDING", 500_000);
                }
            }
        }
    }

    private void tx(long userId, LocalDate date, String type, String category, String spendingType, long amount) {
        jdbc.update("""
                INSERT INTO `transaction` (transaction_id, user_id, account_id, card_id, category_id, transaction_date, transaction_time,
                                           transaction_type, amount, merchant_name, payment_method, spending_type, hidden, source_type, created_at)
                VALUES (?, ?, NULL, NULL, ?, ?, '10:00:00', ?, ?, ?, 'ACCOUNT', ?, 0, 'SEED', ?)
                """, "PT516-" + userId + "-" + (++sequence), userId, categories.get(category), date, type, amount, category,
                spendingType, Timestamp.valueOf(NOW.minusDays(20)));
    }

    private void insertUser(long userId, String name) {
        jdbc.update("""
                INSERT INTO `user` (user_id, email, password_hash, name, role, created_at, updated_at)
                VALUES (?, ?, 'unused', ?, 'USER', ?, ?)
                """, userId, "pattern516-" + userId + "@example.com", name, Timestamp.valueOf(NOW), Timestamp.valueOf(NOW));
    }

    private void insertAccount(long userId, long balance) {
        jdbc.update("""
                INSERT INTO account (user_id, bank_code, bank_name, account_no_encrypted, account_no_hash, account_no_masked, account_name,
                                     account_type, status, balance, currency, source_type, created_at, updated_at)
                VALUES (?, '004', 'KB국민은행', X'00', ?, '***-**-1234', '주거래', 'DEMAND_DEPOSIT', 'ACTIVE', ?, 'KRW', 'SEED', ?, ?)
                """, userId, String.format("%064d", userId), balance, Timestamp.valueOf(NOW), Timestamp.valueOf(NOW));
    }

    private void cleanUp() {
        jdbc.update("DELETE FROM pattern_analysis WHERE user_id BETWEEN ? AND ?", USER_BASE, USER_BASE + 999);
        jdbc.update("DELETE FROM `transaction` WHERE transaction_id LIKE 'PT516-%'");
        jdbc.update("DELETE FROM account WHERE user_id BETWEEN ? AND ?", USER_BASE, USER_BASE + 999);
        jdbc.update("DELETE FROM `user` WHERE user_id BETWEEN ? AND ?", USER_BASE, USER_BASE + 999);
    }
}
