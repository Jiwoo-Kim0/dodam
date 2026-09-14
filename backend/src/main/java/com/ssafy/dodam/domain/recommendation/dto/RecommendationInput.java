package com.ssafy.dodam.domain.recommendation.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.dodam.domain.diagnosis.entity.DetectionSource;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 후보 생성기에 넘기는 완료 진단의 읽기 전용 입력.
 *
 * <p>HTTP 응답 DTO(544·545)가 아니라 진단 엔티티에서 직접 만든다. 응답 DTO를 재사용하면 추천 규칙이
 * 진단 API의 표시 형식에 묶인다. 이 모델은 서비스 내부용이며 공개 API로 그대로 노출하지 않는다.
 *
 * @param ruleVersion 진단이 저장한 규칙 버전. 상수와 비교하지 않고 문자열로 보존만 한다(옛 진단도 입력이 된다)
 * @param goalId 생성 요청에서 확정된 목표. 진단의 goalId를 물려받거나 null
 * @param inputSnapshot 진단 시점 입력 스냅샷. 543 초기 데이터처럼 없으면 null이며 생성기는 없음을 전제해야 한다
 * @param issues 진단 문제 목록. issueId 오름차순
 */
public record RecommendationInput(
        Long diagnosisId,
        Long userId,
        String ruleVersion,
        Long goalId,
        LocalDateTime completedAt,
        JsonNode inputSnapshot,
        List<Issue> issues
) {
    public RecommendationInput {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }

    /**
     * 진단 문제 1건. `clusterContext`·`modelVersion`은 군집 신호가 없으면 null이다. 군집은 보조 신호이며
     * 실제 대환 가능성 등을 확정하지 않는다(기획안).
     */
    public record Issue(
            Long issueId,
            String domain,
            String type,
            IssueSeverity severity,
            String title,
            String description,
            JsonNode currentValue,
            JsonNode benchmarkValue,
            JsonNode evidence,
            DetectionSource detectionSource,
            String modelVersion,
            JsonNode clusterContext
    ) { }
}
