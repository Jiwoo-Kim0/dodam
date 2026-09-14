package com.ssafy.dodam.domain.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisIssue;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisStatus;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import com.ssafy.dodam.domain.diagnosis.service.DiagnosisStore;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationCandidate;
import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import com.ssafy.dodam.domain.recommendation.service.RecommendationStore;
import com.ssafy.dodam.domain.recommendation.service.RecommendationStore.PrioritizedCandidate;
import com.ssafy.dodam.domain.recommendation.service.RecommendationStore.SaveRequest;
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
 * <p>H2 테스트가 검증하지 못하는 것: V18 DDL과 엔티티 매핑의 일치(`ddl-auto: validate`), V30이 먼저
 * 적용된 DB에 V18이 out-of-order로 들어가는지, `(user_id, diagnosis_id)`·`(user_id, goal_id)` 복합 FK,
 * `chk_recommendation_*` CHECK, JSON 컬럼 타입.
 *
 * <p>실행 방법: `dodam_547_test` 스키마를 가진 MySQL 8.0 컨테이너를 띄우고
 * `RECOMMENDATION_MYSQL_TEST_URL=jdbc:mysql://127.0.0.1:<port>/dodam_547_test`를 설정한다.
 *
 * <p><b>빈 스키마에서는 Flyway가 V6에서 실패한다.</b> V6(`transaction`)이 V11(`card`)을 FK로 참조하는데 버전
 * 순서상 V6이 먼저 실행되기 때문이다(develop의 기존 문제, 운영 DB는 V11 뒤에 V6이 out-of-order로 들어가
 * 무관). 2026-09-13 검증은 V1~V17을 V6을 V11 뒤로 돌려 mysql 클라이언트로 수동 적용한 뒤
 * `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true SPRING_FLYWAY_BASELINE_VERSION=17`로 실행해 Flyway가 V18·V30만
 * 적용하게 했다. 이 방식에서는 V18의 out-of-order 적용 자체는 검증되지 않는다(`FlywayOutOfOrderTest`가 담당).
 */
@EnabledIfEnvironmentVariable(
        named = "RECOMMENDATION_MYSQL_TEST_URL",
        matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/dodam_547_test(?:\\?.*)?"
)
@ActiveProfiles("test")
@SpringBootTest
@Transactional
class RecommendationMySqlIntegrationTest {

