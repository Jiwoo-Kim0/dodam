package com.ssafy.dodam.domain.goal.repository;

import com.ssafy.dodam.domain.goal.entity.GoalAccount;
import com.ssafy.dodam.domain.goal.entity.GoalAccountId;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface GoalAccountRepository extends JpaRepository<GoalAccount, GoalAccountId> {

    List<GoalAccount> findAllByGoalIdOrderByAccountIdAsc(Long goalId);

    /** 계좌가 어느 목표에 연결돼 있는지. 계좌당 목표 1개 제약을 서비스에서 먼저 확인할 때 쓴다. */
    List<GoalAccount> findAllByAccountIdIn(Collection<Long> accountIds);

    List<GoalAccount> findAllByUserIdAndGoalIdIn(Long userId, Collection<Long> goalIds);

    /**
     * 계좌가 삭제되지 않은 목표에 기여하는 링크(626). 계좌별 총 할당액·가용액 계산에 쓴다.
     * 삭제된 목표의 링크는 제외해 가용액을 줄이지 않는다.
     */
    @Query("""
            select ga from GoalAccount ga
            where ga.userId = :userId and ga.accountId in :accountIds
              and exists (select g.id from Goal g where g.id = ga.goalId and g.deletedAt is null)
            """)
    List<GoalAccount> findAllLiveByUserIdAndAccountIdIn(
            @Param("userId") Long userId, @Param("accountIds") Collection<Long> accountIds);
}
