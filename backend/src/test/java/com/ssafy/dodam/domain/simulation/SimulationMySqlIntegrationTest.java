package com.ssafy.dodam.domain.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.goal.service.GoalClock;
import com.ssafy.dodam.domain.simulation.service.SimulationClock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 로컬 일회용 MySQL에서만 실행한다(S15P21D105-587). 일반 test는 외부 DB 없이 이 테스트를 건너뛴다.
 *
 * <p>H2 테스트가 검증하지 못하는 것: V19 DDL과 엔티티 매핑의 일치(`ddl-auto: validate`), JSON 컬럼 타입과 JSON 경로 조회,
 * `chk_simulation_*` CHECK 3종, `(user_id, simulation_id)`·`(user_id, goal_id)` 복합 FK, 복합 시뮬레이션의 scenario N행 저장,
 * 같은 날 재사용 조회의 DATETIME 범위 비교, 소프트 삭제 후 미조회, 동일 사용자 동시 실행이 `SELECT … FOR UPDATE`로 1건만 저장되는지.
 *
 * <p>실행 방법: `dodam_587_test` 스키마를 가진 MySQL 8.0 컨테이너를 띄우고
 * `SIMULATION_MYSQL_TEST_URL=jdbc:mysql://127.0.0.1:<port>/dodam_587_test`를 설정한다.
 * 빈 스키마에서 Flyway가 V6에서 실패하는 문제와 우회 방법은 {@code RecommendationMySqlIntegrationTest} Javadoc과 같다.
 *
 * <p>동시 실행 검증을 위해 클래스 트랜잭션을 쓰지 않고 HTTP 요청마다 커밋한다. 만든 행은 {@link #cleanup()}에서 지운다.
 */
@EnabledIfEnvironmentVariable(
        named = "SIMULATION_MYSQL_TEST_URL",
        matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/dodam_587_test(?:\\?.*)?"
)
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
class SimulationMySqlIntegrationTest {
    private static final long USER_ID = 587001L;
    private static final long OTHER_USER_ID = 587002L;
    private static final long GOAL_ID = 587901L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 16, 9, 0);
    private static final String JSON = MediaType.APPLICATION_JSON_VALUE;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean SimulationClock clock;
    @MockitoBean GoalClock goalClock;

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("SIMULATION_MYSQL_TEST_URL"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @BeforeEach
    void setup() {
        given(clock.now()).willReturn(NOW);
        given(goalClock.now()).willReturn(NOW);
        given(goalClock.today()).willReturn(NOW.toLocalDate());
        cleanup();
        for (long id : new long[] {USER_ID, OTHER_USER_ID}) {
            jdbc.update("INSERT IGNORE INTO user (user_id, email, name, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                    id, "simulation587-" + id + "@example.com", "시뮬레이션테스트", NOW, NOW);
        }
        // 연결 계좌 없는 모으기 목표 → 속도 0, 남은 1,000,000. saving 시뮬레이션이 goal_impact 행을 만든다
        jdbc.update("""
                INSERT INTO goal (goal_id, user_id, name, type, status, current_amount, created_at, updated_at)
                VALUES (?, ?, '테스트 목표', 'SAVING', 'ACTIVE', 0, ?, ?)
                """, GOAL_ID, USER_ID, NOW.minusMonths(1), NOW);
        jdbc.update("INSERT INTO saving_goal (goal_id, goal_type, target_amount, target_date) VALUES (?, 'SAVING', 1000000, ?)", GOAL_ID,
                NOW.toLocalDate().plusMonths(2));
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM simulation_goal_impact WHERE user_id IN (?, ?)", USER_ID, OTHER_USER_ID);
        jdbc.update("DELETE FROM simulation_scenario WHERE simulation_id IN (SELECT simulation_id FROM simulation WHERE user_id IN (?, ?))",
                USER_ID, OTHER_USER_ID);
        jdbc.update("DELETE FROM simulation WHERE user_id IN (?, ?)", USER_ID, OTHER_USER_ID);
        jdbc.update("DELETE FROM saving_goal WHERE goal_id = ?", GOAL_ID);
        jdbc.update("DELETE FROM goal WHERE goal_id = ?", GOAL_ID);
        jdbc.update("DELETE FROM user WHERE user_id IN (?, ?)", USER_ID, OTHER_USER_ID);
    }

    @Test
    void V19_스키마와_JSON_컬럼_CHECK_복합FK가_적용되어_있다() {
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1 AND version = '19'", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS
                 WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME LIKE 'chk_simulation%'
                """, Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForList("""
                SELECT CONCAT(TABLE_NAME, '.', COLUMN_NAME) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND DATA_TYPE = 'json'
                   AND TABLE_NAME IN ('simulation', 'simulation_scenario')
                 ORDER BY 1
                """, String.class)).containsExactly("simulation.after_snapshot", "simulation.before_snapshot",
                "simulation.cashflow_impact", "simulation.comparison", "simulation.request_snapshot", "simulation_scenario.parameters");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
                 WHERE CONSTRAINT_SCHEMA = DATABASE()
                   AND ((CONSTRAINT_NAME = 'fk_simulation_goal_impact_simulation' AND REFERENCED_TABLE_NAME = 'simulation')
                     OR (CONSTRAINT_NAME = 'fk_simulation_goal_impact_goal' AND REFERENCED_TABLE_NAME = 'goal')
                     OR (CONSTRAINT_NAME = 'fk_simulation_scenario_simulation' AND REFERENCED_TABLE_NAME = 'simulation'))
                """, Integer.class)).isEqualTo(3);
    }

    @Test
    void 실행_저장_재사용_조회_삭제가_실제_스키마에서_동작한다() throws Exception {
        String body = run(USER_ID, "/saving", "{\"monthlySavingAmount\":500000}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.created").value(true))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].goalId").value(GOAL_ID))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].afterForecastDate").value("2026-11-16"))
                .andReturn().getResponse().getContentAsString();
        JsonNode simulation = json.readTree(body).at("/data/simulation");
        long id = simulation.path("simulationId").asLong();

        // JSON 컬럼을 MySQL이 JSON으로 읽는지: 문자열로 저장됐다면 경로 추출이 NULL이다
        assertThat(jdbc.queryForObject("SELECT JSON_EXTRACT(after_snapshot, '$.monthlySaving') FROM simulation WHERE simulation_id = ?",
                String.class, id)).isEqualTo("500000");
        assertThat(jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(request_snapshot, '$.requestHash')) FROM simulation WHERE simulation_id = ?",
                String.class, id)).hasSize(64);
        assertThat(jdbc.queryForObject("SELECT JSON_LENGTH(request_snapshot, '$.accountNo') FROM simulation WHERE simulation_id = ?",
                Integer.class, id)).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM simulation WHERE simulation_id = ?", String.class, id)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM simulation_scenario WHERE simulation_id = ? AND sequence_no = 1 AND scenario_type = 'SAVING'",
                Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM simulation_goal_impact WHERE user_id = ? AND simulation_id = ? AND goal_id = ?",
                Integer.class, USER_ID, id, GOAL_ID)).isEqualTo(1);

        // 같은 날 재사용은 DATETIME 범위 비교(createdAt ≥ 당일 00:00, < 익일 00:00)로 찾는다
        run(USER_ID, "/saving", "{\"monthlySavingAmount\":500000}").andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(false)).andExpect(jsonPath("$.data.simulation.simulationId").value(id));
        given(clock.now()).willReturn(NOW.plusDays(1));
        run(USER_ID, "/saving", "{\"monthlySavingAmount\":500000}").andExpect(status().isCreated());
        given(clock.now()).willReturn(NOW);

        // 조회는 저장 JSON을 그대로 돌려준다
        String detail = mvc.perform(get("/api/v1/simulations/{id}", id).with(user(Long.toString(USER_ID))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(detail).path("data")).isEqualTo(simulation);
        mvc.perform(get("/api/v1/simulations?type=SAVING").with(user(Long.toString(USER_ID))))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[1].simulationId").value(id));
        mvc.perform(get("/api/v1/simulations/{id}", id).with(user(Long.toString(OTHER_USER_ID)))).andExpect(status().isNotFound());

        // 소프트 삭제: 행은 남고 deleted_at만 채워지며 조회·재사용에서 빠진다
        mvc.perform(delete("/api/v1/simulations/{id}", id).with(user(Long.toString(USER_ID)))).andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM simulation WHERE simulation_id = ?", Boolean.class, id)).isTrue();
        mvc.perform(get("/api/v1/simulations/{id}", id).with(user(Long.toString(USER_ID)))).andExpect(status().isNotFound());
        run(USER_ID, "/saving", "{\"monthlySavingAmount\":500000}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.simulation.simulationId").value(org.hamcrest.Matchers.not((int) id)));
    }

    @Test
    void 복합_시뮬레이션은_scenario_N행을_저장하고_CHECK와_복합FK가_살아_있다() throws Exception {
        String body = run(USER_ID, "/combined", "{\"scenarios\":["
                + "{\"type\":\"INSTALLMENT\",\"parameters\":{\"purchaseAmount\":1200000,\"months\":2,\"feeRate\":12}},"
                + "{\"type\":\"CASH_ADVANCE\",\"parameters\":{\"amount\":1000000,\"rate\":12}}]}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).at("/data/simulation/simulationId").asLong();

        assertThat(jdbc.queryForList("SELECT scenario_type FROM simulation_scenario WHERE simulation_id = ? ORDER BY sequence_no", String.class, id))
                .containsExactly("INSTALLMENT", "CASH_ADVANCE");
        assertThat(jdbc.queryForObject(
                "SELECT JSON_EXTRACT(parameters, '$.purchaseAmount') FROM simulation_scenario WHERE simulation_id = ? AND sequence_no = 1",
                String.class, id)).isEqualTo("1200000");
        assertThat(jdbc.queryForObject("SELECT JSON_LENGTH(after_snapshot, '$.details.children') FROM simulation WHERE simulation_id = ?",
                Integer.class, id)).isEqualTo(2);

        // MySQL CHECK 위반(error 3819)은 UncategorizedSQLException으로 매핑되므로 제약 이름으로 확인한다
        assertThatThrownBy(() -> jdbc.update("UPDATE simulation SET type = 'UNKNOWN' WHERE simulation_id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("chk_simulation_type");
        assertThatThrownBy(() -> jdbc.update("UPDATE simulation SET status = 'DONE' WHERE simulation_id = ?", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("chk_simulation_status");
        assertThatThrownBy(() -> jdbc.update("UPDATE simulation_scenario SET sequence_no = 0 WHERE simulation_id = ? AND sequence_no = 2", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("chk_simulation_scenario_sequence_no");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO simulation_scenario (simulation_id, sequence_no, scenario_type, parameters) VALUES (?, 1, 'SPENDING', '{}')", id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("uk_simulation_scenario_sequence");
        // 복합 FK: 타인 user_id로 내 simulation을 참조하는 goal_impact는 (user_id, simulation_id) 조합이 없어 거부된다.
        // 같은 (simulation_id, goal_id)는 UNIQUE에 먼저 걸리므로 목표 id를 바꿔 FK만 검사한다
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO simulation_goal_impact (user_id, simulation_id, goal_id) VALUES (?, ?, ?)", OTHER_USER_ID, id, GOAL_ID + 1))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("fk_simulation_goal_impact");
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO simulation_goal_impact (user_id, simulation_id, goal_id) VALUES (?, ?, ?)", USER_ID, id, GOAL_ID))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("uk_simulation_goal_impact_simulation_goal");
    }

    @Test
    void 동일_사용자_동시_실행은_사용자_행_잠금으로_1건만_저장한다() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                Callable<Integer> call = () -> {
                    MvcResult result = run(USER_ID, "/cash-advance", "{\"amount\":1000000,\"rate\":12}").andReturn();
                    return result.getResponse().getStatus();
                };
                results.add(pool.submit(call));
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : results) {
                statuses.add(f.get());
            }
            assertThat(statuses).containsOnly(200, 201).contains(201);
            assertThat(statuses.stream().filter(s -> s == 201).count()).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM simulation WHERE user_id = ? AND type = 'CASH_ADVANCE'", Integer.class, USER_ID))
                .isEqualTo(1);
    }

    private org.springframework.test.web.servlet.ResultActions run(long userId, String path, String body) throws Exception {
        return mvc.perform(post("/api/v1/simulations" + path).with(user(Long.toString(userId))).contentType(JSON).content(body));
    }
}
