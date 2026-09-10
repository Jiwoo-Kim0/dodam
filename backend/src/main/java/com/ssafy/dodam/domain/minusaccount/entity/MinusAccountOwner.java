package com.ssafy.dodam.domain.minusaccount.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * 마이너스통장의 상위 `account` 행 읽기 전용 매핑.
 *
 * <p>`account`는 BE-A(S15P21D105-501) 소관이므로 이 도메인은 조회에 필요한 컬럼만 노출한다.
 * 카드의 `CardWithdrawalAccount`와 같은 방식이며 계좌번호 원문·암호문은 매핑하지 않는다.
 * `account_no_hash`는 응답에 쓰지 않고 경로 변수 조회 조건으로만 사용한다.
 *
 * <p>읽기 전용이므로 상환·이자납부의 잔액 갱신에는 쓸 수 없다. 그쪽은
 * {@code AccountBalanceWriter}가 담당한다. 같은 테이블에 쓰기 가능한 엔티티를 하나 더 두면
 * 501 구현과 이중 매핑이 되어 피했다.
 */
@Getter
@Entity
@Immutable
@Table(name = "account")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MinusAccountOwner {

    /**
     * 이 도메인이 다루는 유일한 `account_type`. 모든 조회 조건에 포함한다.
     */
    public static final String MINUS = "MINUS";

    @Id
    @Column(name = "account_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "bank_code", nullable = false, length = 20)
    private String bankCode;

    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    // ERD의 해시 컬럼은 고정 길이 CHAR(64)다. columnDefinition을 명시하지 않으면 Hibernate가
    // varchar로 기대해 ddl-auto: validate가 실패한다.
    @Column(name = "account_no_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String accountNoHash;

    @Column(name = "account_no_masked", nullable = false, length = 100)
    private String accountNoMasked;

    @Column(name = "account_name", nullable = false, length = 100)
    private String accountName;

    @Column(name = "account_type", nullable = false, length = 30)
    private String accountType;

    @Column(name = "status", nullable = false, length = 30)
    private String status;

    // MINUS 계좌의 잔액은 -used_amount인 0 이하의 값이다(ERD 계산 기준).
    @Column(name = "balance", nullable = false)
    private Long balance;

    @Column(name = "last_transaction_date")
    private LocalDate lastTransactionDate;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currency;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
