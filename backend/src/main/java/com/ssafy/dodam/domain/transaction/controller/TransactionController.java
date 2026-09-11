package com.ssafy.dodam.domain.transaction.controller;

import com.ssafy.dodam.domain.transaction.dto.request.UpdateTransactionCategoryRequest;
import com.ssafy.dodam.domain.transaction.dto.request.UpdateTransactionMemoRequest;
import com.ssafy.dodam.domain.transaction.dto.request.UpdateTransactionVisibilityRequest;
import com.ssafy.dodam.domain.transaction.dto.response.TransactionCategoryResponse;
import com.ssafy.dodam.domain.transaction.dto.response.TransactionResponse;
import com.ssafy.dodam.domain.transaction.service.TransactionCommandService;
import com.ssafy.dodam.domain.transaction.service.TransactionQueryService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.response.PageResponse;
import com.ssafy.dodam.global.security.AuthPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 명세 6. Transaction 중 S15P21D105-504 범위(거래 조회·메모·카테고리·숨김).
 * 수동 거래 등록·수정·삭제와 `/spending/**` 집계는 다음 티켓에서 이어 붙인다.
 */
@Validated
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    /** 코드 계열 파라미터: 카테고리 코드·결제수단. 빈 값은 "전체"로 서비스가 해석한다. */
    private static final String CODE_PATTERN = "^\\s*$|^[A-Za-z0-9_]+$";
    /** 거래 ID: 금융망 transactionUniqueNo, seed(`TX-...`), 수동 등록 ID를 모두 허용한다. */
    private static final String TRANSACTION_ID_PATTERN = "^[A-Za-z0-9_-]+$";

    private final TransactionQueryService transactionQueryService;
    private final TransactionCommandService transactionCommandService;

    public TransactionController(
            TransactionQueryService transactionQueryService,
            TransactionCommandService transactionCommandService
    ) {
        this.transactionQueryService = transactionQueryService;
        this.transactionCommandService = transactionCommandService;
    }

    /**
     * 전체 거래 조회. `hidden`은 명세에 없는 추가 파라미터로, 생략하면 숨긴 거래를 제외하고
     * `true`면 숨긴 거래만 돌려준다(숨김 해제 화면용).
     */
    @GetMapping
    public ApiResponse<PageResponse<TransactionResponse>> getTransactions(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) @Size(max = 50)
            @Pattern(regexp = CODE_PATTERN) String category,
            @RequestParam(required = false) @Size(max = 30)
            @Pattern(regexp = CODE_PATTERN) String paymentMethod,
            @RequestParam(required = false) Boolean hidden,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(transactionQueryService.getTransactions(
                principal.userId(),
                startDate,
                endDate,
                category,
                paymentMethod,
                hidden,
                page,
                size
        ));
    }

    /**
     * 소비 카테고리 목록. 명세 6에는 없지만 카테고리 수정 Body의 `categoryId`와 목록 필터 `category`(코드)의
     * 선택지를 FE가 알아야 하므로 함께 제공한다.
     */
    @GetMapping("/categories")
    public ApiResponse<List<TransactionCategoryResponse>> getCategories() {
        return ApiResponse.success(transactionQueryService.getCategories());
    }

    @GetMapping("/{transactionId}")
    public ApiResponse<TransactionResponse> getTransaction(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Size(max = 100) @Pattern(regexp = TRANSACTION_ID_PATTERN) String transactionId
    ) {
        return ApiResponse.success(transactionQueryService.getTransaction(principal.userId(), transactionId));
    }

    @PatchMapping("/{transactionId}/category")
    public ApiResponse<TransactionResponse> updateCategory(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Size(max = 100) @Pattern(regexp = TRANSACTION_ID_PATTERN) String transactionId,
            @Valid @RequestBody UpdateTransactionCategoryRequest request
    ) {
        return ApiResponse.success(transactionCommandService.changeCategory(
                principal.userId(),
                transactionId,
                request.categoryId()
        ));
    }

    @PatchMapping("/{transactionId}/memo")
    public ApiResponse<TransactionResponse> updateMemo(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Size(max = 100) @Pattern(regexp = TRANSACTION_ID_PATTERN) String transactionId,
            @RequestBody UpdateTransactionMemoRequest request
    ) {
        return ApiResponse.success(transactionCommandService.changeMemo(
                principal.userId(),
                transactionId,
                request.memo()
        ));
    }

    @PatchMapping("/{transactionId}/visibility")
    public ApiResponse<TransactionResponse> updateVisibility(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Size(max = 100) @Pattern(regexp = TRANSACTION_ID_PATTERN) String transactionId,
            @Valid @RequestBody UpdateTransactionVisibilityRequest request
    ) {
        return ApiResponse.success(transactionCommandService.changeVisibility(
                principal.userId(),
                transactionId,
                request.hidden()
        ));
    }
}
