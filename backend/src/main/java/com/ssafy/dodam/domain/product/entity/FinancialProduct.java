package com.ssafy.dodam.domain.product.entity;

import com.fasterxml.jackson.databind.JsonNode;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "financial_product")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinancialProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "product_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private FinancialCompany company;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private ProductSourceType sourceType;

    @Column(name = "external_product_code", length = 100)
    private String externalProductCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 30)
    private ProductType productType;

    @Column(name = "product_name", nullable = false, length = 255)
    private String productName;

    @Column(name = "join_way", length = 255)
    private String joinWay;

    @Column(name = "saving_type", length = 30)
    private String savingType;

    @Column(name = "loan_type", length = 30)
    private String loanType;

    @Column(name = "special_condition", columnDefinition = "TEXT")
    private String specialCondition;

    @Column(name = "join_member", columnDefinition = "TEXT")
    private String joinMember;

    @Column(name = "min_rate", precision = 10, scale = 4)
    private BigDecimal minRate;

    @Column(name = "max_rate", precision = 10, scale = 4)
    private BigDecimal maxRate;

    @Column(name = "max_limit")
    private Long maxLimit;

    @Column(name = "baseline_performance")
    private Long baselinePerformance;

    @Column(name = "max_benefit_limit")
    private Long maxBenefitLimit;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "benefits", columnDefinition = "JSON")
    private JsonNode benefits;

    @Column(name = "synced_at", nullable = false)
    private LocalDateTime syncedAt;

    @OrderBy("term ASC, id ASC")
    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    private List<FinancialProductOption> options = new ArrayList<>();
}
