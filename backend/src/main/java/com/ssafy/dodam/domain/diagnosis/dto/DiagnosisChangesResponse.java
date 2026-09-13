package com.ssafy.dodam.domain.diagnosis.dto;

import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisStatus;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 이전 진단 대비 변화(GET /diagnoses/{diagnosisId}/changes). 기획안 6-3 「이전 진단 대비 변화」.
 *
 * <p>이전 진단은 같은 사용자·같은 {@code goalId}(null 포함 완전 일치)의 직전 완료본이다. 평가 대상 목표
 * 집합이 다른 진단끼리는 문제 수 증감이 의미를 갖지 않아 비교하지 않는다.
 *
 * @param previousDiagnosisId 이전 진단. 첫 진단이면 null이며 {@code comparable=false}
 * @param ruleVersionChanged  두 진단의 규칙 버전이 다르면 true. 규칙 추가로 생긴 added를 악화로 읽지 않도록 표시한다
 */
public record DiagnosisChangesResponse(Long diagnosisId, Long previousDiagnosisId, boolean comparable,
        boolean ruleVersionChanged, Snapshot current, Snapshot previous, StatusChange statusChange,
        IssueChanges issueChanges, List<GoalChange> goalChanges) {

    public record Snapshot(Long diagnosisId, LocalDateTime completedAt, String ruleVersion,
            DiagnosisStatus overallStatus, int issueCount, int highSeverityCount) { }

    /** direction: IMPROVED / WORSENED / UNCHANGED, 어느 한쪽이 null(부분 평가)이거나 비교 불가면 UNKNOWN */
    public record StatusChange(DiagnosisStatus from, DiagnosisStatus to, String direction) { }

    /**
     * 문제 동일성은 issueId가 아니라 근거 JSON으로 만든 {@code key}로 판단한다. issueId는 진단마다 새로
     * 발급되므로 같은 문제를 이어 볼 수 없다.
     */
    public record IssueChanges(List<IssueChange> added, List<IssueChange> resolved, List<IssueChange> changed,
            int unchangedCount) { }

    public record IssueChange(String key, Long issueId, Long previousIssueId, String domain, String type,
            IssueSeverity severity, IssueSeverity previousSeverity) { }

    /** 금액은 원(long), 날짜는 일 수(long) 차이. current − previous */
    public record GoalChange(Long goalId, String type, String metric, Object previous, Object current, Long delta) { }
}
