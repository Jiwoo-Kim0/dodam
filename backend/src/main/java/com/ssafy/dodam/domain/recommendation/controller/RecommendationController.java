package com.ssafy.dodam.domain.recommendation.controller;

import com.ssafy.dodam.domain.recommendation.dto.request.GenerateRecommendationRequest;
import com.ssafy.dodam.domain.recommendation.dto.response.RecommendationEffectResponse;
import com.ssafy.dodam.domain.recommendation.dto.response.RecommendationGenerationResponse;
import com.ssafy.dodam.domain.recommendation.dto.response.RecommendationGoalImpactResponse;
import com.ssafy.dodam.domain.recommendation.dto.response.RecommendationPrioritiesResponse;
import com.ssafy.dodam.domain.recommendation.dto.response.RecommendationReasonResponse;
import com.ssafy.dodam.domain.recommendation.dto.response.RecommendationResponse;
import com.ssafy.dodam.domain.recommendation.dto.response.RecommendationSummaryResponse;
import com.ssafy.dodam.domain.recommendation.entity.RecommendationStatus;
import com.ssafy.dodam.domain.recommendation.service.RecommendationGenerationService;
import com.ssafy.dodam.domain.recommendation.service.RecommendationQueryService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.response.PageResponse;
import com.ssafy.dodam.global.security.AuthenticatedUserIdResolver;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 16 Recommendation 생성·조회(548). 상태 변경·이력은 549. 리터럴 경로(/generate, /summary, /priorities)는 {@code /{recommendationId}}보다 우선 매칭되고,
 * path variable이 Long이라 문자열은 400이다.
 */
@Validated
@RestController
@RequestMapping("/api/v1/recommendations")
public class RecommendationController {
    private static final String CODE = "^[A-Z][A-Z0-9_]{0,49}$";
    private final RecommendationGenerationService generation;
    private final RecommendationQueryService query;
    private final AuthenticatedUserIdResolver users;

    public RecommendationController(RecommendationGenerationService generation, RecommendationQueryService query,
            AuthenticatedUserIdResolver users) {
        this.generation = generation;
        this.query = query;
        this.users = users;
    }

    @PostMapping("/generate")
    public ResponseEntity<ApiResponse<RecommendationGenerationResponse>> generate(
            @Valid @RequestBody GenerateRecommendationRequest request, Authentication authentication) {
        RecommendationGenerationResponse response = generation.generate(users.resolve(authentication), request.diagnosisId(),
                request.goalId());
        return ResponseEntity.status(response.created() > 0 ? 201 : 200).body(ApiResponse.success(response));
    }

    @GetMapping
    public ApiResponse<PageResponse<RecommendationResponse>> list(
            @RequestParam(required = false) RecommendationStatus status,
            @RequestParam(required = false) @Pattern(regexp = CODE) String domain,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, Authentication authentication) {
        return ApiResponse.success(query.list(users.resolve(authentication), status, domain, page, size));
    }

    @GetMapping("/summary")
    public ApiResponse<RecommendationSummaryResponse> summary(Authentication authentication) {
        return ApiResponse.success(query.summary(users.resolve(authentication)));
    }

    @GetMapping("/priorities")
    public ApiResponse<RecommendationPrioritiesResponse> priorities(@RequestParam(required = false) @Positive Long goalId,
            Authentication authentication) {
        return ApiResponse.success(query.priorities(users.resolve(authentication), goalId));
    }

    @GetMapping("/{recommendationId}")
    public ApiResponse<RecommendationResponse> detail(@PathVariable @Positive Long recommendationId, Authentication authentication) {
        return ApiResponse.success(query.detail(users.resolve(authentication), recommendationId));
    }

    @GetMapping("/{recommendationId}/reasons")
    public ApiResponse<List<RecommendationReasonResponse>> reasons(@PathVariable @Positive Long recommendationId,
            Authentication authentication) {
        return ApiResponse.success(query.reasons(users.resolve(authentication), recommendationId));
    }

    @GetMapping("/{recommendationId}/effect")
    public ApiResponse<RecommendationEffectResponse> effect(@PathVariable @Positive Long recommendationId,
            Authentication authentication) {
        return ApiResponse.success(query.effect(users.resolve(authentication), recommendationId));
    }

    @GetMapping("/{recommendationId}/goal-impact")
    public ApiResponse<RecommendationGoalImpactResponse> goalImpact(@PathVariable @Positive Long recommendationId,
            Authentication authentication) {
        return ApiResponse.success(query.goalImpact(users.resolve(authentication), recommendationId));
    }
}
