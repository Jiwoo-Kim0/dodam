package com.ssafy.dodam.domain.diagnosis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.dodam.domain.diagnosis.dto.DiagnosisGoalImpactResponse;
import com.ssafy.dodam.domain.diagnosis.dto.DiagnosisGoalImpactResponse.GoalImpact;
import com.ssafy.dodam.domain.diagnosis.dto.DiagnosisGoalImpactResponse.RelatedIssue;
import com.ssafy.dodam.domain.diagnosis.dto.DiagnosisGoalImpactResponse.Unavailable;
import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisIssue;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisIssueRepository;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisRepository;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 목표 영향 조회. 저장된 {@code input_snapshot.goals[]}와 GOAL 문제만 읽고 현재 목표·잔액으로 재계산하지 않는다.
 *
 * <p>스냅샷 형식은 규칙 버전에 따라 다르다. v3 이전 완료본에는 {@code forecast}가 없어 예상 달성일·남은 금액을
 * 만들 수 없으므로 해당 필드를 null로 두고 {@code coverage}에 사유를 남긴다. 없는 값을 계산식으로 메우면
 * "재계산하지 않는다"는 544 계약이 깨진다.
 */
@Service
@Transactional(readOnly = true)
public class DiagnosisGoalImpactService {
    static final String SNAPSHOT_WITHOUT_FORECAST = "SNAPSHOT_WITHOUT_FORECAST";
    private final DiagnosisRepository diagnoses;
    private final DiagnosisIssueRepository issues;

    public DiagnosisGoalImpactService(DiagnosisRepository diagnoses, DiagnosisIssueRepository issues) {
        this.diagnoses = diagnoses;
        this.issues = issues;
    }

    public DiagnosisGoalImpactResponse goalImpact(Long userId, Long diagnosisId) {
        Diagnosis diagnosis = diagnoses.findByDiagnosisIdAndUserId(diagnosisId, userId)
                .filter(Diagnosis::isCompleted)
                .orElseThrow(() -> new ResourceNotFoundException("완료된 진단을 찾을 수 없습니다."));
        List<DiagnosisIssue> goalIssues = issues.findAllOwned(userId, diagnosisId).stream()
                .filter(issue -> "GOAL".equals(issue.getDomain())).toList();
        JsonNode snapshot = diagnosis.getInputSnapshot();
        List<GoalImpact> goals = new ArrayList<>();
        for (JsonNode goal : snapshotGoals(snapshot)) {
            goals.add(toImpact(goal, goalIssues));
        }
        // 기획안 6-2의 "금융비용으로 인한 목표 달성 지연"은 대출 이자·상환 규칙이 확정되지 않아 근거가 없다.
        // 임의 수치로 채우지 않고 미제공 사유를 드러낸다.
        return new DiagnosisGoalImpactResponse(diagnosis.getDiagnosisId(), diagnosis.getGoalId(), diagnosis.getCreatedAt(),
                diagnosis.getRuleVersion(), goals,
                List.of(new Unavailable("FINANCING_COST_DELAY", "RULE_POLICY_NOT_DEFINED")),
                snapshot == null ? null : snapshot.get("assumptions"));
    }

    static List<JsonNode> snapshotGoals(JsonNode snapshot) {
        List<JsonNode> goals = new ArrayList<>();
        if (snapshot != null && snapshot.path("goals").isArray()) {
            snapshot.get("goals").forEach(goals::add);
        }
        return goals;
    }

    static GoalImpact toImpact(JsonNode goal, List<DiagnosisIssue> goalIssues) {
        Long goalId = longOf(goal, "goalId");
        String type = textOf(goal, "type");
        JsonNode metrics = goal.path("metrics");
        JsonNode goalSnapshot = goal.path("goalSnapshot");
        JsonNode forecast = goal.path("forecast");
        List<String> factors = new ArrayList<>();
        goal.path("factors").forEach(f -> factors.add(f.asText()));
        List<String> coverage = new ArrayList<>();
        Long required = longOf(metrics, "requiredMonthlySaving");
        Long average = longOf(metrics, "averageMonthlySaving");
        Long budget = longOf(metrics, "monthlyBudget");
        Long spent = longOf(metrics, "spent");
        Long projected = longOf(metrics, "projectedMonthEnd");
        boolean saving = "SAVING".equals(type);
        if (saving && forecast.isMissingNode()) {
            coverage.add(SNAPSHOT_WITHOUT_FORECAST);
        }
        List<RelatedIssue> related = goalIssues.stream()
                .filter(issue -> Objects.equals(longOf(issue.getEvidence(), "targetId"), goalId))
                .map(issue -> new RelatedIssue(issue.getIssueId(), issue.getDomain(), issue.getType(), issue.getSeverity()))
                .toList();
        return new GoalImpact(goalId, textOf(goalSnapshot, "name"), type, textOf(goal, "goalStatus"),
                textOf(goal, "riskLevel"), factors,
                saving ? longOf(forecast, "targetAmount", goal.path("savingGoalSnapshot")) : null,
                saving ? longOf(forecast, "currentAmount", goalSnapshot) : null,
                longOf(forecast, "remainingAmount"),
                required, average, nonNegativeDifference(required, average),
                saving ? dateOf(forecast, "targetDate", goal.path("savingGoalSnapshot")) : null,
                dateOf(forecast, "expectedAchievementDate", null), longOf(forecast, "daysDifference"),
                forecast.hasNonNull("onSchedule") ? forecast.get("onSchedule").asBoolean() : null,
                budget, spent, projected,
                budget == null ? null : nonNegativeDifference(max(spent, projected), budget),
                related, coverage);
    }

    private static Long max(Long a, Long b) {
        if (a == null) {
            return b;
        }
        return b == null ? a : Math.max(a, b);
    }

    private static Long nonNegativeDifference(Long minuend, Long subtrahend) {
        if (minuend == null || subtrahend == null) {
            return null;
        }
        return Math.max(0L, minuend - subtrahend);
    }

    /** 첫 노드에 없으면 대체 노드에서 읽는다. v2 스냅샷은 forecast가 없어 목표 원본 스냅샷에서 가져온다. */
    private static Long longOf(JsonNode primary, String field, JsonNode fallback) {
        Long value = longOf(primary, field);
        return value != null ? value : longOf(fallback, field);
    }

    private static LocalDate dateOf(JsonNode primary, String field, JsonNode fallback) {
        JsonNode node = primary != null && primary.hasNonNull(field) ? primary.get(field)
                : fallback != null && fallback.hasNonNull(field) ? fallback.get(field) : null;
        return node == null ? null : LocalDate.parse(node.asText());
    }

    static Long longOf(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) && node.get(field).canConvertToLong() ? node.get(field).asLong() : null;
    }

    static String textOf(JsonNode node, String field) {
        return node != null && node.hasNonNull(field) ? node.get(field).asText() : null;
    }
}
