package com.ssafy.dodam.domain.diagnosis.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.dodam.domain.diagnosis.entity.DetectionSource;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisIssue;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import java.time.LocalDateTime;

public record DiagnosisIssueResponse(Long issueId, Long diagnosisId, String domain, String type,
        IssueSeverity severity, String title, String description, JsonNode currentValue,
        JsonNode benchmarkValue, JsonNode evidence, DetectionSource detectionSource,
        String modelVersion, JsonNode clusterContext, LocalDateTime detectedAt) {
    public static DiagnosisIssueResponse from(DiagnosisIssue issue) {
        return new DiagnosisIssueResponse(issue.getIssueId(), issue.getDiagnosisId(), issue.getDomain(),
                issue.getType(), issue.getSeverity(), issue.getTitle(), issue.getDescription(),
                issue.getCurrentValue(), issue.getBenchmarkValue(), issue.getEvidence(),
                issue.getDetectionSource(), issue.getModelVersion(), issue.getClusterContext(), issue.getDetectedAt());
    }
}
