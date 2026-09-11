package com.ssafy.dodam.domain.goal.entity;

import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.util.Locale;

/**
 * ERD `goal.type` CHECK 제약과 동일한 값. FE의 "모으기(saving)" / "줄이기(reducing)"에 대응한다.
 *
 * <ul>
 *   <li>SAVING — 목표 금액·목표일이 있는 모으기 목표. 하위행 `saving_goal`</li>
 *   <li>SPENDING — 월 예산이 있는 줄이기 목표. 하위행 `spending_goal`</li>
 * </ul>
 */
public enum GoalType {
    SAVING,
    SPENDING;

    /**
     * @throws InvalidRequestException 정의되지 않은 값
     */
    public static GoalType parse(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException("목표 유형은 필수입니다.");
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("지원하지 않는 목표 유형입니다.");
        }
    }

    /** 목록 필터. null·빈 값은 전체. */
    public static GoalType parseFilter(String value) {
        return value == null || value.isBlank() ? null : parse(value);
    }
}
