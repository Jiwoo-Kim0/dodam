package com.ssafy.dodam.domain.product.dto.response;

import com.ssafy.dodam.domain.product.entity.FinancialCompany;

public record ProductCompanyResponse(
        Long companyId,
        String companyCode,
        String companyName,
        String sector
) {

    public static ProductCompanyResponse from(FinancialCompany company) {
        return new ProductCompanyResponse(
                company.getId(),
                company.getCompanyCode(),
                company.getCompanyName(),
                company.getSector()
        );
    }
}
