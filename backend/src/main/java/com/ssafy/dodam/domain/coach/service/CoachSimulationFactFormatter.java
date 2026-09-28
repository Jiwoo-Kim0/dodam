package com.ssafy.dodam.domain.coach.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ssafy.dodam.domain.coach.dto.CoachFacts.Fact;
import com.ssafy.dodam.domain.coach.dto.CoachFacts.Section;
import com.ssafy.dodam.domain.simulation.dto.response.SimulationGoalImpactResponse;
import com.ssafy.dodam.domain.simulation.dto.response.SimulationResponse;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * {@link SimulationResponse} → 코치 사실(640 A-2, 계획 §1-4 마지막 단락). 정해진 필드만 옮긴다:
 * {@code goalImpacts[].differenceDays}, {@code comparison.items/costs}의 이자 항목, 단일 추가상환의 다음 회차 납입액.
 * 값은 서버가 포맷한 문자열(천단위·ISO 날짜)이며 그 문자열이 그대로 {@code CoachNumberGuard}의 허용 숫자가 된다(ai-coach.md §4).
 *
 * <p>자유 JSON(스냅샷 전체)을 모델에 넘기지 않는다. 계좌번호 원문은 DTO에 없지만, 넘기는 필드를 화이트리스트로 두어야 도메인에 컬럼이
 * 늘어도 프롬프트가 조용히 넓어지지 않는다.
 *
 * <p><b>사실에는 내부 값을 넣지 않는다.</b> 여기서 만든 문자열은 모델 입력이면서 동시에 응답 {@code facts}로 FE 결과 카드에 라벨·값
 * 그대로 그려지고, 세션 이력에도 남는다. simulationId는 {@code toolResults[].simulationId}로 따로 내려가므로 사실에 다시 쓰지 않고,
 * 유형·가정 코드({@code LOAN_PREPAYMENT}, {@code FUNDING_SOURCE_UNKNOWN})는 한글 문장으로 바꿔 넣거나 넣지 않는다.
 *
 * <p><b>목표 달성일을 계산하지 않은 시뮬레이션</b>(대출 추가 상환·대환처럼 자금 출처를 모르는 유형, 그 유형이 섞인 복합)은 엔진이
 * {@code NOT_COMPUTABLE}로 표시한다. 이것을 "계산 불가"라는 값으로 내보내면 사용자는 "대출 갚으면 목표는 계산 불가"라는 뜻 없는 문장을
 * 읽게 되므로, 그 목표의 달성일 줄은 만들지 않고 [주의]로만 모델에 알린다. 복합에서 소비·저축 자식이 하나 있으면 그 자식의 단독 결과
 * ({@code after.details.children[].goalImpacts}, simulation.md §복합)를 "소비 줄이기·저축 변경만 반영한 값"임을 밝히고 대신 쓴다 —
 * "둘 다 하면?"에 목표 이야기를 아예 못 하는 것보다 범위를 밝힌 답이 낫다.
 */
@Component
public class CoachSimulationFactFormatter {
    static final String RESULT_TITLE = "예상 결과";
    static final String ASSUMPTIONS_TITLE = "적용 가정(서버)";
    static final String NOTE_SERVER_NUMBERS = "달성일·절감액은 서버 시뮬레이션 엔진이 계산한 값이다. 새 숫자를 만들지 말 것.";
    /** 달성일 줄이 하나도 없을 때. 모델이 목표 이야기를 꾸며 내지 않게 한다. */
    static final String NOTE_GOALS_NOT_PROJECTED = "이 시뮬레이션은 모으기 목표 달성일 변화를 계산하지 않았다"
            + "(상환·가입 자금이 어느 계좌·목표에서 나가는지 알 수 없음). 목표 달성일을 언급하거나 추정하지 말 것.";
    /** 일부 목표만 달성일이 있을 때. */
    static final String NOTE_SOME_GOALS_NOT_PROJECTED = "달성일이 적히지 않은 목표는 변화를 계산하지 않은 것이다. 그 목표의 달성일을 말하거나 추정하지 말 것.";
    /** 복합에서 소비·저축 자식의 단독 결과를 대신 쓴 경우. */
    static final String NOTE_GOALS_PARTIAL = "목표 달성일 변화는 소비 줄이기·저축 변경 부분만 반영한 값이다. "
            + "대출 상환·대환이 목표에 미치는 영향은 계산하지 않았다고 함께 말할 것.";
    static final String NOTE_NOT_ACHIEVABLE = "'달성 불가'는 그 저축 속도로는 목표 금액에 이르지 못한다는 뜻이다. 날짜를 추정하지 말 것.";

