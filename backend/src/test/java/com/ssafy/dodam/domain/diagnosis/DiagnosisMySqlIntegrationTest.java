package com.ssafy.dodam.domain.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisIssue;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisStatus;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisIssueRepository;
import com.ssafy.dodam.domain.diagnosis.service.DiagnosisStore;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로컬 일회용 MySQL에서만 실행한다. 일반 test는 외부 DB 없이 이 테스트를 건너뛴다.
 *
 * <p>H2 테스트는 Flyway를 끄고 엔티티로 스키마를 만들기 때문에 `V5`·`V17`의 DDL과 엔티티 매핑이
 * 어긋나도, MySQL `CHECK`가 없어도, `goal` 외래키가 없어도 통과한다. 이 테스트가 확인하는 것은
 * H2가 검증할 수 없는 세 가지다.
 *
 * <ol>
 *   <li>`goal` 테이블 없이는 만들 수 없는 `fk_diagnosis_goal`이 실제로 존재하는가
 *   <li>JSON 컬럼 5종이 MySQL `JSON` 타입으로 만들어지고 `ddl-auto: validate`를 통과하는가
 *   <li>집계값 상하한과 코드값 `CHECK`가 DB 단계에서 살아 있는가
 * </ol>
 *
 * <p>실행 방법: 빈 `dodam_543_test` 스키마를 가진 MySQL 8.0 컨테이너를 띄우고
 * `DIAGNOSIS_MYSQL_TEST_URL=jdbc:mysql://127.0.0.1:<port>/dodam_543_test`를 설정한다.
 */
