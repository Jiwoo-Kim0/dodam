package com.ssafy.dodam.domain.product.service;

import static com.ssafy.dodam.domain.product.repository.FinancialProductSpecifications.hasBenefitCategory;
import static com.ssafy.dodam.domain.product.repository.FinancialProductSpecifications.hasCompanyCode;
import static com.ssafy.dodam.domain.product.repository.FinancialProductSpecifications.hasCompanyId;
import static com.ssafy.dodam.domain.product.repository.FinancialProductSpecifications.hasLoanType;
import static com.ssafy.dodam.domain.product.repository.FinancialProductSpecifications.hasLoanTypeIn;
import static com.ssafy.dodam.domain.product.repository.FinancialProductSpecifications.hasMaxRateAtLeast;
import static com.ssafy.dodam.domain.product.repository.FinancialProductSpecifications.hasMaxRateAtMost;
import static com.ssafy.dodam.domain.product.repository.FinancialProductSpecifications.hasMinRateAtLeast;
import static com.ssafy.dodam.domain.product.repository.FinancialProductSpecifications.hasOptionRateType;
import static com.ssafy.dodam.domain.product.repository.FinancialProductSpecifications.hasOptionRepaymentType;
import static com.ssafy.dodam.domain.product.repository.FinancialProductSpecifications.hasOptionTerm;
import static com.ssafy.dodam.domain.product.repository.FinancialProductSpecifications.hasSavingType;
import static com.ssafy.dodam.domain.product.repository.FinancialProductSpecifications.hasType;

import com.ssafy.dodam.domain.product.dto.response.CardProductDetailResponse;
import com.ssafy.dodam.domain.product.dto.response.CardProductResponse;
import com.ssafy.dodam.domain.product.dto.response.DepositProductDetailResponse;
import com.ssafy.dodam.domain.product.dto.response.DepositProductResponse;
import com.ssafy.dodam.domain.product.dto.response.FinancialCompanyResponse;
import com.ssafy.dodam.domain.product.dto.response.LoanProductDetailResponse;
import com.ssafy.dodam.domain.product.dto.response.LoanProductResponse;
import com.ssafy.dodam.domain.product.dto.response.SavingsProductDetailResponse;
import com.ssafy.dodam.domain.product.dto.response.SavingsProductResponse;
import com.ssafy.dodam.domain.product.entity.FinancialCompany;
import com.ssafy.dodam.domain.product.entity.FinancialProduct;
import com.ssafy.dodam.domain.product.entity.ProductType;
import com.ssafy.dodam.domain.product.repository.FinancialCompanyRepository;
import com.ssafy.dodam.domain.product.repository.FinancialProductRepository;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.response.PageResponse;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Set;
import java.util.function.Function;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@Transactional(readOnly = true)
public class ProductQueryService {

    private static final Sort COMPANY_SORT = Sort.by(
            Sort.Order.asc("companyName"),
            Sort.Order.asc("id")
    );
    private static final Sort PRODUCT_SORT = Sort.by(
            Sort.Order.asc("company.companyName"),
            Sort.Order.asc("productName"),
            Sort.Order.asc("id")
    );

    private static final Set<String> PERSONAL_LOAN_TYPES = Set.of(
            "PERSONAL_CREDIT", "SALARY_CREDIT", "PENSION_CREDIT", "PUBLIC_OFFICIAL",
            "MILITARY_CREDIT", "MILITARY_PENSION", "VIP_CREDIT", "MEDICAL_PROFESSIONAL",
            "POLICY_CREDIT", "VETERAN_POLICY", "MID_CREDIT", "MID_CREDIT_POLICY",
            "MID_CREDIT_REFINANCE", "EMERGENCY_CREDIT", "MID_CREDIT_EMERGENCY",
            "MID_EMERGENCY_REFINANCE", "CREDIT_LINE", "CREDIT_REFINANCE",
            "LINE_REFINANCE", "JOINT_CREDIT", "LIVING_EXPENSE", "SOHO_CREDIT",
            "SOHO_REFINANCE"
    );
    private static final Set<String> MORTGAGE_LOAN_TYPES = Set.of(
            "MORTGAGE", "MORTGAGE_REFINANCE", "POLICY_MORTGAGE", "POLICY_HOUSING",
            "SOHO_MORTGAGE"
    );
    private static final Set<String> JEONSE_LOAN_TYPES = Set.of(
            "JEONSE", "LEASE_HF", "LEASE_SGI", "LEASE_YOUTH", "LEASE_REFI_HF",
            "LEASE_REFI_SGI", "LEASE_REFI_YOUTH"
    );

    private final FinancialCompanyRepository financialCompanyRepository;
    private final FinancialProductRepository financialProductRepository;

    public ProductQueryService(
            FinancialCompanyRepository financialCompanyRepository,
            FinancialProductRepository financialProductRepository
    ) {
        this.financialCompanyRepository = financialCompanyRepository;
        this.financialProductRepository = financialProductRepository;
    }

