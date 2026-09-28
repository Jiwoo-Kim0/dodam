package com.ssafy.dodam.domain.goal.repository;

import com.ssafy.dodam.domain.goal.entity.GoalHistory;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalHistoryRepository extends JpaRepository<GoalHistory, Long> {

    /** 최신순(같은 날이면 나중에 기록된 것 먼저). */
    Page<GoalHistory> findAllByGoalIdAndRecordedDateBetweenOrderByRecordedDateDescIdDesc(
            Long goalId, LocalDate startDate, LocalDate endDate, Pageable pageable);

    Optional<GoalHistory> findTopByGoalIdOrderByRecordedDateDescIdDesc(Long goalId);

    /**
     * 그 날짜까지의 마지막 기록. 월간 리포트가 "그 달 시작 직전"과 "그 달 마지막" 상태를 복원하는 데 쓴다
     * (S15P21D105-590). 현재 잔액으로 과거 달을 다시 계산하면 그 달의 변화가 아니라 오늘의 값이 나온다.
     */
    Optional<GoalHistory> findTopByGoalIdAndRecordedDateLessThanEqualOrderByRecordedDateDescIdDesc(
            Long goalId, LocalDate recordedDate);
}
