package com.ssafy.dodam.domain.coach.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
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

/** 640 A-2: 시뮬레이션 응답에서 정해진 필드만 서버 포맷 문자열로 옮긴다. 이 문자열이 숫자 검증의 허용 목록이 된다. */
class CoachSimulationFactFormatterTest {

    private final ObjectMapper json = new ObjectMapper();
    private final CoachSimulationFactFormatter formatter = new CoachSimulationFactFormatter();

    @Test
    void combinedResultCarriesGoalDatesDaysAndInterestOnly() {
        ObjectNode before = json.createObjectNode();
        before.putArray("goals").addObject().put("goalId", 5).put("name", "비상금 500만 원").put("expectedAchievementDate", "2028-02-13");
        ObjectNode after = json.createObjectNode();
        after.putArray("goals").addObject().put("goalId", 5).put("name", "비상금 500만 원").put("expectedAchievementDate", "2027-10-15");
        after.putArray("assumptions").add("CHILD_SCENARIOS_INDEPENDENT").add("FINANCE_COST_EVALUATED");
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
                new Fact("시뮬레이션", "복합 시뮬레이션 (id 88)"),
                new Fact("현재 예상 달성일(비상금 500만 원)", "2028-02-13"),
                new Fact("실행 후 예상 달성일(비상금 500만 원)", "2027-10-15"),
                new Fact("단축(비상금 500만 원)", "121일"),
                new Fact("만기까지 이자 절감(수수료 제외)", "384,000원"),
                new Fact("만기까지 총 이자(현재 → 실행 후)", "1,773,405원 → 1,389,405원"));
        // 월별 상환액 행은 넘기지 않는다
        assertThat(result.facts()).extracting(Fact::label).noneMatch(label -> label.contains("10월"));
        assertThat(sections.get(1).title()).isEqualTo("적용 가정(서버)");
        assertThat(sections.get(1).facts()).extracting(Fact::value).containsExactly("CHILD_SCENARIOS_INDEPENDENT", "FINANCE_COST_EVALUATED");
        assertThat(formatter.notes(response)).containsExactly(CoachSimulationFactFormatter.NOTE_SERVER_NUMBERS);
    }

    @Test
    void notComputableGoalsAreSaidSoAndDelayIsLabelledAsDelay() {
        ObjectNode before = json.createObjectNode();
        before.putArray("goals").addObject().put("goalId", 5).put("name", "비상금");
        SimulationResponse response = new SimulationResponse(9L, SimulationType.LOAN_PREPAYMENT, null, null, SimulationStatus.COMPLETED,
                null, null, null, List.of(), before, json.createObjectNode(), json.createObjectNode(), null,
                List.of(new SimulationGoalImpactResponse(5L, LocalDate.of(2028, 2, 13), null, null, "NOT_COMPUTABLE", List.of("FUNDING_SOURCE_UNKNOWN")),
                        new SimulationGoalImpactResponse(6L, null, LocalDate.of(2028, 5, 1), 30, "COMPUTED", List.of())));

        List<Section> sections = formatter.sections(response);

        assertThat(sections).hasSize(1);
        assertThat(sections.get(0).facts()).containsExactly(
                new Fact("시뮬레이션", "LOAN_PREPAYMENT (id 9)"),
                new Fact("현재 예상 달성일(비상금)", "2028-02-13"),
                new Fact("실행 후 예상 달성일(비상금)", "계산 불가"),
                new Fact("현재 예상 달성일(목표 6)", "현재 속도로는 달성 불가"),
                new Fact("실행 후 예상 달성일(목표 6)", "2028-05-01"),
                new Fact("지연(목표 6)", "30일"));
        assertThat(formatter.notes(response)).containsExactly(CoachSimulationFactFormatter.NOTE_SERVER_NUMBERS,
                CoachSimulationFactFormatter.NOTE_NOT_COMPUTABLE);
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
