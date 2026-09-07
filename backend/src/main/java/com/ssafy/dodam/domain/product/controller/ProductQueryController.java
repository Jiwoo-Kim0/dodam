package com.ssafy.dodam.domain.product.controller;

import com.ssafy.dodam.domain.product.dto.response.CardProductDetailResponse;
import com.ssafy.dodam.domain.product.dto.response.CardProductResponse;
import com.ssafy.dodam.domain.product.dto.response.DepositProductDetailResponse;
import com.ssafy.dodam.domain.product.dto.response.DepositProductResponse;
import com.ssafy.dodam.domain.product.dto.response.FinancialCompanyResponse;
import com.ssafy.dodam.domain.product.dto.response.LoanProductDetailResponse;
import com.ssafy.dodam.domain.product.dto.response.LoanProductResponse;
import com.ssafy.dodam.domain.product.dto.response.SavingsProductDetailResponse;
import com.ssafy.dodam.domain.product.dto.response.SavingsProductResponse;
import com.ssafy.dodam.domain.product.service.ProductQueryService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.response.PageResponse;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/products")
public class ProductQueryController {

    private static final String CODE_PATTERN = "^\\s*$|[A-Za-z0-9_]+$";

    private final ProductQueryService productQueryService;

    public ProductQueryController(ProductQueryService productQueryService) {
        this.productQueryService = productQueryService;
    }

    @GetMapping("/companies")
    public ApiResponse<PageResponse<FinancialCompanyResponse>> getCompanies(
            @RequestParam(required = false) @Size(max = 150) String name,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(productQueryService.getCompanies(name, page, size));
    }

    @GetMapping("/deposits")
    public ApiResponse<PageResponse<DepositProductResponse>> getDeposits(
            @RequestParam(required = false) @Positive Long companyId,
            @RequestParam(required = false) @Positive Integer term,
            @RequestParam(required = false) @DecimalMin("0.0") BigDecimal minRate,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(
                productQueryService.getDeposits(companyId, term, minRate, page, size)
        );
    }

    @GetMapping("/deposits/{productId}")
    public ApiResponse<DepositProductDetailResponse> getDeposit(
            @PathVariable @Positive Long productId
    ) {
        return ApiResponse.success(productQueryService.getDeposit(productId));
    }

    @GetMapping("/savings")
    public ApiResponse<PageResponse<SavingsProductResponse>> getSavings(
            @RequestParam(required = false) @Positive Long companyId,
            @RequestParam(required = false) @Positive Integer term,
            @RequestParam(required = false) @DecimalMin("0.0") BigDecimal minRate,
            @RequestParam(required = false) @Size(max = 30)
            @Pattern(regexp = CODE_PATTERN) String savingType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(productQueryService.getSavings(
                companyId,
                term,
                minRate,
                savingType,
                page,
                size
        ));
    }

    @GetMapping("/savings/{productId}")
    public ApiResponse<SavingsProductDetailResponse> getSaving(
            @PathVariable @Positive Long productId
    ) {
        return ApiResponse.success(productQueryService.getSaving(productId));
    }

    @GetMapping("/loans/personal")
    public ApiResponse<PageResponse<LoanProductResponse>> getPersonalLoans(
            @RequestParam(required = false) @Positive Long companyId,
            @RequestParam(required = false) @Size(max = 30)
            @Pattern(regexp = CODE_PATTERN) String rateType,
            @RequestParam(required = false) @DecimalMin("0.0") BigDecimal minRate,
            @RequestParam(required = false) @DecimalMin("0.0") BigDecimal maxRate,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(productQueryService.getPersonalLoans(
                companyId,
                rateType,
                minRate,
                maxRate,
                page,
                size
        ));
    }

    @GetMapping("/loans/mortgage")
    public ApiResponse<PageResponse<LoanProductResponse>> getMortgageLoans(
            @RequestParam(required = false) @Positive Long companyId,
            @RequestParam(required = false) @Size(max = 30)
            @Pattern(regexp = CODE_PATTERN) String mortgageType,
            @RequestParam(required = false) @Size(max = 30)
            @Pattern(regexp = CODE_PATTERN) String repaymentType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(productQueryService.getMortgageLoans(
                companyId,
                mortgageType,
                repaymentType,
                page,
                size
        ));
    }

    @GetMapping("/loans/jeonse")
    public ApiResponse<PageResponse<LoanProductResponse>> getJeonseLoans(
            @RequestParam(required = false) @Positive Long companyId,
            @RequestParam(required = false) @Size(max = 30)
            @Pattern(regexp = CODE_PATTERN) String repaymentType,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(productQueryService.getJeonseLoans(
                companyId,
                repaymentType,
                page,
                size
        ));
    }

    @GetMapping("/loans/{productId}")
    public ApiResponse<LoanProductDetailResponse> getLoan(
            @PathVariable @Positive Long productId
    ) {
        return ApiResponse.success(productQueryService.getLoan(productId));
    }

    @GetMapping("/cards")
    public ApiResponse<PageResponse<CardProductResponse>> getCards(
            @RequestParam(required = false) @Size(max = 30)
            @Pattern(regexp = CODE_PATTERN) String issuerCode,
            @RequestParam(required = false) @Size(max = 50)
            @Pattern(regexp = CODE_PATTERN) String category,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(
                productQueryService.getCards(issuerCode, category, page, size)
        );
    }

    @GetMapping("/cards/{productId}")
    public ApiResponse<CardProductDetailResponse> getCard(
            @PathVariable @Positive Long productId
    ) {
        return ApiResponse.success(productQueryService.getCard(productId));
    }
}
