package com.ssafy.dodam.domain.goal.dto.response;

import com.ssafy.dodam.domain.goal.entity.GoalHistory;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 목표 변화 이력 한 건(GET /goals/{goalId}/history). 최신순.
 *
 * @param progressRatePercent 기록 시점 진행률(%). 줄이기 목표는 null
 * @param forecastDate        기록 시점 예상 달성일. 속도가 0이거나 줄이기 목표면 null
 * @param event               {@code GoalHistoryEvent} 이름
 */
public record GoalHistoryResponse(
        Long historyId,
        LocalDate recordedDate,
        long currentAmount,
        Double progressRatePercent,
        LocalDate forecastDate,
        String event,
        LocalDateTime createdAt
) {

    public static GoalHistoryResponse from(GoalHistory history) {
        return new GoalHistoryResponse(
                history.getId(),
                history.getRecordedDate(),
                history.getCurrentAmount(),
                history.getProgressRate() == null ? null : history.getProgressRate().doubleValue(),
                history.getForecastDate(),
                history.getEvent(),
                history.getCreatedAt()
        );
    }
}
