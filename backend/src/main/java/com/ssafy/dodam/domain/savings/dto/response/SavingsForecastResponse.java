package com.ssafy.dodam.domain.savings.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 적금 중도해지·만기 예상금액.
 *
 * @param calculationDate 이자를 끊는 날. 중도해지는 기준일, 만기 예상은 만기일이다.
 * @param annualRate 적용 연이율. 중도해지는 계약이율이 아니라 중도해지이율이다.
 * @param ratePolicy 중도해지이율 산정 정책 코드. 만기 예상에서는 null이다.
 * @param rateTier 적용된 경과기간 구간. 만기 예상에서는 null이다.
 * @param elapsedMonths 개설일부터 기준일까지 경과한 만 개월 수. 만기 예상에서는 null이다.
 * @param paidPrincipal 기준일까지 실제로 납입한 원금 합
 * @param futurePrincipal 만기까지 더 낼 것으로 본 원금. 중도해지는 0이다.
 * @param expectedInterest 세전이자
 * @param tax 이자소득세 원천징수액. 예금과 같은 {@code GENERAL_INTEREST_INCOME_TAX_15_4} 정책이다
 * @param expectedNetInterest 세후이자 = 세전이자 − 세액
 * @param expectedAmount 원금 + 세후이자. 2026-09-16 전에는 세전 합계였다
 */
public record SavingsForecastResponse(
        Long accountId,
        String accountNoMasked,
        LocalDate asOfDate,
        LocalDate calculationDate,
        String status,
        List<String> reasons,
        List<String> assumptions,
        BigDecimal annualRate,
        String ratePolicy,
        String rateTier,
        Long elapsedMonths,
        Long paidPrincipal,
        Long futurePrincipal,
        Long expectedPrincipal,
        Long expectedInterest,
        Long tax,
        Long expectedNetInterest,
        Long expectedAmount
) {
}
