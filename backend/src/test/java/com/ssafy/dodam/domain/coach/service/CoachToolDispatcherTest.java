package com.ssafy.dodam.domain.coach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.coach.dto.CoachFacts.Fact;
import com.ssafy.dodam.domain.coach.service.CoachTextGenerator.ToolCall;
import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisIssue;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisStatus;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisIssueRepository;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisRepository;
import com.ssafy.dodam.domain.forecast.dto.response.SpendingForecastResponse;
import com.ssafy.dodam.domain.forecast.service.SpendingForecastQueryService;
import com.ssafy.dodam.domain.goal.dto.response.GoalForecastResponse;
import com.ssafy.dodam.domain.goal.entity.Goal;
import com.ssafy.dodam.domain.goal.entity.GoalType;
import com.ssafy.dodam.domain.goal.repository.GoalRepository;
import com.ssafy.dodam.domain.goal.service.GoalCalculationService;
import com.ssafy.dodam.domain.loan.entity.Loan;
import com.ssafy.dodam.domain.loan.entity.LoanStatus;
import com.ssafy.dodam.domain.loan.repository.LoanRepository;
import com.ssafy.dodam.domain.simulation.dto.CombinedInput;
import com.ssafy.dodam.domain.simulation.dto.LoanPrepaymentInput;
import com.ssafy.dodam.domain.simulation.dto.LoanRefinanceInput;
import com.ssafy.dodam.domain.simulation.dto.SpendingReductionInput;
import com.ssafy.dodam.domain.simulation.dto.request.CombinedSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.LoanPrepaymentSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.LoanRefinanceSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.SavingSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.SpendingSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.response.SimulationResponse;
import com.ssafy.dodam.domain.simulation.entity.Simulation;
import com.ssafy.dodam.domain.simulation.entity.SimulationStatus;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import com.ssafy.dodam.domain.simulation.service.SimulationInputResolver;
import com.ssafy.dodam.domain.simulation.service.SimulationQueryService;
import com.ssafy.dodam.domain.simulation.service.SimulationRunner;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 640 A-2: 도구 호출 → 서버 실행. 인자 검증·라벨 매칭·실패 전달·호출 상한을 확인한다. 시뮬레이션 계산 자체는 Simulation 테스트가 맡으므로
 * Runner·Resolver는 대체한다.
 */
class CoachToolDispatcherTest {

