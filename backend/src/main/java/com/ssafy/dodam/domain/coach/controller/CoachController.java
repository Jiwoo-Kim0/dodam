package com.ssafy.dodam.domain.coach.controller;

import com.ssafy.dodam.domain.coach.dto.request.FinancialSummaryRequest;
import com.ssafy.dodam.domain.coach.dto.request.InefficiencyExplanationRequest;
import com.ssafy.dodam.domain.coach.dto.request.RecommendationExplanationRequest;
import com.ssafy.dodam.domain.coach.dto.response.CoachMessageResponse;
import com.ssafy.dodam.domain.coach.service.CoachService;
import com.ssafy.dodam.global.response.ApiResponse;
import com.ssafy.dodam.global.security.AuthenticatedUserIdResolver;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 21 AI Coach(S15P21D105-589).
 *
 * <p>세 엔드포인트 모두 인증이 필요하다({@code SecurityConfig}가 {@code /api/v1/**}를 인증 대상으로
 * 잡으므로 여기서 다시 열지 않는다). 사용자 ID는 요청 본문이 아니라 토큰에서만 온다 —
 * 본문으로 받으면 그 값이 곧 남의 데이터를 읽는 입력이 된다. 리소스 소유권은
 * {@code CoachFactAssembler}가 쿼리 조건으로 확인한다.
 *
 * <p>GET이 아니라 POST인 것은 명세 그대로다. 외부 AI 호출이 따르므로 브라우저·프록시 캐시가
 * 끼어들지 않는 편이 낫고, 요청 파라미터가 본문 JSON이라 명세의 다른 POST들과 형태가 같다.
 */
@Validated
@RestController
@RequestMapping("/api/v1/coach")
public class CoachController {

    private final CoachService coach;
    private final AuthenticatedUserIdResolver users;

    public CoachController(CoachService coach, AuthenticatedUserIdResolver users) {
        this.coach = coach;
        this.users = users;
    }

    /** 금융현황 AI 요약. 본문과 {@code diagnosisId} 모두 생략 가능하며, 생략하면 최신 완료 진단을 쓴다. */
    @PostMapping("/financial-summary")
    public ApiResponse<CoachMessageResponse> financialSummary(
            @Valid @RequestBody(required = false) FinancialSummaryRequest request, Authentication authentication) {
        Long diagnosisId = request == null ? null : request.diagnosisId();
        return ApiResponse.success(coach.financialSummary(users.resolve(authentication), diagnosisId));
    }

    /** 비효율 원인 설명. 진단과 문제가 모두 본인 것이어야 하며 아니면 404다. */
    @PostMapping("/inefficiency-explanation")
    public ApiResponse<CoachMessageResponse> inefficiency(
            @Valid @RequestBody InefficiencyExplanationRequest request, Authentication authentication) {
        return ApiResponse.success(
                coach.inefficiency(users.resolve(authentication), request.diagnosisId(), request.issueId()));
    }

    /** 추천 근거 설명. */
    @PostMapping("/recommendation-explanation")
    public ApiResponse<CoachMessageResponse> recommendation(
            @Valid @RequestBody RecommendationExplanationRequest request, Authentication authentication) {
        return ApiResponse.success(
                coach.recommendation(users.resolve(authentication), request.recommendationId()));
    }
}
