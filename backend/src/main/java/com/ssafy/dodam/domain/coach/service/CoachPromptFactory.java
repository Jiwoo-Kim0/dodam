package com.ssafy.dodam.domain.coach.service;

import com.ssafy.dodam.domain.coach.dto.CoachFacts;
import com.ssafy.dodam.domain.coach.dto.CoachTopic;
import org.springframework.stereotype.Component;

/**
 * 프롬프트를 만든다. 모델에게 주는 재료는 {@link CoachFacts} 하나뿐이며 엔티티·요청 본문·사용자 식별자는
 * 이 클래스에 들어오지 않는다.
 *
 * <p>사실 목록을 JSON이 아니라 줄 단위 텍스트로 직렬화한다. JSON으로 주면 모델이 응답도 JSON 구조를
 * 흉내 내려 하고, 중괄호·따옴표가 토큰만 차지한다. 사람이 읽는 형태가 지시를 더 잘 따른다.
 *
 * <p>금지 규칙을 시스템 프롬프트에 두는 것은 1차 방어일 뿐이다. 실제 강제는 {@link CoachNumberGuard}가
 * 응답을 검사해서 한다 — 프롬프트는 지켜지길 바라는 것이고, 검사는 지켜졌는지 확인하는 것이다.
 */
@Component
public class CoachPromptFactory {

    private static final String COMMON_RULES = """
            너는 개인 재무 관리 앱 '도담'의 금융 코치다. 사용자에게 존댓말로 설명한다.

            반드시 지킬 것:
            1. 아래 [사실]에 적힌 값만 사용한다. 사실에 없는 금액·비율·기간·건수를 쓰지 않는다.
            2. 계산하지 않는다. 더하기·빼기·곱하기·나누기, 연/월 환산, 비율 계산, 순위 매기기를 하지 않는다.
               필요한 값은 이미 [사실]에 계산되어 있다.
            3. 판정하지 않는다. 심각도·위험도·건전성 상태·군집(또래 집단)은 서버가 이미 판정했다.
               [사실]과 다른 판정을 내리거나 새로 분류하지 않는다.
            4. [주의]로 시작하는 줄의 지시를 어기지 않는다.
            5. 특정 금융상품·금융기관을 권유하지 않고, 수익이나 결과를 단정하지 않는다.
            6. 사실에 없는 내용을 추측으로 채우지 않는다. 근거가 부족하면 부족하다고 쓴다.

            형식:
            - 한국어 평문. 마크다운 제목·표·굵은 글씨를 쓰지 않는다.
            - 두 문단 이내, 전체 400자 이내.
            - 첫 문단은 현재 상태, 둘째 문단은 사용자가 확인하면 좋을 지점을 쓴다.
            """;

    public String system(CoachTopic topic) {
        return COMMON_RULES + "\n" + switch (topic) {
            case FINANCIAL_SUMMARY -> """
                    이번 요청은 '금융현황 요약'이다. 진단 결과 전체를 사용자가 한눈에 이해하도록 풀어 쓴다.
                    문제가 없으면 없다고 쓴다.
                    """;
            case INEFFICIENCY_EXPLANATION -> """
                    이번 요청은 '비효율 원인 설명'이다. 서버가 이미 문제로 판정한 항목에 대해,
                    왜 그런 상태가 되는지를 [사실]의 현재 값과 기준 값을 근거로 설명한다.
                    이것이 문제인지 아닌지를 새로 판단하지 않는다.
                    """;
            case RECOMMENDATION_EXPLANATION -> """
                    이번 요청은 '추천 근거 설명'이다. 서버가 이미 만든 개선안에 대해,
                    왜 이 개선안이 제안되었는지를 [사실]의 근거와 효과로 설명한다.
                    새로운 개선안을 제안하거나 효과 금액을 추정하지 않는다.
                    """;
        };
    }

    public String user(CoachFacts facts) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("[대상] ").append(facts.subject()).append('\n');
        facts.notes().forEach(note -> prompt.append("[주의] ").append(note).append('\n'));
        prompt.append("\n[사실]\n");
        facts.sections().forEach(section -> {
            prompt.append('\n').append(section.title()).append('\n');
            section.facts().forEach(fact ->
                    prompt.append("- ").append(fact.label()).append(": ").append(fact.value()).append('\n'));
        });
        return prompt.toString();
    }
}
