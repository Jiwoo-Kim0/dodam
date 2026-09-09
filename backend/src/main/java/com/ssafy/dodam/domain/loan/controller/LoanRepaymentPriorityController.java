package com.ssafy.dodam.domain.loan.controller;

import com.ssafy.dodam.domain.loan.dto.response.LoanRepaymentPriorityResponse;
import com.ssafy.dodam.domain.loan.service.LoanRepaymentPriorityService;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.security.AuthenticatedUserIdResolver;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/loans")
public class LoanRepaymentPriorityController {
    private final LoanRepaymentPriorityService service;
    private final AuthenticatedUserIdResolver userIdResolver;

    public LoanRepaymentPriorityController(
            LoanRepaymentPriorityService service, AuthenticatedUserIdResolver userIdResolver
    ) {
        this.service = service;
        this.userIdResolver = userIdResolver;
    }

    @GetMapping("/repayment-priority")
    public ApiResponse<LoanRepaymentPriorityResponse> repaymentPriority(
            @RequestParam Map<String, String> parameters, Authentication authentication
    ) {
        rejectGoalId(parameters);
        return ApiResponse.success(service.repaymentPriority(userIdResolver.resolve(authentication)));
    }

    /**
     * TODO(S15P21D105-533): Goal 스키마와 도메인이 준비되면 goalId 기반 우선순위 지원 여부를 다시 정한다.
     *
     * <p>명세에 있는 파라미터를 조용히 무시하지 않고 기능 미지원임을 400으로 알린다. `goal` 테이블
     * 마이그레이션과 Goal 도메인이 없어 목표의 존재 여부조차 조회할 수 없으므로, 존재를 확인한 것처럼
     * 404를 반환하지 않는다. 형식 오류와 0·음수도 같은 400으로 처리하기 위해 값을 해석하지 않고
     * 파라미터가 있는지만 본다. 목표를 반영한 우선순위는 명세 16의 recommendations/priorities가 소유한다.
     */
    private void rejectGoalId(Map<String, String> parameters) {
        if (parameters.containsKey("goalId")) {
            throw new InvalidRequestException("goalId를 사용한 상환 우선순위는 아직 지원하지 않습니다.");
        }
    }
}
