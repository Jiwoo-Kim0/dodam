package com.ssafy.dodam.domain.cashflow.dto;

import java.time.LocalDate;

/**
 * 예정 출금 시점에 잔액이 모자란 사건 1건.
 *
 * <p>기획안 "공통 현금흐름 예측 로직"의 위험 판정 지점과 같다. 하루 마감 잔액이 아니라
 * <b>출금 직전 잔액</b>을 기준으로 판정한다. 마감 잔액만 보면 같은 날 여러 건이 빠져나갈 때
 * 어느 출금에서 처음 막히는지 알 수 없고, 이월된 음수 잔액 때문에 이후 날짜가 전부 위험으로 잡힌다.
 *
 * <p>위험 유형·심각도·문구는 여기서 정하지 않는다. 계산과 위험 정책을 분리해
 * {@code CashflowRiskDetector}가 맡는다.
 *
 * @param balanceBeforePayment 출금 직전 잔액. 음수일 수 있다(이전 출금으로 이미 부족한 상태)
 */
public record CashflowShortage(
        LocalDate date,
        String eventType,
        String eventName,
        long requiredAmount,
        long balanceBeforePayment
) {

    /** 모자란 금액. 정의상 항상 1원 이상이다. */
    public long shortageAmount() {
        return Math.subtractExact(requiredAmount, balanceBeforePayment);
    }
}
