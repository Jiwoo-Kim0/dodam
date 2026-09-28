package com.ssafy.dodam.domain.minusaccount.repository;

import com.ssafy.dodam.domain.minusaccount.entity.MinusAccount;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MinusAccountRepository extends JpaRepository<MinusAccount, Long> {

    List<MinusAccount> findByAccountIdIn(List<Long> accountIds);

    /**
     * 상환·이자납부용 조회. `available_amount`가 파생 저장값이라 읽고-쓰는 사이의 경쟁 조건을
     * 막아야 하므로 행을 잠근다. 소유권은 조건에 포함해 SQL 단계에서 강제한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MinusAccount m where m.accountId = :accountId and m.userId = :userId")
    Optional<MinusAccount> findForUpdate(
            @Param("accountId") Long accountId,
            @Param("userId") Long userId
    );
}
