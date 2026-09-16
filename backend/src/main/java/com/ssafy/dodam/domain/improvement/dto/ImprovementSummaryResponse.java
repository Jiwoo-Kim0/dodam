package com.ssafy.dodam.domain.improvement.dto;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 개선 결과 요약(API 19 #1).
 *
 * <p>{@code byStatus}는 4개 상태 키를 <b>항상 모두</b> 포함한다(0이어도). FE가 키 존재 여부를 분기하지 않도록 —
 * 추천 요약(`RecommendationSummaryResponse`)이 이미 쓰는 규칙이다.
 *
 * <p>합계는 <b>측정된 실측값만</b> 더한다. 예상치는 섞지 않는다. 측정된 행동이 없으면 0이며, 이때 0은
 * "측정 결과가 없다"는 뜻이지 "효과가 0원이다"가 아니다 — {@code measuredActions}로 구분한다.
 *
 * @param totalMonthlySaving 측정된 행동의 월 절감액(소비 감소 + 이자 감소) 합
 * @param totalAnnualSaving  위 값의 연환산 합
 * @param totalDebtReduction 측정된 행동의 부채 잔액 감소 합. 월 단위가 아니다
 */
public record ImprovementSummaryResponse(
        long totalActions,
        Map<String, Long> byStatus,
        long measuredActions,
        long totalMonthlySaving,
        long totalAnnualSaving,
        long totalDebtReduction,
        LocalDateTime firstActionAt,
        LocalDateTime lastMeasuredAt
) {

    public ImprovementSummaryResponse {
        byStatus = Map.copyOf(byStatus);
    }
}
