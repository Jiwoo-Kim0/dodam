package com.ssafy.dodam.domain.recommendation.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import java.util.List;

/**
 * 생성기가 만든 추천 후보. 아직 저장되지 않았고 우선순위도 없다.
 *
 * @param generatorVersion 후보를 만든 생성기의 버전. `generationKey`에 들어가므로 규칙이 바뀌면 올려야
 *                         같은 진단에서 새 추천이 만들어진다
 * @param candidateKey 같은 생성기 안에서 후보를 구분하는 안정 키. 문제 유형과 대상(예: 카드번호 해시가 아닌
 *                     식별 id)으로 만들고 시각·순번·표시 문구를 넣지 않는다
 * @param sourceIssueId 후보의 근거가 된 진단 문제. 여러 문제를 합쳐 만든 후보면 null
 * @param effect 효과 추정치. 547에서는 {@link Recommendation.Effect#NONE}
 * @param goalId 이 후보가 겨냥한 목표(696). 날짜 효과({@code currentForecastDate}·{@code improvedForecastDate})를 계산한 목표이며
 *               {@code Recommendation.goalId}에 저장된다. null이면 생성 요청의 goalId(진단 goalId)를 물려받는다. 목표를 지정하지
 *               않은 진단에서도 목표 기여(goal-impact)가 {@code NO_GOAL}이 아니라 계산한 목표를 가리키게 하기 위한 필드다
 */
public record RecommendationCandidate(
        String generatorVersion,
        String candidateKey,
        Long sourceIssueId,
        String domain,
        String type,
        String title,
        String description,
        List<Reason> reasons,
        Recommendation.Effect effect,
        Long goalId
) {
    /** 목표를 지정하지 않는 후보(요청 goalId를 물려받는다). */
    public RecommendationCandidate(String generatorVersion, String candidateKey, Long sourceIssueId, String domain, String type,
            String title, String description, List<Reason> reasons, Recommendation.Effect effect) {
        this(generatorVersion, candidateKey, sourceIssueId, domain, type, title, description, reasons, effect, null);
    }

    public RecommendationCandidate {
        requireText("generatorVersion", generatorVersion);
        requireText("candidateKey", candidateKey);
        requireText("domain", domain);
        requireText("type", type);
        requireText("title", title);
        requireText("description", description);
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        if (effect == null) {
            effect = Recommendation.Effect.NONE;
        }
    }

    /** 근거 후보. `code`는 후보 안에서 유일해야 하며 저장 시 다시 검사한다. */
    public record Reason(String code, String description, JsonNode evidence) {
        public Reason {
            requireText("code", code);
            requireText("description", description);
        }
    }

    private static void requireText(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + "은(는) 비어 있을 수 없습니다.");
        }
    }
}
