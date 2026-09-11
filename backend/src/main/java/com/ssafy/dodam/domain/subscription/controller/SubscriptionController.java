package com.ssafy.dodam.domain.subscription.controller;

import com.ssafy.dodam.domain.subscription.dto.request.ChangeSubscriptionStatusRequest;
import com.ssafy.dodam.domain.subscription.dto.request.UpdateSubscriptionRequest;
import com.ssafy.dodam.domain.subscription.dto.response.RecurringPaymentCandidateResponse;
import com.ssafy.dodam.domain.subscription.dto.response.SubscriptionCategoryResponse;
import com.ssafy.dodam.domain.subscription.dto.response.SubscriptionDetailResponse;
import com.ssafy.dodam.domain.subscription.dto.response.SubscriptionPaymentHistoryResponse;
import com.ssafy.dodam.domain.subscription.dto.response.SubscriptionResponse;
import com.ssafy.dodam.domain.subscription.dto.response.SubscriptionServiceResponse;
import com.ssafy.dodam.domain.subscription.dto.response.SubscriptionSummaryResponse;
import com.ssafy.dodam.domain.subscription.service.SubscriptionDiscoveryService;
import com.ssafy.dodam.domain.subscription.service.SubscriptionManagementService;
import com.ssafy.dodam.domain.subscription.service.SubscriptionService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 명세 7. Subscription 전체(10개).
 * S15P21D105-512 목록·상세·이력·요약, 513 반복결제 후보·서비스 카탈로그·카테고리, 514 수정·정지/재개·취소.
 */
@Validated
@RestController
@RequestMapping("/api/v1/subscriptions")
public class SubscriptionController {

    /** 코드 계열 파라미터. 빈 값은 "전체"로 서비스가 해석한다. */
    private static final String CODE_PATTERN = "^\\s*$|^[A-Za-z0-9_]+$";
    /** 정기결제 ID. seed(`SUB-U0001-01`)와 금융망 식별자를 모두 받도록 영숫자·`-`·`_`·`:`·`.`만 허용한다. */
    private static final String ID_PATTERN = "^[A-Za-z0-9_\\-:.]+$";

    private final SubscriptionService subscriptionService;
    private final SubscriptionDiscoveryService discoveryService;
    private final SubscriptionManagementService managementService;

    public SubscriptionController(
            SubscriptionService subscriptionService,
            SubscriptionDiscoveryService discoveryService,
            SubscriptionManagementService managementService
    ) {
        this.subscriptionService = subscriptionService;
        this.discoveryService = discoveryService;
        this.managementService = managementService;
    }

    // ---------- S15P21D105-512 목록·상세·이력·요약 ----------

    @GetMapping
    public ApiResponse<PageResponse<SubscriptionResponse>> getSubscriptions(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) @Size(max = 30) @Pattern(regexp = CODE_PATTERN) String status,
            @RequestParam(required = false) @Size(max = 50) @Pattern(regexp = CODE_PATTERN) String category,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(subscriptionService.getSubscriptions(principal.userId(), status, category, page, size));
    }

    @GetMapping("/summary")
    public ApiResponse<SubscriptionSummaryResponse> getSummary(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.success(subscriptionService.getSummary(principal.userId()));
    }

    @GetMapping("/{subscriptionId}")
    public ApiResponse<SubscriptionDetailResponse> getSubscription(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Size(max = 100) @Pattern(regexp = ID_PATTERN) String subscriptionId
    ) {
        return ApiResponse.success(subscriptionService.getSubscription(principal.userId(), subscriptionId));
    }

    @GetMapping("/{subscriptionId}/history")
    public ApiResponse<PageResponse<SubscriptionPaymentHistoryResponse>> getHistory(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Size(max = 100) @Pattern(regexp = ID_PATTERN) String subscriptionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(
                subscriptionService.getHistory(principal.userId(), subscriptionId, startDate, endDate, page, size));
    }

    // ---------- S15P21D105-513 반복결제 후보·서비스 카탈로그·카테고리 ----------

    /**
     * @param minOccurrences 후보로 볼 최소 결제 횟수(기본 3, 2~12)
     * @param lookbackMonths 거슬러 볼 개월 수(기본 6, 1~12)
     */
    @GetMapping("/candidates")
    public ApiResponse<List<RecurringPaymentCandidateResponse>> getCandidates(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam(required = false) @Min(2) @Max(12) Integer minOccurrences,
            @RequestParam(required = false) @Min(1) @Max(12) Integer lookbackMonths
    ) {
        return ApiResponse.success(discoveryService.getCandidates(principal.userId(), minOccurrences, lookbackMonths));
    }

    @GetMapping("/services")
    public ApiResponse<PageResponse<SubscriptionServiceResponse>> getServices(
            @RequestParam(required = false) @Size(max = 50) @Pattern(regexp = CODE_PATTERN) String category,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return ApiResponse.success(discoveryService.getServices(category, page, size));
    }

    @GetMapping("/categories")
    public ApiResponse<List<SubscriptionCategoryResponse>> getCategories(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.success(discoveryService.getCategories(principal.userId()));
    }

    // ---------- S15P21D105-514 수정·정지/재개·취소 ----------

    @PatchMapping("/{subscriptionId}")
    public ApiResponse<SubscriptionResponse> update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Size(max = 100) @Pattern(regexp = ID_PATTERN) String subscriptionId,
            @Valid @RequestBody UpdateSubscriptionRequest request
    ) {
        return ApiResponse.success(managementService.update(principal.userId(), subscriptionId, request));
    }

    @PatchMapping("/{subscriptionId}/status")
    public ApiResponse<SubscriptionResponse> changeStatus(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Size(max = 100) @Pattern(regexp = ID_PATTERN) String subscriptionId,
            @Valid @RequestBody ChangeSubscriptionStatusRequest request
    ) {
        return ApiResponse.success(managementService.changeStatus(principal.userId(), subscriptionId, request));
    }

    @DeleteMapping("/{subscriptionId}")
    public ApiResponse<Void> cancel(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable @Size(max = 100) @Pattern(regexp = ID_PATTERN) String subscriptionId
    ) {
        managementService.cancel(principal.userId(), subscriptionId);
        return ApiResponse.success(null);
    }
}
