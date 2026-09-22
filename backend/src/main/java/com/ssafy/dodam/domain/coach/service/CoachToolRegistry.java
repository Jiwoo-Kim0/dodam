package com.ssafy.dodam.domain.coach.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.coach.service.CoachTextGenerator.ToolCall;
import com.ssafy.dodam.domain.coach.service.CoachTextGenerator.ToolDefinition;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * "토리에게 묻기" 도구 목록(640 A-2, 계획 §1-4). 모델에게 보내는 OpenAI function 스키마와 시연 칩의 고정 매핑을 한 곳에 둔다.
 *
 * <p>스키마 원칙: 인자는 전부 <b>enum·description으로 고정</b>한다(637 실측에서 설명 없는 문자열 인자에 "카페" 같은 한글 값이 들어왔다).
 * 사용자 식별자·계좌번호·loanId·goalId는 스키마에 없다 — 대출·목표는 이름(라벨)으로 받고 서버가 본인 것 중에서 찾는다.
 *
 * <p>시연 칩은 LLM 1차 호출 없이 서버가 도구를 직접 매핑한다(계획 §1-1). 금액은 시연 계정 U0003(633·636) 검증 규칙을 통과하는 값이다.
 */
@Component
public class CoachToolRegistry {
    public static final String SIMULATE_SPENDING_REDUCTION = "simulate_spending_reduction";
    public static final String SIMULATE_SAVING_CHANGE = "simulate_saving_change";
    public static final String SIMULATE_LOAN_PREPAYMENT = "simulate_loan_prepayment";
    public static final String SIMULATE_LOAN_REFINANCE = "simulate_loan_refinance";
    public static final String SIMULATE_COMBINED = "simulate_combined";
    public static final String GET_GOAL_FORECAST = "get_goal_forecast";
    public static final String GET_LATEST_DIAGNOSIS = "get_latest_diagnosis";

    /** 소비 감소 시뮬레이션이 받는 카테고리 코드(V6 변동 소비 11종). 저축·수입·이체·고정비는 대상이 아니다. */
    public static final List<String> SPENDING_CATEGORY_CODES = List.of(
            "FOOD", "CAFE", "GROCERY", "PUBLIC_TRANSPORT", "TAXI", "SHOPPING", "BEAUTY", "CULTURE", "TRAVEL", "MEDICAL", "EDUCATION", "ETC");
    public static final Set<String> SPENDING_CATEGORY_CODE_SET = Set.copyOf(SPENDING_CATEGORY_CODES);
    /** 시뮬레이션 대상 카테고리의 표시 이름(V6 `transaction_category.name`). 맞춤 질문 칩이 코드만 있는 맥락에서 이름을 만들 때 쓴다. */
    public static final java.util.Map<String, String> SPENDING_CATEGORY_NAMES = java.util.Map.ofEntries(
            java.util.Map.entry("FOOD", "식비"), java.util.Map.entry("CAFE", "카페"), java.util.Map.entry("GROCERY", "마트·편의점"),
            java.util.Map.entry("PUBLIC_TRANSPORT", "대중교통"), java.util.Map.entry("TAXI", "택시"), java.util.Map.entry("SHOPPING", "쇼핑"),
            java.util.Map.entry("BEAUTY", "뷰티·미용"), java.util.Map.entry("CULTURE", "문화·여가"), java.util.Map.entry("TRAVEL", "여행"),
            java.util.Map.entry("MEDICAL", "의료·건강"), java.util.Map.entry("EDUCATION", "교육"), java.util.Map.entry("ETC", "기타"));
    /** 복합 시뮬레이션의 자식 유형(계획 1차 범위). */
    public static final List<String> COMBINED_SCENARIO_TYPES = List.of("SPENDING", "SAVING", "LOAN_PREPAYMENT", "LOAN_REFINANCE");
    public static final Set<String> COMBINED_SCENARIO_TYPE_SET = Set.copyOf(COMBINED_SCENARIO_TYPES);

