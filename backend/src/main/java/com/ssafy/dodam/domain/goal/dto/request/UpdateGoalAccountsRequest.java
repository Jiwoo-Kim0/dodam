package com.ssafy.dodam.domain.goal.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * PUT /api/v1/goals/{goalId}/accounts — 연결 계좌 전체 교체.
 *
 * <p>계좌는 ID로 지정한다(2026-09-16 변경, 이전에는 계좌번호 원문 `accountNos`). 계좌 목록(5)·연결계좌 조회(12) 응답이
 * `accountId`와 마스킹 번호만 주고 원문은 응답에 돌려주지 않으므로 FE가 원문 목록을 만들 수 없었다.
 *
 * @param accountIds 연결할 계좌 ID 목록(계좌 응답의 `accountId`). 빈 배열이면 모두 해제한다. 중복은 한 번으로 본다
 */
public record UpdateGoalAccountsRequest(
        @NotNull(message = "accountIds는 필수입니다.")
        @Size(max = 50, message = "계좌는 최대 50개까지 연결할 수 있습니다.")
        List<@NotNull(message = "계좌 ID는 필수입니다.") @Positive(message = "계좌 ID는 1 이상이어야 합니다.") Long> accountIds
) {
}
