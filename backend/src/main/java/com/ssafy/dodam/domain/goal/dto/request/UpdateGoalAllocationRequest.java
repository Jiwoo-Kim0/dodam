package com.ssafy.dodam.domain.goal.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 자유입출금 담기·꺼내기(S15P21D105-703) 본문.
 *
 * <p>증감이 아니라 <b>절대값</b>이다 — "이 목표에 이 계좌에서 담아 둘 금액". 담기도 꺼내기도 같은 요청이고 더 작은 값을
 * 보내면 꺼내기다. 그래서 재시도·중복 탭이 자연히 멱등이고 요청 id나 멱등 테이블을 두지 않는다(626 §4 확정).
 *
 * @param amount 담아 둘 금액(원). 0이면 할당을 해제한다. `잔액 − 다른 목표에 담긴 금액` 이하여야 한다
 */
public record UpdateGoalAllocationRequest(
        @NotNull(message = "금액은 필수입니다.")
        @PositiveOrZero(message = "금액은 0 이상이어야 합니다.")
        Long amount
) {
}
