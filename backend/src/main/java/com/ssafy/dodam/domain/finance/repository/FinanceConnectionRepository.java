package com.ssafy.dodam.domain.finance.repository;

import com.ssafy.dodam.domain.finance.entity.FinanceConnection;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FinanceConnectionRepository extends JpaRepository<FinanceConnection, Long> {

    Optional<FinanceConnection> findByUserId(Long userId);

    /**
     * 연결·해제·동기화 시작처럼 상태 전이를 일으키는 요청은 사용자 행을 잠근 채 처리한다.
     * 같은 사용자의 동시 요청이 상태 검사를 함께 통과하는 것을 막기 위해서다 (SELECT ... FOR UPDATE).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from FinanceConnection c where c.userId = :userId")
    Optional<FinanceConnection> findByUserIdForUpdate(@Param("userId") Long userId);
}
