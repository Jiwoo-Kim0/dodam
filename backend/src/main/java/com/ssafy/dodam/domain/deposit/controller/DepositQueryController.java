package com.ssafy.dodam.domain.deposit.controller;

import com.ssafy.dodam.domain.deposit.dto.response.DepositDetailResponse;
import com.ssafy.dodam.domain.deposit.service.DepositQueryService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.security.AuthenticatedUserIdResolver;
import jakarta.validation.constraints.Pattern;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/deposits")
public class DepositQueryController {
    /**
     * 계좌번호는 숫자만 받는다. 은행별 자릿수 차이를 흡수하도록 범위를 넓게 두고 실제 소유 여부는
     * HMAC 해시 조회로 판별한다. 대출·마이너스통장 컨트롤러와 같은 패턴이다.
     */
    static final String ACCOUNT_NO_PATTERN = "^[0-9]{10,20}$";

    private final DepositQueryService service;
    private final AuthenticatedUserIdResolver userIdResolver;

    public DepositQueryController(DepositQueryService service, AuthenticatedUserIdResolver userIdResolver) {
        this.service = service;
        this.userIdResolver = userIdResolver;
    }

    @GetMapping("/{accountNo}")
    public ApiResponse<DepositDetailResponse> detail(
            @PathVariable @Pattern(regexp = ACCOUNT_NO_PATTERN) String accountNo,
            Authentication authentication
    ) {
        return ApiResponse.success(service.detail(userIdResolver.resolve(authentication), accountNo));
    }
}
