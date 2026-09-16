package com.ssafy.dodam.domain.improvement.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 개선 행동의 실행 전·후 재무 관측치. `improvement_action.before_snapshot` / `after_snapshot`에 그대로 저장한다.
 *
 * <p>월 금액은 모두 <b>월평균</b>이다. before는 3개월, after는 완료된 개월 수로 나누므로 기간 길이가 달라도
 * 비교할 수 있다. 잔액성 값(부채)은 스냅샷 시점의 값이다.
 *
 * @param capturedAt       스냅샷을 만든 시각
 * @param startDate        집계 기간 시작(포함). 잔액성 값에는 적용되지 않는다
 * @param endDate          집계 기간 종료(포함)
 * @param months           집계에 쓴 개월 수. 월평균의 분모다
 * @param monthlySpending  월평균 소비(506 정의: EXPENSE에서 NON_SPENDING·숨긴 거래 제외)
 * @param monthlySaving    월평균 저축성 지출(NON_SPENDING EXPENSE)
 * @param debtBalance      스냅샷 시점의 상환 중(ACTIVE·OVERDUE) 대출 잔액 합
 * @param monthlyInterest  위 대출의 월 이자 합(잔액 x 연이율 / 1200, 원 단위 반올림)
 * @param goalId           연결된 모으기 목표. 추천에 목표가 없으면 null
 * @param goalForecastDate 그 목표의 예상 달성일. 저축속도가 0이거나 목표가 없으면 null
 * @param unavailable      값을 만들지 못한 항목의 사유 코드. 비어 있으면 전부 관측됐다
 */
public record ImprovementSnapshot(
        LocalDateTime capturedAt,
        LocalDate startDate,
        LocalDate endDate,
        int months,
        Long monthlySpending,
        Long monthlySaving,
        Long debtBalance,
        Long monthlyInterest,
        Long goalId,
        LocalDate goalForecastDate,
        List<String> unavailable
) {

    public ImprovementSnapshot {
        unavailable = unavailable == null ? List.of() : List.copyOf(unavailable);
    }
}
