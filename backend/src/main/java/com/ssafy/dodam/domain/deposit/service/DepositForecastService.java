package com.ssafy.dodam.domain.deposit.service;

import com.ssafy.dodam.domain.deposit.dto.response.DepositEarlyTerminationResponse;
import com.ssafy.dodam.domain.deposit.dto.response.DepositMaturityForecastResponse;
import com.ssafy.dodam.domain.deposit.repository.DepositAccountRow;
import com.ssafy.dodam.domain.deposit.service.DepositInterestCalculator.DepositInterest;
import com.ssafy.dodam.global.policy.EarlyTerminationRatePolicy;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예금 중도해지·만기 예상금액.
 *
 * <p><b>데이터 출처는 로컬 시연 DB다.</b> 원본 명세 11-1의 출처는 SSAFY지만 535와 같은 판단으로 외부
 * 금융망을 호출하지 않고 거래고유번호도 만들지 않는다. 금융망에는 예상금액을 돌려주는 조회가 따로 없어
 * 어차피 계약 조건을 받아 서버에서 계산해야 하고, 계산식은 이 서비스가 소유한다.
 *
 * <p>두 API는 계산 구조가 같고 <b>적용이율과 일수만 다르다.</b> 중도해지는 중도해지이율과 기준일까지의
 * 예치일수를, 만기 예상은 계약이율과 만기까지의 계약일수를 쓴다. 그래서 같은 서비스에 둔다.
 *
 * <p>세 API 모두 판정 어휘는 대출 분석(532·533)의 COMPLETE·UNAVAILABLE과 reasons를 따른다. 계산할 수
 * 없을 때 0원을 돌려주지 않는다. 0원은 "이자가 없다"는 뜻이지 "계산하지 못했다"는 뜻이 아니다.
 */
@Service
@Transactional(readOnly = true)
public class DepositForecastService {
    private static final String STATUS_COMPLETE = "COMPLETE";
    private static final String STATUS_UNAVAILABLE = "UNAVAILABLE";
    private static final String STATUS_CLOSED = "CLOSED";

    private static final String REASON_MISSING_DEPOSIT_DETAIL = "MISSING_DEPOSIT_DETAIL";
    private static final String REASON_ACCOUNT_CLOSED = "ACCOUNT_CLOSED";
    private static final String REASON_ZERO_PRINCIPAL = "ZERO_PRINCIPAL";
    private static final String REASON_INVALID_TERMS = "INVALID_TERMS";
    private static final String REASON_MISSING_EXPIRY_DATE = "MISSING_EXPIRY_DATE";
    private static final String REASON_ALREADY_MATURED = "ALREADY_MATURED";
    private static final String REASON_OUT_OF_RANGE = "CALCULATION_AMOUNT_OUT_OF_RANGE";

    /**
     * 두 API가 공유하는 가정 코드. 코드명 오름차순이다.
     *
     * <p>{@code ESTIMATED_EARLY_TERMINATION_RATE}는 중도해지에만 추가한다. 그 이율만 서비스 추정치이고
     * 만기 계산은 저장된 계약이율을 그대로 쓴다.
     */
    private static final List<String> BASE_ASSUMPTIONS = List.of(
            "EXCLUDES_PREFERENTIAL_RATE",
            "SIMPLE_INTEREST_AT_MATURITY",
            "TAXED_AT_GENERAL_RATE");
    private static final String ASSUMPTION_ESTIMATED_RATE = "ESTIMATED_EARLY_TERMINATION_RATE";

    private final DepositAccountLookup lookup;
    private final DepositInterestCalculator calculator;
    private final DepositClock clock;

    public DepositForecastService(
            DepositAccountLookup lookup, DepositInterestCalculator calculator, DepositClock clock
    ) {
        this.lookup = lookup;
        this.calculator = calculator;
        this.clock = clock;
    }

    public DepositEarlyTerminationResponse earlyTermination(Long userId, String accountNo) {
        DepositAccountRow deposit = lookup.require(userId, accountNo);
        LocalDate today = clock.today();
        List<String> assumptions = sorted(concat(BASE_ASSUMPTIONS, ASSUMPTION_ESTIMATED_RATE));

        List<String> reasons = commonReasons(deposit, today);
        if (deposit.expiryDate() != null && !deposit.expiryDate().isAfter(today)) {
            // 만기가 지난 예금은 중도해지 대상이 아니다. 만기 예상 API가 계약 기준 금액을 준다.
            reasons.add(REASON_ALREADY_MATURED);
        }
        if (!reasons.isEmpty()) {
            return unavailableTermination(today, assumptions, deposit, reasons);
        }

        // 예치일수는 개설일부터 해지일 전일까지다. 개설 당일 해지는 0일이라 이자가 붙지 않는다.
        long elapsedDays = ChronoUnit.DAYS.between(deposit.openingDate(), today);
        long elapsedMonths = ChronoUnit.MONTHS.between(deposit.openingDate(), today);
        BigDecimal appliedRate =
                EarlyTerminationRatePolicy.resolve(deposit.interestRate(), elapsedMonths);
        DepositInterest interest;
        try {
            interest = calculator.calculate(deposit.balance(), appliedRate, elapsedDays);
        } catch (ArithmeticException exception) {
            // 검증된 양수 원금·이율에서 나오는 금액 범위 초과만 격리한다. 0원으로 바꾸지 않는다.
            return unavailableTermination(today, assumptions, deposit, List.of(REASON_OUT_OF_RANGE));
        }
        return new DepositEarlyTerminationResponse(today, assumptions, deposit.accountId(),
                deposit.accountName(), deposit.accountNoMasked(), deposit.bankName(),
                STATUS_COMPLETE, List.of(), deposit.balance(), deposit.interestRate(),
                deposit.openingDate(), deposit.expiryDate(), elapsedDays, elapsedMonths,
                appliedRate, EarlyTerminationRatePolicy.POLICY_CODE,
                EarlyTerminationRatePolicy.describe(elapsedMonths),
                interest.grossInterest(), interest.tax(), interest.netInterest(),
                interest.payoutAmount());
    }

