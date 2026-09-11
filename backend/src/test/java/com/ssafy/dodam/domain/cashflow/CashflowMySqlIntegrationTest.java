package com.ssafy.dodam.domain.cashflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.dodam.domain.cashflow.entity.CashflowDailyBalance;
import com.ssafy.dodam.domain.cashflow.entity.CashflowEvent;
import com.ssafy.dodam.domain.cashflow.entity.CashflowForecast;
import com.ssafy.dodam.domain.cashflow.entity.CashflowRisk;
import com.ssafy.dodam.domain.cashflow.entity.EventDirection;
import com.ssafy.dodam.domain.cashflow.entity.RiskSeverity;
import com.ssafy.dodam.domain.cashflow.repository.CashflowDailyBalanceRepository;
import com.ssafy.dodam.domain.cashflow.repository.CashflowEventRepository;
import com.ssafy.dodam.domain.cashflow.repository.CashflowResultWriter;
import com.ssafy.dodam.domain.cashflow.repository.CashflowRiskRepository;
import com.ssafy.dodam.domain.cashflow.service.CashflowForecastStore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로컬 일회용 MySQL에서만 실행한다. 일반 test는 외부 DB 없이 이 테스트를 건너뛴다.
 *
 * <p>H2 테스트는 Flyway를 끄고 엔티티로 스키마를 만들기 때문에 `V16`의 DDL과 엔티티 매핑이 어긋나도,
 * MySQL `CHECK`가 없어도 통과한다. 이 테스트는 실제 마이그레이션 위에서 `ddl-auto: validate`를
 * 통과시키고, 중복 반영 차단과 금액 부호 제약이 DB 단계에서 살아 있는지 확인한다.
 *
 * <p>실행 방법: 빈 `dodam_538_test` 스키마를 가진 MySQL 8.0 컨테이너를 띄우고
 * `CASHFLOW_MYSQL_TEST_URL=jdbc:mysql://127.0.0.1:<port>/dodam_538_test`를 설정한다.
 */
