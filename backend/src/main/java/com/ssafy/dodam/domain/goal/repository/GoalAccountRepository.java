package com.ssafy.dodam.domain.goal.repository;

import com.ssafy.dodam.domain.goal.entity.GoalAccount;
import com.ssafy.dodam.domain.goal.entity.GoalAccountId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GoalAccountRepository extends JpaRepository<GoalAccount, GoalAccountId> {

    List<GoalAccount> findAllByGoalIdOrderByAccountIdAsc(Long goalId);

    /** 계좌가 어느 목표에 연결돼 있는지. 계좌당 목표 1개 제약을 서비스에서 먼저 확인할 때 쓴다. */
    List<GoalAccount> findAllByAccountIdIn(Collection<Long> accountIds);

    List<GoalAccount> findAllByUserIdAndGoalIdIn(Long userId, Collection<Long> goalIds);
}
