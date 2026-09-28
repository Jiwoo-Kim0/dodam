package com.ssafy.dodam.domain.account.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;

/**
 * ERD `account`(V3). 계좌 도메인이 소유하는 정식 매핑이다.
 *
 * <p>같은 테이블을 카드(`CardWithdrawalAccount`, 읽기 전용)와 마이너스통장(`MinusAccountOwner`)이 부분 매핑하고 있다.
 * 두 매핑은 각 도메인이 Account 도메인 구현 전에 임시로 둔 것이며, 교체는 remaining-work.md에 기록했다.
 *
 * <p>`account_no_encrypted`·`source_type`은 DDL에서 NOT NULL이지만 여기서는 nullable로 둔다. local/prod의
 * `ddl-auto: validate`는 NULL 허용 여부를 검사하지 않고, H2 테스트는 엔티티로 스키마를 만드는데 예·적금·현금흐름
 * 테스트 fixture가 이 두 컬럼 없이 `account`를 적재하기 때문이다. NOT NULL은 V3 DDL이 보장한다.
 */
@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "account_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "bank_code", nullable = false, length = 20)
    private String bankCode;

    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    /** 계좌번호 암호문. 조회 응답에는 절대 싣지 않고 마스킹 값만 노출한다. */
    @Column(name = "account_no_encrypted", columnDefinition = "VARBINARY(512)")
    private byte[] accountNoEncrypted;

    // ERD의 해시 컬럼은 고정 길이 CHAR(64)다. columnDefinition을 명시하지 않으면 Hibernate가
    // VARCHAR로 보고 validate에 실패한다(카드·대출 엔티티와 같은 처리).
    @Column(name = "account_no_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String accountNoHash;

    @Column(name = "account_no_masked", nullable = false, length = 100)
    private String accountNoMasked;

    @Column(name = "account_name", nullable = false, length = 100)
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 30)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AccountStatus status;

    @Column(name = "balance", nullable = false)
    private long balance;

    @Column(name = "last_transaction_date")
    private LocalDate lastTransactionDate;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 20)
    private AccountSourceType sourceType;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Account() {
    }

    private Account(
            Long userId,
            String bankCode,
            String bankName,
            byte[] accountNoEncrypted,
            String accountNoHash,
            String accountNoMasked,
            String accountName,
            AccountType accountType,
            long balance,
            LocalDate lastTransactionDate,
            String currency,
            AccountSourceType sourceType,
            LocalDateTime now
    ) {
        this.userId = userId;
        this.bankCode = bankCode;
        this.bankName = bankName;
        this.accountNoEncrypted = accountNoEncrypted;
        this.accountNoHash = accountNoHash;
        this.accountNoMasked = accountNoMasked;
        this.accountName = accountName;
        this.accountType = accountType;
        this.status = AccountStatus.ACTIVE;
        this.balance = balance;
        this.lastTransactionDate = lastTransactionDate;
        this.currency = currency;
        this.sourceType = sourceType;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 금융망에서 처음 확인된 계좌를 만든다. 상태는 ACTIVE, 출처는 SSAFY다.
     * 계좌번호 원문은 받지 않는다. 암호문·해시·마스킹은 호출자가 만들어 넘긴다.
     */
    public static Account fromFinance(
            Long userId,
            FinanceSnapshot snapshot,
            byte[] accountNoEncrypted,
            String accountNoHash,
            String accountNoMasked,
            LocalDateTime now
    ) {
        return new Account(
                userId,
                snapshot.bankCode(),
                snapshot.bankName(),
                accountNoEncrypted,
                accountNoHash,
                accountNoMasked,
                snapshot.accountName(),
                snapshot.accountType(),
                snapshot.balance(),
                snapshot.lastTransactionDate(),
                snapshot.currency(),
                AccountSourceType.SSAFY,
                now
        );
    }

    /**
     * 금융망 최신 값을 반영한다. 금융망에 다시 나타난 계좌는 CLOSED였더라도 ACTIVE로 되돌린다.
     *
     * @return 실제로 값이 바뀌었으면 true. 같은 값이면 updated_at도 건드리지 않는다.
     */
    public boolean applyFinanceSnapshot(FinanceSnapshot snapshot, LocalDateTime now) {
        boolean changed = false;
        if (!Objects.equals(this.bankCode, snapshot.bankCode())) {
            this.bankCode = snapshot.bankCode();
            changed = true;
        }
        if (!Objects.equals(this.bankName, snapshot.bankName())) {
            this.bankName = snapshot.bankName();
            changed = true;
        }
        if (!Objects.equals(this.accountName, snapshot.accountName())) {
            this.accountName = snapshot.accountName();
            changed = true;
        }
        if (this.accountType != snapshot.accountType()) {
            this.accountType = snapshot.accountType();
            changed = true;
        }
        if (this.balance != snapshot.balance()) {
            this.balance = snapshot.balance();
            changed = true;
        }
        if (!Objects.equals(this.lastTransactionDate, snapshot.lastTransactionDate())) {
            this.lastTransactionDate = snapshot.lastTransactionDate();
            changed = true;
        }
        if (!Objects.equals(this.currency, snapshot.currency())) {
            this.currency = snapshot.currency();
            changed = true;
        }
        if (this.status != AccountStatus.ACTIVE) {
            this.status = AccountStatus.ACTIVE;
            changed = true;
        }
        if (changed) {
            this.updatedAt = now;
        }
        return changed;
    }

    /**
     * 금융망 목록에서 사라진 계좌를 해지 처리한다.
     *
     * @return 상태가 실제로 바뀌었으면 true.
     */
    public boolean close(LocalDateTime now) {
        if (this.status == AccountStatus.CLOSED) {
            return false;
        }
        this.status = AccountStatus.CLOSED;
        this.updatedAt = now;
        return true;
    }

    public boolean isFromFinance() {
        return this.sourceType == AccountSourceType.SSAFY;
    }

    public void withdraw(long amount, LocalDateTime now) {
        if (amount <= 0 || balance < amount) {
            throw new BusinessException(ErrorCode.TRANSFER_INSUFFICIENT_BALANCE);
        }
        balance -= amount;
        lastTransactionDate = now.toLocalDate();
        updatedAt = now;
    }

    public void deposit(long amount, LocalDateTime now) {
        if (amount <= 0 || balance > Long.MAX_VALUE - amount) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
        balance += amount;
        lastTransactionDate = now.toLocalDate();
        updatedAt = now;
    }

    public void reconcileBalance(long financeBalance, LocalDateTime now) {
        if (financeBalance < 0) {
            throw new BusinessException(ErrorCode.FINANCE_API_UNAVAILABLE);
        }
        balance = financeBalance;
        lastTransactionDate = now.toLocalDate();
        updatedAt = now;
    }

    /**
     * 금융망 응답에서 계좌번호를 뺀 값. 엔티티가 원문 문자열을 다루지 않게 하기 위한 경계다.
     */
    public record FinanceSnapshot(
            String bankCode,
            String bankName,
            String accountName,
            AccountType accountType,
            long balance,
            LocalDate lastTransactionDate,
            String currency
    ) {
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getBankCode() {
        return bankCode;
    }

    public String getBankName() {
        return bankName;
    }

    public String getAccountNoHash() {
        return accountNoHash;
    }

    public byte[] getAccountNoEncrypted() {
        return accountNoEncrypted;
    }

    public String getAccountNoMasked() {
        return accountNoMasked;
    }

    public String getAccountName() {
        return accountName;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public long getBalance() {
        return balance;
    }

    public LocalDate getLastTransactionDate() {
        return lastTransactionDate;
    }

    public String getCurrency() {
        return currency;
    }

    public AccountSourceType getSourceType() {
        return sourceType;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
