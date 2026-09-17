package com.ssafy.dodam.domain.user.entity;

/**
 * ERD `user.gender` CHECK 제약과 동일한 값. 미입력은 컬럼 NULL로 두고 enum 값에 포함하지 않는다.
 */
public enum Gender {
    MALE,
    FEMALE
}