    static final String LABEL_BEFORE_DATE = "현재 예상 달성일(";
    static final String LABEL_AFTER_DATE = "실행 후 예상 달성일(";
    static final String LABEL_SHORTENED = "단축(";
    static final String LABEL_DELAYED = "지연(";
    static final String LABEL_GOAL_SCOPE = "달성일 계산 범위";
    static final String LABEL_INTEREST_SAVING = "만기까지 이자 절감(수수료 제외)";
    static final String LABEL_NEXT_REPAYMENT = "다음 회차 예상 납입액";
    static final String LABEL_NEXT_REDUCTION = "다음 회차 납입액 감소";
    static final String LABEL_NO_REPAYMENT = "추가상환 후 남은 납입 회차";
    static final String BEFORE_NOT_ACHIEVABLE = "현재 속도로는 달성 불가";
    static final String AFTER_NOT_ACHIEVABLE = "실행 후에도 달성 불가";
    static final String GOAL_SCOPE_PARTIAL = "소비 줄이기·저축 변경만 반영(대출 상환·대환은 제외)";
    /** 스냅샷에 이름이 없는 목표. "목표 6"처럼 내부 id를 쓰지 않는다. */
    static final String UNNAMED_GOAL = "모으기 목표";

    private static final String STATUS_NOT_COMPUTABLE = "NOT_COMPUTABLE";
    private static final String REASON_SAVING_PACE_ZERO = "SAVING_PACE_ZERO";
    private static final int ASSUMPTION_LIMIT = 6;

    /** 사실 섹션과 [주의]를 한 번에 만든 결과. 둘은 같은 판단(어느 목표를 말할지)에서 나와야 한다. */
    public record Formatted(List<Section> sections, List<String> notes) {
    }

    public Formatted format(SimulationResponse simulation) {
        List<Fact> facts = new ArrayList<>();
        List<String> notes = new ArrayList<>();
        notes.add(NOTE_SERVER_NUMBERS);

        Map<Long, String> goalNames = goalNames(simulation.before());
        goalNames.putAll(goalNames(simulation.after()));
        int projected = 0;
        int skipped = 0;
        boolean partial = false;
        boolean notAchievable = false;
        for (SimulationGoalImpactResponse impact : simulation.goalImpacts()) {
            String goal = goalNames.getOrDefault(impact.goalId(), UNNAMED_GOAL);
            LocalDate before = impact.beforeForecastDate();
            LocalDate after = impact.afterForecastDate();
            Integer days = impact.differenceDays();
            if (STATUS_NOT_COMPUTABLE.equals(impact.status()) && !hasReason(impact, REASON_SAVING_PACE_ZERO)) {
                // 엔진이 이 목표의 날짜를 내지 않았다. 복합이면 소비·저축 자식의 단독 결과를 범위를 밝히고 대신 쓴다
                Optional<JsonNode> child = singleSpendingOrSavingChildImpact(simulation.after(), impact.goalId());
                if (child.isEmpty()) {
                    skipped++;
                    continue;
                }
                after = dateOf(child.get().get("afterForecastDate"));
                days = before == null || after == null ? null : (int) ChronoUnit.DAYS.between(before, after);
                partial = true;
            }
            projected++;
            facts.add(new Fact(LABEL_BEFORE_DATE + goal + ")", before == null ? BEFORE_NOT_ACHIEVABLE : CoachFormat.date(before)));
            if (after == null) {
                facts.add(new Fact(LABEL_AFTER_DATE + goal + ")", AFTER_NOT_ACHIEVABLE));
                notAchievable = true;
                continue;
            }
            if (before == null) {
                notAchievable = true;
            }
            facts.add(new Fact(LABEL_AFTER_DATE + goal + ")", CoachFormat.date(after)));
            if (days != null && days != 0) {
                facts.add(new Fact((days < 0 ? LABEL_SHORTENED : LABEL_DELAYED) + goal + ")", CoachFormat.days(Math.abs(days))));
            }
        }
        if (partial) {
            facts.add(new Fact(LABEL_GOAL_SCOPE, GOAL_SCOPE_PARTIAL));
            notes.add(NOTE_GOALS_PARTIAL);
        }
        if (skipped > 0) {
            notes.add(projected == 0 ? NOTE_GOALS_NOT_PROJECTED : NOTE_SOME_GOALS_NOT_PROJECTED);
        }
        if (notAchievable) {
            notes.add(NOTE_NOT_ACHIEVABLE);
        }
        addComparison(facts, simulation.comparison(), simulation.type());
        if (simulation.type() == SimulationType.LOAN_PREPAYMENT) {
            addNextRepayment(facts, simulation.after());
            facts.sort(Comparator.comparingInt(CoachSimulationFactFormatter::prepaymentFactOrder));
        }

        List<Section> sections = new ArrayList<>();
        sections.add(new Section(RESULT_TITLE, facts));
        List<Fact> assumptions = assumptions(simulation.after());
        if (!assumptions.isEmpty()) {
            sections.add(new Section(ASSUMPTIONS_TITLE, assumptions));
        }
        return new Formatted(List.copyOf(sections), List.copyOf(notes));
    }

