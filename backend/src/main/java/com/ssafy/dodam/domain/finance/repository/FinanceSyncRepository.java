package com.ssafy.dodam.domain.finance.repository;

import com.ssafy.dodam.domain.finance.entity.FinanceSync;
import com.ssafy.dodam.domain.finance.entity.FinanceSyncStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinanceSyncRepository extends JpaRepository<FinanceSync, Long> {

    List<FinanceSync> findAllByUserIdAndStatusIn(Long userId, Collection<FinanceSyncStatus> statuses);

    boolean existsByUserIdAndStatusIn(Long userId, Collection<FinanceSyncStatus> statuses);

    /**
     * 가장 최근에 시작한 동기화 (상태 무관). 같은 시각이면 나중에 만들어진 행.
     */
    Optional<FinanceSync> findTopByUserIdOrderByStartedAtDescIdDesc(Long userId);

    /**
     * 가장 최근에 성공한 동기화.
     */
    Optional<FinanceSync> findTopByUserIdAndStatusOrderByCompletedAtDescIdDesc(Long userId, FinanceSyncStatus status);
}