    /** 한 턴에 실행하는 도구 호출 상한(계획 §1-2). 그 이상은 실행하지 않고 사유를 돌려준다. */
    public static final int MAX_TOOL_CALLS_PER_TURN = 3;

    private final ObjectMapper json;
    private final List<ToolDefinition> definitions;

    public CoachToolRegistry(ObjectMapper json) {
        this.json = json;
        this.definitions = List.of(spendingReduction(), savingChange(), loanPrepayment(), loanRefinance(), combined(), goalForecast(),
                latestDiagnosis());
    }

    public List<ToolDefinition> definitions() {
        return definitions;
    }

    public boolean supports(String toolName) {
        return definitions.stream().anyMatch(tool -> tool.name().equals(toolName));
    }

    /**
     * 시연 칩 → 고정 도구 호출(계획 §1-4 표). 알 수 없는 칩이면 비어 있다.
     * 호출 id는 서버가 만든 것임을 드러내는 접두어를 붙인다(모델 응답의 id와 섞이지 않는다).
     */
    public Optional<List<ToolCall>> chip(String chipId) {
        if (chipId == null) {
            return Optional.empty();
        }
        return switch (chipId.strip().toUpperCase(Locale.ROOT)) {
            case "CHIP_SPENDING_CAFE_50K" -> Optional.of(List.of(new ToolCall("chip_spending", SIMULATE_SPENDING_REDUCTION,
                    "{\"category\":\"CAFE\",\"monthlyReduction\":50000}")));
            case "CHIP_LOAN_PREPAY_1M" -> Optional.of(List.of(new ToolCall("chip_prepay", SIMULATE_LOAN_PREPAYMENT,
                    "{\"prepaymentAmount\":1000000}")));
            case "CHIP_COMBINED" -> Optional.of(List.of(new ToolCall("chip_combined", SIMULATE_COMBINED,
                    "{\"scenarios\":[{\"type\":\"SPENDING\",\"category\":\"CAFE\",\"monthlyReduction\":50000},"
                            + "{\"type\":\"LOAN_PREPAYMENT\",\"prepaymentAmount\":1000000}]}")));
            default -> Optional.empty();
        };
    }

    /** 칩 표시 문구(FE 정적 칩과 같은 값). 서버 응답 {@code suggestedQuestions}에도 쓴다. */
    public static List<String> chipLabels() {
        return List.of("카페를 월 5만원 줄이면?", "대출 100만원 더 갚으면?", "둘 다 하면?");
    }

    /** 칩 하나의 표시 문구. 채팅 세션에 사용자 문장 대신 남기고, 2차 호출의 user 메시지로 쓴다. 알 수 없는 칩이면 비어 있다. */
    public static Optional<String> chipLabel(String chipId) {
        if (chipId == null) {
            return Optional.empty();
        }
        return switch (chipId.strip().toUpperCase(Locale.ROOT)) {
            case "CHIP_SPENDING_CAFE_50K" -> Optional.of(chipLabels().get(0));
            case "CHIP_LOAN_PREPAY_1M" -> Optional.of(chipLabels().get(1));
            case "CHIP_COMBINED" -> Optional.of(chipLabels().get(2));
            default -> Optional.empty();
        };
    }

    /** 도구 이름의 사용자용 표기. 실패 사실의 라벨과 채팅 facts의 {@code subject}에 쓴다 — 영문 도구 이름이 화면에 나가지 않게 한다. */
    public static String displayName(String tool) {
        if (tool == null) {
            return "요청";
        }
        return switch (tool) {
            case SIMULATE_SPENDING_REDUCTION -> "소비 줄이기 시뮬레이션";
            case SIMULATE_SAVING_CHANGE -> "저축 변경 시뮬레이션";
            case SIMULATE_LOAN_PREPAYMENT -> "대출 추가 상환 시뮬레이션";
            case SIMULATE_LOAN_REFINANCE -> "대출 대환 시뮬레이션";
            case SIMULATE_COMBINED -> "복합 시뮬레이션";
            case GET_GOAL_FORECAST -> "목표 달성 예측";
            case GET_LATEST_DIAGNOSIS -> "최근 진단";
            default -> tool;
        };
    }

