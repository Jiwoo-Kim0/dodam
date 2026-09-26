package com.ssafy.dodam.domain.goal.repository;

import com.ssafy.dodam.domain.goal.entity.SafeboxMovement;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SafeboxMovementRepository extends JpaRepository<SafeboxMovement, Long> {
    @Query("select coalesce(sum(m.delta), 0) from SafeboxMovement m where m.userId = :userId and m.goalId = :goalId "
            + "and m.occurredAt >= :from and m.occurredAt < :until")
    long sumDelta(@Param("userId") Long userId, @Param("goalId") Long goalId,
            @Param("from") LocalDateTime from, @Param("until") LocalDateTime until);
}
