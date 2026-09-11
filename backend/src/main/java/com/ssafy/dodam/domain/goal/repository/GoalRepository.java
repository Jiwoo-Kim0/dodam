package com.ssafy.dodam.domain.goal.repository;

import com.ssafy.dodam.domain.goal.entity.Goal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    /** 삭제되지 않은 본인 목표. 타인·삭제된 목표는 없는 것과 같게 취급한다. */
    Optional<Goal> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    List<Goal> findAllByUserIdAndDeletedAtIsNull(Long userId);
}
