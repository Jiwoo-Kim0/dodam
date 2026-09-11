package com.ssafy.dodam.domain.account.repository;

import com.ssafy.dodam.domain.account.entity.Account;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findAllByUserId(Long userId);

    /**
     * 계좌번호 원문 대신 HMAC 해시로 찾는다(`uk_account_user_account_hash`). 타인 계좌는 없는 것과 같게 취급한다.
     */
    Optional<Account> findByUserIdAndAccountNoHash(Long userId, String accountNoHash);
}
