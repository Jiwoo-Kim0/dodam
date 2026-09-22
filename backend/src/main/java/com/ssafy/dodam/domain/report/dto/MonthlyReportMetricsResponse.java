package com.ssafy.dodam.domain.report.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/**
 * 월간 리포트 "주요 지표"·"금융 생활 패턴"(S15P21D105-697, {@code MonthlyReport.metrics}).
 *
 * <p>FE 리포트 화면(605 협의안)이 개별 API를 조합해 만들던 두 카드를 서버 값으로 내려 준다. 리포트 생성 시점에 효율 점수와 같은 입력
 * (리포트 합계·고정지출·그 달 완료 진단·그 달 마지막 완료 예측)으로 한 번 계산해 {@code monthly_report.metrics_summary}에 굳힌다.
 * 대출 잔액만 이력이 없어 생성(보강) 시점의 현재 값이다. 산식은 {@code MonthlyReportMetricsAssembler}(버전 {@code calculationVersion})이며
 * 임계값(저축률 20%)은 <b>서비스 초기값</b>이다.
 *
 * <ul>
 *   <li>계산할 수 없는 값은 {@code null} + {@code unavailableReason}이다. 0과 "모름"은 다르다.</li>
 *   <li>전월 비교는 리포트 본문의 {@code previousMonth} 규칙과 같다 — 사용자 첫 거래 이전 달이면 전월 값·차이가 모두 null
 *       ({@code previousUnavailableReason = NO_PREVIOUS_MONTH}).</li>
 * </ul>
 *
 * @param calculationVersion 산식 버전(예: {@code report-metrics-v1})
 * @param keyMetrics         주요 지표 4개. 순서는 SAVING_RATE, FIXED_EXPENSE_SHARE, CARD_SPENDING, LOAN_BALANCE로 고정
 * @param patterns           금융 생활 패턴 3장. 순서는 SAVING_RATE, SPENDING_INCREASE, CASHFLOW_RISK로 고정
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MonthlyReportMetricsResponse(
        String calculationVersion,
        List<KeyMetric> keyMetrics,
        List<Pattern> patterns
) {

    public MonthlyReportMetricsResponse {
        keyMetrics = keyMetrics == null ? List.of() : List.copyOf(keyMetrics);
        patterns = patterns == null ? List.of() : List.copyOf(patterns);
    }

    /**
     * 주요 지표 1개.
     *
     * @param key                       SAVING_RATE / FIXED_EXPENSE_SHARE / CARD_SPENDING / LOAN_BALANCE
     * @param label                     표시 이름
     * @param valueType                 RATIO(0~1, 소수 4자리) / CURRENCY(원 단위 정수)
     * @param value                     이번 달 값. 계산 불가면 null
     * @param previousValue             전월 값. 비교 불가면 null
     * @param difference                {@code value − previousValue}(부호 유지). 어느 한쪽이 null이면 null
     * @param unavailableReason         이번 달 값이 없는 이유: INCOME_MISSING, NO_SPENDING
     * @param previousUnavailableReason 전월 값이 없는 이유: NO_PREVIOUS_MONTH(첫 거래 이전), INCOME_MISSING, NO_SPENDING,
     *                                  HISTORY_NOT_STORED(대출 잔액은 월별 이력을 저장하지 않는다)
     * @param asOf                      값의 기준. MONTH(그 달 집계) / CURRENT(생성·보강 시점 현재 값 — 대출 잔액)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record KeyMetric(
            String key,
            String label,
            String valueType,
            Number value,
            Number previousValue,
            Number difference,
            String unavailableReason,
            String previousUnavailableReason,
            String asOf
    ) {
    }

    /**
     * 금융 생활 패턴 1장. 문구는 서버 템플릿이며 AI 문장이 아니다(코치 문구는 API 21).
     *
     * @param key               SAVING_RATE / SPENDING_INCREASE / CASHFLOW_RISK
     * @param status            GOOD(양호) / CHECK(점검) / UNAVAILABLE(자료 없음). "위험 없음"과 "자료 없음"을 구분한다
     * @param title             한 줄 제목
     * @param description       근거 문장(숫자 포함)
     * @param evidencePeriod    판정에 쓴 기간 표기(예: {@code 2026-08}, {@code 2026-07 vs 2026-04~2026-06}, {@code 2026-08-20~2026-09-19})
     * @param unavailableReason UNAVAILABLE 사유: INCOME_MISSING, DIAGNOSIS_NOT_FOUND, FORECAST_NOT_FOUND
     * @param evidence          FE가 그대로 쓸 수 있는 근거 수치(패턴마다 다름). 없으면 null
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Pattern(
            String key,
            String status,
            String title,
            String description,
            String evidencePeriod,
            String unavailableReason,
            com.fasterxml.jackson.databind.JsonNode evidence
    ) {
    }
}
