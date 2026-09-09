package com.ssafy.dodam.domain.loan.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자가 보유한 대출.
 *
 * <p>ERD 공통 정책에 따라 계좌번호 원문은 저장하지 않으며 `account_no_encrypted`는 조회 API에서
 * 사용하지 않으므로 매핑하지 않는다. 조회는 `account_no_hash`, 화면 표시는 `account_no_masked`를 쓴다.
 *
 * <p>출금계좌(`withdrawal_account_id`)는 명세와 기획안의 대출 조회 항목에 없어 연관관계로 매핑하지
 * 않고 식별자만 둔다. 출금계좌 정보가 필요해지면 Account 도메인 구현과 함께 다시 검토한다.
 */
@Getter
@Entity
@Table(name = "loan")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Loan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "loan_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "withdrawal_account_id", nullable = false)
    private Long withdrawalAccountId;

    @Column(name = "withdrawal_account_type", nullable = false, length = 30)
    private String withdrawalAccountType;

    @Column(name = "financial_product_id")
    private Long financialProductId;

    @Column(name = "institution_code", nullable = false, length = 30)
    private String institutionCode;

    @Column(name = "institution_name", nullable = false, length = 150)
    private String institutionName;

    // ERD의 해시 컬럼은 고정 길이 CHAR(64)다. columnDefinition을 명시하지 않으면 Hibernate가
    // varchar로 기대해 ddl-auto: validate가 실패한다.
    @Column(name = "account_no_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String accountNoHash;

    @Column(name = "account_no_masked", nullable = false, length = 100)
    private String accountNoMasked;

    @Column(name = "account_name", nullable = false, length = 150)
    private String accountName;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_type", nullable = false, length = 30)
    private LoanType loanType;

    @Enumerated(EnumType.STRING)
    @Column(name = "repayment_type", nullable = false, length = 30)
    private RepaymentType repaymentType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private LoanStatus status;

    @Column(name = "loan_period")
    private Integer loanPeriod;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(name = "original_principal", nullable = false)
    private Long originalPrincipal;

    @Column(name = "balance", nullable = false)
    private Long balance;

    // 연이율(%)이다. 금액 계산에 쓰이므로 부동소수점을 사용하지 않는다.
    @Column(name = "interest_rate", nullable = false, precision = 10, scale = 4)
    private BigDecimal interestRate;

    @Column(name = "repayment_day")
    private Integer repaymentDay;

    @Column(name = "next_repayment_date")
    private LocalDate nextRepaymentDate;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
