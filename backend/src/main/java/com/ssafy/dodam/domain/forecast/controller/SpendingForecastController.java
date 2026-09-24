package com.ssafy.dodam.domain.forecast.controller;

import com.ssafy.dodam.domain.forecast.dto.response.SpendingForecastResponse;
import com.ssafy.dodam.domain.forecast.service.SpendingForecastQueryService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.security.AuthenticatedUserIdResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 월별 변동 소비 예측(S15P21D105-706). 복합어 기준 경로는 {@code /api/v1/deposit-savings} 전례를 따른다.
 *
 * <p>경로에 {@code /latest}를 붙이지 않는다. 저장된 최신 결과를 돌려주는 것이 아니라 <b>요청할 때 계산</b>하기 때문이다.
 * 결과를 저장하는 실행 API가 생기면 그때 {@code /latest}를 따로 둔다.
 *
 * <p>조회 조건에 인증 사용자 id가 들어가므로 타인의 거래는 애초에 읽히지 않는다.
 */
@RestController
@RequestMapping("/api/v1/spending-forecasts")
public class SpendingForecastController {

    private final SpendingForecastQueryService query;
    private final AuthenticatedUserIdResolver users;

    public SpendingForecastController(SpendingForecastQueryService query, AuthenticatedUserIdResolver users) {
        this.query = query;
        this.users = users;
    }

    /** @param horizonMonths 예측할 달 수. 비어 있으면 3, 1~12 밖이면 400 */
    @GetMapping
    public ResponseEntity<ApiResponse<SpendingForecastResponse>> forecast(
            Authentication authentication,
            @RequestParam(required = false) Integer horizonMonths) {
        Long userId = users.resolve(authentication);
        return ResponseEntity.ok(ApiResponse.success(query.forecast(userId, horizonMonths)));
    }
}
