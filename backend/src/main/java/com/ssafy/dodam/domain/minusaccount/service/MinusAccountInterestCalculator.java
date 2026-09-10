package com.ssafy.dodam.domain.minusaccount.service;

import com.ssafy.dodam.domain.minusaccount.dto.response.MinusAccountInterestResponse;
import com.ssafy.dodam.domain.minusaccount.entity.MinusAccount;
import com.ssafy.dodam.domain.minusaccount.entity.MinusAccountOwner;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Component;

/**
 * 마이너스통장 예상이자 계산.
 *
 * <p>계산식: {@code usedAmount × interestRate ÷ 100 ÷ 365}를 원 단위로 절사한 값이 하루치다.
 * 기산 기준(actual/365)은 API 명세와 기획안에 정의가 없어 관행값을 택한 것이며, 저장된
 * `accrued_interest`를 재현하지 않는다. seed 값을 역산하면 30~34일치로 흩어져 특정 기준을
 * 확정할 수 없었다. 따라서 저장된 미수이자와 이 계산값은 별개로 다룬다.
 *
 * <p>연체 가산금리 계약이 없으므로 연체 상태에서도 같은 금리로 계산한다. 연체이자는 반영되지 않는다.
 *
 * <p>금액은 부동소수점을 쓰지 않고 BigDecimal로 계산한 뒤 마지막에 절사한다.
 */
@Component
public class MinusAccountInterestCalculator {

    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);
    private static final BigDecimal DAYS_IN_YEAR = BigDecimal.valueOf(365);

    public MinusAccountInterestResponse calculate(
            MinusAccountOwner owner,
            MinusAccount detail,
            LocalDate today
    ) {
        Integer daysToNextInterestDate = daysToNextInterestDate(detail.getNextInterestDate(), today);
        long dailyInterest = estimatedDailyInterest(detail);

        Long interestUntilNextDate = daysToNextInterestDate == null
                ? null
                : dailyInterest * daysToNextInterestDate;

        return new MinusAccountInterestResponse(
                detail.getAccountId(),
                owner.getAccountNoMasked(),
                detail.getInterestRate(),
                detail.getUsedAmount(),
                detail.getAccruedInterest(),
                detail.getNextInterestDate(),
                daysToNextInterestDate,
                dailyInterest,
                interestUntilNextDate,
                detail.getOverdueDays(),
                detail.isOverdue()
        );
    }

    long estimatedDailyInterest(MinusAccount detail) {
        return BigDecimal.valueOf(detail.getUsedAmount())
                .multiply(detail.getInterestRate())
                .divide(PERCENT, 10, RoundingMode.HALF_UP)
                .divide(DAYS_IN_YEAR, 0, RoundingMode.DOWN)
                .longValue();
    }

    /**
     * 이자일이 없거나 이미 지났으면 null이다. 기준일과 같은 날이면 0을 반환한다.
     *
     * <p>seed의 `next_interest_date`는 1,000행 모두 2026-09-25이므로 그날이 지나면 시연 데이터
     * 전체가 null이 된다. seed(S15P21D105-523) 쪽 보완이 필요한 사항으로 보고한다.
     */
    Integer daysToNextInterestDate(LocalDate nextInterestDate, LocalDate today) {
        if (nextInterestDate == null || nextInterestDate.isBefore(today)) {
            return null;
        }
        return (int) ChronoUnit.DAYS.between(today, nextInterestDate);
    }
}
