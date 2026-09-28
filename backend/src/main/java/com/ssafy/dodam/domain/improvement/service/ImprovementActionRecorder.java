package com.ssafy.dodam.domain.improvement.service;

import com.ssafy.dodam.domain.improvement.entity.ImprovementAction;
import com.ssafy.dodam.domain.improvement.repository.ImprovementActionRepository;
import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import com.ssafy.dodam.domain.recommendation.entity.RecommendationStatus;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 추천 상태 변경을 개선 행동에 반영한다. API 19에는 행동을 만들거나 상태를 바꾸는 엔드포인트가 없으므로
 * 이 클래스가 유일한 입구다(`RecommendationStatusService`가 같은 트랜잭션에서 부른다).
 *
 * <p><b>중복 없이 연결한다.</b> 행동은 추천 1건당 최대 1건이고 `uk_improvement_action_recommendation_id`가
 * 이를 DB에서 보장한다. DEFERRED를 거쳐 다시 수락해도 새 행을 만들지 않고 기존 행을 계획 상태로 되돌린다 —
 * 그러지 않으면 같은 개선안의 효과가 두 번 집계된다.
 *
 * <pre>
 * PENDING/DEFERRED → ACCEPTED  : 행동 생성(PLANNED) 또는 기존 행동 재계획
 * ACCEPTED         → COMPLETED : 행동 COMPLETED
 * ACCEPTED         → REJECTED  : 행동 CANCELED (수락을 되돌렸다)
 * ACCEPTED         → DEFERRED  : 행동 CANCELED (실행을 미뤘다 — 다시 수락하면 기준선을 새로 잡는다)
 * </pre>
 */
@Component
public class ImprovementActionRecorder {

    private final ImprovementActionRepository actions;
    private final ImprovementSnapshotReader snapshots;
    private final ImprovementSnapshots codec;
    private final ImprovementMeasurementPolicy periods;

    public ImprovementActionRecorder(ImprovementActionRepository actions, ImprovementSnapshotReader snapshots,
            ImprovementSnapshots codec, ImprovementMeasurementPolicy periods) {
        this.actions = actions;
        this.snapshots = snapshots;
        this.codec = codec;
        this.periods = periods;
    }

    public void onStatusChanged(Recommendation recommendation, RecommendationStatus next, LocalDateTime now) {
        Optional<ImprovementAction> existing = actions.findByRecommendationId(recommendation.getRecommendationId());
        switch (next) {
            case ACCEPTED -> accept(recommendation, existing, now);
            case COMPLETED -> existing.ifPresent(action -> action.complete(now));
            case REJECTED, DEFERRED -> existing.ifPresent(action -> action.cancel(now));
            default -> {
                // PENDING으로 되돌리는 전이는 허용되지 않는다(RecommendationTransitionPolicy).
            }
        }
    }

    private void accept(Recommendation recommendation, Optional<ImprovementAction> existing, LocalDateTime now) {
        var before = codec.toJson(snapshots.read(recommendation.getUserId(), periods.before(now),
                recommendation.getGoalId(), now));
        if (existing.isPresent()) {
            // 기준선은 이번 수락 시점으로 다시 잡는다. 미뤄 둔 사이의 소비 변화를 이 행동의 효과로 계산하지 않는다.
            existing.get().replan(before, now);
            return;
        }
        actions.save(ImprovementAction.plan(recommendation.getUserId(), recommendation.getRecommendationId(),
                recommendation.getTitle(), before, now));
    }
}
