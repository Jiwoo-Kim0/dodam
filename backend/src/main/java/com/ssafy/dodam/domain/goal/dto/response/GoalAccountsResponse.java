package com.ssafy.dodam.domain.goal.dto.response;

import com.ssafy.dodam.domain.account.entity.Account;
import com.ssafy.dodam.domain.goal.service.GoalAllocationReader;
import java.util.List;

/**
 * 목표 연결계좌(GET /goals/{goalId}/accounts, PUT 응답). 계좌 응답(5)과 같은 필드 이름을 쓰고 계좌번호는 마스킹 값만 준다.
 *
 * @param linkedCount       이 목표에 연결된 계좌 수(페이지와 무관한 전체 수)
 * @param totalBalance      목표 확보액 = 연결 계좌 기여액 합 = 목표의 `currentAmount`. 626부터 입출금은 담은 금액만 더한다
 *                          (필드 이름은 509 계약 유지)
 * @param accounts          이 페이지의 계좌(계좌 ID 순)
 * @param page, size, totalPages, last 공통 페이지 정보
 */
public record GoalAccountsResponse(
        Long goalId,
        long linkedCount,
        long totalBalance,
        List<Item> accounts,
        int page,
        int size,
        int totalPages,
        boolean last
) {

    public GoalAccountsResponse {
        accounts = List.copyOf(accounts);
    }

    /**
     * 계좌 한 건. 626 할당 필드는 {@link GoalAllocationReader}의 정의를 따른다.
     *
     * @param contributionAmount    저장된 `contribution_amount` 원문. 예·적금과 626 이전 입출금 링크는 null(잔액 전체를 기여로 본다)
     * @param allocatedToThisGoal   이 계좌가 이 목표에 기여하는 금액(null 기여액은 잔액 전체로 환산)
     * @param allocatedElsewhere    같은 계좌가 다른(삭제되지 않은) 목표에 담긴 금액 합
     * @param availableToAllocate   추가로 담을 수 있는 금액 = max(0, balance − 전체 할당). 예·적금은 항상 0
     * @param shortfallAmount       계좌 단위 부족액 = max(0, 전체 할당 − max(0, balance)). 실제 출금으로 설정액이 잔액을 넘은 상태
     * @param allocationStatus      `OK` 또는 `NEEDS_ADJUSTMENT`(shortfallAmount > 0)
     */
    public record Item(
            Long accountId,
            String bankCode,
            String bankName,
            String accountNoMasked,
            String accountName,
            String accountType,
            String status,
            long balance,
            Long contributionAmount,
            long allocatedToThisGoal,
            long allocatedElsewhere,
            long availableToAllocate,
            long shortfallAmount,
            String allocationStatus
    ) {

        public static Item from(GoalAllocationReader.LinkedAccount linked) {
            Account account = linked.account();
            GoalAllocationReader.AccountAllocation allocation = linked.allocation();
            return new Item(
                    account.getId(),
                    account.getBankCode(),
                    account.getBankName(),
                    account.getAccountNoMasked(),
                    account.getAccountName(),
                    account.getAccountType().name(),
                    account.getStatus().name(),
                    account.getBalance(),
                    linked.link().getContributionAmount(),
                    linked.contribution(),
                    linked.allocatedElsewhere(),
                    allocation.availableToAllocate(),
                    allocation.shortfallAmount(),
                    allocation.status()
            );
        }
    }
}
