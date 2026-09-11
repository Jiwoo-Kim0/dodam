package com.ssafy.dodam.domain.transaction.dto.response;

import com.ssafy.dodam.domain.transaction.entity.TransactionCategory;

/**
 * 소비 카테고리 기준 행. 카테고리 수정 화면의 선택지와 목록 필터 값(code)의 출처다.
 */
public record TransactionCategoryResponse(
        Long categoryId,
        String code,
        String name,
        String defaultSpendingType
) {

    public static TransactionCategoryResponse from(TransactionCategory category) {
        return new TransactionCategoryResponse(
                category.getId(),
                category.getCode(),
                category.getName(),
                category.getDefaultSpendingType() == null ? null : category.getDefaultSpendingType().name()
        );
    }
}