    public PageResponse<FinancialCompanyResponse> getCompanies(
            String name,
            int page,
            int size
    ) {
        Pageable pageable = PageRequest.of(page, size, COMPANY_SORT);
        String normalizedName = normalizeText(name);
        Page<FinancialCompany> companies = normalizedName == null
                ? financialCompanyRepository.findAll(pageable)
                : financialCompanyRepository.findByCompanyNameContainingIgnoreCase(
                        normalizedName,
                        pageable
                );
        return PageResponse.from(companies, FinancialCompanyResponse::from);
    }

    public PageResponse<DepositProductResponse> getDeposits(
            Long companyId,
            Integer term,
            BigDecimal minRate,
            int page,
            int size
    ) {
        Specification<FinancialProduct> specification = hasType(ProductType.DEPOSIT)
                .and(hasCompanyId(companyId))
                .and(hasOptionTerm(term))
                .and(hasMaxRateAtLeast(minRate));
        return findProducts(specification, page, size, DepositProductResponse::from);
    }

    public DepositProductDetailResponse getDeposit(Long productId) {
        return DepositProductDetailResponse.from(findProduct(productId, ProductType.DEPOSIT));
    }

    public PageResponse<SavingsProductResponse> getSavings(
            Long companyId,
            Integer term,
            BigDecimal minRate,
            String savingType,
            int page,
            int size
    ) {
        Specification<FinancialProduct> specification = hasType(ProductType.SAVINGS)
                .and(hasCompanyId(companyId))
                .and(hasOptionTerm(term))
                .and(hasMaxRateAtLeast(minRate))
                .and(hasSavingType(normalizeCode(savingType)));
        return findProducts(specification, page, size, SavingsProductResponse::from);
    }

    public SavingsProductDetailResponse getSaving(Long productId) {
        return SavingsProductDetailResponse.from(findProduct(productId, ProductType.SAVINGS));
    }

    public PageResponse<LoanProductResponse> getPersonalLoans(
            Long companyId,
            String rateType,
            BigDecimal minRate,
            BigDecimal maxRate,
            int page,
            int size
    ) {
        validateRateRange(minRate, maxRate);
        Specification<FinancialProduct> specification = hasType(ProductType.LOAN)
                .and(hasLoanTypeIn(PERSONAL_LOAN_TYPES))
                .and(hasCompanyId(companyId))
                .and(hasOptionRateType(normalizeCode(rateType)))
                .and(hasMinRateAtLeast(minRate))
                .and(hasMaxRateAtMost(maxRate));
        return findProducts(specification, page, size, LoanProductResponse::from);
    }

    public PageResponse<LoanProductResponse> getMortgageLoans(
            Long companyId,
            String mortgageType,
            String repaymentType,
            int page,
            int size
    ) {
        Specification<FinancialProduct> specification = hasType(ProductType.LOAN)
                .and(hasLoanTypeIn(MORTGAGE_LOAN_TYPES))
                .and(hasCompanyId(companyId))
                .and(hasLoanType(normalizeCode(mortgageType)))
                .and(hasOptionRepaymentType(normalizeCode(repaymentType)));
        return findProducts(specification, page, size, LoanProductResponse::from);
    }

    public PageResponse<LoanProductResponse> getJeonseLoans(
            Long companyId,
            String repaymentType,
            int page,
            int size
    ) {
        Specification<FinancialProduct> specification = hasType(ProductType.LOAN)
                .and(hasLoanTypeIn(JEONSE_LOAN_TYPES))
                .and(hasCompanyId(companyId))
                .and(hasOptionRepaymentType(normalizeCode(repaymentType)));
        return findProducts(specification, page, size, LoanProductResponse::from);
    }

    public LoanProductDetailResponse getLoan(Long productId) {
        return LoanProductDetailResponse.from(findProduct(productId, ProductType.LOAN));
    }

    public PageResponse<CardProductResponse> getCards(
            String issuerCode,
            String category,
            int page,
            int size
    ) {
        Specification<FinancialProduct> specification = hasType(ProductType.CARD)
                .and(hasCompanyCode(normalizeCode(issuerCode)))
                .and(hasBenefitCategory(normalizeCode(category)));
        return findProducts(specification, page, size, CardProductResponse::from);
    }

    public CardProductDetailResponse getCard(Long productId) {
        return CardProductDetailResponse.from(findProduct(productId, ProductType.CARD));
    }

    private <T> PageResponse<T> findProducts(
            Specification<FinancialProduct> specification,
            int page,
            int size,
            Function<FinancialProduct, T> mapper
    ) {
        Page<FinancialProduct> products = financialProductRepository.findAll(
                specification,
                PageRequest.of(page, size, PRODUCT_SORT)
        );
        return PageResponse.from(products, mapper);
    }

    private FinancialProduct findProduct(Long productId, ProductType productType) {
        return financialProductRepository.findByIdAndProductType(productId, productType)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "해당 금융상품을 찾을 수 없습니다."
                ));
    }

    private void validateRateRange(BigDecimal minRate, BigDecimal maxRate) {
        if (minRate != null && maxRate != null && minRate.compareTo(maxRate) > 0) {
            throw new InvalidRequestException("최소 금리는 최대 금리보다 클 수 없습니다.");
        }
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String normalizeCode(String value) {
        String normalized = normalizeText(value);
        return normalized == null ? null : normalized.toUpperCase(Locale.ROOT);
    }
}
