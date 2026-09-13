package com.ssafy.dodam.domain.recommendation.entity;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 추천 근거 1건.
 *
 * <p>`code`는 ERD상 NULL 허용이지만 서비스 생성 경로에서는 필수다. MySQL은 NULL끼리 UNIQUE 충돌이
 * 없어 `uk_recommendation_reason_code`가 NULL 행의 중복을 막지 못한다. 한 코드에 근거가 여럿이면
 * 행을 늘리지 않고 `evidence`를 배열로 둔다.
 *
 * <p>`userId`가 없다. 소유권은 상위 `recommendation`을 조인해서 건다
 * ({@code RecommendationReasonRepository} 참고). `evidence`가 `String`이 아니라 {@link JsonNode}인
 * 이유는 {@code Diagnosis#getInputSnapshot()} 주석 참고.
 */
@Getter
@Entity
@Table(name = "recommendation_reason", uniqueConstraints =
        @UniqueConstraint(name = "uk_recommendation_reason_code", columnNames = {"recommendation_id", "code"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RecommendationReason {

    public static final int CODE_MAX_LENGTH = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reason_id")
    private Long reasonId;

    @Column(name = "recommendation_id", nullable = false)
    private Long recommendationId;

    @Column(name = "code", length = CODE_MAX_LENGTH)
    private String code;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "evidence", columnDefinition = "JSON")
    private JsonNode evidence;

    private RecommendationReason(Long recommendationId, String code, String description, JsonNode evidence) {
        this.recommendationId = recommendationId;
        this.code = code;
        this.description = description;
        this.evidence = evidence;
    }

    /**
     * @param code 추천 안에서 유일한 안정 코드. 비어 있으면 거부한다
     * @param evidence 근거 수치 JSON. 없으면 null
     */
    public static RecommendationReason of(Long recommendationId, String code, String description, JsonNode evidence) {
        if (recommendationId == null) {
            throw new IllegalArgumentException("추천을 지정해야 합니다.");
        }
        if (code == null || code.isBlank() || code.length() > CODE_MAX_LENGTH) {
            throw new IllegalArgumentException("근거 코드는 1~" + CODE_MAX_LENGTH + "자여야 합니다.");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("근거 설명은 비어 있을 수 없습니다.");
        }
        return new RecommendationReason(recommendationId, code, description, evidence);
    }
}