    private static final long USER_ID = 547001L;
    private static final long OTHER_USER_ID = 547002L;
    private static final long GOAL_ID = 547901L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 13, 9, 0);
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    @Autowired private DiagnosisStore diagnosisStore;
    @Autowired private RecommendationStore store;
    @Autowired private JdbcTemplate jdbc;

    @PersistenceContext private EntityManager entityManager;

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("RECOMMENDATION_MYSQL_TEST_URL"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    void 실제_Flyway_스키마에서_저장과_제약을_검증한다() {
        insertUsersAndGoal();

        // V18·V30이 Flyway로 적용됐는지만 본다. V5·V17은 baseline 아래에 있을 수 있어(아래 Javadoc) 세지 않는다.
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1 AND version IN ('18', '30')",
                Integer.class
        )).isEqualTo(2);

        assertThat(jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.REFERENTIAL_CONSTRAINTS
                 WHERE CONSTRAINT_SCHEMA = DATABASE()
                   AND ((CONSTRAINT_NAME = 'fk_recommendation_diagnosis' AND REFERENCED_TABLE_NAME = 'diagnosis')
                     OR (CONSTRAINT_NAME = 'fk_recommendation_goal' AND REFERENCED_TABLE_NAME = 'goal'))
                """,
                Integer.class
        )).isEqualTo(2);

        // status, priority, saving_amounts, history_status
        assertThat(jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM information_schema.CHECK_CONSTRAINTS
                 WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME LIKE 'chk_recommendation%'
                """,
                Integer.class
        )).isEqualTo(4);

        assertThat(jdbc.queryForObject(
                """
                SELECT DATA_TYPE FROM information_schema.COLUMNS
                 WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'recommendation_reason' AND COLUMN_NAME = 'evidence'
                """,
                String.class
        )).isEqualTo("json");

        Long diagnosisId = completedDiagnosis();
        Recommendation saved = store.save(new SaveRequest(USER_ID, diagnosisId, GOAL_ID, List.of(
                new PrioritizedCandidate(candidate("CARD:1"), 1))), NOW).get(0).recommendation();
        entityManager.flush();

        assertThat(jdbc.queryForObject(
                "SELECT status FROM recommendation WHERE recommendation_id = ?", String.class, saved.getRecommendationId()
        )).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject(
                "SELECT CHAR_LENGTH(generation_key) FROM recommendation WHERE recommendation_id = ?",
                Integer.class, saved.getRecommendationId()
        )).isEqualTo(64);
        // JSON 컬럼을 MySQL이 JSON으로 읽는지 확인한다. 문자열로 저장됐다면 경로 추출이 NULL이다.
        assertThat(jdbc.queryForObject(
                "SELECT JSON_EXTRACT(evidence, '$.fee') FROM recommendation_reason WHERE recommendation_id = ?",
                String.class, saved.getRecommendationId()
        )).isEqualTo("12000");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM recommendation_history WHERE recommendation_id = ? AND status = 'PENDING'",
                Integer.class, saved.getRecommendationId()
        )).isEqualTo(1);
    }

    /** 복합 FK: 타인의 진단 id를 내 user_id로 넣으면 (user_id, diagnosis_id) 조합이 없어 거부된다. */
    @Test
    void 타인_진단을_참조하는_행을_DB가_막는다() {
        insertUsersAndGoal();
        Long diagnosisId = completedDiagnosis();
        entityManager.flush();

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO recommendation
                       (user_id, diagnosis_id, domain, type, title, description, priority, status, created_at)
                VALUES (?, ?, 'CARD', 'T', 't', 'd', 1, 'PENDING', ?)
                """, OTHER_USER_ID, diagnosisId, NOW))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("fk_recommendation_diagnosis");
    }

    @Test
    void 우선순위와_금액_CHECK가_살아_있다() {
        insertUsersAndGoal();
        Long diagnosisId = completedDiagnosis();
        Recommendation saved = store.save(new SaveRequest(USER_ID, diagnosisId, GOAL_ID, List.of(
                new PrioritizedCandidate(candidate("CARD:1"), 1))), NOW).get(0).recommendation();
        entityManager.flush();

        // MySQL CHECK 위반(error 3819)은 UncategorizedSQLException으로 매핑되므로 제약 이름으로 확인한다.
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE recommendation SET priority = 0 WHERE recommendation_id = ?", saved.getRecommendationId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_recommendation_priority");
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE recommendation SET monthly_cost_saving = -1 WHERE recommendation_id = ?", saved.getRecommendationId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_recommendation_saving_amounts");
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE recommendation SET status = 'UNKNOWN' WHERE recommendation_id = ?", saved.getRecommendationId()))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("chk_recommendation_status");
    }

    private Long completedDiagnosis() {
        Diagnosis diagnosis = diagnosisStore.start(USER_ID, GOAL_ID, NOW);
        Long id = diagnosis.getDiagnosisId();
        diagnosisStore.complete(diagnosis,
                List.of(DiagnosisIssue.ofRule(id, "CARD", "HIGH_CARD_FEE", IssueSeverity.HIGH, "t", "d",
                        json("{\"amount\":150000}"), null, null, NOW)),
                DiagnosisStatus.CAUTION, json("{\"asOf\":\"2026-09-13\"}"), NOW.plusMinutes(1));
        return id;
    }

    private RecommendationCandidate candidate(String key) {
        return new RecommendationCandidate("test-v1", key, null, "CARD", "SWITCH_CARD", "제목", "설명",
                List.of(new RecommendationCandidate.Reason("FEE", "연회비", json("{\"fee\":12000}"))), null);
    }

    private JsonNode json(String raw) {
        try {
            return JSON_MAPPER.readTree(raw);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** `recommendation`은 `user`·`diagnosis`·`goal`을 참조하므로 부모 행을 먼저 만든다. */
    private void insertUsersAndGoal() {
        jdbc.update("""
                INSERT IGNORE INTO user (user_id, email, name, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """, USER_ID, "recommendation547@example.com", "추천테스트", NOW, NOW);
        jdbc.update("""
                INSERT IGNORE INTO user (user_id, email, name, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?)
                """, OTHER_USER_ID, "recommendation547b@example.com", "다른사용자", NOW, NOW);
        jdbc.update("""
                INSERT IGNORE INTO goal
                       (goal_id, user_id, name, type, status, current_amount, created_at, updated_at)
                VALUES (?, ?, ?, 'SAVING', 'ACTIVE', 0, ?, ?)
                """, GOAL_ID, USER_ID, "테스트 목표", NOW, NOW);
    }
}
