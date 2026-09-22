package com.ssafy.dodam.domain.report.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 월간 리포트 금융 효율 점수(S15P21D105-635, {@code MonthlyReport.efficiency}). 육각형 그래프용 6개 항목과 전체 점수.
 *
 * <p>리포트 생성 시점에 한 번 계산해 {@code monthly_report.efficiency_summary}에 굳힌다. 산식은
 * {@code EfficiencyScorePolicy}(버전 {@code calculationVersion})이며 <b>서비스 초기값</b>이다 — 기획·FE와 임계값을
 * 확정하면 버전을 올린다. 현재 버전은 {@code efficiency-v2}이며 과거 리포트의 점수는 저장본 그대로라 바뀌지 않는다.
 *
 * <ul>
 *   <li>항목 점수는 모두 0~100 정수다. 계산할 수 없는 항목은 {@code score = null} + {@code unavailableReason}이며
 *       {@code status = UNAVAILABLE}이다. 0점과 "모름"은 다르다.</li>
 *   <li>전체 점수는 계산된 항목의 <b>단순 평균</b>(반올림)이다. 계산된 항목이 {@code minimumDimensions}보다 적으면
 *       전체 점수도 null이고 {@code unavailableReason = INSUFFICIENT_DIMENSIONS}다. 계산 불가 항목을 0점으로 넣어
 *       평균을 끌어내리지 않는다.</li>
 *   <li>{@code previousMonthDifference}는 전월 리포트 점수(저장본, 없으면 같은 산식으로 즉시 계산) 대비 차이다.
 *       전월이 사용자 데이터 이전이거나 어느 한쪽 전체 점수가 null이면 null이다.</li>
 * </ul>
 *
 * @param score                   전체 점수(0~100). 계산 불가면 null
 * @param previousMonthDifference 전월 대비 점수 차(부호 유지). 비교 불가면 null
 * @param summary                 서버 템플릿 한 줄 요약
 * @param calculationVersion      산식 버전(예: {@code efficiency-v2})
 * @param availableDimensionCount 점수가 계산된 항목 수
 * @param unavailableReason       전체 점수가 없는 이유({@code INSUFFICIENT_DIMENSIONS}). 있으면 null
 * @param dimensions              6개 항목. 순서는 SPENDING, SAVING, FIXED_EXPENSE, CREDIT, CASHFLOW, INEFFICIENCY로 고정
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EfficiencyScoreResponse(
        Integer score,
        int maxScore,
        Integer previousMonthDifference,
        String summary,
        String calculationVersion,
        int availableDimensionCount,
        @JsonInclude(JsonInclude.Include.NON_NULL) String unavailableReason,
        List<Dimension> dimensions
) {

    public static final int MAX_SCORE = 100;
    public static final String INSUFFICIENT_DIMENSIONS = "INSUFFICIENT_DIMENSIONS";

    public EfficiencyScoreResponse {
        dimensions = dimensions == null ? List.of() : List.copyOf(dimensions);
    }

    public EfficiencyScoreResponse withPreviousMonthDifference(Integer difference) {
        return new EfficiencyScoreResponse(score, maxScore, difference, summary, calculationVersion,
                availableDimensionCount, unavailableReason, dimensions);
    }

    /**
     * @param key               SPENDING / SAVING / FIXED_EXPENSE / CREDIT / CASHFLOW / INEFFICIENCY
     * @param label             표시 이름
     * @param score             0~100. 계산 불가면 null
     * @param status            GOOD(≥70) / NORMAL(40~69) / CHECK(<40) / UNAVAILABLE
     * @param description       근거 문장(숫자 포함). 계산 불가면 그 이유 문장
     * @param value             근거 수치. 비율은 0~1, 건수는 건수, 신용점수는 1~1,000, 등급 입력이면 null
     * @param valueType         RATIO / COUNT / SCORE / GRADE
     * @param grade             호환 입력으로 쓴 금융망 신용등급(A~E). 신용점수가 있으면 null
     * @param unavailableReason 계산 불가 사유. INCOME_MISSING, NO_SPENDING, CREDIT_SCORE_MISSING, FORECAST_NOT_FOUND,
     *                          DIAGNOSIS_NOT_FOUND
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Dimension(
            String key,
            String label,
            Integer score,
            int maxScore,
            String status,
            String description,
            Double value,
            String valueType,
            String grade,
            String unavailableReason
    ) {
    }
}
