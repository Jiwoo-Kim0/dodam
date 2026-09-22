package com.ssafy.dodam.domain.diagnosis.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisStatus;
import java.time.LocalDateTime;

/**
 * 진단 응답.
 *
 * @param issueCount          진단이 저장한 문제 수 전체(GOAL 포함). 종합 상태·심각도 집계와 같은 모집단이다
 * @param discoveryIssueCount 발견(비효율) 화면이 세는 문제 수(675) = {@code issueCount − GOAL 도메인 문제 수}. 목표 위험은 목표 화면이
 *                            보여 주므로 발견 목록·개수에서 뺀다(607). FE는 목록 길이를 세지 않고 이 값을 쓴다
 */
public record DiagnosisResponse(Long diagnosisId, Long goalId, LocalDateTime createdAt,
        LocalDateTime completedAt, String ruleVersion, DiagnosisStatus overallStatus,
        int issueCount, int highSeverityCount, int discoveryIssueCount, JsonNode coverage, JsonNode assumptions) {

    public static final String DISCOVERY_EXCLUDED_DOMAIN = "GOAL";

    /** @param goalIssueCount 이 진단의 GOAL 도메인 문제 수 */
    public static DiagnosisResponse from(Diagnosis diagnosis, long goalIssueCount) {
        JsonNode snapshot = diagnosis.getInputSnapshot();
        return new DiagnosisResponse(diagnosis.getDiagnosisId(), diagnosis.getGoalId(),
                diagnosis.getCreatedAt(), diagnosis.getCompletedAt(), diagnosis.getRuleVersion(),
                diagnosis.getOverallStatus(), diagnosis.getIssueCount(), diagnosis.getHighSeverityCount(),
                (int) Math.max(0L, diagnosis.getIssueCount() - goalIssueCount),
                snapshot == null ? null : snapshot.get("coverage"),
                snapshot == null ? null : snapshot.get("assumptions"));
    }
}
