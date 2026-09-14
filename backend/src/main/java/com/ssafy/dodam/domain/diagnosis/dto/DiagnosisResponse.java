package com.ssafy.dodam.domain.diagnosis.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisStatus;
import java.time.LocalDateTime;

public record DiagnosisResponse(Long diagnosisId, Long goalId, LocalDateTime createdAt,
        LocalDateTime completedAt, String ruleVersion, DiagnosisStatus overallStatus,
        int issueCount, int highSeverityCount, JsonNode coverage, JsonNode assumptions) {
    public static DiagnosisResponse from(Diagnosis diagnosis) {
        JsonNode snapshot = diagnosis.getInputSnapshot();
        return new DiagnosisResponse(diagnosis.getDiagnosisId(), diagnosis.getGoalId(),
                diagnosis.getCreatedAt(), diagnosis.getCompletedAt(), diagnosis.getRuleVersion(),
                diagnosis.getOverallStatus(), diagnosis.getIssueCount(), diagnosis.getHighSeverityCount(),
                snapshot == null ? null : snapshot.get("coverage"),
                snapshot == null ? null : snapshot.get("assumptions"));
    }
}
