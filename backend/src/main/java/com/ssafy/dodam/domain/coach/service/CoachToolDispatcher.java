package com.ssafy.dodam.domain.coach.service;

import static com.ssafy.dodam.domain.loan.repository.LoanSpecifications.ownedBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.coach.dto.CoachFacts.Fact;
import com.ssafy.dodam.domain.coach.dto.CoachFacts.Section;
import com.ssafy.dodam.domain.coach.service.CoachTextGenerator.ToolCall;
import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisIssue;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisIssueRepository;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisRepository;
import com.ssafy.dodam.domain.goal.dto.response.GoalForecastResponse;
import com.ssafy.dodam.domain.goal.entity.Goal;
import com.ssafy.dodam.domain.goal.entity.GoalStatus;
import com.ssafy.dodam.domain.goal.entity.GoalType;
import com.ssafy.dodam.domain.goal.repository.GoalRepository;
import com.ssafy.dodam.domain.goal.service.GoalCalculationService;
import com.ssafy.dodam.domain.loan.entity.Loan;
import com.ssafy.dodam.domain.loan.repository.LoanRepository;
import com.ssafy.dodam.domain.simulation.dto.SimulationInput;
import com.ssafy.dodam.domain.simulation.dto.request.CombinedSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.LoanPrepaymentSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.LoanRefinanceSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.SavingSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.request.SpendingSimulationRequest;
import com.ssafy.dodam.domain.simulation.dto.response.SimulationResponse;
import com.ssafy.dodam.domain.simulation.service.SimulationInputResolver;
import com.ssafy.dodam.domain.simulation.service.SimulationQueryService;
import com.ssafy.dodam.domain.simulation.service.SimulationRunner;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 모델이 고른 도구 호출을 서버 API와 같은 경로로 실행한다(640 A-2, 계획 §1-2 4단계).
 *
 * <ul>
 *   <li>사용자 ID는 토큰에서 온 값만 쓴다. 인자에는 식별자가 없고, 목표·대출은 이름으로 받아 <b>본인 것 중에서</b> 찾는다.
 *       매칭이 없거나 여럿이면 실행하지 않고 되묻는 결과를 돌려준다(엉뚱한 대상으로 시뮬레이션하지 않는다).</li>
 *   <li>시뮬레이션은 {@code SimulationInputResolver} → {@code SimulationRunner.run}(잠금·지문·저장) → {@code SimulationQueryService.toResponse}로
 *       정상 API와 같은 행을 남긴다. 같은 날 같은 지문이면 재사용({@code created=false})이라 반복 질문에 행이 늘지 않는다.</li>
 *   <li>검증 실패(400·404·시뮬레이션 규칙)는 예외로 올리지 않고 도구 결과 {@code error}로 담아 2차 호출에 넘긴다 — 모델이 서버 메시지를 그대로
 *       전달한다("카페 월평균 소비보다 큰 금액이에요"). HTTP는 200이다.</li>
 *   <li>한 턴에 {@link CoachToolRegistry#MAX_TOOL_CALLS_PER_TURN}개까지만 실행하고, 그 밖의 호출은 실행하지 않은 사유를 돌려준다.</li>
 *   <li>로그에는 도구 이름·인자 키·simulationId만 남긴다(사용자 문장·인자 값·응답 전문은 남기지 않는다).</li>
 * </ul>
 */
@Component
public class CoachToolDispatcher {
    private static final Logger log = LoggerFactory.getLogger(CoachToolDispatcher.class);
    static final String UNKNOWN_TOOL = "알 수 없는 도구예요. 지원하는 시뮬레이션인지 확인해 주세요.";
    static final String TOO_MANY_TOOLS = "한 번에 실행할 수 있는 시뮬레이션은 3개까지예요. 나눠서 물어봐 주세요.";
    static final String INTERNAL_FAILURE = "시뮬레이션을 실행하는 중 문제가 생겼어요. 잠시 뒤 다시 시도해 주세요.";

    private final CoachToolRegistry registry;
    private final SimulationInputResolver resolver;
    private final SimulationRunner runner;
    private final SimulationQueryService simulations;
    private final CoachSimulationFactFormatter formatter;
    private final GoalRepository goals;
    private final GoalCalculationService goalCalculation;
    private final LoanRepository loans;
    private final DiagnosisRepository diagnoses;
    private final DiagnosisIssueRepository issues;
    private final CoachClock clock;
    private final ObjectMapper json;

    public CoachToolDispatcher(CoachToolRegistry registry, SimulationInputResolver resolver, SimulationRunner runner,
            SimulationQueryService simulations, CoachSimulationFactFormatter formatter, GoalRepository goals,
            GoalCalculationService goalCalculation, LoanRepository loans, DiagnosisRepository diagnoses,
            DiagnosisIssueRepository issues, CoachClock clock, ObjectMapper json) {
        this.registry = registry;
        this.resolver = resolver;
        this.runner = runner;
        this.simulations = simulations;
        this.formatter = formatter;
        this.goals = goals;
        this.goalCalculation = goalCalculation;
        this.loans = loans;
        this.diagnoses = diagnoses;
        this.issues = issues;
        this.clock = clock;
        this.json = json;
    }

    public List<CoachToolResult> dispatch(Long userId, List<ToolCall> calls) {
        List<CoachToolResult> results = new ArrayList<>();
        for (int i = 0; i < calls.size(); i++) {
            ToolCall call = calls.get(i);
            if (i >= CoachToolRegistry.MAX_TOOL_CALLS_PER_TURN) {
                results.add(CoachToolResult.error(call.id(), call.name(), TOO_MANY_TOOLS));
                continue;
            }
            results.add(dispatchOne(userId, call));
        }
        return results;
    }

    CoachToolResult dispatchOne(Long userId, ToolCall call) {
        if (!registry.supports(call.name())) {
            return CoachToolResult.error(call.id(), call.name(), UNKNOWN_TOOL);
        }
        try {
            CoachToolArguments arguments = CoachToolArguments.parse(json, call.argumentsJson());
            log.info("Coach tool call: tool={} argumentKeys={}", call.name(), arguments.raw().fieldNames().hasNext()
                    ? String.join(",", iterableToList(arguments.raw().fieldNames())) : "-");
            return switch (call.name()) {
                case CoachToolRegistry.SIMULATE_SPENDING_REDUCTION -> simulate(userId, call, resolver.resolve(spendingRequest(arguments)));
                case CoachToolRegistry.SIMULATE_SAVING_CHANGE -> simulate(userId, call, resolver.resolve(userId, savingRequest(userId, arguments)));
                case CoachToolRegistry.SIMULATE_LOAN_PREPAYMENT -> simulate(userId, call, resolver.resolve(userId, prepaymentRequest(userId, arguments)));
                case CoachToolRegistry.SIMULATE_LOAN_REFINANCE -> simulate(userId, call, resolver.resolve(userId, refinanceRequest(userId, arguments)));
                case CoachToolRegistry.SIMULATE_COMBINED -> simulate(userId, call, resolver.resolve(userId, combinedRequest(userId, arguments)));
                case CoachToolRegistry.GET_GOAL_FORECAST -> goalForecast(userId, call, arguments);
                case CoachToolRegistry.GET_LATEST_DIAGNOSIS -> latestDiagnosis(userId, call);
                default -> CoachToolResult.error(call.id(), call.name(), UNKNOWN_TOOL);
            };
        } catch (CoachToolArgumentException | InvalidRequestException | ResourceNotFoundException | BusinessException rejected) {
            // 서버 검증 메시지를 그대로 전달한다. 모델이 사용자에게 되묻는 근거가 된다
            log.info("Coach tool rejected: tool={} reason={}", call.name(), rejected.getClass().getSimpleName());
            return CoachToolResult.error(call.id(), call.name(), rejected.getMessage());
        } catch (RuntimeException failure) {
            log.warn("Coach tool failed: tool={} error={}", call.name(), failure.getMessage());
            return CoachToolResult.error(call.id(), call.name(), INTERNAL_FAILURE);
        }
    }

    // ---------- 시뮬레이션 ----------

    private CoachToolResult simulate(Long userId, ToolCall call, SimulationInput input) {
        SimulationRunner.Outcome outcome = runner.run(userId, input);
        SimulationResponse response = simulations.toResponse(userId, outcome.simulation());
        log.info("Coach tool simulated: tool={} simulationId={} created={}", call.name(), response.simulationId(), outcome.created());
        return CoachToolResult.ok(call.id(), call.name(), response.simulationId(), outcome.created(),
                formatter.sections(response), formatter.notes(response));
    }

    SpendingSimulationRequest spendingRequest(CoachToolArguments arguments) {
        String category = arguments.code("category", CoachToolRegistry.SPENDING_CATEGORY_CODE_SET, "카테고리");
        long reduction = arguments.requiredAmount("monthlyReduction", "줄일 금액");
        return new SpendingSimulationRequest(category, reduction, arguments.months("durationMonths"));
    }

    SavingSimulationRequest savingRequest(Long userId, CoachToolArguments arguments) {
        long amount = arguments.requiredAmount("monthlySavingAmount", "변경 후 월 저축액");
        Long goalId = savingGoalId(userId, arguments.text("goalName"), true);
        return new SavingSimulationRequest(null, goalId, amount, arguments.months("durationMonths"));
    }

    LoanPrepaymentSimulationRequest prepaymentRequest(Long userId, CoachToolArguments arguments) {
        long amount = arguments.requiredAmount("prepaymentAmount", "추가 상환액");
        Long loanId = loanId(userId, arguments.text("loanLabel"));
        return new LoanPrepaymentSimulationRequest(null, loanId, amount, null);
    }

    LoanRefinanceSimulationRequest refinanceRequest(Long userId, CoachToolArguments arguments) {
        Long loanId = loanId(userId, arguments.text("loanLabel"));
        Long targetProductId = refinanceTargetProductId(userId, loanId);
        return new LoanRefinanceSimulationRequest(null, loanId, targetProductId, null);
    }

    /** 자식 인자를 단독 도구와 같은 규칙으로 해석해 명세 17 복합 본문({@code type}·{@code parameters})으로 만든다. */
    CombinedSimulationRequest combinedRequest(Long userId, CoachToolArguments arguments) {
        List<CoachToolArguments> scenarios = arguments.items("scenarios");
        if (scenarios.size() < 2) {
            throw new CoachToolArgumentException("함께 실행할 행동을 두 개 이상 알려 주세요.");
        }
        if (scenarios.size() > 10) {
            throw new CoachToolArgumentException("함께 실행할 행동은 10개까지예요.");
        }
        List<JsonNode> nodes = new ArrayList<>();
        for (CoachToolArguments scenario : scenarios) {
            String type = scenario.code("type", CoachToolRegistry.COMBINED_SCENARIO_TYPE_SET, "행동 유형");
            if (type == null) {
                throw new CoachToolArgumentException("각 행동의 유형(SPENDING·SAVING·LOAN_PREPAYMENT·LOAN_REFINANCE)을 알려 주세요.");
            }
            ObjectNode node = json.createObjectNode();
            node.put("type", type);
            ObjectNode parameters = node.putObject("parameters");
            switch (type) {
                case "SPENDING" -> {
                    SpendingSimulationRequest request = spendingRequest(scenario);
                    if (request.category() != null) {
                        parameters.put("category", request.category());
                    }
                    parameters.put("monthlyReduction", request.monthlyReduction());
                    if (request.durationMonths() != null) {
                        parameters.put("durationMonths", request.durationMonths());
                    }
                }
                case "SAVING" -> {
                    SavingSimulationRequest request = savingRequest(userId, scenario);
                    if (request.goalId() != null) {
                        parameters.put("goalId", request.goalId());
                    }
                    parameters.put("monthlySavingAmount", request.monthlySavingAmount());
                    if (request.durationMonths() != null) {
                        parameters.put("durationMonths", request.durationMonths());
                    }
                }
                case "LOAN_PREPAYMENT" -> {
                    LoanPrepaymentSimulationRequest request = prepaymentRequest(userId, scenario);
                    parameters.put("loanId", request.loanId()).put("prepaymentAmount", request.prepaymentAmount());
                }
                case "LOAN_REFINANCE" -> {
                    LoanRefinanceSimulationRequest request = refinanceRequest(userId, scenario);
                    parameters.put("loanId", request.loanId()).put("targetProductId", request.targetProductId());
                }
                default -> throw new CoachToolArgumentException("지원하지 않는 행동 유형이에요: " + type);
            }
            nodes.add(node);
        }
        return new CombinedSimulationRequest(nodes);
    }

    // ---------- 조회 도구 ----------

    private CoachToolResult goalForecast(Long userId, ToolCall call, CoachToolArguments arguments) {
        Long goalId = savingGoalId(userId, arguments.text("goalName"), false);
        GoalForecastResponse forecast = goalCalculation.getForecast(userId, goalId, clock.now().toLocalDate());
        String name = goals.findByIdAndUserIdAndDeletedAtIsNull(goalId, userId).map(Goal::getName).orElse("목표 " + goalId);
        List<Fact> facts = new ArrayList<>();
        facts.add(new Fact("목표", name));
        facts.add(new Fact("목표 금액", CoachFormat.money(forecast.targetAmount())));
        facts.add(new Fact("현재 모은 금액", CoachFormat.money(forecast.currentAmount())));
        facts.add(new Fact("남은 금액", CoachFormat.money(forecast.remainingAmount())));
        facts.add(new Fact("현재 월 저축 속도", CoachFormat.money(forecast.averageMonthlySaving())));
        facts.add(new Fact("목표일", CoachFormat.date(forecast.targetDate())));
        facts.add(new Fact("예상 달성일(현재 속도)", forecast.expectedAchievementDate() == null ? "현재 속도로는 달성 불가"
                : CoachFormat.date(forecast.expectedAchievementDate())));
        if (forecast.daysDifference() != null) {
            long days = forecast.daysDifference();
            facts.add(new Fact(days > 0 ? "목표일보다 늦음" : "목표일보다 빠름", CoachFormat.days((int) Math.abs(days))));
        }
        return CoachToolResult.ok(call.id(), call.name(), null, null, List.of(new Section("목표 예상 달성일(서버 계산)", facts)),
                List.of("예상 달성일은 서버가 현재 저축 속도로 계산한 값이다. 새 날짜를 만들지 말 것."));
    }

    private CoachToolResult latestDiagnosis(Long userId, ToolCall call) {
        Optional<Diagnosis> latest = diagnoses.findFirstByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDescDiagnosisIdDesc(userId);
        if (latest.isEmpty()) {
            return CoachToolResult.error(call.id(), call.name(), "아직 완료된 진단이 없어요. 발견 화면에서 진단을 먼저 실행해 주세요.");
        }
        Diagnosis diagnosis = latest.get();
        List<Fact> facts = new ArrayList<>();
        facts.add(new Fact("진단 완료 시각", CoachFormat.dateTime(diagnosis.getCompletedAt())));
        facts.add(new Fact("종합 상태(서버 판정)", diagnosis.getOverallStatus() == null ? "판정 없음" : diagnosis.getOverallStatus().name()));
        facts.add(new Fact("문제 수", CoachFormat.count(diagnosis.getIssueCount())));
        int index = 1;
        for (DiagnosisIssue issue : issues.findAllOwned(userId, diagnosis.getDiagnosisId())) {
            if (index > 5) {
                break;
            }
            facts.add(new Fact("문제 " + index++ + " (" + issue.getSeverity().name() + ")", issue.getTitle()));
        }
        return CoachToolResult.ok(call.id(), call.name(), null, null, List.of(new Section("최근 진단(서버 판정)", facts)),
                List.of("문제 목록과 심각도는 서버 진단 규칙의 판정이다. 새 문제를 만들거나 심각도를 바꾸지 말 것."));
    }

    // ---------- 라벨 매칭 ----------

    /**
     * 본인의 ACTIVE 모으기 목표 중에서 이름으로 찾는다. 이름이 없고 목표가 하나면 그것, 여럿이면 되묻는다.
     *
     * @param nullableWhenAbsent 이름도 없고 목표도 여럿일 때 null을 허용할지(저축 시뮬레이션은 목표 미지정 = 전체 배분이 허용된다)
     */
    Long savingGoalId(Long userId, String goalName, boolean nullableWhenAbsent) {
        List<Goal> candidates = goals.findAllByUserIdAndDeletedAtIsNull(userId).stream()
                .filter(goal -> goal.getType() == GoalType.SAVING && goal.getStatus() == GoalStatus.ACTIVE)
                .toList();
        if (candidates.isEmpty()) {
            throw new CoachToolArgumentException("진행 중인 모으기 목표가 없어요. 목표를 먼저 만들어 주세요.");
        }
        if (goalName == null || goalName.isBlank()) {
            if (candidates.size() == 1) {
                return candidates.get(0).getId();
            }
            if (nullableWhenAbsent) {
                return null;
            }
            throw new CoachToolArgumentException("어느 목표인지 알려 주세요: " + names(candidates.stream().map(Goal::getName).toList()));
        }
        List<Goal> matched = match(candidates, goalName, Goal::getName);
        if (matched.size() == 1) {
            return matched.get(0).getId();
        }
        if (matched.isEmpty()) {
            throw new CoachToolArgumentException("'" + goalName + "' 목표를 찾지 못했어요. 진행 중인 목표: "
                    + names(candidates.stream().map(Goal::getName).toList()));
        }
        throw new CoachToolArgumentException("'" + goalName + "'에 맞는 목표가 여러 개예요: " + names(matched.stream().map(Goal::getName).toList()));
    }

    /** 본인의 잔액 있는 대출 중에서 라벨(계좌명·기관명)로 찾는다. 대출이 하나면 라벨 없이도 그것이다. */
    Long loanId(Long userId, String label) {
        List<Loan> candidates = loans.findAll(ownedBy(userId)).stream()
                .filter(loan -> loan.getStatus().isOutstanding() && loan.getBalance() != null && loan.getBalance() > 0)
                .toList();
        if (candidates.isEmpty()) {
            throw new CoachToolArgumentException("잔액이 있는 대출이 없어요.");
        }
        if (label == null || label.isBlank()) {
            if (candidates.size() == 1) {
                return candidates.get(0).getId();
            }
            throw new CoachToolArgumentException("어느 대출인지 알려 주세요: " + names(candidates.stream().map(Loan::getAccountName).toList()));
        }
        List<Loan> matched = match(candidates, label, loan -> loan.getAccountName() + " " + loan.getInstitutionName());
        if (matched.size() == 1) {
            return matched.get(0).getId();
        }
        if (matched.isEmpty()) {
            throw new CoachToolArgumentException("'" + label + "' 대출을 찾지 못했어요. 보유 대출: "
                    + names(candidates.stream().map(Loan::getAccountName).toList()));
        }
        throw new CoachToolArgumentException("'" + label + "'에 맞는 대출이 여러 개예요: " + names(matched.stream().map(Loan::getAccountName).toList()));
    }

    /**
     * 대환 대상 상품은 최신 완료 진단의 {@code LOAN/HIGH_INTEREST_RATE} 문제가 추정한 {@code currentValue.refinance.targetProductId}다
     * (계획 §1-4). 진단이 없거나 그 대출에 조건 맞는 상품이 없으면 실행하지 않고 사유를 돌려준다. {@code targetProductName}은 1차 범위에서
     * 상품 검색에 쓰지 않는다 — 진단이 이미 같은 비교군·업권에서 최저금리 상품을 골라 두었다.
     */
    Long refinanceTargetProductId(Long userId, Long loanId) {
        Optional<Diagnosis> latest = diagnoses.findFirstByUserIdAndCompletedAtIsNotNullOrderByCompletedAtDescDiagnosisIdDesc(userId);
        if (latest.isEmpty()) {
            throw new CoachToolArgumentException("대환 상품을 고르려면 먼저 진단이 필요해요. 발견 화면에서 진단을 실행해 주세요.");
        }
        for (DiagnosisIssue issue : issues.findAllOwned(userId, latest.get().getDiagnosisId())) {
            if (!"LOAN".equals(issue.getDomain()) || !"HIGH_INTEREST_RATE".equals(issue.getType()) || issue.getCurrentValue() == null) {
                continue;
            }
            JsonNode current = issue.getCurrentValue();
            if (current.path("loanId").asLong(-1) != loanId) {
                continue;
            }
            JsonNode refinance = current.path("refinance");
            if (refinance.path("available").asBoolean(false) && refinance.path("targetProductId").isNumber()) {
                return refinance.path("targetProductId").longValue();
            }
            throw new CoachToolArgumentException("이 대출은 잔여 기간·상환방식에 맞는 대환 상품이 없어 절감액을 계산할 수 없어요.");
        }
        throw new CoachToolArgumentException("최근 진단에서 이 대출은 고금리로 판정되지 않아 대환 시뮬레이션 대상이 아니에요.");
    }

    private static <T> List<T> match(List<T> candidates, String label, java.util.function.Function<T, String> nameOf) {
        String needle = normalize(label);
        List<T> exact = candidates.stream().filter(c -> normalize(nameOf.apply(c)).equals(needle)).toList();
        if (!exact.isEmpty()) {
            return exact;
        }
        return candidates.stream().filter(c -> normalize(nameOf.apply(c)).contains(needle) || needle.contains(normalize(nameOf.apply(c))))
                .toList();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static String names(List<String> names) {
        return String.join(", ", names);
    }

    private static List<String> iterableToList(java.util.Iterator<String> iterator) {
        List<String> list = new ArrayList<>();
        iterator.forEachRemaining(list::add);
        return list;
    }
}
