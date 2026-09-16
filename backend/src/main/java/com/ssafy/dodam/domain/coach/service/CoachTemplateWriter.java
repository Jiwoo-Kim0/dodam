package com.ssafy.dodam.domain.coach.service;

import com.ssafy.dodam.domain.coach.dto.CoachFacts;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * AI 없이 서버 계산값만으로 문장을 만든다. 기획안 11-3이 AI 금융코치의 대체 방법으로 명시한
 * "템플릿 기반 응답"이다.
 *
 * <p><b>이것이 부가 기능이 아니라 기본 경로인 이유</b>: 외부 API는 느려지거나 멈춘다. 코치 문장이
 * 없다고 화면이 비면 사용자는 "진단이 안 됐다"고 읽는다. 실제로 없는 것은 문장뿐이고 수치·판정은
 * 전부 서버에 있으므로, 문장을 서버가 쓰면 기능은 계속 동작한다. 그래서 AI 경로와 템플릿 경로는
 * <b>같은 {@link CoachFacts}</b>를 쓰고 응답 스키마도 같다 — FE는 {@code source}만 보고 배지를
 * 바꿀 뿐 분기하지 않는다.
 *
 * <p>{@code highlights}는 AI 경로에서도 이 클래스가 만든다. 화면의 수치는 어떤 경우에도 모델을
 * 거치지 않는다.
 */
@Component
public class CoachTemplateWriter {

    /** 한 화면에 들어가는 개수. 더 넣으면 요약이 아니라 목록이 된다. */
    static final int HIGHLIGHT_LIMIT = 5;

    private static final String CLOSING =
            "지금은 AI 설명 대신 서버가 계산한 값을 그대로 보여드립니다. 자세한 항목은 진단·개선안 화면에서 확인하실 수 있습니다.";

    public String write(CoachFacts facts) {
        StringBuilder message = new StringBuilder(intro(facts));
        List<String> highlights = highlights(facts);
        if (!highlights.isEmpty()) {
            message.append("\n\n").append(String.join("\n", highlights.stream().map(line -> "- " + line).toList()));
        }
        return message.append("\n\n").append(CLOSING).toString();
    }

    /** 응답의 {@code highlights}. 앞쪽 구간이 곧 핵심 구간이도록 조립기가 순서를 잡아 둔다. */
    public List<String> highlights(CoachFacts facts) {
        List<String> highlights = new ArrayList<>();
        for (CoachFacts.Section section : facts.sections()) {
            for (CoachFacts.Fact fact : section.facts()) {
                if (highlights.size() >= HIGHLIGHT_LIMIT) {
                    return highlights;
                }
                highlights.add(fact.label() + ": " + fact.value());
            }
        }
        return highlights;
    }

    private static String intro(CoachFacts facts) {
        return switch (facts.topic()) {
            case FINANCIAL_SUMMARY -> "최근 금융 진단 결과를 서버 계산값 그대로 정리했습니다.";
            case INEFFICIENCY_EXPLANATION -> "'" + facts.subject() + "' 항목에 대해 서버가 계산한 근거입니다.";
            case RECOMMENDATION_EXPLANATION -> "'" + facts.subject() + "' 개선안이 제안된 근거입니다.";
        };
    }
}
