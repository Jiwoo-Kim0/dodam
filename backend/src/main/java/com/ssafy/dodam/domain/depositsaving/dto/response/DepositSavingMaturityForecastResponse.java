package com.ssafy.dodam.domain.depositsaving.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 예금·적금 공통 만기 예상금액 (S15P21D105-678).
 *
 * <p>예금(536)과 적금(537)의 만기 예상을 FE가 목록의 {@code accountId}만으로 한 화면에서 보여주기 위해 두 응답을
 * 같은 이름으로 맞춘 것이다. 계산은 각 도메인 서비스가 그대로 하고 여기서는 이름만 통일한다.
 *
 * @param type DEPOSIT 또는 SAVINGS
 * @param status COMPLETE 또는 UNAVAILABLE. UNAVAILABLE이면 금액 필드가 모두 null이고 {@code reasons}가 사유를 든다.
 *     0원은 "이자가 없다"는 뜻이지 "계산하지 못했다"는 뜻이 아니므로 계산 불가를 0으로 표현하지 않는다.
 * @param reasons 각 도메인의 사유 코드를 그대로 싣는다(예금 {@code MISSING_EXPIRY_DATE}, 적금 {@code INCOMPLETE_SAVINGS_TERMS} 등)
 * @param principal 기준일의 원금. 예금은 잔액, 적금은 납입 원금 합이며 이자를 포함하지 않는다
 * @param daysUntilMaturity 기준일부터 만기일까지 남은 일수. 만기일이 없으면 null, 이미 지났으면 음수
 * @param expectedPrincipal 만기 시점 원금. 예금은 {@code principal}과 같고 적금은 남은 예정 회차를 더한 값
 * @param expectedInterest 세전이자
 * @param tax 이자소득세 원천징수액(15.4%, 원 단위 절사)
 * @param expectedNetInterest 세후이자
 * @param expectedAmount 만기 예상 수령액 = 만기 원금 + 세후이자. 추정치이며 우대금리·만기 후 이율은 반영하지 않는다
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public record DepositSavingMaturityForecastResponse(
        LocalDate asOfDate,
        Long accountId,
        String accountName,
        String accountNoMasked,
        String bankCode,
        String type,
        String status,
        List<String> reasons,
        List<String> assumptions,
        Long principal,
        BigDecimal interestRate,
        LocalDate openingDate,
        LocalDate expiryDate,
        Long daysUntilMaturity,
        Long expectedPrincipal,
        Long expectedInterest,
        Long tax,
        Long expectedNetInterest,
        Long expectedAmount
) {
}
