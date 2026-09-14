package com.ssafy.dodam.domain.pattern.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;

/**
 * GET /api/v1/patterns/me, POST /analyze의 `analysis`. 저장된 행만으로 만들고 현재 데이터로 재계산하지 않는다.
 *
 * @param analysisId      분석 ID
 * @param status          COMPLETED(조회 API는 완료 결과만 돌려준다)
 * @param analyzedAt      완료 시각
 * @param lookbackMonths  분석 기간(개월)
 * @param requestedMode   요청 모드
 * @param analysisMode    실제 적용 모드
 * @param fallbackReason  군집 결과가 없는 사유(HYBRID 요청 시). 군집이 있으면 null
 * @param metrics         규칙 기반 지표 7종(비율, 소수 4자리). 계산 불가면 항목이 null
 * @param liquidityLevel  HIGH | MEDIUM | LOW. 계좌 정보 없으면 null
 * @param featureSummary  분석 입력 요약
 * @param clusterProfile  군집 프로필. 조건 미충족이면 null(명세 "조건 미충족 시 null")
 */
public record PatternAnalysisResponse(
        Long analysisId,
        String status,
        LocalDateTime analyzedAt,
        int lookbackMonths,
        String requestedMode,
        String analysisMode,
        String fallbackReason,
        Metrics metrics,
        String liquidityLevel,
        PatternFeatureSummary featureSummary,
        @JsonInclude(JsonInclude.Include.ALWAYS) ClusterProfileResponse clusterProfile
) {

    /**
     * @param spendingRegularity        1 − 월 소비 변동계수(0~1). 높을수록 매달 비슷하게 쓴다
     * @param savingConsistency         저축성 지출이 있는 달 비율(0~1)
     * @param fixedExpenseRatio         고정지출 / 소비
     * @param variableExpenseRatio      변동지출 / 소비
     * @param discretionarySpendingRatio 재량소비(외식·카페·쇼핑·뷰티·문화·여행·택시) / 소비
     * @param debtBurden                월평균 대출상환 / 월 소득
     * @param cashflowStability         (소득 − 소비) ≥ 0인 달 비율(0~1)
     * @param dataPointCount            기간 안 거래 건수
     */
    public record Metrics(
            Double spendingRegularity,
            Double savingConsistency,
            Double fixedExpenseRatio,
            Double variableExpenseRatio,
            Double discretionarySpendingRatio,
            Double debtBurden,
            Double cashflowStability,
            int dataPointCount
    ) {
    }
}
