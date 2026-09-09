package com.ssafy.dodam.domain.minusaccount.repository;

import com.ssafy.dodam.domain.minusaccount.entity.MinusAccountOwner;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 마이너스통장의 상위 `account` 행 조회.
 *
 * <p>모든 조회에 `accountType = 'MINUS'` 조건을 포함한다. 다른 유형의 계좌를 마이너스통장
 * 경로로 조회하면 리소스 존재 여부가 드러나므로 조건을 서비스가 아니라 쿼리에 둔다.
 */
public interface MinusAccountOwnerRepository extends JpaRepository<MinusAccountOwner, Long> {

    Optional<MinusAccountOwner> findByUserIdAndAccountNoHashAndAccountType(
            Long userId,
            String accountNoHash,
            String accountType
    );

    List<MinusAccountOwner> findByUserIdAndAccountType(Long userId, String accountType);

    List<MinusAccountOwner> findByUserIdAndAccountTypeAndStatus(
            Long userId,
            String accountType,
            String status
    );
}
