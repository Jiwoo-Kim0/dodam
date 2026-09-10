package com.ssafy.dodam.domain.savings.service;

import com.ssafy.dodam.domain.savings.dto.response.SavingsCashflowResponse;
import com.ssafy.dodam.domain.savings.dto.response.SavingsMonthlyAmountResponse;
import com.ssafy.dodam.domain.savings.repository.SavingsQueryRepository;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저축을 반영한 월 현금흐름 부담과 가용금액.
 *
 * <p>명세 11-2의 <b>현금흐름 부담</b>과 <b>저축 후 가용금액</b>은 같은 계산의 두 관점이라 한 서비스가
 * 소유한다. 부담 API는 비율을, 가용금액 API는 잔액을 앞세울 뿐 구성 요소는 같다. 두 값을 따로 계산하면
 * 같은 달에 서로 어긋난 숫자가 나올 수 있다.
 *
 * <p><b>데이터 출처는 로컬 DB다.</b> 명세의 출처가 "자체"이며 외부 금융망을 호출하지 않는다.
 *
 * <p><b>예정 이벤트를 중복 반영하지 않는다.</b> 명세의 제한사항이다. 저축은
 * {@link SavingsQueryService#monthlyAmount}가 이미 납입 완료·저장된 예정·약정 추정을 중복 없이 합산한
 * 값을 그대로 쓰고, 대출 상환은 낸 금액과 남은 예정액을 상태별로 분리해 더한다.
 */
@Service
@Transactional(readOnly = true)
public class SavingsCashflowService {
    /** 이미 빠져나간 상환. 남은 예정액과 겹치지 않는다. */
    private static final String REPAYMENT_PAID = "PAID";
    /** 아직 남은 예정 상환. */
    private static final String REPAYMENT_SCHEDULED = "SCHEDULED";

    private static final String REASON_INCOME_NOT_REGISTERED = "INCOME_NOT_REGISTERED";
    private static final String REASON_ZERO_INCOME = "ZERO_INCOME";
    private static final String REASON_FIXED_EXPENSE_NOT_REGISTERED = "FIXED_EXPENSE_NOT_REGISTERED";
    private static final String REASON_AMOUNT_OUT_OF_RANGE = "AMOUNT_OUT_OF_RANGE";

    /**
     * 가정 코드. 코드명 오름차순으로 반환한다.
     *
     * <p>소비 지출은 고정지출 등록값만 본다. 카드·계좌 거래를 월 지출로 집계하는 것은 명세 15의
     * Cashflow가 소유하며 여기서 다시 계산하면 두 API가 다른 값을 낸다.
     */
    private static final List<String> ASSUMPTIONS = List.of(
            "EXCLUDES_VARIABLE_SPENDING",
            "FIXED_EXPENSE_FROM_PROFILE",
            "FUTURE_MONTHLY_PAYMENTS_AS_SCHEDULED",
            "INCOME_ASSUMED_CONSTANT");

    private final SavingsQueryRepository repository;
    private final SavingsQueryService savings;
    private final SavingsClock clock;

    public SavingsCashflowService(
            SavingsQueryRepository repository, SavingsQueryService savings, SavingsClock clock
    ) {
        this.repository = repository;
        this.savings = savings;
        this.clock = clock;
    }

    /** 현금흐름 부담. 조회 월을 받는다. */
    public SavingsCashflowResponse cashflowBurden(Long userId, String yearMonth) {
        LocalDate today = clock.today();
        return calculate(userId, savings.resolveMonth(yearMonth, today), today, null);
    }

    /**
     * 저축 후 가용금액. 기준일을 받아 그 날이 속한 달로 계산한다.
     *
     * <p>부담 API와 같은 달이면 같은 값을 낸다. 기준일을 월로 접는 이유는 소득·고정지출·저축이 모두
     * 월 단위 항목이라 일 단위로 나눌 근거가 없기 때문이다.
     */
    public SavingsCashflowResponse disposableBalance(Long userId, LocalDate baseDate) {
        LocalDate today = clock.today();
        LocalDate resolved = baseDate == null ? today : baseDate;
        YearMonth month = YearMonth.from(resolved);
        if (month.isAfter(YearMonth.from(today).plusMonths(11))) {
            throw new InvalidRequestException("기준일은 현재 월부터 12개월 이내여야 합니다.");
        }
        return calculate(userId, month, today, resolved);
    }

    private SavingsCashflowResponse calculate(
            Long userId, YearMonth month, LocalDate today, LocalDate baseDate
    ) {
        TreeSet<String> reasons = new TreeSet<>();
        Optional<SavingsQueryRepository.IncomeProfile> profile = repository.findIncomeProfile(userId);
        Long income = profile.map(SavingsQueryRepository.IncomeProfile::monthlyIncome).orElse(null);
        Long fixedExpense = profile.map(SavingsQueryRepository.IncomeProfile::fixedExpense).orElse(null);
        if (income == null) {
            reasons.add(REASON_INCOME_NOT_REGISTERED);
        } else if (income <= 0) {
            reasons.add(REASON_ZERO_INCOME);
        }
        if (fixedExpense == null) {
            // 고정지출 미등록은 부담률 계산을 막지 않는다. 저축 부담률은 소득만으로 낼 수 있다.
            reasons.add(REASON_FIXED_EXPENSE_NOT_REGISTERED);
        }

        SavingsMonthlyAmountResponse savingsAmount = savings.monthlyAmount(userId, month.toString(), today);
        reasons.addAll(savingsAmount.reasons());
        Long savingsTotal = savingsAmount.expectedMonthlyAmount();

        Map<String, BigDecimal> repayments =
                repository.sumLoanRepaymentsByStatus(userId, month.atDay(1), month.atEndOfMonth());
        Long paidRepayment = SavingsAmounts.amount(repayments.getOrDefault(REPAYMENT_PAID, BigDecimal.ZERO));
        Long remainingRepayment =
                SavingsAmounts.amount(repayments.getOrDefault(REPAYMENT_SCHEDULED, BigDecimal.ZERO));
        if (paidRepayment == null || remainingRepayment == null) {
            reasons.add(REASON_AMOUNT_OUT_OF_RANGE);
        }

        Long outflow = sum(savingsTotal, fixedExpense, paidRepayment, remainingRepayment);
        Long disposable = income == null || outflow == null
                ? null : SavingsAmounts.amount(BigDecimal.valueOf(income).subtract(BigDecimal.valueOf(outflow)));
        BigDecimal savingsBurdenRate = SavingsAmounts.percent(savingsTotal, income);
        Long totalOutflow = outflow;
        BigDecimal totalBurdenRate = SavingsAmounts.percent(totalOutflow, income);

        return new SavingsCashflowResponse(month, today, baseDate, income, fixedExpense, savingsTotal,
                paidRepayment, remainingRepayment, disposable, savingsBurdenRate, totalBurdenRate,
                disposable == null ? null : disposable < 0, reasons.isEmpty(),
                List.copyOf(reasons), ASSUMPTIONS);
    }

    /** 하나라도 계산하지 못한 항목이 있으면 합계를 만들지 않는다. 빠진 항목을 0으로 보면 부담이 작아 보인다. */
    private static Long sum(Long... amounts) {
        BigDecimal total = BigDecimal.ZERO;
        for (Long amount : amounts) {
            if (amount == null) {
                return null;
            }
            total = total.add(BigDecimal.valueOf(amount));
        }
        return SavingsAmounts.amount(total);
    }
}
