package com.ssafy.dodam.domain.goal.repository;

import com.ssafy.dodam.domain.goal.entity.Goal;
import com.ssafy.dodam.domain.goal.entity.GoalStatus;
import com.ssafy.dodam.domain.goal.entity.GoalType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    /** 삭제되지 않은 본인 목표. 타인·삭제된 목표는 없는 것과 같게 취급한다. */
    Optional<Goal> findByIdAndUserIdAndDeletedAtIsNull(Long id, Long userId);

    List<Goal> findAllByUserIdAndDeletedAtIsNull(Long userId);

    /** 유형·상태 조건의 삭제되지 않은 목표가 있는가. 줄이기 목표 1개 제한(GoalService.create)에 쓴다. */
    boolean existsByUserIdAndTypeAndStatusInAndDeletedAtIsNull(Long userId, GoalType type, Collection<GoalStatus> statuses);
}
