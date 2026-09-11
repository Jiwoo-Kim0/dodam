package com.ssafy.dodam.domain.goal.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * PUT /api/v1/goals/{goalId}/accounts — 연결 계좌 전체 교체.
 *
 * @param accountNos 연결할 계좌번호 원문 목록(숫자 10~20자리). 빈 배열이면 모두 해제한다. 중복은 한 번으로 본다
 */
public record UpdateGoalAccountsRequest(
        @NotNull(message = "accountNos는 필수입니다.")
        @Size(max = 50, message = "계좌는 최대 50개까지 연결할 수 있습니다.")
        List<@Pattern(regexp = "^[0-9]{10,20}$", message = "계좌번호는 숫자 10~20자리여야 합니다.") String> accountNos
) {
}
