package com.ssafy.dodam.domain.deposit.service;

import com.ssafy.dodam.global.policy.InterestIncomeTaxPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Component;

/**
 * 예금 이자와 세금 계산.
 *
 * <p><b>계산 가정</b> — 명세·ERD·기획안에 계산식이 없어 정한 기본안이며 응답의 {@code assumptions}로도 나간다.
 *
 * <ul>
 *   <li>단리·만기일시지급으로 계산한다. 복리 주기나 이자지급 방식을 나타내는 컬럼이 ERD에 없다.</li>
 *   <li>이자 = 원금 × 연이율 ÷ 100 × 예치일수 ÷ 365, 원 단위 절사다. 윤년에도 365를 쓴다.</li>
 *   <li>원금은 {@code account.balance}다. 524 seed의 규약대로 이자가 포함되지 않은 값이다.</li>
 *   <li>우대금리·금리쿠폰은 반영하지 않는다. 충족 여부를 확인할 자료가 없다.</li>
 * </ul>
 *
 * <p>모든 중간값은 {@link BigDecimal}로 다룬다. 금액은 원 단위 정수라 {@code double}을 쓰면 큰 원금에서
 * 1원 단위가 틀어진다.
 */
@Component
public class DepositInterestCalculator {
    /** 세율·절사 규칙은 적금과 공유하는 {@link InterestIncomeTaxPolicy}를 따른다. */
    static final String TAX_POLICY = InterestIncomeTaxPolicy.POLICY_CODE;
    static final String DAY_COUNT = "ACT_365_SIMPLE";
    private static final BigDecimal DAYS_IN_YEAR = new BigDecimal("365");
    private static final BigDecimal PERCENT = new BigDecimal("100");

    /**
     * @param principal 원금(원). 음수는 호출 전에 걸러진다.
     * @param annualRatePercent 적용 연이율(%)
     * @param days 예치일수. 0이면 이자도 0이다.
     * @throws ArithmeticException 이자나 수령액이 long 범위를 넘는 경우
     */
    DepositInterest calculate(long principal, BigDecimal annualRatePercent, long days) {
        // 나눗셈을 한 번만 해서 중간 반올림을 없앤다. 절사는 마지막 한 번이다.
        BigDecimal gross = BigDecimal.valueOf(principal)
                .multiply(annualRatePercent)
                .multiply(BigDecimal.valueOf(days))
                .divide(PERCENT.multiply(DAYS_IN_YEAR), 0, RoundingMode.DOWN);
        long grossInterest = gross.longValueExact();
        long tax = InterestIncomeTaxPolicy.withhold(grossInterest);
        long netInterest = grossInterest - tax;
        long payout = Math.addExact(principal, netInterest);
        return new DepositInterest(grossInterest, tax, netInterest, payout);
    }

    /**
     * @param grossInterest 세전이자
     * @param tax 이자소득세 원천징수액
     * @param netInterest 세후이자
     * @param payoutAmount 원금 + 세후이자
     */
    record DepositInterest(long grossInterest, long tax, long netInterest, long payoutAmount) {
    }
}
