package com.ssafy.dodam.domain.diagnosis.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 금융진단 실행 1건.
 *
 * <p>하위행 `diagnosis_issue`는 연관관계로 매핑하지 않고 {@code diagnosisId} 값으로만 잇는다. 조회는
 * 언제나 `diagnosisId` 기준 배치이고, 명세 14의 문제 목록은 필터·페이지가 붙는 별도 API(545)라
 * 헤더를 읽을 때마다 컬렉션을 끌고 올 이유가 없다. cashflow(538)가 택한 구조와 같다.
 *
 * <p>`goalId`도 `@ManyToOne`이 아니라 값으로 둔다. `goal`은 BE-A 소관 도메인이라 엔티티가 아직 없고,
 * 여기서 매핑을 만들면 diagnosis가 Goal 엔티티의 형태 변경에 묶인다.
 *
 * <p><b>상태 표현</b>: 별도 `status` 컬럼이 없다. ERD가 진단에는 실행 상태 컬럼을 두지 않았고
 * `completed_at`의 NULL 여부로 완료를 판별한다(cashflow의 `ForecastStatus`와 다른 점).
 * 집계값 `issueCount`·`highSeverityCount`·`overallStatus`·`ruleVersion`은 완료 시점에만 채워진다.
 */
@Getter
@Entity
@Table(name = "diagnosis")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Diagnosis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "diagnosis_id")
    private Long diagnosisId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 목표 연계 진단이 아니면 NULL이다. 명세의 `goalId`는 선택 입력이다.
    @Column(name = "goal_id")
    private Long goalId;

    @Column(name = "issue_count", nullable = false)
    private int issueCount;

    @Column(name = "high_severity_count", nullable = false)
    private int highSeverityCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "overall_status", length = 30)
    private DiagnosisStatus overallStatus;

    @Column(name = "rule_version", length = 50)
    private String ruleVersion;

    /**
     * 진단이 사용한 입력값의 스냅샷.
     *
     * <p>스키마를 엔티티로 고정하지 않는다. 저장 시점의 원본을 그대로 남기는 것이 목적이라,
     * 항목이 바뀔 때마다 과거 행을 읽지 못하게 되면 안 된다. 형식과 항목은 진단 실행을 구현하는
     * S15P21D105-544에서 정한다.
     *
     * <p><b>타입이 `String`이 아니라 {@link JsonNode}인 이유</b>: `@JdbcTypeCode(SqlTypes.JSON)`가
     * 붙은 `String`은 Hibernate가 저장할 때 JSON 문자열 리터럴로 한 번 더 감싼다. `{"a":1}`이 객체가
     * 아니라 `"{\"a\":1}"`로 들어가 `JSON_EXTRACT`가 통하지 않는다. `String`은 JSON이 아닌 값도
     * 받아 MySQL이 런타임에 거부한다. 두 문제 모두 H2 테스트가 잡지 못한다(저장·조회가 대칭이다).
     * `FinancialProduct.benefits`가 같은 이유로 `JsonNode`를 쓴다.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_snapshot", columnDefinition = "JSON")
    private JsonNode inputSnapshot;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    private Diagnosis(Long userId, Long goalId, LocalDateTime now) {
        this.userId = userId;
        this.goalId = goalId;
        this.issueCount = 0;
        this.highSeverityCount = 0;
        this.createdAt = now;
    }

    /** 진단을 접수한다. 계산 전 상태이므로 집계값과 `completedAt`은 비어 있다. */
    public static Diagnosis start(Long userId, Long goalId, LocalDateTime now) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자를 지정해야 합니다.");
        }
        return new Diagnosis(userId, goalId, now);
    }

    /**
     * 계산 결과 집계값을 반영하고 완료 처리한다.
     *
     * <p>`highSeverityCount`가 `issueCount`를 넘을 수 없다는 것은 `chk_diagnosis_high_severity_count`가
     * 최종 방어선이지만, DB까지 가서 실패하면 어느 계산이 틀렸는지 알기 어려워 여기서 먼저 막는다.
     */
    public void complete(
            int issueCount,
            int highSeverityCount,
            DiagnosisStatus overallStatus,
            String ruleVersion,
            JsonNode inputSnapshot,
            LocalDateTime now
    ) {
        requireIncomplete();
        if (issueCount < 0 || highSeverityCount < 0) {
            throw new IllegalArgumentException("문제 개수는 음수일 수 없습니다.");
        }
        if (highSeverityCount > issueCount) {
            throw new IllegalArgumentException("높은 심각도 개수가 전체 문제 개수를 넘을 수 없습니다.");
        }
        this.issueCount = issueCount;
        this.highSeverityCount = highSeverityCount;
        this.overallStatus = overallStatus;
        this.ruleVersion = ruleVersion;
        this.inputSnapshot = inputSnapshot;
        this.completedAt = now;
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    // 완료된 진단을 덮어써 조회 결과가 조용히 바뀌는 것을 막는다. 재진단은 새 행으로 남긴다.
    private void requireIncomplete() {
        if (isCompleted()) {
            throw new IllegalStateException("이미 완료된 진단입니다.");
        }
    }
}
