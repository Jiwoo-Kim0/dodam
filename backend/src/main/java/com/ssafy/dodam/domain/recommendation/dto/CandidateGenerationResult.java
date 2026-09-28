package com.ssafy.dodam.domain.recommendation.dto;

import java.util.List;

/**
 * 후보 수집 결과. 후보가 비어 있는 이유를 구분해 보존한다.
 *
 * <p>"문제가 없어서 후보가 없음"과 "생성기가 없어서 후보가 없음"은 사용자에게 다른 의미다. 전자는 건강한
 * 상태이고 후자는 미구현이다. 548이 응답에서 이를 구분해 보여줄 수 있도록 건너뛴 문제와 사유를 남긴다.
 *
 * @param candidates 생성된 후보. 문제 issueId 순 → 생성기 등록 순으로 결정적이다
 * @param skipped 후보를 만들지 못한 문제와 사유
 */
public record CandidateGenerationResult(List<RecommendationCandidate> candidates, List<Skipped> skipped) {

    public CandidateGenerationResult {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        skipped = skipped == null ? List.of() : List.copyOf(skipped);
    }

    public static final CandidateGenerationResult EMPTY = new CandidateGenerationResult(List.of(), List.of());

    public record Skipped(Long issueId, String issueType, SkipReason reason) { }

    public enum SkipReason {
        /** 등록된 생성기가 하나도 없다. 547 골격만 있는 상태가 여기 해당한다. */
        NO_GENERATOR,
        /** 생성기는 있지만 이 문제 유형을 지원하는 것이 없다. */
        UNSUPPORTED_ISSUE_TYPE,
        /** 지원하는 생성기가 있으나 입력(스냅샷·현재값 등)이 부족해 후보를 만들지 못했다. */
        INSUFFICIENT_DATA,
        /** 군집 근거가 필요한 규칙인데 문제에 군집 컨텍스트가 없다. */
        NO_CLUSTER_CONTEXT
    }
}