    // ---------- 스키마 ----------

    private ToolDefinition spendingReduction() {
        ObjectNode schema = object();
        ObjectNode properties = schema.putObject("properties");
        enumProperty(properties, "category", "줄일 소비 카테고리 코드. 사용자가 '카페'라고 하면 CAFE, '식비'·'외식·배달'은 FOOD, '택시'는 TAXI, '마트·편의점'은 GROCERY, '문화·여가'는 CULTURE. 전체 소비를 줄이는 질문이면 생략",
                SPENDING_CATEGORY_CODES);
        integerProperty(properties, "monthlyReduction", "한 달에 줄일 금액(원). '5만원'은 50000");
        integerProperty(properties, "durationMonths", "몇 개월 동안 유지할지(1~120). 말하지 않았으면 생략");
        required(schema, "monthlyReduction");
        return new ToolDefinition(SIMULATE_SPENDING_REDUCTION,
                "소비를 매달 일정 금액 줄였을 때 모으기 목표 달성일이 얼마나 빨라지는지 서버가 계산한다. '카페 5만원 줄이면', '식비 10만원 아끼면' 같은 질문.", schema);
    }

    private ToolDefinition savingChange() {
        ObjectNode schema = object();
        ObjectNode properties = schema.putObject("properties");
        integerProperty(properties, "monthlySavingAmount", "변경 후 한 달 저축액(원). 늘리는 금액이 아니라 변경 후 총액. '월 30만원으로 올리면'은 300000");
        stringProperty(properties, "goalName", "대상 모으기 목표 이름의 일부(예: 비상금). 사용자가 목표를 말하지 않았으면 생략");
        integerProperty(properties, "durationMonths", "몇 개월 동안 유지할지(1~120). 말하지 않았으면 생략");
        required(schema, "monthlySavingAmount");
        return new ToolDefinition(SIMULATE_SAVING_CHANGE,
                "월 저축액을 바꿨을 때 모으기 목표 달성일 변화를 서버가 계산한다. '저축을 월 30만원으로 올리면' 같은 질문.", schema);
    }

    private ToolDefinition loanPrepayment() {
        ObjectNode schema = object();
        ObjectNode properties = schema.putObject("properties");
        integerProperty(properties, "prepaymentAmount", "추가로 갚을 금액(원). '100만원'은 1000000");
        stringProperty(properties, "loanLabel", "대상 대출 이름의 일부(예: 국민ONE). 대출이 하나면 생략");
        required(schema, "prepaymentAmount");
        return new ToolDefinition(SIMULATE_LOAN_PREPAYMENT,
                "대출을 추가 상환했을 때 만기까지 이자 절감과 목표 영향을 서버가 계산한다. '대출 100만원 더 갚으면' 같은 질문.", schema);
    }

    private ToolDefinition loanRefinance() {
        ObjectNode schema = object();
        ObjectNode properties = schema.putObject("properties");
        stringProperty(properties, "loanLabel", "대상 대출 이름의 일부. 대출이 하나면 생략");
        stringProperty(properties, "targetProductName", "갈아탈 상품 이름. 사용자가 말하지 않았으면 생략(서버가 진단이 찾은 최저금리 상품을 쓴다)");
        return new ToolDefinition(SIMULATE_LOAN_REFINANCE,
                "고금리 대출을 더 낮은 금리 상품으로 대환했을 때 이자 절감과 목표 영향을 서버가 계산한다. '대출 갈아타면' 같은 질문.", schema);
    }

