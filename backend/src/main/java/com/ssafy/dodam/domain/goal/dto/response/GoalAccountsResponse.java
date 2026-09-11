package com.ssafy.dodam.domain.goal.dto.response;

import com.ssafy.dodam.domain.account.entity.Account;
import com.ssafy.dodam.domain.goal.entity.GoalAccount;
import java.util.List;

/**
 * 목표 연결계좌(GET /goals/{goalId}/accounts, PUT 응답). 계좌 응답(5)과 같은 필드 이름을 쓰고 계좌번호는 마스킹 값만 준다.
 *
 * @param linkedCount       이 목표에 연결된 계좌 수(페이지와 무관한 전체 수)
 * @param totalBalance      연결 계좌 잔액 합 = 목표의 `currentAmount`
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
     * @param contributionAmount 계좌별 기여 금액. 지정 기능이 없어 현재는 항상 null(잔액 전체를 기여로 본다)
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
            Long contributionAmount
    ) {

        public static Item from(GoalAccount link, Account account) {
            return new Item(
                    account.getId(),
                    account.getBankCode(),
                    account.getBankName(),
                    account.getAccountNoMasked(),
                    account.getAccountName(),
                    account.getAccountType().name(),
                    account.getStatus().name(),
                    account.getBalance(),
                    link.getContributionAmount()
            );
        }
    }
}
