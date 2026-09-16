package com.ssafy.dodam.domain.recommendation.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import java.util.List;

/**
 * 추천을 시뮬레이션으로 이어 주는 실행 제안(S15P21D105-587). 저장된 추천 값을 명세 17 요청 본문 모양으로 재배열한 것이며
 * 재계산·실행이 아니다. FE는 {@code request}를 그대로 또는 사용자가 고쳐서 {@code endpoint}에 POST한다.
 *
 * <p>{@code request}는 해당 유형의 시뮬레이션 요청 DTO({@code SpendingSimulationRequest}·{@code SavingSimulationRequest})를 그대로 싣는다.
 * 요청 모양이 바뀌면 컴파일 시점에 드러나게 하기 위해서다. {@code accountNo}는 항상 null이다 — 원문 계좌번호를 응답에 싣지 않고
 * FE도 마스킹 값만 가진다.
 *
 * @param available         프리셋이 있으면 true. false면 {@code type}·{@code endpoint}·{@code request}는 null이고 {@code unavailableReason}이 이유다
 * @param unavailableReason {@code NO_MATCHING_SIMULATION_TYPE}: 대응하는 시뮬레이션 유형이 없음(CASHFLOW 등).
 *                          {@code EFFECT_AMOUNT_MISSING}: 소비 추천에 월 절감액이 없음.
 *                          {@code SNAPSHOT_FIELD_MISSING}: 저축 추천 근거에 목표 필요 저축액이 없거나 카테고리 소비 추천 근거에
 *                          카테고리 코드가 없음. {@code REFINANCE_TARGET_MISSING}: 대환 추천에 대상 상품이 없음(진단이 절감액을 추정하지 못함)
 * @param assumptions       프리셋 값에 적용된 가정. {@code REDUCTION_APPLIES_TO_TOTAL_SPENDING}: 카테고리를 지정하지 않아 전체 소비 기준.
 *                          {@code TARGET_GOAL_NOT_SELECTABLE_WITHOUT_ACCOUNT}: 추천에 goalId가 없어 저축액이 모든 모으기 목표에 남은 금액 비례로
 *                          배분됨. 619부터 저축 프리셋은 {@code request.goalId}로 대상 목표를 넘기므로 정상 자료에서는 나오지 않는다.
 *                          {@code ACCOUNT_NO_REQUIRED_BY_CLIENT}: 대환 프리셋의 {@code accountNo}가 null이며 명세 17 본문이 원문
 *                          계좌번호를 요구함 — 추천 근거의 {@code loanId}로 대출을 특정하는 본문 확장 전까지 FE가 채울 수 없다
 */
public record RecommendationSimulationPreset(
        boolean available,
        @JsonInclude(JsonInclude.Include.NON_NULL) SimulationType type,
        @JsonInclude(JsonInclude.Include.NON_NULL) String endpoint,
        @JsonInclude(JsonInclude.Include.NON_NULL) Object request,
        @JsonInclude(JsonInclude.Include.NON_NULL) String unavailableReason,
        List<String> assumptions
) {
    public static final String NO_MATCHING_SIMULATION_TYPE = "NO_MATCHING_SIMULATION_TYPE";
    public static final String EFFECT_AMOUNT_MISSING = "EFFECT_AMOUNT_MISSING";
    public static final String SNAPSHOT_FIELD_MISSING = "SNAPSHOT_FIELD_MISSING";
    public static final String REDUCTION_APPLIES_TO_TOTAL_SPENDING = "REDUCTION_APPLIES_TO_TOTAL_SPENDING";
    public static final String TARGET_GOAL_NOT_SELECTABLE_WITHOUT_ACCOUNT = "TARGET_GOAL_NOT_SELECTABLE_WITHOUT_ACCOUNT";
    public static final String REFINANCE_TARGET_MISSING = "REFINANCE_TARGET_MISSING";
    public static final String ACCOUNT_NO_REQUIRED_BY_CLIENT = "ACCOUNT_NO_REQUIRED_BY_CLIENT";

    public RecommendationSimulationPreset {
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
    }

    public static RecommendationSimulationPreset of(SimulationType type, String endpoint, Object request, List<String> assumptions) {
        return new RecommendationSimulationPreset(true, type, endpoint, request, null, assumptions);
    }

    public static RecommendationSimulationPreset unavailable(String reason) {
        return new RecommendationSimulationPreset(false, null, null, null, reason, List.of());
    }
}
