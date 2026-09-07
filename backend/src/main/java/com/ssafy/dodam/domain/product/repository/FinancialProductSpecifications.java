package com.ssafy.dodam.domain.product.repository;

import com.ssafy.dodam.domain.product.entity.FinancialProduct;
import com.ssafy.dodam.domain.product.entity.FinancialProductOption;
import com.ssafy.dodam.domain.product.entity.ProductType;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.data.jpa.domain.Specification;

public final class FinancialProductSpecifications {

    private FinancialProductSpecifications() {
    }

    public static Specification<FinancialProduct> hasType(ProductType productType) {
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(
                root.get("productType"),
                productType
        );
    }

    public static Specification<FinancialProduct> hasCompanyId(Long companyId) {
        if (companyId == null) {
            return unrestricted();
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(
                root.get("company").get("id"),
                companyId
        );
    }

    public static Specification<FinancialProduct> hasCompanyCode(String companyCode) {
        if (companyCode == null) {
            return unrestricted();
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(
                criteriaBuilder.upper(root.get("company").get("companyCode")),
                companyCode
        );
    }

    public static Specification<FinancialProduct> hasSavingType(String savingType) {
        if (savingType == null) {
            return unrestricted();
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(
                criteriaBuilder.upper(root.get("savingType")),
                savingType
        );
    }

    public static Specification<FinancialProduct> hasLoanType(String loanType) {
        if (loanType == null) {
            return unrestricted();
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(
                criteriaBuilder.upper(root.get("loanType")),
                loanType
        );
    }

    public static Specification<FinancialProduct> hasLoanTypeIn(Set<String> loanTypes) {
        return (root, query, criteriaBuilder) -> root.get("loanType").in(loanTypes);
    }

    public static Specification<FinancialProduct> hasMaxRateAtLeast(BigDecimal minRate) {
        if (minRate == null) {
            return unrestricted();
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(
                root.get("maxRate"),
                minRate
        );
    }

    public static Specification<FinancialProduct> hasMinRateAtLeast(BigDecimal minRate) {
        if (minRate == null) {
            return unrestricted();
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.greaterThanOrEqualTo(
                root.get("minRate"),
                minRate
        );
    }

    public static Specification<FinancialProduct> hasMaxRateAtMost(BigDecimal maxRate) {
        if (maxRate == null) {
            return unrestricted();
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.lessThanOrEqualTo(
                root.get("maxRate"),
                maxRate
        );
    }

    public static Specification<FinancialProduct> hasOptionTerm(Integer term) {
        if (term == null) {
            return unrestricted();
        }
        return (root, query, criteriaBuilder) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<FinancialProductOption> option = subquery.from(FinancialProductOption.class);
            subquery.select(option.get("id"));
            subquery.where(
                    criteriaBuilder.equal(option.get("product"), root),
                    criteriaBuilder.equal(option.get("term"), term)
            );
            return criteriaBuilder.exists(subquery);
        };
    }

    public static Specification<FinancialProduct> hasOptionRateType(String rateType) {
        if (rateType == null) {
            return unrestricted();
        }
        return (root, query, criteriaBuilder) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<FinancialProductOption> option = subquery.from(FinancialProductOption.class);
            subquery.select(option.get("id"));
            subquery.where(
                    criteriaBuilder.equal(option.get("product"), root),
                    criteriaBuilder.equal(criteriaBuilder.upper(option.get("rateType")), rateType)
            );
            return criteriaBuilder.exists(subquery);
        };
    }

    public static Specification<FinancialProduct> hasOptionRepaymentType(String repaymentType) {
        if (repaymentType == null) {
            return unrestricted();
        }
        return (root, query, criteriaBuilder) -> {
            Subquery<Long> subquery = query.subquery(Long.class);
            Root<FinancialProductOption> option = subquery.from(FinancialProductOption.class);
            subquery.select(option.get("id"));
            subquery.where(
                    criteriaBuilder.equal(option.get("product"), root),
                    criteriaBuilder.equal(
                            criteriaBuilder.upper(option.get("repaymentType")),
                            repaymentType
                    )
            );
            return criteriaBuilder.exists(subquery);
        };
    }

    public static Specification<FinancialProduct> hasBenefitCategory(String category) {
        if (category == null) {
            return unrestricted();
        }
        return (root, query, criteriaBuilder) -> {
            Expression<String> benefits = root.get("benefits").as(String.class);
            String pattern = "%\"CATEGORY\"%\"" + category + "\"%";
            return criteriaBuilder.like(criteriaBuilder.upper(benefits), pattern);
        };
    }

    private static Specification<FinancialProduct> unrestricted() {
        return (root, query, criteriaBuilder) -> criteriaBuilder.conjunction();
    }
}
