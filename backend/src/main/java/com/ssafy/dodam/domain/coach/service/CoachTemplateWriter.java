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
 * 전부 서버에 있으므로, 문장을 서버가 쓰면 기능은 계속 동작한다. AI 경로와 템플릿 경로는
 * <b>같은 {@link CoachFacts}</b>를 쓰고 응답 스키마도 같다 — FE는 {@code source}만 보고 배지를
 * 바꿀 뿐 분기하지 않는다.
 *
 * <p><b>{@code message}에 사실 목록을 나열하지 않는다</b>(S15P21D105-604). 예전에는 하이라이트를
 * 본문에 붙였는데, 라벨·값이 {@code 영역: SPENDING}, {@code 유형: CATEGORY_SPENDING_INCREASE}처럼
 * 내부 코드 그대로라 그것이 사용자 화면에 나갔다. {@code message} 안에 있으면 FE가 걸러낼 수도 없다.
 * 템플릿 문장은 AI 문장이 들어갈 자리를 대신하므로, 같은 톤('-어요')의 한 줄이면 충분하다.
 * 수치는 화면이 진단·추천 API로 이미 그리고 있다.
 */
@Component
public class CoachTemplateWriter {

    /** 한 화면에 들어가는 개수. 더 넣으면 요약이 아니라 목록이 된다. */
    static final int HIGHLIGHT_LIMIT = 5;

    public String write(CoachFacts facts) {
        return switch (facts.topic()) {
            case FINANCIAL_SUMMARY -> "진단 결과를 아래에서 확인해 보세요.";
            case INEFFICIENCY_EXPLANATION -> "이 항목을 조금 더 살펴보면 좋겠어요.";
            case RECOMMENDATION_EXPLANATION -> "무리하지 않는 선에서 하나씩 시작해 보세요.";
        };
    }

    /**
     * 응답의 {@code highlights}. <b>화면에 그대로 출력하는 용도가 아니다</b> — 라벨·값이 내부 코드일 수
     * 있다(`영역: SPENDING`). 서버가 무엇을 근거로 삼았는지 확인하는 용도이며, 사용자에게 보여줄
     * 수치는 진단·추천 API의 필드를 쓴다. 이 값은 어떤 경로에서도 모델을 거치지 않는다.
     */
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
}
