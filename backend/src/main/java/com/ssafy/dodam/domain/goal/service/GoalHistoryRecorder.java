package com.ssafy.dodam.domain.goal.service;

import com.ssafy.dodam.domain.goal.entity.Goal;
import com.ssafy.dodam.domain.goal.entity.GoalHistory;
import com.ssafy.dodam.domain.goal.entity.GoalHistoryEvent;
import com.ssafy.dodam.domain.goal.entity.GoalType;
import com.ssafy.dodam.domain.goal.entity.SavingGoal;
import com.ssafy.dodam.domain.goal.repository.GoalHistoryRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.springframework.stereotype.Component;

/**
 * `goal_history` 스냅샷 기록(S15P21D105-511). 목표를 바꾸는 서비스들이 변경을 반영한 뒤 호출한다.
 *
 * <p>기록 시점의 진행률과 예상 달성일을 함께 남긴다. 예상 달성일은 510과 같은 규칙(현재 저축속도 = {@link GoalSavingPaceReader},
 * 산식 = {@link GoalForecastFormula})으로 계산하므로, 이력을 이어 보면 "예상 달성일이 어떻게 움직였는지"(기획안 「금융목표 예상
 * 달성 시점 변화」)를 알 수 있다.
 */
@Component
public class GoalHistoryRecorder {

    private final GoalHistoryRepository goalHistoryRepository;
    private final GoalAllocationReader allocationReader;
    private final GoalSavingPaceReader paceReader;

    public GoalHistoryRecorder(GoalHistoryRepository goalHistoryRepository, GoalAllocationReader allocationReader,
            GoalSavingPaceReader paceReader) {
        this.goalHistoryRepository = goalHistoryRepository;
        this.allocationReader = allocationReader;
        this.paceReader = paceReader;
    }

    /**
     * @param savingGoal 모으기 목표면 하위행, 줄이기 목표면 null
     */
    public GoalHistory record(Goal goal, SavingGoal savingGoal, GoalHistoryEvent event, LocalDateTime now) {
        BigDecimal progressRate = null;
        LocalDate forecastDate = null;
        if (goal.getType() == GoalType.SAVING && savingGoal != null) {
            progressRate = progressRate(goal.getCurrentAmount(), savingGoal.getTargetAmount());
            forecastDate = forecastDate(goal, savingGoal, now.toLocalDate());
        }
        return goalHistoryRepository.save(GoalHistory.record(
                goal.getId(), goal.getCurrentAmount(), progressRate, forecastDate, event, now));
    }

    private static BigDecimal progressRate(long current, long target) {
        if (target <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal rate = BigDecimal.valueOf(current)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(target), 4, RoundingMode.HALF_UP);
        return rate.min(BigDecimal.valueOf(100).setScale(4, RoundingMode.HALF_UP));
    }

    /** 510 {@code GoalCalculationService}의 예상 달성일과 같은 규칙({@link GoalForecastFormula}). 속도는 호출 시점의 연결 계좌로 읽는다. */
    private LocalDate forecastDate(Goal goal, SavingGoal savingGoal, LocalDate today) {
        long remaining = savingGoal.getTargetAmount() - goal.getCurrentAmount();
        long pace = paceReader.monthlyPace(goal.getUserId(), allocationReader.readGoal(goal.getUserId(), goal.getId()), today);
        return GoalForecastFormula.expectedDate(remaining, pace, today);
    }
}
