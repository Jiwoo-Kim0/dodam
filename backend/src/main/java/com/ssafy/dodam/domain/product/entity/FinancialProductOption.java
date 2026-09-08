package com.ssafy.dodam.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "financial_product_option")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinancialProductOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_option_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private FinancialProduct product;

    @Column(name = "term")
    private Integer term;

    @Column(name = "rate", precision = 10, scale = 4)
    private BigDecimal rate;

    @Column(name = "preferred_rate", precision = 10, scale = 4)
    private BigDecimal preferredRate;

    @Column(name = "rate_type", length = 30)
    private String rateType;

    @Column(name = "min_rate", precision = 10, scale = 4)
    private BigDecimal minRate;

    @Column(name = "max_rate", precision = 10, scale = 4)
    private BigDecimal maxRate;

    @Column(name = "repayment_type", length = 30)
    private String repaymentType;

    @Column(name = "limit_amount")
    private Long limitAmount;
}
