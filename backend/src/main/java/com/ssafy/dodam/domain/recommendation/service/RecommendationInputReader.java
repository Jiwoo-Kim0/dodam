package com.ssafy.dodam.domain.recommendation.service;

import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisIssue;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisIssueRepository;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisRepository;
import com.ssafy.dodam.domain.goal.repository.GoalRepository;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationInput;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 완료 진단을 추천 입력으로 읽는 경계.
 *
 * <p>진단 도메인의 HTTP DTO·실행 서비스는 쓰지 않고 Repository의 소유권 조회만 사용한다. 추천 규칙이
 * 진단 API의 표시 형식이나 실행 흐름에 묶이지 않게 하기 위해서다.
 *
 * <p><b>goalId 결정</b>: 요청에 goalId가 없으면 진단의 goalId를 물려받는다(진단도 없으면 null). 요청
 * goalId가 진단 goalId와 다르면 거부한다. 다른 목표로 진단한 근거를 이 목표의 추천에 재사용하면 목표기여
 * 계산이 틀어진다. 허용 여부를 넓힐지는 548에서 확정한다.
 *
 * <p>미완료 진단은 {@link ErrorCode#DIAGNOSIS_NOT_COMPLETED}, goalId 불일치는 {@link ErrorCode#RECOMMENDATION_GOAL_MISMATCH}로, 타인·없는 진단과 타인·없는·삭제된
 * 목표는 {@link ResourceNotFoundException}으로 거부한다. 전용 ErrorCode는 HTTP 계약을 만드는 548에서 추가한다.
 */
@Service
public class RecommendationInputReader {

    private final DiagnosisRepository diagnoses;
    private final DiagnosisIssueRepository issues;
    private final GoalRepository goals;

    public RecommendationInputReader(DiagnosisRepository diagnoses, DiagnosisIssueRepository issues,
            GoalRepository goals) {
        this.diagnoses = diagnoses;
        this.issues = issues;
        this.goals = goals;
    }

    /**
     * @param requestedGoalId 생성 요청의 goalId. 생략이면 null
     */
    @Transactional(readOnly = true)
    public RecommendationInput read(Long userId, Long diagnosisId, Long requestedGoalId) {
        Diagnosis diagnosis = diagnoses.findByDiagnosisIdAndUserId(diagnosisId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("진단을 찾을 수 없습니다."));
        if (!diagnosis.isCompleted()) {
            throw new BusinessException(ErrorCode.DIAGNOSIS_NOT_COMPLETED);
        }
        Long goalId = resolveGoalId(userId, diagnosis.getGoalId(), requestedGoalId);

        List<RecommendationInput.Issue> issueInputs = issues.findAllOwned(userId, diagnosisId).stream()
                .map(RecommendationInputReader::toIssue)
                .toList();
        return new RecommendationInput(
                diagnosis.getDiagnosisId(),
                userId,
                diagnosis.getRuleVersion(),
                goalId,
                diagnosis.getCompletedAt(),
                diagnosis.getInputSnapshot(),
                issueInputs);
    }

    private Long resolveGoalId(Long userId, Long diagnosisGoalId, Long requestedGoalId) {
        Long goalId = requestedGoalId == null ? diagnosisGoalId : requestedGoalId;
        if (goalId == null) {
            return null;
        }
        // 존재·소유·미삭제를 먼저 확인해야 타인 목표에 대해 "불일치"라는 정보를 흘리지 않는다.
        goals.findByIdAndUserIdAndDeletedAtIsNull(goalId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("목표를 찾을 수 없습니다."));
        if (requestedGoalId != null && !Objects.equals(requestedGoalId, diagnosisGoalId)) {
            throw new BusinessException(ErrorCode.RECOMMENDATION_GOAL_MISMATCH);
        }
        return goalId;
    }

    private static RecommendationInput.Issue toIssue(DiagnosisIssue issue) {
        return new RecommendationInput.Issue(
                issue.getIssueId(),
                issue.getDomain(),
                issue.getType(),
                issue.getSeverity(),
                issue.getTitle(),
                issue.getDescription(),
                issue.getCurrentValue(),
                issue.getBenchmarkValue(),
                issue.getEvidence(),
                issue.getDetectionSource(),
                issue.getModelVersion(),
                issue.getClusterContext());
    }
}
