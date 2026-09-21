package com.ssafy.dodam.domain.coach.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.coach.dto.CoachFacts.Fact;
import com.ssafy.dodam.domain.coach.dto.CoachFacts.Section;
import com.ssafy.dodam.domain.simulation.dto.response.SimulationGoalImpactResponse;
import com.ssafy.dodam.domain.simulation.dto.response.SimulationResponse;
import com.ssafy.dodam.domain.simulation.entity.SimulationStatus;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 640 A-2: 시뮬레이션 응답에서 정해진 필드만 서버 포맷 문자열로 옮긴다. 이 문자열이 숫자 검증의 허용 목록이 되고, 응답 {@code facts}로
 * FE 결과 카드에 그대로 그려진다 — 그래서 simulationId·유형 코드·가정 코드 같은 내부 값이 사실에 들어가면 안 된다.
 */
class CoachSimulationFactFormatterTest {

    private final ObjectMapper json = new ObjectMapper();
    private final CoachSimulationFactFormatter formatter = new CoachSimulationFactFormatter();

    @Test
    void combinedResultCarriesGoalDatesDaysAndInterestOnly() {
        ObjectNode before = json.createObjectNode();
        before.putArray("goals").addObject().put("goalId", 5).put("name", "비상금 500만 원").put("expectedAchievementDate", "2028-02-13");
        ObjectNode after = json.createObjectNode();
        after.putArray("goals").addObject().put("goalId", 5).put("name", "비상금 500만 원").put("expectedAchievementDate", "2027-10-15");
        after.putArray("assumptions").add("CHILD_SCENARIOS_INDEPENDENT").add("FINANCE_COST_EVALUATED").add("SOME_FUTURE_CODE");
        ObjectNode comparison = json.createObjectNode();
        var items = comparison.putArray("items");
        items.addObject().put("key", "INTEREST_SAVING").put("label", "이자 절감액").put("before", 0).put("after", 384_000).put("delta", 384_000);
        items.addObject().put("key", "MONTHLY_REPAYMENT_2026_10").put("label", "10월 상환").put("before", 410_331).put("after", 380_000);
        comparison.putArray("costs").addObject().put("key", "INTEREST_TOTAL").put("label", "총 이자").put("before", 1_773_405)
                .put("after", 1_389_405).put("delta", -384_000);
        SimulationResponse response = new SimulationResponse(88L, SimulationType.COMBINED, "복합 시뮬레이션", "요약", SimulationStatus.COMPLETED,
                LocalDateTime.of(2026, 9, 18, 10, 0), LocalDateTime.of(2026, 9, 18, 10, 0), null, List.of(), before, after, comparison, null,
                List.of(new SimulationGoalImpactResponse(5L, LocalDate.of(2028, 2, 13), LocalDate.of(2027, 10, 15), -121, "COMPUTED", List.of())));

        List<Section> sections = formatter.sections(response);

        assertThat(sections).hasSize(2);
        Section result = sections.get(0);
        assertThat(result.title()).isEqualTo("시뮬레이션 결과(서버 계산)");
        assertThat(result.facts()).containsExactly(
                new Fact("현재 예상 달성일(비상금 500만 원)", "2028-02-13"),
                new Fact("실행 후 예상 달성일(비상금 500만 원)", "2027-10-15"),
                new Fact("단축(비상금 500만 원)", "121일"),
                new Fact("만기까지 이자 절감(수수료 제외)", "384,000원"),
                new Fact("만기까지 총 이자(현재 → 실행 후)", "1,773,405원 → 1,389,405원"));
        // 월별 상환액 행은 넘기지 않는다. simulationId·제목도 사실에 없다(FE 링크는 toolResults.simulationId)
        assertThat(result.facts()).extracting(Fact::label).noneMatch(label -> label.contains("10월")).noneMatch("시뮬레이션"::equals);
        assertThat(result.facts()).extracting(Fact::value).noneMatch(value -> value.contains("88"));
        // 가정 코드는 한글 문장으로, 모르는 코드는 넘기지 않는다
        assertThat(sections.get(1).title()).isEqualTo("적용 가정(서버)");
        assertThat(sections.get(1).facts()).containsExactly(
                new Fact("가정 1", "각 행동은 같은 기준에서 따로 계산했고 서로의 결과를 잇지 않았다"),
                new Fact("가정 2", "금융비용은 평가 기간 안의 이자·수수료 합이며 평생 총비용이 아니다"));
        assertThat(formatter.notes(response)).containsExactly(CoachSimulationFactFormatter.NOTE_SERVER_NUMBERS);
    }

