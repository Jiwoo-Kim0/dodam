package com.ssafy.dodam.domain.goal.repository;

import com.ssafy.dodam.domain.goal.entity.SpendingGoal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpendingGoalRepository extends JpaRepository<SpendingGoal, Long> {

    List<SpendingGoal> findAllByGoalIdIn(Collection<Long> goalIds);
}
