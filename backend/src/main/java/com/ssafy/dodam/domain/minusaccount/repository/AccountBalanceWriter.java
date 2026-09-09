package com.ssafy.dodam.domain.minusaccount.repository;

import com.ssafy.dodam.domain.minusaccount.entity.MinusAccountOwner;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 상환·이자납부에 필요한 `account` 잔액 갱신.
 *
 * <p>`account`는 BE-A(S15P21D105-501) 소관이라 쓰기 가능한 JPA 엔티티를 새로 만들지 않는다.
 * 읽기 전용 매핑인 {@link MinusAccountOwner}와 같은 테이블에 쓰기 엔티티를 하나 더 두면 501
 * 구현과 이중 매핑이 되기 때문이다. 501이 Account 엔티티를 만들면 이 어댑터를 교체한다.
 *
 * <p>모든 메서드는 조건절에 `user_id`와 `account_type`을 포함해 소유권과 유형을 SQL 단계에서
 * 강제하고, 갱신 행 수가 1이 아니면 예외로 트랜잭션을 되돌린다.
 */
public interface AccountBalanceWriter {

    /**
     * 출금계좌를 잠그고 읽는다. 잠금 순서를 서비스가 통제할 수 있도록 조회와 갱신을 나눈다.
     */
    Optional<MinusAccountOwner> findWithdrawalAccountForUpdate(
            Long userId,
            String accountNoHash
    );

    /**
     * 본인의 입출금계좌 목록. 출금계좌를 지정하지 않았을 때 기본값을 특정하는 데 쓴다.
     */
    java.util.List<MinusAccountOwner> findDemandDepositAccounts(Long userId);

    /**
     * 마이너스통장의 상위 계좌 잔액을 `-usedAmount`로 맞춘다.
     *
     * <p>ERD 계산 기준: "MINUS의 account.balance와 minus_account.used_amount는 같은 트랜잭션에서
     * balance = -used_amount가 되도록 갱신한다".
     */
    void updateMinusBalance(Long userId, Long accountId, long usedAmount, LocalDateTime now);

    /**
     * 출금계좌에서 금액을 차감한다.
     */
    void withdraw(Long userId, Long accountId, long amount, LocalDateTime now);
}