    @Test
    void goalsTheEngineDidNotProjectAreLeftOutInsteadOfSayingNotComputable() {
        // 대출 추가 상환: 자금 출처를 몰라 엔진이 목표 날짜를 내지 않는다(FUNDING_SOURCE_UNKNOWN)
        ObjectNode before = json.createObjectNode();
        before.putArray("goals").addObject().put("goalId", 5).put("name", "비상금");
        ObjectNode after = json.createObjectNode();
        after.putArray("assumptions").add("FUNDING_SOURCE_UNKNOWN");
        ObjectNode comparison = json.createObjectNode();
        comparison.putArray("items").addObject().put("key", "INTEREST_SAVING").put("before", 0).put("after", 120_000);
        SimulationResponse response = new SimulationResponse(9L, SimulationType.LOAN_PREPAYMENT, null, null, SimulationStatus.COMPLETED,
                null, null, null, List.of(), before, after, comparison, null,
                List.of(new SimulationGoalImpactResponse(5L, LocalDate.of(2028, 2, 13), null, null, "NOT_COMPUTABLE", List.of("FUNDING_SOURCE_UNKNOWN"))));

        CoachSimulationFactFormatter.Formatted formatted = formatter.format(response);

        assertThat(formatted.sections().get(0).facts()).containsExactly(new Fact("만기까지 이자 절감(수수료 제외)", "120,000원"));
        assertThat(formatted.sections().get(0).facts()).extracting(Fact::value).noneMatch(value -> value.contains("계산 불가"))
                .noneMatch(value -> value.contains("LOAN_PREPAYMENT"));
        assertThat(formatted.notes()).containsExactly(CoachSimulationFactFormatter.NOTE_SERVER_NUMBERS,
                CoachSimulationFactFormatter.NOTE_GOALS_NOT_PROJECTED);
    }

    @Test
    void combinedWithLoanUsesTheSingleSpendingChildProjectionAndSaysSo() {
        // "둘 다 하면?": 부모 목표 영향은 NOT_COMPUTABLE이지만 소비 자식의 단독 결과가 details.children에 있다
        ObjectNode before = json.createObjectNode();
        before.putArray("goals").addObject().put("goalId", 5).put("name", "비상금");
        ObjectNode after = json.createObjectNode();
        ArrayNode children = after.putObject("details").putArray("children");
        ObjectNode spending = children.addObject().put("sequenceNo", 1).put("type", "SPENDING");
        spending.putArray("goalImpacts").addObject().put("goalId", 5).put("beforeForecastDate", "2028-02-13").put("afterForecastDate", "2027-10-15");
        ObjectNode prepayment = children.addObject().put("sequenceNo", 2).put("type", "LOAN_PREPAYMENT");
        prepayment.putArray("goalImpacts").addObject().put("goalId", 5).put("beforeForecastDate", "2028-02-13").putNull("afterForecastDate");
        ObjectNode comparison = json.createObjectNode();
        comparison.putArray("items").addObject().put("key", "FINANCE_COST_EVALUATED").put("before", 1_773_405).put("after", 1_389_405);
        SimulationResponse response = new SimulationResponse(25L, SimulationType.COMBINED, "복합", null, SimulationStatus.COMPLETED,
                null, null, null, List.of(), before, after, comparison, null,
                List.of(new SimulationGoalImpactResponse(5L, LocalDate.of(2028, 2, 13), null, null, "NOT_COMPUTABLE",
                        List.of("COMBINED_GOAL_EFFECT_NOT_COMPUTABLE", "FUNDING_SOURCE_UNKNOWN"))));

        CoachSimulationFactFormatter.Formatted formatted = formatter.format(response);

        assertThat(formatted.sections().get(0).facts()).containsExactly(
                new Fact("현재 예상 달성일(비상금)", "2028-02-13"),
                new Fact("실행 후 예상 달성일(비상금)", "2027-10-15"),
                new Fact("단축(비상금)", "121일"),
                new Fact("달성일 계산 범위", "소비 줄이기·저축 변경만 반영(대출 상환·대환은 제외)"),
                new Fact("평가 기간 금융비용 변화", "1,773,405원 → 1,389,405원"));
        assertThat(formatted.notes()).containsExactly(CoachSimulationFactFormatter.NOTE_SERVER_NUMBERS,
                CoachSimulationFactFormatter.NOTE_GOALS_PARTIAL);
    }

