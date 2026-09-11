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
}
