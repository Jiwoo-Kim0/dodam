package com.ssafy.dodam.domain.product.dto.response;

import com.ssafy.dodam.domain.product.entity.FinancialCompany;

public record FinancialCompanyResponse(
        Long companyId,
        String companyCode,
        String companyName,
        String sector
) {

    public static FinancialCompanyResponse from(FinancialCompany company) {
        return new FinancialCompanyResponse(
                company.getId(),
                company.getCompanyCode(),
                company.getCompanyName(),
                company.getSector()
        );
    }
}
