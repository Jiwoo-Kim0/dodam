package com.ssafy.dodam.domain.recommendation.dto.response;

import com.ssafy.dodam.domain.recommendation.dto.CandidateGenerationResult;
import java.util.List;

/**
 * 생성 응답(POST /generate).
 *
 * @param created 이번 요청에서 새로 만든 건수
 * @param reused  같은 키의 기존 추천을 돌려준 건수(재요청)
 * @param items   priority 순. {@code score}는 이번 생성의 점수이며 저장되지 않는다(기존 행이면 이번 계산값)
 * @param skipped 후보를 만들지 못한 문제와 사유. 문제가 0건이면 비어 있다
 */
public record RecommendationGenerationResponse(
        Long diagnosisId,
        Long goalId,
        int created,
        int reused,
        List<Item> items,
        List<CandidateGenerationResult.Skipped> skipped
) {
    public RecommendationGenerationResponse {
        items = List.copyOf(items);
        skipped = List.copyOf(skipped);
    }

    public record Item(RecommendationResponse recommendation, int score, boolean created) { }
}
