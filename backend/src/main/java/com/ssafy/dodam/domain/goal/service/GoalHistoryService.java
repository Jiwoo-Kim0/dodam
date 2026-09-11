package com.ssafy.dodam.domain.goal.service;

import com.ssafy.dodam.domain.goal.dto.response.GoalHistoryResponse;
import com.ssafy.dodam.domain.goal.repository.GoalHistoryRepository;
import com.ssafy.dodam.domain.goal.repository.GoalRepository;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.policy.QueryPeriod;
import com.ssafy.dodam.global.policy.QueryPeriodPolicy;
import com.ssafy.dodam.global.response.PageResponse;
import java.time.LocalDate;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API 12 목표 변화 이력 조회(S15P21D105-511). 기록은 {@link GoalHistoryRecorder}가 한다.
 */
@Service
@Transactional(readOnly = true)
public class GoalHistoryService {

    private final GoalRepository goalRepository;
    private final GoalHistoryRepository goalHistoryRepository;
    private final QueryPeriodPolicy queryPeriodPolicy;
    private final GoalClock goalClock;

    public GoalHistoryService(
            GoalRepository goalRepository,
            GoalHistoryRepository goalHistoryRepository,
            QueryPeriodPolicy queryPeriodPolicy,
            GoalClock goalClock
    ) {
        this.goalRepository = goalRepository;
        this.goalHistoryRepository = goalHistoryRepository;
        this.queryPeriodPolicy = queryPeriodPolicy;
        this.goalClock = goalClock;
    }

    /**
     * 기간 정책은 거래내역과 같은 {@link QueryPeriodPolicy}(기본 최근 3개월, 최대 12개월). 최신순.
     */
    public PageResponse<GoalHistoryResponse> getHistory(
            Long userId, Long goalId, LocalDate startDate, LocalDate endDate, int page, int size
    ) {
        goalRepository.findByIdAndUserIdAndDeletedAtIsNull(goalId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(GoalService.GOAL_NOT_FOUND_MESSAGE));
        QueryPeriod period = queryPeriodPolicy.resolve(startDate, endDate, goalClock.today());

        return PageResponse.from(
                goalHistoryRepository.findAllByGoalIdAndRecordedDateBetweenOrderByRecordedDateDescIdDesc(
                        goalId, period.startDate(), period.endDate(), PageRequest.of(page, size)),
                GoalHistoryResponse::from
        );
    }
}
