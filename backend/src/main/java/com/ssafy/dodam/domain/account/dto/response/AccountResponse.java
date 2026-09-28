package com.ssafy.dodam.domain.account.dto.response;

import com.ssafy.dodam.domain.account.entity.Account;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 계좌 한 건. 목록과 상세가 같은 형태를 쓴다.
 *
 * <p>계좌번호는 마스킹 값만 준다. 원문은 상세·잔액 조회의 경로 변수로만 받고 응답에 되돌리지 않는다.
 * 카드(8) 응답의 출금계좌(`bankCode`, `bankName`, `accountName`, `accountNoMasked`, `accountType`)와 필드 이름을 맞췼다.
 *
 * @param accountId 거래(6)·카드(8) 응답의 `accountId`와 같은 값
 * @param balance   MINUS 계좌는 0 이하(대출 잔액), 나머지는 0 이상
 */
public record AccountResponse(
        Long accountId,
        String bankCode,
        String bankName,
        String accountNoMasked,
        String accountName,
        String accountType,
        String status,
        long balance,
        String currency,
        LocalDate lastTransactionDate,
        String sourceType,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getBankCode(),
                account.getBankName(),
                account.getAccountNoMasked(),
                account.getAccountName(),
                account.getAccountType().name(),
                account.getStatus().name(),
                account.getBalance(),
                account.getCurrency(),
                account.getLastTransactionDate(),
                account.getSourceType() == null ? null : account.getSourceType().name(),
                account.getCreatedAt(),
                account.getUpdatedAt()
        );
    }
}
