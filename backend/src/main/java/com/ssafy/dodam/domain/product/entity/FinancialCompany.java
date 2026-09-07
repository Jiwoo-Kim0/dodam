package com.ssafy.dodam.domain.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "financial_company")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FinancialCompany {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "company_id")
    private Long id;

    @Column(name = "company_code", nullable = false, length = 30)
    private String companyCode;

    @Column(name = "company_name", nullable = false, length = 150)
    private String companyName;

    @Column(name = "sector", length = 50)
    private String sector;
}
