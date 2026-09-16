package com.ssafy.dodam.domain.improvement.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 실행 전후 비교(API 19 #4). 기획안 5-6 "실행 전후의 금융 데이터를 비교하여 개선 효과를 수치화".
 *
 * <p>{@code delta = after − before}이며 <b>부호를 그대로</b> 내려준다. "좋아졌는지"는 {@code improved}가 알려준다
 * (소비는 줄어야 개선, 저축은 늘어야 개선이라 부호만으로 판단할 수 없다).
 *
 * <p>비교할 수 없으면 {@code metrics}는 빈 배열이고 {@code unavailableReason}이 사유를 담는다.
 * <ul>
 *   <li>{@code ACTION_NOT_MEASURABLE} — 취소된 행동이다</li>
 *   <li>{@code BEFORE_SNAPSHOT_MISSING} — 수락 시점 기준선을 만들지 못했다</li>
 *   <li>{@code AFTER_PERIOD_NOT_READY} — 실행 후 완료된 달이 아직 없다(최소 1개월 필요)</li>
 *   <li>{@code INSUFFICIENT_DATA} — 기간은 찼지만 비교할 지표가 하나도 관측되지 않았다</li>
 * </ul>
 */
public record ActionComparisonResponse(
        Long actionId,
        String status,
        Period beforePeriod,
        Period afterPeriod,
        List<Metric> metrics,
        String unavailableReason
) {

    public ActionComparisonResponse {
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
    }

    public static ActionComparisonResponse unavailable(Long actionId, String status, Period before, Period after,
            String reason) {
        return new ActionComparisonResponse(actionId, status, before, after, List.of(), reason);
    }

    public record Period(LocalDate startDate, LocalDate endDate) {
    }

    /**
     * {@code metric} 코드는 확정이며 늘어날 수 있다. FE는 모르는 코드를 만나면 {@code displayName}으로 렌더링한다.
     */
    public record Metric(
            String metric,
            String displayName,
            Long before,
            Long after,
            Long delta,
            Boolean improved
    ) {
    }
}
