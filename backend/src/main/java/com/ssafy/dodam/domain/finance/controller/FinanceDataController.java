package com.ssafy.dodam.domain.finance.controller;

import com.ssafy.dodam.domain.finance.dto.FinanceConnectionResponse;
import com.ssafy.dodam.domain.finance.dto.FinanceMemberResponse;
import com.ssafy.dodam.domain.finance.dto.FinanceSyncLatestResponse;
import com.ssafy.dodam.domain.finance.dto.FinanceSyncRequest;
import com.ssafy.dodam.domain.finance.dto.FinanceSyncResponse;
import com.ssafy.dodam.domain.finance.dto.FinanceSyncStatusResponse;
import com.ssafy.dodam.domain.finance.service.FinanceConnectionService;
import com.ssafy.dodam.domain.finance.service.FinanceSyncService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.security.AuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 명세 4. Finance Data (S15P21D105-498 연결·회원 관리, S15P21D105-499 동기화).
 */
@RestController
@RequestMapping("/api/v1/finance-data")
public class FinanceDataController {

    private final FinanceConnectionService financeConnectionService;
    private final FinanceSyncService financeSyncService;

    public FinanceDataController(
            FinanceConnectionService financeConnectionService,
            FinanceSyncService financeSyncService
    ) {
        this.financeConnectionService = financeConnectionService;
        this.financeSyncService = financeSyncService;
    }

    // ---------- S15P21D105-498 금융망 연결·회원 관리 ----------

    @GetMapping("/connection")
    public ApiResponse<FinanceConnectionResponse> getConnection(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.success(financeConnectionService.getConnection(principal.userId()));
    }

    @PostMapping("/connection")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FinanceConnectionResponse> connect(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.success(financeConnectionService.connect(principal.userId()));
    }

    @DeleteMapping("/connection")
    public ApiResponse<FinanceConnectionResponse> disconnect(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.success(financeConnectionService.disconnect(principal.userId()));
    }

    @GetMapping("/member")
    public ApiResponse<FinanceMemberResponse> getMember(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.success(financeConnectionService.getMember(principal.userId()));
    }

    // ---------- S15P21D105-499 금융 데이터 동기화 ----------

    /**
     * Body는 선택이다 (`{}` 또는 본문 없음 → scope ALL).
     */
    @PostMapping("/sync")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FinanceSyncResponse> sync(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestBody(required = false) FinanceSyncRequest request
    ) {
        return ApiResponse.success(financeSyncService.sync(principal.userId(), request));
    }

    @GetMapping("/sync/status")
    public ApiResponse<FinanceSyncStatusResponse> getSyncStatus(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.success(financeSyncService.getStatus(principal.userId()));
    }

    @GetMapping("/sync/latest")
    public ApiResponse<FinanceSyncLatestResponse> getLatestSync(@AuthenticationPrincipal AuthPrincipal principal) {
        return ApiResponse.success(financeSyncService.getLatest(principal.userId()));
    }
}
