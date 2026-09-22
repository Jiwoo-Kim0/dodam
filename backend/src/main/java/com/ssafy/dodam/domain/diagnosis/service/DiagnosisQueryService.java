package com.ssafy.dodam.domain.diagnosis.service;

import com.ssafy.dodam.domain.diagnosis.dto.DiagnosisResponse;
import com.ssafy.dodam.domain.diagnosis.dto.DiagnosisIssueResponse;
import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisRepository;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisIssueRepository;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.response.PageResponse;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DiagnosisQueryService {
    private final DiagnosisRepository diagnoses;
    private final DiagnosisIssueRepository issues;

    public DiagnosisQueryService(DiagnosisRepository diagnoses, DiagnosisIssueRepository issues) {
        this.diagnoses = diagnoses;
        this.issues = issues;
    }

    public PageResponse<DiagnosisResponse> history(Long userId, int page, int size) {
        return PageResponse.from(diagnoses.findByUserIdAndCompletedAtIsNotNullOrderByCreatedAtDescDiagnosisIdDesc(
                userId, PageRequest.of(page, size)), this::toResponse);
    }

    public DiagnosisResponse latest(Long userId, Long goalId) {
        return toResponse((goalId == null
                ? diagnoses.findFirstByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDescDiagnosisIdDesc(userId)
                : diagnoses.findFirstByUserIdAndGoalIdAndCompletedAtIsNotNullOrderByCompletedAtDescDiagnosisIdDesc(userId, goalId))
                .orElseThrow(DiagnosisQueryService::notFound));
    }

    public DiagnosisResponse detail(Long userId, Long diagnosisId) {
        return toResponse(owned(userId, diagnosisId));
    }

    /** 675: 발견 화면용 문제 수는 GOAL 도메인을 뺀 값이다. 소유권은 호출자가 확인한 진단만 넘어온다. */
    private DiagnosisResponse toResponse(Diagnosis diagnosis) {
        return DiagnosisResponse.from(diagnosis,
                issues.countByDiagnosisIdAndDomain(diagnosis.getDiagnosisId(), DiagnosisResponse.DISCOVERY_EXCLUDED_DOMAIN));
    }

    public Summary summary(Long userId, Long diagnosisId) {
        DiagnosisResponse diagnosis = detail(userId, diagnosisId);
        Map<String, Long> domains = new TreeMap<>();
        Map<String, Long> severities = new TreeMap<>();
        issues.findAllOwned(userId, diagnosisId).forEach(issue -> {
            domains.merge(issue.getDomain(), 1L, Long::sum);
            severities.merge(issue.getSeverity().name(), 1L, Long::sum);
        });
        return new Summary(diagnosis, domains, severities);
    }

    public PageResponse<DiagnosisIssueResponse> issues(Long userId, Long diagnosisId, String domain,
            String type, IssueSeverity severity, int page, int size) {
        owned(userId, diagnosisId);
        return PageResponse.from(issues.findOwned(userId, diagnosisId, domain, type, severity,
                PageRequest.of(page, size)), DiagnosisIssueResponse::from);
    }

    public DiagnosisIssueResponse issue(Long userId, Long diagnosisId, Long issueId) {
        owned(userId, diagnosisId);
        return DiagnosisIssueResponse.from(issues.findOwnedById(userId, diagnosisId, issueId)
                .orElseThrow(DiagnosisQueryService::notFound));
    }

    private Diagnosis owned(Long userId, Long diagnosisId) {
        return diagnoses.findByDiagnosisIdAndUserId(diagnosisId, userId)
                .filter(Diagnosis::isCompleted).orElseThrow(DiagnosisQueryService::notFound);
    }

    private static ResourceNotFoundException notFound() {
        return new ResourceNotFoundException("완료된 진단 또는 문제를 찾을 수 없습니다.");
    }

    public record Summary(DiagnosisResponse diagnosis, Map<String, Long> byDomain, Map<String, Long> bySeverity) { }
}
