package com.ssafy.dodam.domain.diagnosis.service;

import com.ssafy.dodam.domain.diagnosis.dto.DiagnosisChangesResponse;
import com.ssafy.dodam.domain.diagnosis.dto.DiagnosisChangesResponse.GoalChange;
import com.ssafy.dodam.domain.diagnosis.dto.DiagnosisChangesResponse.IssueChange;
import com.ssafy.dodam.domain.diagnosis.dto.DiagnosisChangesResponse.IssueChanges;
import com.ssafy.dodam.domain.diagnosis.dto.DiagnosisChangesResponse.Snapshot;
import com.ssafy.dodam.domain.diagnosis.dto.DiagnosisChangesResponse.StatusChange;
import com.ssafy.dodam.domain.diagnosis.dto.DiagnosisGoalImpactResponse.GoalImpact;
import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisIssue;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisStatus;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisIssueRepository;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisRepository;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiFunction;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 이전 진단 대비 변화. 두 완료본의 저장된 문제와 스냅샷만 비교하며 현재 데이터를 읽지 않는다.
 *
 * <p>문제 동일성은 issueId가 아니라 근거 JSON으로 만든 키로 판단한다. GOAL은 목표 ID, CASHFLOW는 위험 날짜다.
 * 현금흐름의 {@code targetId}(riskId)와 {@code forecastId}는 예측을 다시 돌릴 때마다 새로 발급되므로 키에 넣으면
 * 같은 날의 같은 위험이 매번 새 문제로 보인다.
 */
@Service
@Transactional(readOnly = true)
public class DiagnosisChangeService {
    private final DiagnosisRepository diagnoses;
    private final DiagnosisIssueRepository issues;

    public DiagnosisChangeService(DiagnosisRepository diagnoses, DiagnosisIssueRepository issues) {
        this.diagnoses = diagnoses;
        this.issues = issues;
    }

    public DiagnosisChangesResponse changes(Long userId, Long diagnosisId) {
        Diagnosis current = diagnoses.findByDiagnosisIdAndUserId(diagnosisId, userId)
                .filter(Diagnosis::isCompleted)
                .orElseThrow(() -> new ResourceNotFoundException("완료된 진단을 찾을 수 없습니다."));
        Diagnosis previous = diagnoses.findPreviousCompleted(userId, current.getGoalId(), current.getCompletedAt(),
                current.getDiagnosisId(), PageRequest.of(0, 1)).stream().findFirst().orElse(null);
        if (previous == null) {
            // 첫 진단은 정상 상태다. 비교 대상이 없을 뿐이므로 404가 아니라 빈 비교 결과를 준다.
            return new DiagnosisChangesResponse(diagnosisId, null, false, false, snapshot(current), null,
                    new StatusChange(null, current.getOverallStatus(), "UNKNOWN"),
                    new IssueChanges(List.of(), List.of(), List.of(), 0), List.of());
        }
        List<DiagnosisIssue> currentIssues = issues.findAllOwned(userId, current.getDiagnosisId());
        List<DiagnosisIssue> previousIssues = issues.findAllOwned(userId, previous.getDiagnosisId());
        return new DiagnosisChangesResponse(diagnosisId, previous.getDiagnosisId(), true,
                !Objects.equals(current.getRuleVersion(), previous.getRuleVersion()),
                snapshot(current), snapshot(previous), statusChange(previous, current),
                issueChanges(previousIssues, currentIssues), goalChanges(previous, current, previousIssues, currentIssues));
    }

    private static Snapshot snapshot(Diagnosis d) {
        return new Snapshot(d.getDiagnosisId(), d.getCompletedAt(), d.getRuleVersion(), d.getOverallStatus(),
                d.getIssueCount(), d.getHighSeverityCount());
    }

    // 부분 평가(overallStatus=null)는 건강도 위험도 아니므로 방향을 판단하지 않는다. enum 선언 순서가 HEALTHY < CAUTION < RISK다.
    private static StatusChange statusChange(Diagnosis previous, Diagnosis current) {
        DiagnosisStatus from = previous.getOverallStatus();
        DiagnosisStatus to = current.getOverallStatus();
        String direction = from == null || to == null ? "UNKNOWN"
                : from == to ? "UNCHANGED" : to.ordinal() < from.ordinal() ? "IMPROVED" : "WORSENED";
        return new StatusChange(from, to, direction);
    }

    private static IssueChanges issueChanges(List<DiagnosisIssue> previousIssues, List<DiagnosisIssue> currentIssues) {
        Map<String, DiagnosisIssue> before = byKey(previousIssues);
        Map<String, DiagnosisIssue> after = byKey(currentIssues);
        List<IssueChange> added = new ArrayList<>();
        List<IssueChange> resolved = new ArrayList<>();
        List<IssueChange> changed = new ArrayList<>();
        int unchanged = 0;
        for (var entry : after.entrySet()) {
            DiagnosisIssue now = entry.getValue();
            DiagnosisIssue old = before.get(entry.getKey());
            if (old == null) {
                added.add(new IssueChange(entry.getKey(), now.getIssueId(), null, now.getDomain(), now.getType(),
                        now.getSeverity(), null));
            } else if (old.getSeverity() != now.getSeverity()) {
                changed.add(new IssueChange(entry.getKey(), now.getIssueId(), old.getIssueId(), now.getDomain(),
                        now.getType(), now.getSeverity(), old.getSeverity()));
            } else {
                unchanged++;
            }
        }
        for (var entry : before.entrySet()) {
            if (!after.containsKey(entry.getKey())) {
                DiagnosisIssue old = entry.getValue();
                resolved.add(new IssueChange(entry.getKey(), null, old.getIssueId(), old.getDomain(), old.getType(),
                        old.getSeverity(), null));
            }
        }
        return new IssueChanges(added, resolved, changed, unchanged);
    }

