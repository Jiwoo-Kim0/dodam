package com.ssafy.dodam.domain.finance.dto;

/**
 * @param scope ALL / ACCOUNT / CARD / LOAN / SAVING. 생략하면 ALL. 대소문자 무관, 그 외 값은 INVALID_REQUEST.
 */
public record FinanceSyncRequest(String scope) {
}
