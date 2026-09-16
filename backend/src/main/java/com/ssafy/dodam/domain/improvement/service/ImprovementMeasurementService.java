package com.ssafy.dodam.domain.improvement.service;

import com.ssafy.dodam.domain.improvement.dto.ImprovementSnapshot;
import com.ssafy.dodam.domain.improvement.entity.ImprovementAction;
import com.ssafy.dodam.domain.improvement.entity.ImprovementAction.Measurement;
import com.ssafy.dodam.domain.improvement.repository.ImprovementActionRepository;
import com.ssafy.dodam.domain.improvement.service.ImprovementMeasurementPolicy.Window;
import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import com.ssafy.dodam.domain.recommendation.repository.RecommendationRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실측 효과를 기록한다. 배치 스케줄러가 없으므로 <b>조회 시점에</b> 측정이 밀린 행동을 따라잡는다
 * (`ImprovementQueryService`와 리포트 생성이 조회 전에 한 번 부른다).
 *
 * <p>측정 조건: 취소되지 않았고, 수락 다음 달부터 <b>완료된 달</b>이 1개월 이상 쌓였고, 수락 시점 기준선이 있다.
 * 완료 월이 늘어나면(1 → 2 → 3) 같은 행동을 다시 측정해 창을 넓힌다. 3개월이 차면 더 재지 않는다 —
 * 그 뒤의 변화는 이 행동이 아니라 다른 요인의 결과로 봐야 한다.
 *
 * <p>측정값은 모두 0 이상이다. 악화된 경우는 0으로 기록하고, 방향과 실제 수치는 전후 비교(#4)가
 * {@code delta}·{@code improved}로 보여 준다. 여기서 음수를 "절감"으로 더하면 합계가 상쇄돼 버린다.
 */
@Service
public class ImprovementMeasurementService {

    private static final int MONTHS_PER_YEAR = 12;

    private final ImprovementActionRepository actions;
    private final RecommendationRepository recommendations;
    private final ImprovementSnapshotReader snapshots;
    private final ImprovementSnapshots codec;
    private final ImprovementMeasurementPolicy periods;
    private final ImprovementClock clock;

    public ImprovementMeasurementService(ImprovementActionRepository actions, RecommendationRepository recommendations,
            ImprovementSnapshotReader snapshots, ImprovementSnapshots codec, ImprovementMeasurementPolicy periods,
            ImprovementClock clock) {
        this.actions = actions;
        this.recommendations = recommendations;
        this.snapshots = snapshots;
        this.codec = codec;
        this.periods = periods;
        this.clock = clock;
    }

    /** 사용자의 모든 행동에 대해 밀린 상태 전이와 측정을 따라잡는다. 조회 경로가 읽기 전에 부른다. */
    @Transactional
    public void refresh(Long userId) {
        List<ImprovementAction> owned = actions.findAllByUserId(userId);
        if (owned.isEmpty()) {
            return;
        }
        LocalDateTime now = clock.now();
        LocalDate today = clock.today();
        Map<Long, Recommendation> byId = new HashMap<>();
        recommendations.findAllByUserIdAndRecommendationIdIn(userId,
                        owned.stream().map(ImprovementAction::getRecommendationId).toList())
                .forEach(recommendation -> byId.put(recommendation.getRecommendationId(), recommendation));
        for (ImprovementAction action : owned) {
            refresh(action, byId.get(action.getRecommendationId()), today, now);
        }
    }

    private void refresh(ImprovementAction action, Recommendation recommendation, LocalDate today, LocalDateTime now) {
        if (!action.getStatus().isMeasurable()) {
            return;
        }
        if (!today.isBefore(periods.afterStartDate(action.getPlannedAt()))) {
            action.start(periods.afterStartDate(action.getPlannedAt()).atStartOfDay(), now);
        }
        Window after = periods.after(action.getPlannedAt(), today);
        if (after == null) {
            return;
        }
        ImprovementSnapshot before = codec.fromJson(action.getBeforeSnapshot());
        if (before == null) {
            return;
        }
        ImprovementSnapshot measured = codec.fromJson(action.getAfterSnapshot());
        if (measured != null && measured.months() >= after.months()) {
            return;
        }
        ImprovementSnapshot afterSnapshot = snapshots.read(action.getUserId(), after,
                recommendation == null ? null : recommendation.getGoalId(), now);
        action.measure(measure(before, afterSnapshot), codec.toJson(afterSnapshot), now);
    }

    static Measurement measure(ImprovementSnapshot before, ImprovementSnapshot after) {
        long spendingReduction = positiveDrop(before.monthlySpending(), after.monthlySpending());
        long savingIncrease = positiveDrop(after.monthlySaving(), before.monthlySaving());
        long debtReduction = positiveDrop(before.debtBalance(), after.debtBalance());
        long interestSaving = positiveDrop(before.monthlyInterest(), after.monthlyInterest());
        long monthlyCostSaving = spendingReduction + interestSaving;
        return new Measurement(spendingReduction, savingIncrease, debtReduction, interestSaving,
                monthlyCostSaving, monthlyCostSaving * MONTHS_PER_YEAR,
                advancedDays(before.goalForecastDate(), after.goalForecastDate()));
    }

    /** {@code from − to}가 양수일 때만 값으로 본다. 한쪽이라도 관측되지 않았으면 0이다(모르는 값을 효과로 세지 않는다). */
    private static long positiveDrop(Long from, Long to) {
        if (from == null || to == null) {
            return 0L;
        }
        return Math.max(0L, from - to);
    }

    /** 양수 = 달성일이 앞당겨졌다. 한쪽 예상일이 없으면 null이다(0일 앞당겨진 것과 구분한다). */
    private static Integer advancedDays(LocalDate baseline, LocalDate current) {
        if (baseline == null || current == null) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(current, baseline);
    }
}
