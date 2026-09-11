package com.ssafy.dodam.domain.goal.repository;

import com.ssafy.dodam.domain.goal.entity.SavingGoal;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SavingGoalRepository extends JpaRepository<SavingGoal, Long> {

    List<SavingGoal> findAllByGoalIdIn(Collection<Long> goalIds);
}
