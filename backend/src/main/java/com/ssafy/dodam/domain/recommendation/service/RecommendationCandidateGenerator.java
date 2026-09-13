package com.ssafy.dodam.domain.recommendation.service;

import com.ssafy.dodam.domain.recommendation.dto.CandidateGenerationResult.SkipReason;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationCandidate;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationInput;
import java.util.List;

/**
 * 진단 문제 1건을 추천 후보로 바꾸는 규칙의 확장점.
 *
 * <p>구현체는 Spring 빈으로 등록하면 {@link RecommendationCandidateCollector}가 모아 쓴다. 순수 함수여야
 * 한다: 저장·외부 API 호출·시각 조회를 하지 않고 입력만으로 후보를 만든다. 그래야 같은 진단에서 같은
 * 후보(같은 `candidateKey`)가 나와 중복 생성 방지가 성립한다.
 *
 * <p>547에는 운영 구현체가 없다. 실제 금융상품·행동 추천 규칙은 548이 추가한다. 테스트 구현체는
 * 테스트 소스에만 둔다.
 */
public interface RecommendationCandidateGenerator {

    /**
     * 규칙 버전. 후보 키에 포함돼 규칙이 바뀌면 같은 진단에서도 새 추천이 만들어진다. 규칙의 의미가 바뀔
     * 때만 올리고, 문구 수정에는 올리지 않는다.
     */
    String version();

    boolean supports(RecommendationInput.Issue issue);

    /**
     * @return 후보 목록(비어 있을 수 있음) 또는 건너뛴 사유. {@link #supports}가 true인 문제만 넘어온다
     */
    Outcome generate(RecommendationInput input, RecommendationInput.Issue issue);

    /** 후보이거나 건너뛴 사유이거나 둘 중 하나다. 사유 없이 빈 목록을 돌려주면 "문제는 있으나 후보 없음"이다. */
    record Outcome(List<RecommendationCandidate> candidates, SkipReason skipReason) {

        public Outcome {
            candidates = candidates == null ? List.of() : List.copyOf(candidates);
            if (skipReason != null && !candidates.isEmpty()) {
                throw new IllegalArgumentException("건너뛴 사유와 후보를 함께 돌려줄 수 없습니다.");
            }
        }

        public static Outcome of(RecommendationCandidate... candidates) {
            return new Outcome(List.of(candidates), null);
        }

        public static Outcome skipped(SkipReason reason) {
            if (reason == null) {
                throw new IllegalArgumentException("건너뛴 사유가 필요합니다.");
            }
            return new Outcome(List.of(), reason);
        }
    }
}
