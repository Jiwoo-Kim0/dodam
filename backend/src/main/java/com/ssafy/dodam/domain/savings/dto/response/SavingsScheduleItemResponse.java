package com.ssafy.dodam.domain.savings.dto.response;

import java.time.LocalDate;

/**
 * 납입 예정 한 건.
 *
 * @param paymentId 저장된 회차의 id. 약정액으로 만든 추정 항목은 null이다.
 * @param source {@code STORED_SCHEDULED}(저장된 예정 회차) 또는 {@code ESTIMATED_MONTHLY}(월 약정액 추정)
 */
public record SavingsScheduleItemResponse(
        Long accountId, String accountNoMasked, Long paymentId, LocalDate paymentDate,
        long amount, String source
) {
}
