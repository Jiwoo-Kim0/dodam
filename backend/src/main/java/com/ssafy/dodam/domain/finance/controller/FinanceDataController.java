package com.ssafy.dodam.domain.finance.controller;

import com.ssafy.dodam.domain.finance.dto.FinanceConnectionResponse;
import com.ssafy.dodam.domain.finance.dto.FinanceMemberResponse;
import com.ssafy.dodam.domain.finance.service.FinanceConnectionService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.security.AuthPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 명세 4. Finance Data (S15P21D105-498 연결·회원 관리).
 */
@RestController
@RequestMapping("/api/v1/finance-data")
public class FinanceDataController {

    private final FinanceConnectionService financeConnectionService;

    public FinanceDataController(FinanceConnectionService financeConnectionService) {
        this.financeConnectionService = financeConnectionService;
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
}