@EnabledIfEnvironmentVariable(
        named = "CASHFLOW_MYSQL_TEST_URL",
        matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/dodam_538_test(?:\\?.*)?"
)
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class CashflowMySqlIntegrationTest {

    private static final long USER_ID = 538001L;
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 9, 10);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 10, 9, 0);

    @Autowired private CashflowForecastStore store;
    @Autowired private CashflowResultWriter resultWriter;
    @Autowired private CashflowDailyBalanceRepository dailyBalanceRepository;
    @Autowired private CashflowEventRepository eventRepository;
    @Autowired private CashflowRiskRepository riskRepository;
    @Autowired private JdbcTemplate jdbc;

    @PersistenceContext private EntityManager entityManager;

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("CASHFLOW_MYSQL_TEST_URL"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    void 실제_Flyway_스키마에서_저장과_제약을_검증한다() {
        insertUser();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1 AND version = '16'",
                Integer.class
        )).isEqualTo(1);

        // ERD의 CHECK 4개(status, horizon_days, direction, amount, severity 중 cashflow_* 접두)를 확인한다.
        assertThat(jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS
                 WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME LIKE 'chk_cashflow%'
                """,
                Integer.class
        )).isEqualTo(5);

        CashflowForecast forecast = store.start(USER_ID, BASE_DATE, 3, "hash-mysql", NOW);
        Long forecastId = forecast.getForecastId();
        store.complete(
                forecast,
                List.of(
                        CashflowDailyBalance.of(forecastId, BASE_DATE, 1_000_000L),
                        // 음수 잔액이 BIGINT 컬럼에 그대로 들어가는지 확인한다.
                        CashflowDailyBalance.of(forecastId, BASE_DATE.plusDays(1), -50_000L)),
                List.of(CashflowEvent.of(forecastId, BASE_DATE, "CARD_BILLING", "카드대금",
                        EventDirection.OUTFLOW, 510_000L, "CARD", "1", "dedupe-mysql")),
                List.of(CashflowRisk.of(forecastId, BASE_DATE.plusDays(1), "NEGATIVE_BALANCE",
                        RiskSeverity.CRITICAL, "잔액이 마이너스가 됩니다.", -50_000L)),
                1_000_000L, 900_000L, -50_000L, BASE_DATE.plusDays(1), NOW.plusMinutes(1));

        // 이 테스트는 한 트랜잭션 안에서 롤백되므로 JPA 변경분이 아직 DB에 없다. 운영에서는 커밋이
        // 하는 일을 여기서는 flush로 대신해, 아래 원시 SQL이 실제 저장된 값을 읽게 한다.
        entityManager.flush();

        assertThat(jdbc.queryForObject(
                "SELECT expected_balance FROM cashflow_daily_balance"
                        + " WHERE forecast_id = ? AND balance_date = ?",
                Long.class, forecastId, BASE_DATE.plusDays(1)
        )).isEqualTo(-50_000L);
        assertThat(jdbc.queryForObject(
                "SELECT status FROM cashflow_forecast WHERE forecast_id = ?", String.class, forecastId
        )).isEqualTo("COMPLETED");

        // 조회 계약을 MySQL 방언으로도 실행한다. `:type is null` 선택 필터와 `nulls last` 정렬은
        // 방언마다 생성 SQL이 달라 H2 통과만으로는 확인되지 않는다.
        assertThat(dailyBalanceRepository.findOwnedByPeriod(
                USER_ID, forecastId, BASE_DATE, BASE_DATE.plusDays(7))).hasSize(2);
        assertThat(eventRepository.findOwnedByPeriod(
                USER_ID, forecastId, BASE_DATE, BASE_DATE.plusDays(7), null)).hasSize(1);
        assertThat(eventRepository.findOwnedByPeriod(
                USER_ID, forecastId, BASE_DATE, BASE_DATE.plusDays(7), "CARD_BILLING")).hasSize(1);
        assertThat(eventRepository.findOwnedByPeriod(
                USER_ID, forecastId, BASE_DATE, BASE_DATE.plusDays(7), "UNKNOWN_TYPE")).isEmpty();
        assertThat(riskRepository.findOwned(USER_ID, forecastId, null, null)).hasSize(1);
        assertThat(riskRepository.findOwned(USER_ID, forecastId, null, RiskSeverity.CRITICAL)).hasSize(1);
        assertThat(riskRepository.findOwned(USER_ID, forecastId, "NEGATIVE_BALANCE", null)).hasSize(1);
        // 타인 사용자로는 같은 forecastId도 읽히지 않는다.
        assertThat(dailyBalanceRepository.findOwnedByPeriod(
                USER_ID + 1, forecastId, BASE_DATE, BASE_DATE.plusDays(7))).isEmpty();

        // 같은 예측에 같은 dedupe_key를 다시 넣으면 유니크 제약이 막는다(중복 반영 금지의 최종 방어선).
        assertThatThrownBy(() -> resultWriter.insertEvents(List.of(
                CashflowEvent.of(forecastId, BASE_DATE, "CARD_BILLING", "중복 카드대금",
                        EventDirection.OUTFLOW, 510_000L, "CARD", "1", "dedupe-mysql"))))
                .isInstanceOf(DataIntegrityViolationException.class);

        // 금액 양수 CHECK가 살아 있는지 SQL로 직접 확인한다. 엔티티 검증을 우회해도 DB가 막아야 한다.
        // MySQL은 CHECK 위반을 오류코드 3819로 돌려주고 Spring은 이를 DataIntegrityViolationException이
        // 아니라 UncategorizedSQLException으로 매핑한다. 그래서 상위 타입과 제약 이름으로 단정한다.
        assertThatThrownBy(() -> jdbc.update(
                """
                INSERT INTO cashflow_event (forecast_id, event_date, type, name, direction, amount,
                    source_type, source_id, dedupe_key)
                VALUES (?, ?, 'CARD_BILLING', '음수 금액', 'OUTFLOW', -1, NULL, NULL, 'dedupe-negative')
                """,
                forecastId, BASE_DATE))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_cashflow_event_amount");

        assertThatThrownBy(() -> jdbc.update(
                """
                INSERT INTO cashflow_risk (forecast_id, risk_date, type, severity, message, expected_balance)
                VALUES (?, NULL, 'NEGATIVE_BALANCE', 'URGENT', NULL, NULL)
                """,
                forecastId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_cashflow_risk_severity");

        assertThatThrownBy(() -> jdbc.update(
                """
                INSERT INTO cashflow_forecast (user_id, base_date, horizon_days, status, created_at)
                VALUES (?, ?, 0, 'PENDING', NOW())
                """,
                USER_ID, BASE_DATE))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_cashflow_forecast_horizon_days");
    }

    private void insertUser() {
        jdbc.update(
                "INSERT INTO user (user_id, email, name, created_at, updated_at)"
                        + " VALUES (?, ?, 'test', NOW(), NOW())",
                USER_ID, "cashflow-" + USER_ID + "@example.com"
        );
    }
}
