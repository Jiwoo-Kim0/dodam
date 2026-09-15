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
import java.util.Map;
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
        dropTriggers();
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

    /**
     * 617: 저장 도중 실패하면 세 테이블 모두 남지 않아야 한다. 운영 코드를 바꾸지 않고 "N행 중간"에서 실패시키기 위해
     * DB 트리거로 특정 행의 INSERT를 거부한다(Mockito로 리포지토리를 바꾸면 Spring 컨텍스트가 하나 더 생겨 587에서 겪은
     * 캐시 축출이 재발한다). 저장 순서는 simulation → scenario N행(IDENTITY라 save마다 INSERT) → goal_impact다.
     */
    @Test
    void 복합_저장_중간_실패는_세_테이블_모두_0행으로_롤백된다() throws Exception {
        // SAVING 자식이 있어야 goal_impact 행이 생긴다(setup의 목표). 3번째 자식(CASH_ADVANCE)에서 (a), goal_impact에서 (b) 실패시킨다
        String body = "{\"scenarios\":["
                + "{\"type\":\"SAVING\",\"parameters\":{\"monthlySavingAmount\":300000}},"
                + "{\"type\":\"INSTALLMENT\",\"parameters\":{\"purchaseAmount\":1200000,\"months\":2,\"feeRate\":12}},"
                + "{\"type\":\"CASH_ADVANCE\",\"parameters\":{\"amount\":1000000,\"rate\":12}}]}";
        try {
            // (a) simulation 1행 + scenario 2행이 들어간 뒤 3번째 scenario에서 실패
            jdbc.execute("""
                    CREATE TRIGGER trg_617_scenario BEFORE INSERT ON simulation_scenario FOR EACH ROW
                    BEGIN
                      IF NEW.sequence_no = 3 THEN
                        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '617 test: reject third scenario';
                      END IF;
                    END""");
            run(USER_ID, "/combined", body).andExpect(status().is5xxServerError());
            assertNoRowsFor(USER_ID);
            dropTriggers();

            // (b) simulation + scenario 3행이 모두 들어간 뒤 goal_impact에서 실패
            jdbc.execute("""
                    CREATE TRIGGER trg_617_goal_impact BEFORE INSERT ON simulation_goal_impact FOR EACH ROW
                    BEGIN
                      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = '617 test: reject goal impact';
                    END""");
            run(USER_ID, "/combined", body).andExpect(status().is5xxServerError());
            assertNoRowsFor(USER_ID);
        } finally {
            dropTriggers();
        }
        // 부분 저장 행이 남았다면 같은 날 재사용(200)이 됐을 것이다. 201이면 롤백과 사용자 행 잠금 해제가 모두 확인된다
        String saved = run(USER_ID, "/combined", body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.created").value(true)).andReturn().getResponse().getContentAsString();
        long id = json.readTree(saved).at("/data/simulation/simulationId").asLong();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM simulation_scenario WHERE simulation_id = ?", Integer.class, id)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM simulation_goal_impact WHERE simulation_id = ?", Integer.class, id)).isEqualTo(1);
    }

    /**
     * 617: 목록은 created_at DESC, simulation_id DESC(SimulationRepository.findOwned). 같은 날 같은 요청은 재사용되므로
     * clock을 하루씩 옮겨 행을 만들고, 동률 정렬은 created_at을 jdbc로 맞춰 본다. 유형 없는 목록 조회(JPQL의
     * {@code :type is null or ...} null 바인딩)는 MySQL에서 처음 실행한다.
     */
    @Test
    void 목록은_created_at_DESC_id_DESC_이며_idx_simulation_user_type_created가_있다() throws Exception {
        long[] cashAdvance = new long[3];
        for (int day = 0; day < 3; day++) {
            given(clock.now()).willReturn(NOW.plusDays(day));
            cashAdvance[day] = idOf(run(USER_ID, "/cash-advance", "{\"amount\":1000000,\"rate\":12}").andExpect(status().isCreated()));
        }
        given(clock.now()).willReturn(NOW.plusDays(1));
        long installment = idOf(run(USER_ID, "/installment", "{\"purchaseAmount\":1200000,\"months\":2,\"feeRate\":12}")
                .andExpect(status().isCreated()));
        given(clock.now()).willReturn(NOW.plusDays(5));
        long other = idOf(run(OTHER_USER_ID, "/cash-advance", "{\"amount\":1000000,\"rate\":12}").andExpect(status().isCreated()));
        given(clock.now()).willReturn(NOW);
        // 동률: 첫 두 건의 created_at을 같게 → id DESC가 순서를 정한다
        jdbc.update("UPDATE simulation SET created_at = ? WHERE simulation_id IN (?, ?)", NOW, cashAdvance[0], cashAdvance[1]);

        mvc.perform(get("/api/v1/simulations?type=CASH_ADVANCE").with(user(Long.toString(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content[0].simulationId").value(cashAdvance[2]))
                .andExpect(jsonPath("$.data.content[1].simulationId").value(cashAdvance[1]))
                .andExpect(jsonPath("$.data.content[2].simulationId").value(cashAdvance[0]));
        mvc.perform(get("/api/v1/simulations").with(user(Long.toString(USER_ID))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.content[0].simulationId").value(cashAdvance[2]))
                .andExpect(jsonPath("$.data.content[1].simulationId").value(installment))
                .andExpect(jsonPath("$.data.content[2].simulationId").value(cashAdvance[1]))
                .andExpect(jsonPath("$.data.content[3].simulationId").value(cashAdvance[0]));
        mvc.perform(get("/api/v1/simulations").with(user(Long.toString(OTHER_USER_ID))))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].simulationId").value(other));

        assertThat(indexColumns("simulation", "idx_simulation_user_type_created")).containsExactly("user_id", "type", "created_at");
        // 손으로 쓴 SQL이다. findOwned의 JPQL이 바뀌면 같이 고친다. 행이 적어 옵티마이저가 풀스캔을 고를 수 있으므로
        // 실제 선택(key)은 단언하지 않고 possible_keys까지만 본다
        Map<String, Object> withType = explain("""
                SELECT simulation_id FROM simulation
                 WHERE user_id = ? AND deleted_at IS NULL AND (? IS NULL OR type = ?)
                 ORDER BY created_at DESC, simulation_id DESC
                """, USER_ID, "CASH_ADVANCE", "CASH_ADVANCE");
        Map<String, Object> withoutType = explain("""
                SELECT simulation_id FROM simulation
                 WHERE user_id = ? AND deleted_at IS NULL AND (? IS NULL OR type = ?)
                 ORDER BY created_at DESC, simulation_id DESC
                """, USER_ID, null, null);
        assertThat(String.valueOf(withType.get("possible_keys"))).contains("idx_simulation_user_type_created");
        assertThat(String.valueOf(withoutType.get("possible_keys"))).contains("idx_simulation_user_type_created");
        System.out.printf("617 EXPLAIN findOwned type=CASH_ADVANCE: key=%s Extra=%s%n", withType.get("key"), withType.get("Extra"));
        System.out.printf("617 EXPLAIN findOwned type=null: key=%s Extra=%s%n", withoutType.get("key"), withoutType.get("Extra"));
    }

    /**
     * 617: (user_id, simulation_id) UNIQUE는 goal_impact의 복합 FK가 참조하는 부모 키다. 조회는 PK로도 되므로
     * "본인 조건 조회"는 동작(본인 200·타인 404)과 제약(소유자 변경 거부) 두 면에서 본다.
     */
    @Test
    void uk_simulation_user_simulation_id는_복합FK의_부모이며_본인_조건_조회를_지탱한다() throws Exception {
        assertThat(indexColumns("simulation", "uk_simulation_user_simulation_id")).containsExactly("user_id", "simulation_id");
        assertThat(jdbc.queryForObject("""
                SELECT NON_UNIQUE FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'simulation' AND INDEX_NAME = 'uk_simulation_user_simulation_id' LIMIT 1
                """, Integer.class)).isEqualTo(0);
        assertThat(jdbc.queryForObject("""
                SELECT UNIQUE_CONSTRAINT_NAME FROM information_schema.REFERENTIAL_CONSTRAINTS
                 WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = 'fk_simulation_goal_impact_simulation'
                """, String.class)).isEqualTo("uk_simulation_user_simulation_id");

        long id = idOf(run(USER_ID, "/saving", "{\"monthlySavingAmount\":500000}").andExpect(status().isCreated()));
        String owner = Long.toString(USER_ID);
        String stranger = Long.toString(OTHER_USER_ID);
        mvc.perform(get("/api/v1/simulations/{id}", id).with(user(owner))).andExpect(status().isOk());
        mvc.perform(get("/api/v1/simulations/{id}/goal-impact", id).with(user(owner))).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].goalId").value(GOAL_ID));
        for (String path : new String[] {"/api/v1/simulations/{id}", "/api/v1/simulations/{id}/goal-impact"}) {
            mvc.perform(get(path, id).with(user(stranger))).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
            mvc.perform(get(path, id + 1_000_000).with(user(owner))).andExpect(status().isNotFound());
        }
        mvc.perform(delete("/api/v1/simulations/{id}", id).with(user(stranger))).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NULL FROM simulation WHERE simulation_id = ?", Boolean.class, id)).isTrue();

        // goal_impact가 (user_id, simulation_id)를 참조하므로 소유자만 바꾸면 자식이 고아가 된다 → DB가 거부(ON UPDATE 기본 RESTRICT)
        assertThatThrownBy(() -> jdbc.update("UPDATE simulation SET user_id = ? WHERE simulation_id = ?", OTHER_USER_ID, id))
                .isInstanceOf(DataAccessException.class).hasMessageContaining("fk_simulation_goal_impact_simulation");
        assertThat(jdbc.queryForObject("SELECT user_id FROM simulation WHERE simulation_id = ?", Long.class, id)).isEqualTo(USER_ID);

        Map<String, Object> plan = explain(
                "SELECT simulation_id FROM simulation WHERE user_id = ? AND simulation_id = ? AND deleted_at IS NULL", USER_ID, id);
        assertThat(String.valueOf(plan.get("possible_keys"))).contains("PRIMARY").contains("uk_simulation_user_simulation_id");
        System.out.printf("617 EXPLAIN owner lookup: key=%s%n", plan.get("key"));

        mvc.perform(delete("/api/v1/simulations/{id}", id).with(user(owner))).andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/simulations/{id}/goal-impact", id).with(user(owner))).andExpect(status().isNotFound());
    }

    private void assertNoRowsFor(long userId) {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM simulation WHERE user_id = ?", Integer.class, userId)).isZero();
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM simulation_scenario WHERE simulation_id IN (SELECT simulation_id FROM simulation WHERE user_id = ?)",
                Integer.class, userId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM simulation_goal_impact WHERE user_id = ?", Integer.class, userId)).isZero();
    }

    /** 테스트가 중간에 죽어 트리거가 남으면 이후 저장이 전부 실패하므로 setup에서도 부른다. */
    private void dropTriggers() {
        jdbc.execute("DROP TRIGGER IF EXISTS trg_617_scenario");
        jdbc.execute("DROP TRIGGER IF EXISTS trg_617_goal_impact");
    }

    private List<String> indexColumns(String table, String index) {
        return jdbc.queryForList("""
                SELECT COLUMN_NAME FROM information_schema.STATISTICS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = ? AND INDEX_NAME = ? ORDER BY SEQ_IN_INDEX
                """, String.class, table, index);
    }

    private Map<String, Object> explain(String sql, Object... args) {
        return jdbc.queryForMap("EXPLAIN " + sql, args);
    }

    private long idOf(org.springframework.test.web.servlet.ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString()).at("/data/simulation/simulationId").asLong();
    }

    private org.springframework.test.web.servlet.ResultActions run(long userId, String path, String body) throws Exception {
        return mvc.perform(post("/api/v1/simulations" + path).with(user(Long.toString(userId))).contentType(JSON).content(body));
    }
}