    public List<Section> sections(SimulationResponse simulation) {
        return format(simulation).sections();
    }

    public List<String> notes(SimulationResponse simulation) {
        return format(simulation).notes();
    }

    private static boolean hasReason(SimulationGoalImpactResponse impact, String reason) {
        return impact.reasons() != null && impact.reasons().contains(reason);
    }

    private static Map<Long, String> goalNames(JsonNode snapshot) {
        Map<Long, String> names = new HashMap<>();
        if (snapshot == null) {
            return names;
        }
        for (JsonNode goal : snapshot.path("goals")) {
            JsonNode id = goal.get("goalId");
            JsonNode name = goal.get("name");
            if (id != null && id.isNumber() && name != null && name.isTextual() && !name.textValue().isBlank()) {
                names.put(id.longValue(), name.textValue());
            }
        }
        return names;
    }

    /**
     * 복합 결과의 {@code after.details.children[]} 중 SPENDING·SAVING 자식이 이 목표에 남긴 단독 영향. 그런 자식이 정확히 하나일 때만
     * 돌려준다 — 둘 이상이면 어느 것도 고르지 않는다(둘을 합치는 것이 바로 엔진이 하지 않은 계산이다).
     */
    private static Optional<JsonNode> singleSpendingOrSavingChildImpact(JsonNode after, Long goalId) {
        if (after == null || goalId == null) {
            return Optional.empty();
        }
        JsonNode found = null;
        for (JsonNode child : after.path("details").path("children")) {
            String type = child.path("type").asText("");
            if (!"SPENDING".equals(type) && !"SAVING".equals(type)) {
                continue;
            }
            for (JsonNode impact : child.path("goalImpacts")) {
                if (impact.path("goalId").asLong(-1) != goalId) {
                    continue;
                }
                if (found != null) {
                    return Optional.empty();
                }
                found = impact;
            }
        }
        return Optional.ofNullable(found);
    }

    private static LocalDate dateOf(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        try {
            return LocalDate.parse(node.textValue());
        } catch (DateTimeParseException invalid) {
            return null;
        }
    }

