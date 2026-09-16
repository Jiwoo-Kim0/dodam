package com.ssafy.dodam.domain.recommendation.service;

import com.ssafy.dodam.domain.improvement.service.ImprovementActionRecorder;
import com.ssafy.dodam.domain.recommendation.dto.request.RecommendationStatusChangeRequest;
import com.ssafy.dodam.domain.recommendation.dto.response.RecommendationResponse;
import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import com.ssafy.dodam.domain.recommendation.entity.RecommendationHistory;
import com.ssafy.dodam.domain.recommendation.entity.RecommendationStatus;
import com.ssafy.dodam.domain.recommendation.repository.RecommendationHistoryRepository;
import com.ssafy.dodam.domain.recommendation.repository.RecommendationRepository;
import com.ssafy.dodam.domain.recommendation.repository.RecommendationUserLock;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * accept/reject/complete/defer(549). 한 트랜잭션: 사용자 행 잠금 → 소유 조회 → 전이 검사 → 상태 변경 → 이력 추가.
 *
 * <p>잠금은 같은 추천에 대한 동시 요청을 직렬화해 두 번째 요청이 갱신된 상태를 보고 409를 받게 한다. 이력은 추가만 하고 수정·삭제하지
 * 않는다(ERD 설명의 근거 보존).
 *
 * <p>ACCEPT/COMPLETE/REJECT/DEFER는 같은 트랜잭션에서 개선 행동(API 19)에도 반영된다
 * ({@link ImprovementActionRecorder}). 추천 수락과 개선 행동이 하나의 원자적 변경이어야 "수락했는데 행동이 없다"나
 * 그 반대가 생기지 않는다. 행동은 추천 1건당 1건이며 재수락도 새 행을 만들지 않는다.
 */
@Service
public class RecommendationStatusService {
    private final RecommendationRepository recommendations;
    private final RecommendationHistoryRepository histories;
    private final RecommendationUserLock users;
    private final RecommendationClock clock;
    private final RecommendationSimulationPresetAssembler presets;
    private final ImprovementActionRecorder improvements;

    public RecommendationStatusService(RecommendationRepository recommendations, RecommendationHistoryRepository histories,
            RecommendationUserLock users, RecommendationClock clock, RecommendationSimulationPresetAssembler presets,
            ImprovementActionRecorder improvements) {
        this.recommendations = recommendations;
        this.histories = histories;
        this.users = users;
        this.clock = clock;
        this.presets = presets;
        this.improvements = improvements;
    }

    @Transactional
    public RecommendationResponse change(Long userId, Long recommendationId, RecommendationStatus next,
            RecommendationStatusChangeRequest request) {
        users.lock(userId).orElseThrow(() -> new ResourceNotFoundException("사용자를 찾을 수 없습니다."));
        Recommendation recommendation = recommendations.findByRecommendationIdAndUserId(recommendationId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(RecommendationQueryService.NOT_FOUND_MESSAGE));
        if (!RecommendationTransitionPolicy.canTransition(recommendation.getStatus(), next)) {
            throw new BusinessException(ErrorCode.RECOMMENDATION_STATUS_TRANSITION_NOT_ALLOWED);
        }
        LocalDateTime now = clock.now();
        recommendation.changeStatus(next, now);
        RecommendationStatusChangeRequest body = request == null ? RecommendationStatusChangeRequest.EMPTY : request;
        histories.save(RecommendationHistory.of(recommendation.getRecommendationId(), next, body.reasonOrNull(), body.memoOrNull(), now));
        improvements.onStatusChanged(recommendation, next, now);
        return RecommendationResponse.from(recommendation, presets.assemble(userId, recommendation));
    }
}
