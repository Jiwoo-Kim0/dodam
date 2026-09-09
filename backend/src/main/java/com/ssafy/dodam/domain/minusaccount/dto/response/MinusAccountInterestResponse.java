package com.ssafy.dodam.domain.minusaccount.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 마이너스통장 이자 현황.
 *
 * <p>저장값과 계산값을 분리해 반환한다. seed의 `accrued_interest`를 일할 계산으로 역산하면
 * 30~34일치로 흩어져 특정 기산 기준이 나오지 않으므로, {@code accruedInterest}(저장값)와
 * {@code estimated*}(계산값)는 서로 검증하지 않는다.
 *
 * @param accruedInterest                저장된 미수이자. 서버가 다시 계산하지 않는다.
 * @param daysToNextInterestDate         기준일부터 다음 이자일까지의 일수.
 *                                       이자일이 없거나 이미 지났으면 null이다.
 * @param estimatedDailyInterest         일할 예상이자(원). `usedAmount × interestRate ÷ 100 ÷ 365`를
 *                                       원 단위로 절사한 값이며 실제 청구액이 아니다.
 * @param estimatedInterestUntilNextDate 다음 이자일까지의 예상이자.
 *                                       {@code daysToNextInterestDate}가 null이면 함께 null이다.
 */
public record MinusAccountInterestResponse(
        Long accountId,
        String accountNoMasked,
        BigDecimal interestRate,
        Long usedAmount,
        Long accruedInterest,
        LocalDate nextInterestDate,
        Integer daysToNextInterestDate,
        Long estimatedDailyInterest,
        Long estimatedInterestUntilNextDate,
        Integer overdueDays,
        boolean overdue
) {
}
