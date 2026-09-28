package com.ssafy.dodam.domain.cashflow.controller;

import com.ssafy.dodam.domain.cashflow.dto.request.CashflowForecastRequest;
import com.ssafy.dodam.domain.cashflow.dto.response.CashflowForecastRunResponse;
import com.ssafy.dodam.domain.cashflow.service.CashflowForecastRunner;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.security.AuthenticatedUserIdResolver;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/cashflow")
public class CashflowCommandController {

    private final CashflowForecastRunner forecastRunner;
    private final AuthenticatedUserIdResolver authenticatedUserIdResolver;

    public CashflowCommandController(
            CashflowForecastRunner forecastRunner,
            AuthenticatedUserIdResolver authenticatedUserIdResolver
    ) {
        this.forecastRunner = forecastRunner;
        this.authenticatedUserIdResolver = authenticatedUserIdResolver;
    }

    /**
     * 현금흐름 예측을 실행한다.
     *
     * <p>본문 세 값이 모두 선택이라 본문 자체를 생략할 수 있다. 캐시·멱등 정책상 같은 요청이면 새
     * 예측을 만들지 않고 기존 예측을 돌려주므로(S15P21D105-540) 201이 아니라 200으로 응답한다.
     * 생성 여부는 응답의 `cached`로 구분한다.
     */
    @PostMapping("/forecasts")
    public ApiResponse<CashflowForecastRunResponse> runForecast(
            @Valid @RequestBody(required = false) CashflowForecastRequest request,
            Authentication authentication
    ) {
        Long userId = authenticatedUserIdResolver.resolve(authentication);
        CashflowForecastRequest resolved = CashflowForecastRequest.emptyIfNull(request);
        return ApiResponse.success(CashflowForecastRunResponse.from(forecastRunner.run(
                userId,
                resolved.baseDate(),
                resolved.horizonDays(),
                resolved.forceRefreshOrFalse()
        )));
    }
}
