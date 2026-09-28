package com.ssafy.dodam.domain.pattern.controller;

import com.ssafy.dodam.domain.pattern.dto.request.AnalyzePatternRequest;
import com.ssafy.dodam.domain.pattern.dto.response.AnalyzePatternResult;
import com.ssafy.dodam.domain.pattern.dto.response.CashflowPatternResponse;
import com.ssafy.dodam.domain.pattern.dto.response.PatternAnalysisResponse;
import com.ssafy.dodam.domain.pattern.dto.response.PeerComparisonResponse;
import com.ssafy.dodam.domain.pattern.dto.response.PriorityProfileResponse;
import com.ssafy.dodam.domain.pattern.dto.response.SpendingPatternResponse;
import com.ssafy.dodam.domain.pattern.service.PatternAnalysisRunner;
import com.ssafy.dodam.domain.pattern.service.PatternQueryService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.security.AuthenticatedUserIdResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 명세 3. Preference(사용자 패턴 분석 알고리즘).
 * S15P21D105-516: 분석 실행. S15P21D105-517: 결과·소비·자금운용·우선순위 프로필 조회. S15P21D105-518: 군집 메타데이터·Fallback(응답의 clusterProfile).
 *
 * <p>`months`는 서비스({@code PatternWindow.resolveMonths})가 1~12 범위를 검증한다. 형식이 숫자가 아니면 공통 처리로 400이다.
 */
@RestController
@RequestMapping("/api/v1/patterns")
public class PatternController {

    private final PatternAnalysisRunner runner;
    private final PatternQueryService query;
    private final AuthenticatedUserIdResolver users;

    public PatternController(PatternAnalysisRunner runner, PatternQueryService query, AuthenticatedUserIdResolver users) {
        this.runner = runner;
        this.query = query;
        this.users = users;
    }

    @PostMapping("/analyze")
    public ResponseEntity<ApiResponse<AnalyzePatternResult>> analyze(
            @RequestBody(required = false) AnalyzePatternRequest request, Authentication authentication) {
        AnalyzePatternResult result = runner.run(users.resolve(authentication),
                request == null ? AnalyzePatternRequest.defaults() : request);
        return ResponseEntity.status(result.cached() ? 200 : 201).body(ApiResponse.success(result));
    }

    @GetMapping("/me")
    public ApiResponse<PatternAnalysisResponse> latest(Authentication authentication) {
        return ApiResponse.success(query.getLatest(users.resolve(authentication)));
    }

    @GetMapping("/me/spending")
    public ApiResponse<SpendingPatternResponse> spending(
            @RequestParam(required = false) Integer months, Authentication authentication) {
        return ApiResponse.success(query.getSpendingPattern(users.resolve(authentication), months));
    }

    @GetMapping("/me/cashflow")
    public ApiResponse<CashflowPatternResponse> cashflow(
            @RequestParam(required = false) Integer months, Authentication authentication) {
        return ApiResponse.success(query.getCashflowPattern(users.resolve(authentication), months));
    }

    @GetMapping("/me/priority-profile")
    public ApiResponse<PriorityProfileResponse> priorityProfile(Authentication authentication) {
        return ApiResponse.success(query.getPriorityProfile(users.resolve(authentication)));
    }

    /**
     * 632 동년배 비교. 최근 완료 분석에 저장된 값을 축별로 돌려준다. {@code dimension}은 AGE_BAND / GENDER / AGE_BAND_GENDER(기본) /
     * CLUSTER_AGE_BAND. 분석이 없으면 404, 군집 미배정·인구통계 없음·표본 부족은 200 + {@code unavailableReason}이다.
     */
    @GetMapping("/me/peer-comparison")
    public ApiResponse<PeerComparisonResponse> peerComparison(
            @RequestParam(required = false) String dimension, Authentication authentication) {
        return ApiResponse.success(query.getPeerComparison(users.resolve(authentication), dimension));
    }
}
