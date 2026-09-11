package com.ssafy.dodam.domain.account.entity;

import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.util.Locale;

/**
 * ERD `account.account_type` CHECK 제약과 동일한 값.
 * MINUS 계좌의 잔액은 0 이하, 나머지는 0 이상이어야 한다(`chk_account_balance_by_type`).
 */
public enum AccountType {
    DEMAND_DEPOSIT,
    MINUS,
    DEPOSIT,
    SAVINGS;

    /**
     * 목록 필터 파라미터를 해석한다. null·빈 값은 "전체"(null)이고 대소문자는 구분하지 않는다.
     *
     * @throws InvalidRequestException 정의되지 않은 값
     */
    public static AccountType parseFilter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("지원하지 않는 계좌 유형입니다.");
        }
    }
}
