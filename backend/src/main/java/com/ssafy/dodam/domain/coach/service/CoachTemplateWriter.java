package com.ssafy.dodam.domain.coach.service;

import com.ssafy.dodam.domain.coach.dto.CoachFacts;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
            case CHAT -> chat(facts);
        };
    }

    /** 채팅 템플릿에서 읽는 라벨·값. {@link CoachSimulationFactFormatter}가 만드는 문자열과 같아야 한다. */
    static final String LABEL_BEFORE_DATE = CoachSimulationFactFormatter.LABEL_BEFORE_DATE;
    static final String LABEL_AFTER_DATE = CoachSimulationFactFormatter.LABEL_AFTER_DATE;
    static final String LABEL_SHORTENED = CoachSimulationFactFormatter.LABEL_SHORTENED;
    static final String LABEL_DELAYED = CoachSimulationFactFormatter.LABEL_DELAYED;
    static final String LABEL_GOAL_SCOPE = CoachSimulationFactFormatter.LABEL_GOAL_SCOPE;
    static final String LABEL_INTEREST_SAVING = CoachSimulationFactFormatter.LABEL_INTEREST_SAVING;
    /** 실패한 도구를 담는 섹션 제목. {@code CoachChatService}가 같은 제목으로 넣는다. */
    static final String FAILED_SECTION_TITLE = "실행하지 못한 요청";

    static final String CHAT_NO_ANSWER = "지금은 답변을 만들 수 없어요. 잠시 후 다시 물어봐 주세요.";
    static final String CHAT_SEE_FACTS = "지금은 문장을 만들 수 없어서 계산 결과만 보여드려요.";
    static final String CHAT_GOAL_SCOPE_PARTIAL = "목표 달성일은 소비 줄이기·저축 변경만 반영한 값이고, 대출 상환의 영향은 계산하지 않았어요.";
    /** 한 문장에 담는 목표 수. 목표가 더 많으면 나머지는 결과 카드가 보여준다. */
    private static final int CHAT_GOAL_LIMIT = 2;

    /**
     * 채팅 대체 문장(640 A-3). AI 없이도 "현재 A → 실행 후 B, N일 단축"을 서버 값으로 말한다 — 이것이 없으면
     * 도구는 실행됐는데 화면에 문장이 없어 사용자는 "계산이 안 됐다"고 읽는다. 값은 전부 facts에 이미 포맷된 문자열이다.
     *
     * <p>달성일 사실이 없는 목표(엔진이 변화를 계산하지 않은 것)는 말하지 않는다. "실행 후 계산 불가로 예상돼요" 같은 문장은
     * 사용자에게 뜻이 없다. '달성 불가'는 값이 있는 것이므로(그 속도로는 목표 금액에 이르지 못함) 그대로 말한다.
     */
    private static String chat(CoachFacts facts) {
        StringBuilder text = new StringBuilder();
        // 목표별 달성일 변화. 라벨의 괄호 안이 목표 이름이다: [0]=현재, [1]=실행 후, [2]=단축, [3]=지연
        LinkedHashMap<String, String[]> goals = new LinkedHashMap<>();
        String interestSaving = null;
        String firstForecast = null;
        String firstFailure = null;
        boolean partialGoalScope = false;
        for (CoachFacts.Section section : facts.sections()) {
            for (CoachFacts.Fact fact : section.facts()) {
                String label = fact.label();
                if (FAILED_SECTION_TITLE.equals(section.title())) {
                    if (firstFailure == null) {
                        firstFailure = fact.value();
                    }
                } else if (label.startsWith(LABEL_BEFORE_DATE)) {
                    goals.computeIfAbsent(goalName(label, LABEL_BEFORE_DATE), name -> new String[4])[0] = fact.value();
                } else if (label.startsWith(LABEL_AFTER_DATE)) {
                    goals.computeIfAbsent(goalName(label, LABEL_AFTER_DATE), name -> new String[4])[1] = fact.value();
                } else if (label.startsWith(LABEL_SHORTENED)) {
                    goals.computeIfAbsent(goalName(label, LABEL_SHORTENED), name -> new String[4])[2] = fact.value();
                } else if (label.startsWith(LABEL_DELAYED)) {
                    goals.computeIfAbsent(goalName(label, LABEL_DELAYED), name -> new String[4])[3] = fact.value();
                } else if (LABEL_GOAL_SCOPE.equals(label)) {
                    partialGoalScope = true;
                } else if (LABEL_INTEREST_SAVING.equals(label) && interestSaving == null) {
                    interestSaving = fact.value();
                } else if (label.endsWith(CoachToolDispatcher.LABEL_FORECAST_SUFFIX) && firstForecast == null) {
                    // 소비 예측(707)은 첫 달만 말한다. 사실이 오래된 달부터 쌓이므로 첫 사실이 다음 달이다
                    String month = label.substring(0, label.length() - CoachToolDispatcher.LABEL_FORECAST_SUFFIX.length());
                    firstForecast = month + " 변동 소비는 " + fact.value() + "으로 예상돼요. ";
                }
            }
        }
        int written = 0;
        for (var entry : goals.entrySet()) {
            if (written >= CHAT_GOAL_LIMIT) {
                break;
            }
            String sentence = goalSentence(entry.getKey(), entry.getValue());
            if (sentence == null) {
                continue;
            }
            text.append(sentence).append(' ');
            written++;
        }
        if (written > 0 && partialGoalScope) {
            text.append(CHAT_GOAL_SCOPE_PARTIAL).append(' ');
        }
        if (interestSaving != null) {
            text.append("만기까지 이자를 ").append(interestSaving).append(" 아낄 수 있어요. ");
        }
        if (firstForecast != null) {
            text.append(firstForecast);
        }
        if (text.isEmpty() && firstFailure != null) {
            return "요청을 실행하지 못했어요. " + firstFailure;
        }
        if (text.isEmpty()) {
            return facts.sections().isEmpty() ? CHAT_NO_ANSWER : CHAT_SEE_FACTS;
        }
        return text.toString().strip();
    }

    /** 목표 하나의 문장. 현재·실행 후 값이 모두 있어야 말한다. 없으면 null. */
    private static String goalSentence(String name, String[] values) {
        String before = values[0];
        String after = values[1];
        if (before == null || after == null) {
            return null;
        }
        if (CoachSimulationFactFormatter.AFTER_NOT_ACHIEVABLE.equals(after)) {
            return name + " 목표는 실행 후에도 지금 속도로는 달성일을 예상할 수 없어요.";
        }
        if (CoachSimulationFactFormatter.BEFORE_NOT_ACHIEVABLE.equals(before)) {
            return name + " 목표는 지금 속도로는 달성일을 예상할 수 없지만, 실행 후에는 " + after + "에 달성할 것으로 예상돼요.";
        }
        if (values[2] != null) {
            return name + " 목표는 현재 " + before + "에서 실행 후 " + after + "로 " + values[2] + " 빨라질 것으로 예상돼요.";
        }
        if (values[3] != null) {
            return name + " 목표는 현재 " + before + "에서 실행 후 " + after + "로 " + values[3] + " 늦어질 것으로 예상돼요.";
        }
        return name + " 목표 달성일은 " + after + "로 변화가 없어요.";
    }

    private static String goalName(String label, String prefix) {
        int end = label.lastIndexOf(')');
        return end > prefix.length() ? label.substring(prefix.length(), end) : label.substring(prefix.length());
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