    private static Map<String, DiagnosisIssue> byKey(List<DiagnosisIssue> list) {
        Map<String, DiagnosisIssue> map = new LinkedHashMap<>();
        for (DiagnosisIssue issue : list) {
            // 같은 키가 둘이면(예상하지 않은 형식) issueId를 붙여 조용히 덮어쓰지 않는다.
            String key = issueKey(issue);
            map.put(map.containsKey(key) ? key + ":" + issue.getIssueId() : key, issue);
        }
        return map;
    }

    static String issueKey(DiagnosisIssue issue) {
        String prefix = issue.getDomain() + ":" + issue.getType() + ":";
        String riskDate = DiagnosisGoalImpactService.textOf(issue.getEvidence(), "riskDate");
        if (riskDate != null) {
            return prefix + riskDate;
        }
        Long targetId = DiagnosisGoalImpactService.longOf(issue.getEvidence(), "targetId");
        // 키를 만들 수 없는 행(과거 형식·군집 fixture)은 issueId로 두어 항상 added/resolved로 드러낸다.
        return prefix + (targetId != null ? targetId : "issue-" + issue.getIssueId());
    }

    private static List<GoalChange> goalChanges(Diagnosis previous, Diagnosis current,
            List<DiagnosisIssue> previousIssues, List<DiagnosisIssue> currentIssues) {
        Map<Long, GoalImpact> before = impacts(previous, previousIssues);
        Map<Long, GoalImpact> after = impacts(current, currentIssues);
        List<GoalChange> changes = new ArrayList<>();
        // 양쪽 스냅샷에 모두 있는 목표만 비교한다. 한쪽에만 있는 목표는 issueChanges로 드러난다.
        for (var entry : after.entrySet()) {
            GoalImpact old = before.get(entry.getKey());
            GoalImpact now = entry.getValue();
            if (old == null) {
                continue;
            }
            amount(changes, now, "remainingAmount", old.remainingAmount(), now.remainingAmount());
            amount(changes, now, "requiredMonthlySaving", old.requiredMonthlySaving(), now.requiredMonthlySaving());
            amount(changes, now, "averageMonthlySaving", old.averageMonthlySaving(), now.averageMonthlySaving());
            amount(changes, now, "monthlyShortfall", old.monthlyShortfall(), now.monthlyShortfall());
            date(changes, now, "expectedAchievementDate", old.expectedAchievementDate(), now.expectedAchievementDate());
            amount(changes, now, "spent", old.spent(), now.spent());
            amount(changes, now, "projectedMonthEnd", old.projectedMonthEnd(), now.projectedMonthEnd());
            amount(changes, now, "overBudgetAmount", old.overBudgetAmount(), now.overBudgetAmount());
            if (!Objects.equals(old.riskLevel(), now.riskLevel())) {
                changes.add(new GoalChange(now.goalId(), now.type(), "riskLevel", old.riskLevel(), now.riskLevel(), null));
            }
        }
        return changes;
    }

    private static Map<Long, GoalImpact> impacts(Diagnosis diagnosis, List<DiagnosisIssue> allIssues) {
        List<DiagnosisIssue> goalIssues = allIssues.stream().filter(i -> "GOAL".equals(i.getDomain())).toList();
        Map<Long, GoalImpact> map = new LinkedHashMap<>();
        for (var goal : DiagnosisGoalImpactService.snapshotGoals(diagnosis.getInputSnapshot())) {
            GoalImpact impact = DiagnosisGoalImpactService.toImpact(goal, goalIssues);
            if (impact.goalId() != null) {
                map.put(impact.goalId(), impact);
            }
        }
        return map;
    }

    private static void amount(List<GoalChange> out, GoalImpact goal, String metric, Long old, Long now) {
        change(out, goal, metric, old, now, (a, b) -> b - a);
    }

    private static void date(List<GoalChange> out, GoalImpact goal, String metric, LocalDate old, LocalDate now) {
        change(out, goal, metric, old, now, (a, b) -> ChronoUnit.DAYS.between(a, b));
    }

    // 값이 같으면 기록하지 않는다. 한쪽만 null이면(예: 저축속도 0 → 예상일 없음) 변화는 남기되 delta는 정의하지 않는다.
    private static <T> void change(List<GoalChange> out, GoalImpact goal, String metric, T old, T now,
            BiFunction<T, T, Long> delta) {
        if (Objects.equals(old, now)) {
            return;
        }
        out.add(new GoalChange(goal.goalId(), goal.type(), metric, old, now,
                old == null || now == null ? null : delta.apply(old, now)));
    }
}
