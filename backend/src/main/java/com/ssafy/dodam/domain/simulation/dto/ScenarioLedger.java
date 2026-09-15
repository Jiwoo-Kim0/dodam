package com.ssafy.dodam.domain.simulation.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 공개 응답 조립 전의 내부 계산 자료(586 §4). 복합 계산기가 자식의 공통 평가기간 금융비용을 구할 때 쓴다.
 * 공개 응답의 만기 총이자나 120개월로 잘린 현금흐름 표에서 기간 비용을 역산하지 않기 위해 계산기가 직접 넘긴다.
 * 저장하지 않으므로 과거 결과에서는 복원할 수 없다.
 *
 * @param beforePayments    행동 전 날짜별 납부(원금·이자·수수료). 비용은 이자+수수료만이며 원금은 포함하지 않는다
 * @param afterPayments     행동 후 날짜별 납부
 * @param fullTermEnd       시나리오 종료일(만기·완납일). 완납 여부를 모르면 null
 * @param costCoveredThrough 이 날짜까지의 납부 자료가 완전하다. 이후 비용은 {@code costComplete}가 false면 미상이다
 * @param costComplete      전체 기간의 비용이 확정됐는지. 리볼빙 미완납처럼 계산 상한에서 끝나면 false
 */
public record ScenarioLedger(
        List<Payment> beforePayments,
        List<Payment> afterPayments,
        LocalDate fullTermEnd,
        LocalDate costCoveredThrough,
        boolean costComplete
) {
    public ScenarioLedger {
        beforePayments = List.copyOf(beforePayments);
        afterPayments = List.copyOf(afterPayments);
    }

    /** 이자·수수료가 없는 유형(소비·저축·예적금). 종료일까지 비용 0이 확정이다. */
    public static ScenarioLedger noCost(LocalDate through) {
        return new ScenarioLedger(List.of(), List.of(), through, through, true);
    }

    public record Payment(LocalDate date, long principal, long interest, long fee) {
        public long cost() {
            return Math.addExact(interest, fee);
        }
    }
}
