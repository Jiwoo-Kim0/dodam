package com.ssafy.dodam.domain.card.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/**
 * 카드 출금계좌 표시용 읽기 전용 매핑.
 *
 * <p>Account 도메인 구현 전까지 카드 응답에 필요한 컬럼만 노출한다.
 * 계좌번호 원문·암호문은 매핑하지 않는다.
 */
@Getter
@Entity
@Immutable
@Table(name = "account")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CardWithdrawalAccount {

    @Id
    @Column(name = "account_id")
    private Long id;

    @Column(name = "bank_code", nullable = false, length = 20)
    private String bankCode;

    @Column(name = "bank_name", nullable = false, length = 100)
    private String bankName;

    @Column(name = "account_no_masked", nullable = false, length = 100)
    private String accountNoMasked;

    @Column(name = "account_name", nullable = false, length = 100)
    private String accountName;

    @Column(name = "account_type", nullable = false, length = 30)
    private String accountType;
}