    /** 이자 관련 비교 항목만 옮긴다. 월별 상환액 행(MONTHLY_REPAYMENT_yyyy_MM)은 수십 개라 넘기지 않는다. */
    private static void addComparison(List<Fact> facts, JsonNode comparison, SimulationType type) {
        if (comparison == null) {
            return;
        }
        for (String section : List.of("items", "costs")) {
            for (JsonNode item : comparison.path(section)) {
                String key = item.path("key").asText("");
                switch (key) {
                    case "INTEREST_SAVING" -> facts.add(new Fact(LABEL_INTEREST_SAVING, CoachFormat.money(longOf(item, "after"))));
                    case "INTEREST_TOTAL" -> {
                        if ("costs".equals(section)) {
                            facts.add(new Fact(type == SimulationType.LOAN_PREPAYMENT
                                    ? "만기까지 예상 잔여 이자(추가상환 전 → 추가상환 시)" : "만기까지 총 이자(현재 → 실행 후)",
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

    /** 추가상환 이후 첫 약정 회차만 전한다. 월별 비교 행 전체를 넘기면 답변과 결과 카드가 지나치게 길어진다. */
    private static void addNextRepayment(List<Fact> facts, JsonNode after) {
        if (after == null) {
            return;
        }
        JsonNode details = after.path("details");
        Long balance = longOf(details, "balanceAfterPrepayment");
        if (balance != null && balance == 0L) {
            facts.add(new Fact(LABEL_NO_REPAYMENT, "없음(전액 상환)"));
            return;
        }
        Long before = longOf(details, "nextRepaymentBefore");
        Long next = longOf(details, "nextRepaymentAfter");
        Long reduction = longOf(details, "nextRepaymentReduction");
        LocalDate date = dateOf(details.get("nextRepaymentDate"));
        if (before == null || next == null || reduction == null || date == null
                || before < 0 || next < 0 || reduction < 0 || before - next != reduction) {
            return;
        }
        facts.add(new Fact("다음 약정 상환일", CoachFormat.date(date)));
        facts.add(new Fact(LABEL_NEXT_REPAYMENT,
                CoachFormat.money(before) + " → " + CoachFormat.money(next)));
        facts.add(new Fact(LABEL_NEXT_REDUCTION, CoachFormat.money(reduction)));
    }

    private static int prepaymentFactOrder(Fact fact) {
        return switch (fact.label()) {
            case LABEL_INTEREST_SAVING -> 0;
            case "다음 약정 상환일" -> 1;
            case LABEL_NEXT_REPAYMENT -> 2;
            case LABEL_NEXT_REDUCTION -> 3;
            case "추가 상환액" -> 4;
            default -> 5;
        };
    }

    /**
     * 가정 코드를 한글 문장으로 옮긴다. 코드 그대로 넘기면 모델이 {@code CHILD_SCENARIOS_INDEPENDENT}를 문장에 베껴 쓸 수 있고, 규칙 7
     * (영문 코드 금지)이 막지 못하는 경로가 된다. 표에 없는 코드는 넘기지 않는다 — 모르는 코드를 대충 옮기지 않는다.
     */
    private static List<Fact> assumptions(JsonNode after) {
        List<Fact> facts = new ArrayList<>();
        if (after == null) {
            return facts;
        }
        int index = 1;
        for (JsonNode assumption : after.path("assumptions")) {
            if (!assumption.isTextual() || index > ASSUMPTION_LIMIT) {
                continue;
            }
            String text = ASSUMPTION_TEXT.get(assumption.textValue());
            if (text != null) {
                facts.add(new Fact("가정 " + index++, text));
            }
        }
        return facts;
    }

    /** {@code SimulationAssumptions}·계산기 상수의 뜻(simulation.md §가정). 코치 도구가 실행하는 유형(소비·저축·대출·복합)의 코드만. */
    static final Map<String, String> ASSUMPTION_TEXT = Map.ofEntries(
            Map.entry("REDUCTION_DIRECTED_TO_SAVING_GOALS", "줄인 소비는 모두 모으기 목표 저축으로 돌린다고 가정했다"),
            Map.entry("INTEREST_NOT_APPLIED", "저축 이자는 반영하지 않았다"),
            Map.entry("SPLIT_BY_REMAINING", "여러 목표에는 남은 금액에 비례해 나눠 배분했다"),
            Map.entry("EXCLUDES_TAX_AND_FEES", "세금·수수료(중도상환·대환 부대비용 포함)는 계산에 넣지 않았다"),
            Map.entry("PRODUCT_DB_SNAPSHOT", "상품 조건은 저장된 상품 정보 기준이며 실시간 조회가 아니다"),
            Map.entry("LOAN_LOCAL_SNAPSHOT", "보유 대출 조건은 앱에 저장된 정보 기준이다"),
            Map.entry("MULTIPLE_OPTIONS_LOWEST_RATE_SELECTED", "조건이 같은 상품 옵션이 여럿이라 금리가 가장 낮은 것을 골랐다"),
            Map.entry("LOAN_RATE_PUBLISHED_MINIMUM", "대출 금리는 공시 최저금리 기준이며 실제 승인 금리와 다를 수 있다"),
            Map.entry("VARIABLE_RATE_HELD_CONSTANT", "변동금리를 만기까지 현재 금리로 고정해 계산했다"),
            Map.entry("PRODUCT_LIMIT_UNKNOWN", "상품 한도 정보가 없어 한도는 확인하지 않았다"),
            Map.entry("REPAYMENT_CODE_AMBIGUOUS_MAPPED", "상환 방식은 기존 대출과 같은 방식으로 해석했다"),
            Map.entry("MONTHLY_RATE_NOT_DAILY_PRORATED", "이자는 월 단위로 계산했고 일할 계산하지 않았다"),
            Map.entry("NO_ACCRUED_INTEREST_SETTLEMENT", "추가 상환 시점까지의 경과 이자는 따로 정산하지 않았다"),
            Map.entry("MATURITY_KEPT_PAYMENT_RECALCULATED", "추가 상환 후 만기는 그대로 두고 월 상환액을 다시 계산했다"),
            Map.entry("REMAINING_SCHEDULE_AS_TERM", "대환 기간은 기존 대출의 남은 회차 수로 맞췄다"),
            Map.entry("PARTIAL_REFINANCE_SPLIT_SCHEDULES", "일부 대환은 기존 금리분과 새 금리분을 따로 계산해 합산했다"),
            Map.entry("CASHFLOW_TABLE_TRUNCATED", "월별 표는 120개월까지만 담았다"),
            Map.entry("FUNDING_SOURCE_UNKNOWN", "상환·가입 자금이 어느 계좌·목표에서 나가는지 알 수 없어 목표 달성일은 계산하지 않았다"),
            Map.entry("GOAL_ALLOCATION_UNKNOWN", "받는 돈을 어느 목표에 넣을지 알 수 없어 목표 달성일은 계산하지 않았다"),
            Map.entry("REPAYMENT_DELTA_ALLOCATED_TO_GOALS", "상환액 변화분은 남은 금액에 비례해 목표 저축 속도에 반영했다"),
            Map.entry("GOAL_EFFECT_AT_NEXT_PROJECTION_DATE", "절감액은 발생한 다음 달부터 목표에 반영했다"),
            Map.entry("SAVING_PACE_ZERO", "현재 저축 속도가 0이라 달성일을 낼 수 없다"),
            Map.entry("ALREADY_ACHIEVED", "이미 달성한 목표다"),
            Map.entry("CHILD_SCENARIOS_INDEPENDENT", "각 행동은 같은 기준에서 따로 계산했고 서로의 결과를 잇지 않았다"),
            Map.entry("FINANCE_COST_EVALUATED", "금융비용은 평가 기간 안의 이자·수수료 합이며 평생 총비용이 아니다"),
            Map.entry("FINANCE_COST_PARTIAL", "일부 행동의 비용 자료가 평가 기간을 다 덮지 않아 부분 합계다"),
            Map.entry("GOAL_EFFECT_LIMITED_TO_SCENARIO_DURATION", "목표 반영은 각 행동의 기간 안에서만 적용하고 이후는 기존 속도로 봤다"),
            Map.entry("SAVING_AMOUNT_IS_FINAL_TARGET", "저축액 변경은 변경 후 총 저축액으로 보고 그 기간의 소비 절감액을 다시 더하지 않았다"),
            Map.entry("COMBINED_GOAL_EFFECT_NOT_COMPUTABLE", "소비·저축 외 행동이 섞여 있어 전체 조합의 목표 달성일은 계산하지 않았다"));

    private static Long longOf(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || !value.isNumber() ? null : value.longValue();
    }
}