    private static final long USER = 7L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 18, 10, 0);

    private final ObjectMapper json = new ObjectMapper();
    private final CoachToolRegistry registry = new CoachToolRegistry(json);
    private final SimulationInputResolver resolver = mock(SimulationInputResolver.class);
    private final SimulationRunner runner = mock(SimulationRunner.class);
    private final SimulationQueryService simulations = mock(SimulationQueryService.class);
    private final GoalRepository goals = mock(GoalRepository.class);
    private final GoalCalculationService goalCalculation = mock(GoalCalculationService.class);
    private final LoanRepository loans = mock(LoanRepository.class);
    private final DiagnosisRepository diagnoses = mock(DiagnosisRepository.class);
    private final DiagnosisIssueRepository issues = mock(DiagnosisIssueRepository.class);
    private final SpendingForecastQueryService spendingForecasts = mock(SpendingForecastQueryService.class);
    private final CoachClock clock = mock(CoachClock.class);
    private final SpendingReductionInput spendingInput = new SpendingReductionInput("CAFE", 50_000L, 12);
    private final LoanPrepaymentInput prepaymentInput = new LoanPrepaymentInput(31L, 1_000_000L, null);
    private final LoanRefinanceInput refinanceInput = new LoanRefinanceInput(31L, 10L, null);
    private final CombinedInput combinedInput = new CombinedInput(List.of(new SpendingReductionInput("CAFE", 50_000L, 12),
            new LoanPrepaymentInput(31L, 1_000_000L, null)));
    private final Simulation simulation = mock(Simulation.class);

    private CoachToolDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        given(clock.now()).willReturn(NOW);
        given(simulation.getSimulationId()).willReturn(88L);
        given(runner.run(eq(USER), any())).willReturn(new SimulationRunner.Outcome(simulation, true));
        given(simulations.toResponse(eq(USER), eq(simulation))).willReturn(response(88L, SimulationType.SPENDING));
        dispatcher = new CoachToolDispatcher(registry, resolver, runner, simulations, new CoachSimulationFactFormatter(), goals,
                goalCalculation, loans, diagnoses, issues, spendingForecasts, clock, json);
    }

    @Test
    void spendingForecastCarriesTheServerDecompositionAsFacts() {
        given(spendingForecasts.forecast(USER, null)).willReturn(new SpendingForecastResponse("2026-09", 3, null,
                new SpendingForecastResponse.Explanation(500_000L, 12_000L, true), List.of(),
                List.of(new SpendingForecastResponse.ForecastPoint("2026-10", 520_000L, 450_000L, 590_000L, 10_000L, 10_000L,
                                new BigDecimal("1.02"), 480_000L),
                        new SpendingForecastResponse.ForecastPoint("2026-12", 650_000L, 560_000L, 740_000L, 25_000L, 125_000L,
                                new BigDecimal("1.24"), null))));

        CoachToolResult result = dispatcher.dispatch(USER, List.of(new ToolCall("c1", "get_spending_forecast", "{}"))).get(0);

        assertThat(result.succeeded()).isTrue();
        assertThat(result.sections().get(0).facts()).containsExactly(
                new Fact("평소 한 달 변동 소비(계절 제외)", "500,000원"),
                new Fact("2026년 10월 예상 변동 소비", "520,000원"),
                new Fact("2026년 10월 예상 범위", "450,000원 ~ 590,000원"),
                new Fact("2026년 10월 주요 변화 요인", "추세 영향 +10,000원"),
                new Fact("2026년 10월 작년 같은 달 실적", "480,000원"),
                new Fact("2026년 12월 예상 변동 소비", "650,000원"),
                new Fact("2026년 12월 예상 범위", "560,000원 ~ 740,000원"),
                new Fact("2026년 12월 주요 변화 요인", "계절 영향 +125,000원"));
        verify(runner, never()).run(anyLong(), any());
    }

    @Test
    void spendingForecastWithoutSeasonalityOmitsSeasonalFactsAndSaysWhy() {
        given(spendingForecasts.forecast(USER, null)).willReturn(new SpendingForecastResponse("2026-09", 3, "SEASONALITY_UNAVAILABLE",
                new SpendingForecastResponse.Explanation(500_000L, -3_000L, false), List.of(),
                List.of(new SpendingForecastResponse.ForecastPoint("2026-10", 497_000L, 450_000L, 544_000L, -3_000L, 0L, null, null))));

        CoachToolResult result = dispatcher.dispatch(USER, List.of(new ToolCall("c1", "get_spending_forecast", "{}"))).get(0);

        assertThat(result.sections().get(0).facts()).extracting(Fact::label).noneMatch(label -> label.contains("계절 영향"));
        assertThat(result.sections().get(0).facts()).contains(new Fact("2026년 10월 주요 변화 요인", "추세 영향 -3,000원"));
        assertThat(result.notes()).anyMatch(note -> note.contains("12개월 미만"));
    }

    @Test
    void spendingForecastWithoutPredictionsIsAToolError() {
        given(spendingForecasts.forecast(USER, null)).willReturn(new SpendingForecastResponse("2026-09", 3, "USER_DATA_TOO_SHORT",
                null, List.of(), List.of()));

        CoachToolResult result = dispatcher.dispatch(USER, List.of(new ToolCall("c1", "get_spending_forecast", "{}"))).get(0);

        assertThat(result.succeeded()).isFalse();
        assertThat(result.error()).contains("3개월");
    }

    @Test
    void spendingToolResolvesLikeTheApiAndReturnsSimulationFacts() {
        given(resolver.resolve(any(SpendingSimulationRequest.class))).willReturn(spendingInput);

        List<CoachToolResult> results = dispatcher.dispatch(USER, List.of(
                new ToolCall("call_1", "simulate_spending_reduction", "{\"category\":\"cafe\",\"monthlyReduction\":\"50,000원\"}")));

        ArgumentCaptor<SpendingSimulationRequest> captor = ArgumentCaptor.forClass(SpendingSimulationRequest.class);
        verify(resolver).resolve(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new SpendingSimulationRequest("CAFE", 50_000L, null));
        CoachToolResult result = results.get(0);
        assertThat(result.succeeded()).isTrue();
        assertThat(result.toolCallId()).isEqualTo("call_1");
        assertThat(result.simulationId()).isEqualTo(88L);
        assertThat(result.created()).isTrue();
        assertThat(result.sections().get(0).facts()).contains(new Fact("단축(비상금)", "121일"));
        assertThat(result.notes()).contains(CoachSimulationFactFormatter.NOTE_SERVER_NUMBERS);
    }

    @Test
    void invalidArgumentsAndServerRejectionsBecomeToolErrorsWithoutRunning() {
        List<CoachToolResult> results = dispatcher.dispatch(USER, List.of(
                new ToolCall("c1", "simulate_spending_reduction", "{\"category\":\"카페\",\"monthlyReduction\":50000}"),
                new ToolCall("c2", "simulate_spending_reduction", "{}"),
                new ToolCall("c3", "fly_to_the_moon", "{}")));
        assertThat(results).extracting(CoachToolResult::succeeded).containsOnly(false);
        assertThat(results.get(0).error()).contains("카페").contains("CAFE");
        assertThat(results.get(1).error()).contains("줄일 금액");
        assertThat(results.get(2).error()).isEqualTo(CoachToolDispatcher.UNKNOWN_TOOL);
        verify(runner, never()).run(anyLong(), any());

        // 서버 검증(400) 메시지는 그대로 전달된다 — 모델이 되묻는 근거
        given(resolver.resolve(any(SpendingSimulationRequest.class))).willThrow(new InvalidRequestException("존재하지 않는 카테고리 코드입니다."));
        CoachToolResult rejected = dispatcher.dispatch(USER, List.of(
                new ToolCall("c4", "simulate_spending_reduction", "{\"monthlyReduction\":50000}"))).get(0);
        assertThat(rejected.error()).isEqualTo("존재하지 않는 카테고리 코드입니다.");
        assertThat(rejected.toModelText()).startsWith("실행 실패: ");
    }

    @Test
    void onlyThreeToolCallsRunPerTurn() {
        given(resolver.resolve(any(SpendingSimulationRequest.class))).willReturn(spendingInput);
        ToolCall call = new ToolCall("c", "simulate_spending_reduction", "{\"monthlyReduction\":10000}");
        List<CoachToolResult> results = dispatcher.dispatch(USER, List.of(call, call, call, call));
        assertThat(results).hasSize(4);
        assertThat(results.subList(0, 3)).allMatch(CoachToolResult::succeeded);
        assertThat(results.get(3).error()).isEqualTo(CoachToolDispatcher.TOO_MANY_TOOLS);
        verify(runner, org.mockito.Mockito.times(3)).run(eq(USER), any());
    }

    @Test
    void loanLabelResolvesOnlyAmongOwnOutstandingLoansAndAsksWhenAmbiguous() {
        Loan single = loan(31L, "KB 국민ONE대출", "KB국민은행", LoanStatus.ACTIVE, 12_998_492L);
        given(loans.findAll(any(Specification.class))).willReturn(List.of(single));
        given(resolver.resolve(eq(USER), any(LoanPrepaymentSimulationRequest.class))).willReturn(prepaymentInput);

        dispatcher.dispatch(USER, List.of(new ToolCall("c1", "simulate_loan_prepayment", "{\"prepaymentAmount\":1000000}")));
        ArgumentCaptor<LoanPrepaymentSimulationRequest> captor = ArgumentCaptor.forClass(LoanPrepaymentSimulationRequest.class);
        verify(resolver).resolve(eq(USER), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new LoanPrepaymentSimulationRequest(null, 31L, 1_000_000L, null));

        Loan second = loan(32L, "우리 드림카대출", "우리은행", LoanStatus.ACTIVE, 5_000_000L);
        Loan closed = loan(33L, "KB 옛 대출", "KB국민은행", LoanStatus.COMPLETED, 0L);
        given(loans.findAll(any(Specification.class))).willReturn(List.of(single, second, closed));
        CoachToolResult ambiguous = dispatcher.dispatch(USER, List.of(
                new ToolCall("c2", "simulate_loan_prepayment", "{\"prepaymentAmount\":1000000}"))).get(0);
        assertThat(ambiguous.error()).contains("어느 대출인지").contains("KB 국민ONE대출").contains("우리 드림카대출").doesNotContain("옛 대출");
        CoachToolResult matched = dispatcher.dispatch(USER, List.of(
                new ToolCall("c3", "simulate_loan_prepayment", "{\"prepaymentAmount\":1000000,\"loanLabel\":\"국민ONE\"}"))).get(0);
        assertThat(matched.succeeded()).isTrue();
        CoachToolResult unknown = dispatcher.dispatch(USER, List.of(
                new ToolCall("c4", "simulate_loan_prepayment", "{\"prepaymentAmount\":1000000,\"loanLabel\":\"신한\"}"))).get(0);
        assertThat(unknown.error()).contains("찾지 못했어요");
    }

    @Test
    void refinanceTargetComesFromTheLatestDiagnosisEstimate() {
        Loan loan = loan(31L, "KB 국민ONE대출", "KB국민은행", LoanStatus.ACTIVE, 12_998_492L);
        given(loans.findAll(any(Specification.class))).willReturn(List.of(loan));
        given(resolver.resolve(eq(USER), any(LoanRefinanceSimulationRequest.class))).willReturn(refinanceInput);
        given(diagnoses.findFirstByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDescDiagnosisIdDesc(USER)).willReturn(Optional.empty());
        assertThat(dispatcher.dispatch(USER, List.of(new ToolCall("c1", "simulate_loan_refinance", "{}"))).get(0).error())
                .contains("진단");

        Diagnosis diagnosis = Diagnosis.start(USER, null, NOW);
        ReflectionTestUtils.setField(diagnosis, "diagnosisId", 500L);
        given(diagnoses.findFirstByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDescDiagnosisIdDesc(USER)).willReturn(Optional.of(diagnosis));
        ObjectNode current = json.createObjectNode().put("loanId", 31L);
        current.putObject("refinance").put("available", true).put("targetProductId", 10L).put("appliedRate", 5.028);
        given(issues.findAllOwned(USER, 500L)).willReturn(List.of(DiagnosisIssue.ofRule(500L, "LOAN", "HIGH_INTEREST_RATE",
                IssueSeverity.HIGH, "금리", "높아요", current, null, null, NOW)));

        dispatcher.dispatch(USER, List.of(new ToolCall("c2", "simulate_loan_refinance", "{\"targetProductName\":\"카카오뱅크 신용대출\"}")));
        ArgumentCaptor<LoanRefinanceSimulationRequest> captor = ArgumentCaptor.forClass(LoanRefinanceSimulationRequest.class);
        verify(resolver).resolve(eq(USER), captor.capture());
        assertThat(captor.getValue()).isEqualTo(new LoanRefinanceSimulationRequest(null, 31L, 10L, null));

        current.putObject("refinance").put("available", false).put("unavailableReason", "NO_ELIGIBLE_PRODUCT");
        assertThat(dispatcher.dispatch(USER, List.of(new ToolCall("c3", "simulate_loan_refinance", "{}"))).get(0).error())
                .contains("대환 상품이 없어");
    }

    @Test
    void combinedToolBuildsSpecScenariosWithResolvedIds() {
        Loan loan = loan(31L, "KB 국민ONE대출", "KB국민은행", LoanStatus.ACTIVE, 12_998_492L);
        given(loans.findAll(any(Specification.class))).willReturn(List.of(loan));
        Goal goal = Goal.create(USER, "비상금 500만 원", GoalType.SAVING, NOW);
        ReflectionTestUtils.setField(goal, "id", 5L);
        given(goals.findAllByUserIdAndDeletedAtIsNull(USER)).willReturn(List.of(goal));
        given(resolver.resolve(eq(USER), any(CombinedSimulationRequest.class))).willReturn(combinedInput);
        given(simulations.toResponse(eq(USER), eq(simulation))).willReturn(response(88L, SimulationType.COMBINED));

        CoachToolResult result = dispatcher.dispatch(USER, registry.chip("CHIP_COMBINED").orElseThrow()).get(0);

        assertThat(result.succeeded()).isTrue();
        ArgumentCaptor<CombinedSimulationRequest> captor = ArgumentCaptor.forClass(CombinedSimulationRequest.class);
        verify(resolver).resolve(eq(USER), captor.capture());
        List<JsonNode> scenarios = captor.getValue().scenarios();
        assertThat(scenarios).hasSize(2);
        assertThat(scenarios.get(0).get("type").asText()).isEqualTo("SPENDING");
        assertThat(scenarios.get(0).at("/parameters/category").asText()).isEqualTo("CAFE");
        assertThat(scenarios.get(0).at("/parameters/monthlyReduction").asLong()).isEqualTo(50_000L);
        assertThat(scenarios.get(1).get("type").asText()).isEqualTo("LOAN_PREPAYMENT");
        assertThat(scenarios.get(1).at("/parameters/loanId").asLong()).isEqualTo(31L);
        assertThat(scenarios.get(1).at("/parameters/prepaymentAmount").asLong()).isEqualTo(1_000_000L);
        assertThat(scenarios.get(1).has("parameters")).isTrue();
        assertThat(scenarios.get(1).toString()).doesNotContain("accountNo");

        // 저축 자식은 목표 이름을 goalId로 바꾼다
        dispatcher.dispatch(USER, List.of(new ToolCall("c2", "simulate_combined",
                "{\"scenarios\":[{\"type\":\"SAVING\",\"monthlySavingAmount\":300000,\"goalName\":\"비상금\"},{\"type\":\"SPENDING\",\"monthlyReduction\":30000}]}")));
        verify(resolver, org.mockito.Mockito.times(2)).resolve(eq(USER), captor.capture());
        assertThat(captor.getValue().scenarios().get(0).at("/parameters/goalId").asLong()).isEqualTo(5L);
        assertThat(captor.getValue().scenarios().get(0).at("/parameters/monthlySavingAmount").asLong()).isEqualTo(300_000L);

        assertThat(dispatcher.dispatch(USER, List.of(new ToolCall("c3", "simulate_combined", "{\"scenarios\":[{\"type\":\"SPENDING\",\"monthlyReduction\":1}]}")))
                .get(0).error()).contains("두 개 이상");
    }

    @Test
    void goalForecastAndLatestDiagnosisAreReadOnlyTools() {
        Goal goal = Goal.create(USER, "비상금 500만 원", GoalType.SAVING, NOW);
        ReflectionTestUtils.setField(goal, "id", 5L);
        given(goals.findAllByUserIdAndDeletedAtIsNull(USER)).willReturn(List.of(goal));
        given(goals.findByIdAndUserIdAndDeletedAtIsNull(5L, USER)).willReturn(Optional.of(goal));
        given(goalCalculation.getForecast(USER, 5L, NOW.toLocalDate())).willReturn(new GoalForecastResponse(5L, 5_000_000L, 2_000_000L,
                3_000_000L, NOW.toLocalDate(), LocalDate.of(2027, 6, 1), 200_000L, 15, LocalDate.of(2027, 12, 18), 200L, false, List.of()));

        CoachToolResult forecast = dispatcher.dispatch(USER, List.of(new ToolCall("c1", "get_goal_forecast", "{}"))).get(0);
        assertThat(forecast.succeeded()).isTrue();
        assertThat(forecast.simulationId()).isNull();
        assertThat(forecast.sections().get(0).facts()).contains(
                new Fact("목표", "비상금 500만 원"), new Fact("남은 금액", "3,000,000원"),
                new Fact("예상 달성일(현재 속도)", "2027-12-18"), new Fact("목표일보다 늦음", "200일"));
        verify(runner, never()).run(anyLong(), any());

        given(diagnoses.findFirstByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDescDiagnosisIdDesc(USER)).willReturn(Optional.empty());
        assertThat(dispatcher.dispatch(USER, List.of(new ToolCall("c2", "get_latest_diagnosis", "{}"))).get(0).error()).contains("진단");

        Diagnosis diagnosis = Diagnosis.start(USER, null, NOW);
        ReflectionTestUtils.setField(diagnosis, "diagnosisId", 500L);
        diagnosis.complete(2, 1, DiagnosisStatus.RISK, "v7-cluster-hybrid", null, NOW);
        given(diagnoses.findFirstByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDescDiagnosisIdDesc(USER)).willReturn(Optional.of(diagnosis));
        given(issues.findAllOwned(USER, 500L)).willReturn(List.of(
                DiagnosisIssue.ofRule(500L, "SPENDING", "CATEGORY_SPENDING_INCREASE", IssueSeverity.LOW, "식비 소비가 2개월째 늘고 있어요", "d", null, null, null, NOW),
                DiagnosisIssue.ofRule(500L, "LOAN", "HIGH_INTEREST_RATE", IssueSeverity.HIGH, "금리가 높아요", "d", null, null, null, NOW)));
        CoachToolResult latest = dispatcher.dispatch(USER, List.of(new ToolCall("c3", "get_latest_diagnosis", "{}"))).get(0);
        // 판정 코드(RISK·HIGH)는 사실에 넣지 않는다 — 모델 입력이자 결과 카드 값이다
        assertThat(latest.sections().get(0).facts()).contains(new Fact("종합 상태(서버 판정)", "위험"), new Fact("문제 수", "2건"),
                new Fact("문제 1 (심각도 높음)", "금리가 높아요"));
    }

    // ---------- fixtures ----------

    private SimulationResponse response(long id, SimulationType type) {
        ObjectNode before = json.createObjectNode();
        before.putArray("goals").addObject().put("goalId", 5).put("name", "비상금");
        return new SimulationResponse(id, type, "시뮬레이션", "요약", SimulationStatus.COMPLETED, NOW, NOW, null, List.of(), before,
                json.createObjectNode(), json.createObjectNode(), null,
                List.of(new com.ssafy.dodam.domain.simulation.dto.response.SimulationGoalImpactResponse(5L, LocalDate.of(2028, 2, 13),
                        LocalDate.of(2027, 10, 15), -121, "COMPUTED", List.of())));
    }

    private static Loan loan(long id, String name, String institution, LoanStatus status, long balance) {
        Loan loan = mock(Loan.class);
        given(loan.getId()).willReturn(id);
        given(loan.getAccountName()).willReturn(name);
        given(loan.getInstitutionName()).willReturn(institution);
        given(loan.getStatus()).willReturn(status);
        given(loan.getBalance()).willReturn(balance);
        return loan;
    }
}
