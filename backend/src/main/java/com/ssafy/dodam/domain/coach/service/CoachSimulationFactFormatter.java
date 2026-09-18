package com.ssafy.dodam.domain.coach.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.dodam.domain.coach.dto.CoachFacts.Fact;
import com.ssafy.dodam.domain.coach.dto.CoachFacts.Section;
import com.ssafy.dodam.domain.simulation.dto.response.SimulationGoalImpactResponse;
import com.ssafy.dodam.domain.simulation.dto.response.SimulationResponse;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * {@link SimulationResponse} → 코치 사실(640 A-2, 계획 §1-4 마지막 단락). 정해진 필드만 옮긴다:
 * {@code before/after.goals[].expectedAchievementDate}, {@code goalImpacts[].differenceDays}, {@code comparison.items/costs}의 이자 항목.
 * 값은 서버가 포맷한 문자열(천단위·ISO 날짜)이며 그 문자열이 그대로 {@code CoachNumberGuard}의 허용 숫자가 된다(ai-coach.md §4).
 *
 * <p>자유 JSON(스냅샷 전체)을 모델에 넘기지 않는다. 계좌번호 원문은 DTO에 없지만, 넘기는 필드를 화이트리스트로 두어야 도메인에 컬럼이
 * 늘어도 프롬프트가 조용히 넓어지지 않는다.
 */
@Component
public class CoachSimulationFactFormatter {
    static final String RESULT_TITLE = "시뮬레이션 결과(서버 계산)";
    static final String NOTE_SERVER_NUMBERS = "달성일·절감액은 서버 시뮬레이션 엔진이 계산한 값이다. 새 숫자를 만들지 말 것.";
    static final String NOTE_NOT_COMPUTABLE = "목표 달성일 변화를 계산할 수 없는 항목은 '계산 불가'로만 말하고 날짜를 추정하지 말 것.";

    public List<Section> sections(SimulationResponse simulation) {
        List<Fact> facts = new ArrayList<>();
        facts.add(new Fact("시뮬레이션", label(simulation)));
        Map<Long, String> goalNames = goalNames(simulation.before());
        goalNames.putAll(goalNames(simulation.after()));
        boolean anyNotComputable = false;
        for (SimulationGoalImpactResponse impact : simulation.goalImpacts()) {
            String goal = goalNames.getOrDefault(impact.goalId(), "목표 " + impact.goalId());
            facts.add(new Fact("현재 예상 달성일(" + goal + ")", dateOrUnknown(impact.beforeForecastDate())));
            if ("NOT_COMPUTABLE".equals(impact.status()) || impact.afterForecastDate() == null) {
                facts.add(new Fact("실행 후 예상 달성일(" + goal + ")", "계산 불가"));
                anyNotComputable = true;
                continue;
            }
            facts.add(new Fact("실행 후 예상 달성일(" + goal + ")", CoachFormat.date(impact.afterForecastDate())));
            if (impact.differenceDays() != null) {
                int days = impact.differenceDays();
                facts.add(new Fact(days <= 0 ? "단축(" + goal + ")" : "지연(" + goal + ")", CoachFormat.days(Math.abs(days))));
            }
        }
        addComparison(facts, simulation.comparison());
        List<Section> sections = new ArrayList<>();
        sections.add(new Section(RESULT_TITLE, facts));
        List<Fact> assumptions = assumptions(simulation.after());
        if (!assumptions.isEmpty()) {
            sections.add(new Section("적용 가정(서버)", assumptions));
        }
        return sections;
    }

    public List<String> notes(SimulationResponse simulation) {
        List<String> notes = new ArrayList<>();
        notes.add(NOTE_SERVER_NUMBERS);
        boolean notComputable = simulation.goalImpacts().stream()
                .anyMatch(impact -> "NOT_COMPUTABLE".equals(impact.status()) || impact.afterForecastDate() == null);
        if (notComputable) {
            notes.add(NOTE_NOT_COMPUTABLE);
        }
        return notes;
    }

    private static String label(SimulationResponse simulation) {
        String title = simulation.title() == null || simulation.title().isBlank() ? simulation.type().name() : simulation.title();
        return title + " (id " + simulation.simulationId() + ")";
    }

    private static Map<Long, String> goalNames(JsonNode snapshot) {
        Map<Long, String> names = new HashMap<>();
        if (snapshot == null) {
            return names;
        }
        for (JsonNode goal : snapshot.path("goals")) {
            JsonNode id = goal.get("goalId");
            JsonNode name = goal.get("name");
            if (id != null && id.isNumber() && name != null && name.isTextual()) {
                names.put(id.longValue(), name.textValue());
            }
        }
        return names;
    }

    /** 이자 관련 비교 항목만 옮긴다. 월별 상환액 행(MONTHLY_REPAYMENT_yyyy_MM)은 수십 개라 넘기지 않는다. */
    private static void addComparison(List<Fact> facts, JsonNode comparison) {
        if (comparison == null) {
            return;
        }
        for (String section : List.of("items", "costs")) {
            for (JsonNode item : comparison.path(section)) {
                String key = item.path("key").asText("");
                switch (key) {
                    case "INTEREST_SAVING" -> facts.add(new Fact("만기까지 이자 절감(수수료 제외)", CoachFormat.money(longOf(item, "after"))));
                    case "INTEREST_TOTAL" -> {
                        if ("costs".equals(section)) {
                            facts.add(new Fact("만기까지 총 이자(현재 → 실행 후)",
                                    CoachFormat.money(longOf(item, "before")) + " → " + CoachFormat.money(longOf(item, "after"))));
                        }
                    }
                    case "FINANCE_COST_EVALUATED" -> facts.add(new Fact("평가 기간 금융비용 변화",
                            CoachFormat.money(longOf(item, "before")) + " → " + CoachFormat.money(longOf(item, "after"))));
                    case "PREPAYMENT_AMOUNT" -> facts.add(new Fact("추가 상환액", CoachFormat.money(longOf(item, "after"))));
                    case "REFINANCE_AMOUNT" -> facts.add(new Fact("대환 금액", CoachFormat.money(longOf(item, "after"))));
                    default -> { }
                }
            }
        }
    }

    private static List<Fact> assumptions(JsonNode after) {
        List<Fact> facts = new ArrayList<>();
        if (after == null) {
            return facts;
        }
        int index = 1;
        for (JsonNode assumption : after.path("assumptions")) {
            if (assumption.isTextual() && index <= 6) {
                facts.add(new Fact("가정 " + index++, assumption.textValue()));
            }
        }
        return facts;
    }

    private static Long longOf(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isNumber() ? null : value.longValue();
    }

    private static String dateOrUnknown(LocalDate date) {
        return date == null ? "현재 속도로는 달성 불가" : CoachFormat.date(date);
    }
}