    private ToolDefinition combined() {
        ObjectNode schema = object();
        ObjectNode properties = schema.putObject("properties");
        ObjectNode scenarios = properties.putObject("scenarios");
        scenarios.put("type", "array").put("description", "함께 실행할 행동 2~10개. 각 원소의 type에 따라 아래 필드를 채운다");
        scenarios.put("minItems", 2).put("maxItems", 10);
        ObjectNode item = scenarios.putObject("items");
        item.put("type", "object");
        ObjectNode itemProperties = item.putObject("properties");
        enumProperty(itemProperties, "type", "행동 유형", COMBINED_SCENARIO_TYPES);
        enumProperty(itemProperties, "category", "SPENDING: 줄일 카테고리 코드(전체면 생략)", SPENDING_CATEGORY_CODES);
        integerProperty(itemProperties, "monthlyReduction", "SPENDING: 한 달에 줄일 금액(원)");
        integerProperty(itemProperties, "monthlySavingAmount", "SAVING: 변경 후 한 달 저축액(원)");
        stringProperty(itemProperties, "goalName", "SAVING: 대상 목표 이름의 일부(생략 가능)");
        integerProperty(itemProperties, "prepaymentAmount", "LOAN_PREPAYMENT: 추가 상환액(원)");
        stringProperty(itemProperties, "loanLabel", "LOAN_PREPAYMENT·LOAN_REFINANCE: 대상 대출 이름의 일부(대출이 하나면 생략)");
        stringProperty(itemProperties, "targetProductName", "LOAN_REFINANCE: 갈아탈 상품 이름(생략 가능)");
        integerProperty(itemProperties, "durationMonths", "SPENDING·SAVING: 유지 개월(1~120, 생략 가능)");
        item.putArray("required").add("type");
        required(schema, "scenarios");
        return new ToolDefinition(SIMULATE_COMBINED,
                "두 가지 이상 행동을 함께 했을 때의 합산 효과(목표 달성일·이자 절감)를 서버가 계산한다. '카페 5만원 줄이고 대출 100만원 더 갚으면' 같은 질문은 이 도구 하나로.", schema);
    }

    private ToolDefinition goalForecast() {
        ObjectNode schema = object();
        ObjectNode properties = schema.putObject("properties");
        stringProperty(properties, "goalName", "대상 모으기 목표 이름의 일부. 사용자가 말하지 않았으면 생략(목표가 하나면 그것)");
        return new ToolDefinition(GET_GOAL_FORECAST,
                "지금 저축 속도로 모으기 목표를 언제 달성하는지 서버가 계산한 예상 달성일을 조회한다. 행동 변화 없이 '목표 언제 돼?'만 물을 때.", schema);
    }

    private ToolDefinition latestDiagnosis() {
        ObjectNode schema = object();
        schema.putObject("properties");
        return new ToolDefinition(GET_LATEST_DIAGNOSIS,
                "가장 최근 완료된 금융 진단의 문제 목록(비효율)을 조회한다. '내 문제가 뭐야', '뭘 고쳐야 해' 같은 질문.", schema);
    }

    private ObjectNode object() {
        ObjectNode node = json.createObjectNode();
        node.put("type", "object");
        node.put("additionalProperties", false);
        return node;
    }

    private static void integerProperty(ObjectNode properties, String name, String description) {
        properties.putObject(name).put("type", "integer").put("description", description);
    }

    private static void stringProperty(ObjectNode properties, String name, String description) {
        properties.putObject(name).put("type", "string").put("description", description);
    }

    private static void enumProperty(ObjectNode properties, String name, String description, List<String> values) {
        ObjectNode property = properties.putObject(name).put("type", "string").put("description", description);
        ArrayNode array = property.putArray("enum");
        values.forEach(array::add);
    }

    private static void required(ObjectNode schema, String... fields) {
        ArrayNode array = schema.putArray("required");
        for (String field : fields) {
            array.add(field);
        }
    }
}