    public DepositMaturityForecastResponse maturityForecast(Long userId, String accountNo) {
        return maturityForecast(lookup.require(userId, accountNo), clock.today());
    }

    /**
     * 이미 소유권이 확인된 예금 행의 만기 예상. 678의 accountId 기준 조회가 계산식을 복제하지 않고
     * 같은 결과를 내도록 진입점만 나눈 것이다. 기준일은 호출자가 한 번 읽어 넘긴다.
     */
    public DepositMaturityForecastResponse maturityForecast(DepositAccountRow deposit, LocalDate today) {
        List<String> assumptions = sorted(BASE_ASSUMPTIONS);

        List<String> reasons = commonReasons(deposit, today);
        if (deposit.detailsAvailable() && deposit.expiryDate() == null) {
            reasons.add(REASON_MISSING_EXPIRY_DATE);
        }
        if (!reasons.isEmpty()) {
            return unavailableForecast(today, assumptions, deposit, reasons);
        }

        long contractDays = ChronoUnit.DAYS.between(deposit.openingDate(), deposit.expiryDate());
        DepositInterest interest;
        try {
            interest = calculator.calculate(deposit.balance(), deposit.interestRate(), contractDays);
        } catch (ArithmeticException exception) {
            return unavailableForecast(today, assumptions, deposit, List.of(REASON_OUT_OF_RANGE));
        }
        // 이미 만기가 지났어도 계약 기준 금액은 정해져 있다. matured로 표시하고 계산은 그대로 제공한다.
        return new DepositMaturityForecastResponse(today, assumptions, deposit.accountId(),
                deposit.accountName(), deposit.accountNoMasked(), deposit.bankName(),
                STATUS_COMPLETE, List.of(), deposit.balance(), deposit.interestRate(),
                deposit.openingDate(), deposit.expiryDate(), contractDays,
                ChronoUnit.DAYS.between(today, deposit.expiryDate()),
                !deposit.expiryDate().isAfter(today), interest.grossInterest(), interest.tax(),
                interest.netInterest(), interest.payoutAmount());
    }

    /**
     * 두 계산이 공통으로 요구하는 전제.
     *
     * <p>상세행이 없으면 금리·개설일이 모두 null이라 뒤의 검증을 할 수 없으므로 그 사유만 남기고 끝낸다.
     */
    private List<String> commonReasons(DepositAccountRow deposit, LocalDate today) {
        List<String> reasons = new ArrayList<>();
        if (STATUS_CLOSED.equals(deposit.status())) {
            reasons.add(REASON_ACCOUNT_CLOSED);
        }
        if (deposit.balance() <= 0) {
            reasons.add(REASON_ZERO_PRINCIPAL);
        }
        if (!deposit.detailsAvailable()) {
            reasons.add(REASON_MISSING_DEPOSIT_DETAIL);
            return reasons;
        }
        if (deposit.interestRate() == null || deposit.interestRate().signum() < 0
                || deposit.openingDate() == null || deposit.openingDate().isAfter(today)
                || (deposit.expiryDate() != null
                    && deposit.expiryDate().isBefore(deposit.openingDate()))) {
            reasons.add(REASON_INVALID_TERMS);
        }
        return reasons;
    }

    private DepositEarlyTerminationResponse unavailableTermination(
            LocalDate today, List<String> assumptions, DepositAccountRow deposit, List<String> reasons
    ) {
        return new DepositEarlyTerminationResponse(today, assumptions, deposit.accountId(),
                deposit.accountName(), deposit.accountNoMasked(), deposit.bankName(),
                STATUS_UNAVAILABLE, sorted(reasons), deposit.balance(), deposit.interestRate(),
                deposit.openingDate(), deposit.expiryDate(), null, null, null,
                EarlyTerminationRatePolicy.POLICY_CODE, null, null, null, null, null);
    }

    private DepositMaturityForecastResponse unavailableForecast(
            LocalDate today, List<String> assumptions, DepositAccountRow deposit, List<String> reasons
    ) {
        return new DepositMaturityForecastResponse(today, assumptions, deposit.accountId(),
                deposit.accountName(), deposit.accountNoMasked(), deposit.bankName(),
                STATUS_UNAVAILABLE, sorted(reasons), deposit.balance(), deposit.interestRate(),
                deposit.openingDate(), deposit.expiryDate(), null,
                deposit.expiryDate() == null ? null : ChronoUnit.DAYS.between(today, deposit.expiryDate()),
                deposit.expiryDate() == null ? null : !deposit.expiryDate().isAfter(today),
                null, null, null, null);
    }

    private static List<String> concat(List<String> values, String extra) {
        List<String> result = new ArrayList<>(values);
        result.add(extra);
        return result;
    }

    private static List<String> sorted(List<String> values) {
        return List.copyOf(new TreeSet<>(values));
    }
}