@EnabledIfEnvironmentVariable(
        named = "DIAGNOSIS_MYSQL_TEST_URL",
        matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/dodam_543_test(?:\\?.*)?"
)
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class DiagnosisMySqlIntegrationTest {

    private static final long USER_ID = 543001L;
    private static final long GOAL_ID = 543901L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 10, 9, 0);
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Autowired private DiagnosisStore store;
    @Autowired private DiagnosisIssueRepository issueRepository;
    @Autowired private JdbcTemplate jdbc;

    @PersistenceContext private EntityManager entityManager;

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("DIAGNOSIS_MYSQL_TEST_URL"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    void 실제_Flyway_스키마에서_저장과_제약을_검증한다() {
        insertUserAndGoal();

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1 AND version IN ('5', '17')",
                Integer.class
        )).isEqualTo(2);

        // goal 테이블 없이는 만들 수 없는 외래키다. V5를 빠뜨리면 여기서 걸린다.
        assertThat(jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
                 WHERE CONSTRAINT_SCHEMA = DATABASE()
                   AND CONSTRAINT_NAME = 'fk_diagnosis_goal'
                   AND REFERENCED_TABLE_NAME = 'goal'
                """,
                Integer.class
        )).isEqualTo(1);

        // overall_status, issue_count, high_severity_count, severity, detection_source
        assertThat(jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS
                 WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME LIKE 'chk_diagnosis%'
                """,
                Integer.class
        )).isEqualTo(5);

        // JSON 컬럼이 문자열 타입으로 떨어지면 ddl-auto: validate가 통과해도 저장 형식이 달라진다.
        assertThat(jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND DATA_TYPE = 'json'
                   AND ((TABLE_NAME = 'diagnosis' AND COLUMN_NAME = 'input_snapshot')
                     OR (TABLE_NAME = 'diagnosis_issue'
                         AND COLUMN_NAME IN ('current_value', 'benchmark_value', 'evidence',
                                             'cluster_context_json')))
                """,
                Integer.class
        )).isEqualTo(5);

        Diagnosis diagnosis = store.start(USER_ID, GOAL_ID, NOW);
        Long diagnosisId = diagnosis.getDiagnosisId();
        store.complete(
                diagnosis,
                List.of(
                        DiagnosisIssue.ofRule(diagnosisId, "SPENDING", "OVERSPENDING",
                                IssueSeverity.HIGH, "지출이 많습니다", "설명",
                                json("{\"amount\":1200000}"), json("{\"p50\":900000}"), null, NOW),
                        DiagnosisIssue.ofRule(diagnosisId, "SAVING", "LOW_SAVING_RATE",
                                IssueSeverity.LOW, "저축률이 낮습니다", "설명", null, null, null, NOW)),
                DiagnosisStatus.CAUTION,
                json("{\"asOf\":\"2026-09-10\"}"),
                NOW.plusMinutes(1));

        // 이 테스트는 한 트랜잭션 안에서 롤백되므로 JPA 변경분이 아직 DB에 없다. 운영에서는 커밋이
        // 하는 일을 여기서는 flush로 대신해, 아래 원시 SQL이 실제 저장된 값을 읽게 한다.
        entityManager.flush();

        assertThat(jdbc.queryForObject(
                "SELECT issue_count FROM diagnosis WHERE diagnosis_id = ?", Integer.class, diagnosisId
        )).isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT high_severity_count FROM diagnosis WHERE diagnosis_id = ?",
                Integer.class, diagnosisId
        )).isEqualTo(1);
        // JSON 컬럼을 MySQL이 JSON으로 읽는지 확인한다. 문자열로 저장됐다면 경로 추출이 NULL이다.
        assertThat(jdbc.queryForObject(
                "SELECT JSON_EXTRACT(input_snapshot, '$.asOf') FROM diagnosis WHERE diagnosis_id = ?",
                String.class, diagnosisId
        )).contains("2026-09-10");
        // 하위행은 JPA가 아니라 JDBC 배치로 들어간다. 그 경로도 JSON 객체로 저장돼야 한다.
        assertThat(jdbc.queryForObject(
                """
                SELECT JSON_EXTRACT(current_value, '$.amount') FROM diagnosis_issue
                 WHERE diagnosis_id = ? AND type = 'OVERSPENDING'
                """,
                String.class, diagnosisId
        )).isEqualTo("1200000");
        assertThat(issueRepository.findAllOwned(USER_ID, diagnosisId)).hasSize(2);
    }

    @Test
    void 집계값_상하한을_DB가_막는다() {
        insertUserAndGoal();
        Diagnosis diagnosis = store.start(USER_ID, null, NOW);
        entityManager.flush();

        // 엔티티가 먼저 막지만 DB 제약도 살아 있어야 한다. 애플리케이션을 우회한 경로가 생겨도
        // 집계값이 깨지지 않는다.
        //
        // 예외 타입이 DataIntegrityViolationException이 아니다. Spring은 MySQL의 CHECK 위반
        // (error 3819)을 UncategorizedSQLException으로 매핑한다. 타입 대신 위반한 제약 이름으로
        // 확인한다. 544가 이 오류를 응답으로 바꿀 때도 같은 사실을 고려해야 한다.
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE diagnosis SET issue_count = 1, high_severity_count = 2 WHERE diagnosis_id = ?",
                diagnosis.getDiagnosisId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_diagnosis_high_severity_count");
    }

    @Test
    void 알_수_없는_코드값을_DB가_막는다() {
        insertUserAndGoal();
        Diagnosis diagnosis = store.start(USER_ID, null, NOW);
        entityManager.flush();

        assertThatThrownBy(() -> jdbc.update(
                "UPDATE diagnosis SET overall_status = 'UNKNOWN' WHERE diagnosis_id = ?",
                diagnosis.getDiagnosisId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_diagnosis_overall_status");
    }

    private JsonNode json(String raw) {
        try {
            return JSON_MAPPER.readTree(raw);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** `diagnosis`는 `user`와 `goal`을 참조하므로 부모 행을 먼저 만든다. */
    private void insertUserAndGoal() {
        jdbc.update("""
                INSERT IGNORE INTO user (user_id, email, name, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """, USER_ID, "diagnosis543@example.com", "진단테스트", NOW, NOW);
        jdbc.update("""
                INSERT IGNORE INTO goal
                       (goal_id, user_id, name, type, status, current_amount, created_at, updated_at)
                VALUES (?, ?, ?, 'SAVING', 'ACTIVE', 0, ?, ?)
                """, GOAL_ID, USER_ID, "테스트 목표", NOW, NOW);
    }
}
