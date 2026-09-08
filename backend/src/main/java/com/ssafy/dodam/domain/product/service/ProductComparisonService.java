package com.ssafy.dodam.domain.product.service;

import com.ssafy.dodam.domain.product.dto.response.CardCompareProductResponse;
import com.ssafy.dodam.domain.product.dto.response.DepositSavingsCompareProductResponse;
import com.ssafy.dodam.domain.product.dto.response.LoanCompareProductResponse;
import com.ssafy.dodam.domain.product.dto.response.ProductComparisonResponse;
import com.ssafy.dodam.domain.product.entity.FinancialProduct;
import com.ssafy.dodam.domain.product.entity.ProductType;
import com.ssafy.dodam.domain.product.repository.FinancialProductRepository;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductComparisonService {

    private static final Set<ProductType> DEPOSIT_SAVINGS_TYPES =
            EnumSet.of(ProductType.DEPOSIT, ProductType.SAVINGS);

    private final FinancialProductRepository financialProductRepository;

    public ProductComparisonService(FinancialProductRepository financialProductRepository) {
        this.financialProductRepository = financialProductRepository;
    }

    public ProductComparisonResponse<DepositSavingsCompareProductResponse> compareDepositSavings(
            List<Long> productIds
    ) {
        List<FinancialProduct> products = findProducts(productIds, DEPOSIT_SAVINGS_TYPES);
        return map(products, DepositSavingsCompareProductResponse::from);
    }

    public ProductComparisonResponse<LoanCompareProductResponse> compareLoans(
            List<Long> productIds
    ) {
        List<FinancialProduct> products = findProducts(productIds, Set.of(ProductType.LOAN));
        return map(products, LoanCompareProductResponse::from);
    }

    public ProductComparisonResponse<CardCompareProductResponse> compareCards(
            List<Long> productIds
    ) {
        List<FinancialProduct> products = findProducts(productIds, Set.of(ProductType.CARD));
        return map(products, CardCompareProductResponse::from);
    }

    private List<FinancialProduct> findProducts(
            List<Long> productIds,
            Set<ProductType> allowedTypes
    ) {
        if (new HashSet<>(productIds).size() != productIds.size()) {
            throw new InvalidRequestException("비교할 상품 ID는 중복될 수 없습니다.");
        }

        List<FinancialProduct> found = financialProductRepository.findDetailsByIdIn(productIds);
        if (found.size() != productIds.size()) {
            throw new ResourceNotFoundException("비교할 금융상품 중 존재하지 않는 상품이 있습니다.");
        }

        Map<Long, FinancialProduct> productsById = new HashMap<>();
        for (FinancialProduct product : found) {
            if (!allowedTypes.contains(product.getProductType())) {
                throw new InvalidRequestException("비교 API와 금융상품 유형이 일치하지 않습니다.");
            }
            productsById.put(product.getId(), product);
        }
        return productIds.stream().map(productsById::get).toList();
    }

    private <T> ProductComparisonResponse<T> map(
            List<FinancialProduct> products,
            Function<FinancialProduct, T> mapper
    ) {
        return new ProductComparisonResponse<>(products.stream().map(mapper).toList());
    }
}
