package com.ssafy.dodam.domain.card.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자가 보유한 카드.
 *
 * <p>ERD 공통 정책에 따라 카드번호 원문은 저장하지 않으며 `card_no_encrypted`는
 * 조회 API에서 사용할 일이 없으므로 매핑하지 않는다. 조회는 `card_no_hash`,
 * 화면 표시는 `card_no_masked`를 사용한다.
 */
@Getter
@Entity
@Table(name = "card")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "card_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "withdrawal_account_id", nullable = false)
    private Long withdrawalAccountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "withdrawal_account_id",
            referencedColumnName = "account_id",
            insertable = false,
            updatable = false
    )
    private CardWithdrawalAccount withdrawalAccount;

    @Column(name = "withdrawal_account_type", nullable = false, length = 30)
    private String withdrawalAccountType;

    @Column(name = "financial_product_id")
    private Long financialProductId;

    @Column(name = "card_no_hash", nullable = false, length = 64)
    private String cardNoHash;

    @Column(name = "card_no_masked", nullable = false, length = 100)
    private String cardNoMasked;

    @Column(name = "issuer_code", nullable = false, length = 30)
    private String issuerCode;

    @Column(name = "issuer_name", nullable = false, length = 100)
    private String issuerName;

    @Column(name = "card_name", nullable = false, length = 150)
    private String cardName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private CardStatus status;

    @Column(name = "credit_limit", nullable = false)
    private Long creditLimit;

    @Column(name = "baseline_performance")
    private Long baselinePerformance;

    @Column(name = "max_benefit_limit")
    private Long maxBenefitLimit;

    @Column(name = "annual_fee")
    private Long annualFee;

    @Column(name = "expiry_month", length = 7)
    private String expiryMonth;

    @Column(name = "withdrawal_day")
    private Integer withdrawalDay;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 동일한 설정을 다시 요청하면 갱신 시각도 바꾸지 않는다. */
    public boolean updatePaymentSettings(Long accountId, Integer paymentDay) {
        Long resolvedAccountId = accountId == null ? withdrawalAccountId : accountId;
        Integer resolvedPaymentDay = paymentDay == null ? withdrawalDay : paymentDay;
        if (Objects.equals(withdrawalAccountId, resolvedAccountId)
                && Objects.equals(withdrawalDay, resolvedPaymentDay)) {
            return false;
        }
        withdrawalAccountId = resolvedAccountId;
        withdrawalDay = resolvedPaymentDay;
        updatedAt = LocalDateTime.now();
        return true;
    }
}
