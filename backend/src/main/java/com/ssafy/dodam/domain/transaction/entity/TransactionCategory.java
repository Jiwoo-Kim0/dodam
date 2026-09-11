package com.ssafy.dodam.domain.transaction.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * ERD `transaction_category`. 소비 카테고리 기준 행으로 V6 마이그레이션이 초기값을 넣는다.
 * 코드(`code`)는 대문자 스네이크 케이스이며 API의 카테고리 필터 값으로 그대로 노출된다.
 */
@Entity
@Table(name = "transaction_category")
public class TransactionCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "category_id")
    private Long id;

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_spending_type", length = 20)
    private SpendingType defaultSpendingType;

    @Column(name = "active", nullable = false)
    private boolean active;

    protected TransactionCategory() {
    }

    public Long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public SpendingType getDefaultSpendingType() {
        return defaultSpendingType;
    }

    public boolean isActive() {
        return active;
    }
}
