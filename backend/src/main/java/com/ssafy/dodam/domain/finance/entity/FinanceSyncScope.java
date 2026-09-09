package com.ssafy.dodam.domain.finance.entity;

import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

/**
 * ERD `finance_sync.scope` CHECK 제약과 동일한 값. ALL은 나머지 4개 도메인을 순서대로 모두 실행한다.
 */
public enum FinanceSyncScope {
    ALL,
    ACCOUNT,
    CARD,
    LOAN,
    SAVING;

    /**
     * 요청 Body의 scope 문자열. 생략(null·빈 값)하면 ALL, 대소문자는 구분하지 않는다.
     *
     * @throws BusinessException INVALID_REQUEST — 정의되지 않은 값
     */
    public static FinanceSyncScope from(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    /**
     * 실제로 동기화를 수행할 도메인 목록. ALL은 자기 자신을 포함하지 않는다.
     */
    public List<FinanceSyncScope> targets() {
        if (this == ALL) {
            return List.copyOf(EnumSet.of(ACCOUNT, CARD, LOAN, SAVING));
        }
        return List.of(this);
    }
}