    @Test
    void twoSpendingChildrenAreNotCombinedByTheCoach() {
        ObjectNode before = json.createObjectNode();
        before.putArray("goals").addObject().put("goalId", 5).put("name", "비상금");
        ObjectNode after = json.createObjectNode();
        ArrayNode children = after.putObject("details").putArray("children");
        for (String date : List.of("2027-12-01", "2027-11-01")) {
            children.addObject().put("type", "SPENDING").putArray("goalImpacts").addObject().put("goalId", 5).put("afterForecastDate", date);
        }
        children.addObject().put("type", "LOAN_REFINANCE");
        SimulationResponse response = new SimulationResponse(26L, SimulationType.COMBINED, "복합", null, SimulationStatus.COMPLETED,
                null, null, null, List.of(), before, after, null, null,
                List.of(new SimulationGoalImpactResponse(5L, LocalDate.of(2028, 2, 13), null, null, "NOT_COMPUTABLE",
                        List.of("COMBINED_GOAL_EFFECT_NOT_COMPUTABLE"))));

        CoachSimulationFactFormatter.Formatted formatted = formatter.format(response);

        assertThat(formatted.sections().get(0).facts()).isEmpty();
        assertThat(formatted.notes()).contains(CoachSimulationFactFormatter.NOTE_GOALS_NOT_PROJECTED);
    }

    @Test
    void notAchievableIsAValueAndDelayIsLabelledAsDelay() {
        ObjectNode before = json.createObjectNode();
        before.putArray("goals").addObject().put("goalId", 5).put("name", "비상금");
        SimulationResponse response = new SimulationResponse(10L, SimulationType.SAVING, "저축", null, SimulationStatus.COMPLETED,
                null, null, null, List.of(), before, json.createObjectNode(), json.createObjectNode(), null,
                List.of(
                        // 저축 속도 0 → 전후 모두 날짜가 없다(계산은 했다)
                        new SimulationGoalImpactResponse(5L, null, null, null, null, null),
                        // 이름이 없는 목표: 내부 id 대신 일반 이름. 현재는 달성 불가였지만 실행 후 날짜가 나온다
                        new SimulationGoalImpactResponse(6L, null, LocalDate.of(2028, 5, 1), null, null, null),
                        // 복합 투영에서 속도 0(SAVING_PACE_ZERO)은 계산 불가가 아니라 달성 불가다
                        new SimulationGoalImpactResponse(7L, LocalDate.of(2028, 1, 1), null, null, "NOT_COMPUTABLE", List.of("SAVING_PACE_ZERO")),
                        new SimulationGoalImpactResponse(8L, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 3, 2), 60, "COMPUTED", List.of()),
                        // 변화 0일(이미 달성)은 단축도 지연도 아니다
                        new SimulationGoalImpactResponse(9L, LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 21), 0, "COMPUTED", List.of("ALREADY_ACHIEVED"))));

        CoachSimulationFactFormatter.Formatted formatted = formatter.format(response);

        assertThat(formatted.sections().get(0).facts()).containsExactly(
                new Fact("현재 예상 달성일(비상금)", "현재 속도로는 달성 불가"),
                new Fact("실행 후 예상 달성일(비상금)", "실행 후에도 달성 불가"),
                new Fact("현재 예상 달성일(모으기 목표)", "현재 속도로는 달성 불가"),
                new Fact("실행 후 예상 달성일(모으기 목표)", "2028-05-01"),
                new Fact("현재 예상 달성일(모으기 목표)", "2028-01-01"),
                new Fact("실행 후 예상 달성일(모으기 목표)", "실행 후에도 달성 불가"),
                new Fact("현재 예상 달성일(모으기 목표)", "2027-01-01"),
                new Fact("실행 후 예상 달성일(모으기 목표)", "2027-03-02"),
                new Fact("지연(모으기 목표)", "60일"),
                new Fact("현재 예상 달성일(모으기 목표)", "2026-09-21"),
                new Fact("실행 후 예상 달성일(모으기 목표)", "2026-09-21"));
        assertThat(formatted.notes()).containsExactly(CoachSimulationFactFormatter.NOTE_SERVER_NUMBERS,
                CoachSimulationFactFormatter.NOTE_NOT_ACHIEVABLE);
    }

    @Test
    void toolResultTextListsFactsAndNotesOrTheError() {
        CoachToolResult ok = CoachToolResult.ok("c1", "simulate_combined", 88L, true,
                List.of(new Section("시뮬레이션 결과(서버 계산)", List.of(new Fact("단축", "121일")))), List.of("새 숫자를 만들지 말 것."));
        assertThat(ok.toModelText()).isEqualTo("[시뮬레이션 결과(서버 계산)]\n- 단축: 121일\n※ 새 숫자를 만들지 말 것.");
        assertThat(ok.succeeded()).isTrue();
        CoachToolResult error = CoachToolResult.error("c2", "simulate_spending_reduction", "카페 월평균 소비(38,000원)보다 큰 금액이에요.");
        assertThat(error.succeeded()).isFalse();
        assertThat(error.toModelText()).isEqualTo("실행 실패: 카페 월평균 소비(38,000원)보다 큰 금액이에요.");
        assertThat(error.simulationId()).isNull();
    }
}
