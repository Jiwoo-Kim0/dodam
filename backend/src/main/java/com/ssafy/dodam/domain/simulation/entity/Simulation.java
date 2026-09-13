package com.ssafy.dodam.domain.simulation.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 시뮬레이션 실행 1건. 하위행 `simulation_scenario`·`simulation_goal_impact`는 diagnosis와 같은 이유로
 * 연관관계 대신 {@code simulationId} 값으로 잇는다.
 *
 * <p>스냅샷 4종은 {@link JsonNode}다. `String`에 {@code @JdbcTypeCode(SqlTypes.JSON)}을 붙이면 문자열 리터럴로
 * 한 번 더 감싸져 `JSON_EXTRACT`가 통하지 않는다(Diagnosis.inputSnapshot 주석 참고).
 *
 * <p>UNIQUE를 엔티티에도 선언하는 이유는 H2 테스트가 Flyway 없이 엔티티로 스키마를 만들기 때문이다.
 * CHECK는 H2에 없으므로 생성자가 먼저 막는다.
 */
@Getter
@Entity
@Table(name = "simulation", uniqueConstraints = @UniqueConstraint(
        name = "uk_simulation_user_simulation_id", columnNames = {"user_id", "simulation_id"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Simulation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "simulation_id")
    private Long simulationId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 50)
    private SimulationType type;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "summary", length = 500)
    private String summary;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private SimulationStatus status;

    /** 요청 파라미터와 요청 지문(`requestHash`). 계좌번호 원문·해시는 넣지 않는다. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_snapshot", columnDefinition = "JSON")
    private JsonNode requestSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_snapshot", columnDefinition = "JSON", nullable = false)
    private JsonNode beforeSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_snapshot", columnDefinition = "JSON", nullable = false)
    private JsonNode afterSnapshot;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "comparison", columnDefinition = "JSON")
    private JsonNode comparison;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "cashflow_impact", columnDefinition = "JSON")
    private JsonNode cashflowImpact;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private Simulation(Long userId, SimulationType type, String title, String summary, JsonNode requestSnapshot,
            JsonNode beforeSnapshot, JsonNode afterSnapshot, JsonNode comparison, JsonNode cashflowImpact,
            LocalDateTime now) {
        this.userId = Objects.requireNonNull(userId, "userId");
        this.type = Objects.requireNonNull(type, "type");
        this.title = requireLength("title", title, 255);
        this.summary = requireLength("summary", summary, 500);
        this.status = SimulationStatus.COMPLETED;
        this.requestSnapshot = requestSnapshot;
        this.beforeSnapshot = Objects.requireNonNull(beforeSnapshot, "beforeSnapshot");
        this.afterSnapshot = Objects.requireNonNull(afterSnapshot, "afterSnapshot");
        this.comparison = comparison;
        this.cashflowImpact = cashflowImpact;
        this.createdAt = Objects.requireNonNull(now, "now");
        this.completedAt = now;
    }

    /** 동기 실행이 끝난 결과를 한 번에 만든다. 진행 중 상태로 저장하는 경로는 없다. */
    public static Simulation completed(Long userId, SimulationType type, String title, String summary,
            JsonNode requestSnapshot, JsonNode beforeSnapshot, JsonNode afterSnapshot, JsonNode comparison,
            JsonNode cashflowImpact, LocalDateTime now) {
        return new Simulation(userId, type, title, summary, requestSnapshot, beforeSnapshot, afterSnapshot,
                comparison, cashflowImpact, now);
    }

    public void delete(LocalDateTime now) {
        if (deletedAt == null) {
            deletedAt = Objects.requireNonNull(now, "now");
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    private static String requireLength(String name, String value, int max) {
        if (value != null && value.length() > max) {
            throw new IllegalArgumentException(name + " 길이가 " + max + "자를 넘습니다.");
        }
        return value;
    }
}
