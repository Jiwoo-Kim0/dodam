package com.ssafy.dodam.domain.loan.service;

import com.ssafy.dodam.domain.loan.entity.Loan;
import com.ssafy.dodam.domain.loan.entity.RepaymentType;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 향후 상환일정 계산기.
 *
 * <p>`loan_repayment`는 실제 상환 내역만 보관하므로 남은 일정은 저장값이 아니라 계산값이다.
 * 기준은 현재 잔액, 연이율, 상환일, 만기일, 상환 방식이다.
 *
 * <p>월이율 r = 연이율 / 100 / 12이며 회차별 이자는 직전 잔액 x r을 원 단위로 반올림한다.
 * 금액은 모두 원 단위 정수이고 비율 계산에만 {@link BigDecimal}을 사용한다.
 *
 * <ul>
 *   <li>원금균등: 매 회차 원금 = 잔액 / 남은 회차, 이자 = 직전 잔액 x r</li>
 *   <li>원리금균등: 월 상환액 A = P x r x (1+r)^n / ((1+r)^n - 1), 이자 = 직전 잔액 x r</li>
 *   <li>만기일시: 매 회차 이자만 내고 마지막 회차에 원금 전액을 상환한다</li>
 * </ul>
 *
 * <p>마지막 회차는 반올림 누적 오차를 흡수하도록 남은 잔액 전액을 원금으로 상환한다.
 */
@Component
public class LoanRepaymentScheduleCalculator {

    /**
     * 한 번에 반환할 최대 회차. 30년 만기 대출도 360회를 넘지 않으며 응답 크기를 제한한다.
     */
    public static final int MAX_SCHEDULE_SIZE = 360;

    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);
    private static final BigDecimal PERCENT = BigDecimal.valueOf(100);
    private static final MathContext RATE_CONTEXT = MathContext.DECIMAL64;

    public LoanRepaymentSchedule calculate(Loan loan, LocalDate asOfDate) {
        return calculate(LoanRepaymentTerms.from(loan), asOfDate);
    }

    public LoanRepaymentSchedule calculate(LoanRepaymentTerms terms, LocalDate asOfDate) {
        LocalDate firstDate = resolveFirstRepaymentDate(terms, asOfDate);
        long balance = terms.balance();
        if (firstDate == null || terms.maturityDate() == null || balance <= 0) {
            // 만기일이나 상환일이 없으면 회차를 특정할 수 없다. 추정하지 않고 빈 일정으로 응답한다.
            return new LoanRepaymentSchedule(List.of(), false);
        }

        int totalCount = countRemaining(firstDate, terms.maturityDate());
        if (totalCount <= 0) {
            return new LoanRepaymentSchedule(List.of(), false);
        }
        boolean truncated = totalCount > MAX_SCHEDULE_SIZE;
        int size = Math.min(totalCount, MAX_SCHEDULE_SIZE);

        BigDecimal monthlyRate = monthlyRate(terms.annualRatePercent());
        long equalPayment = terms.repaymentType() == RepaymentType.EQUAL_PAYMENT
                ? equalPaymentAmount(balance, monthlyRate, totalCount)
                : 0L;

        List<LoanRepaymentScheduleItem> items = new ArrayList<>(size);
        long remaining = balance;
        for (int index = 0; index < size; index++) {
            boolean last = index == totalCount - 1;
            long interest = interestOf(remaining, monthlyRate);
            long principal = Math.min(
                    principalOf(
                            terms.repaymentType(),
                            remaining,
                            interest,
                            equalPayment,
                            totalCount - index,
                            last
                    ),
                    remaining
            );
            remaining -= principal;
            items.add(new LoanRepaymentScheduleItem(
                    index + 1,
                    repaymentDate(firstDate, index),
                    principal + interest,
                    principal,
                    interest,
                    remaining
            ));
        }
        return new LoanRepaymentSchedule(items, truncated);
    }

    private static long principalOf(
            RepaymentType repaymentType,
            long remaining,
            long interest,
            long equalPayment,
            int remainingCount,
            boolean last
    ) {
        if (last) {
            return remaining;
        }
        return switch (repaymentType) {
            case EQUAL_PRINCIPAL -> remaining / remainingCount;
            case EQUAL_PAYMENT -> Math.max(equalPayment - interest, 0L);
            case BULLET -> 0L;
        };
    }

    private static long interestOf(long remaining, BigDecimal monthlyRate) {
        return BigDecimal.valueOf(remaining)
                .multiply(monthlyRate)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /**
     * 원리금균등 월 상환액. 무이자면 원금을 회차로 나눈다.
     */
    private static long equalPaymentAmount(long principal, BigDecimal monthlyRate, int count) {
        if (monthlyRate.signum() == 0) {
            return BigDecimal.valueOf(principal)
                    .divide(BigDecimal.valueOf(count), 0, RoundingMode.CEILING)
                    .longValueExact();
        }
        BigDecimal onePlusRate = BigDecimal.ONE.add(monthlyRate);
        BigDecimal growth = onePlusRate.pow(count, RATE_CONTEXT);
        return BigDecimal.valueOf(principal)
                .multiply(monthlyRate, RATE_CONTEXT)
                .multiply(growth, RATE_CONTEXT)
                .divide(growth.subtract(BigDecimal.ONE), RATE_CONTEXT)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private static BigDecimal monthlyRate(BigDecimal annualRatePercent) {
        if (annualRatePercent == null) {
            return BigDecimal.ZERO;
        }
        return annualRatePercent.divide(PERCENT, RATE_CONTEXT)
                .divide(MONTHS_PER_YEAR, RATE_CONTEXT);
    }

    /**
     * 첫 회차 날짜. `next_repayment_date`가 있으면 그대로 쓰고, 없으면 상환일 기준으로 기준일
     * 이후의 가장 빠른 날짜를 사용한다. 둘 다 없으면 회차를 특정할 수 없어 null이다.
     */
    private static LocalDate resolveFirstRepaymentDate(
            LoanRepaymentTerms terms,
            LocalDate asOfDate
    ) {
        if (terms.nextRepaymentDate() != null) {
            return terms.nextRepaymentDate();
        }
        Integer repaymentDay = terms.repaymentDay();
        if (repaymentDay == null) {
            return null;
        }
        LocalDate candidate = atRepaymentDay(YearMonth.from(asOfDate), repaymentDay);
        return candidate.isBefore(asOfDate)
                ? atRepaymentDay(YearMonth.from(asOfDate).plusMonths(1), repaymentDay)
                : candidate;
    }

    /**
     * 회차 날짜. 31일처럼 해당 월에 없는 상환일은 그 달의 말일로 보정한다.
     */
    private static LocalDate repaymentDate(LocalDate firstDate, int monthOffset) {
        YearMonth targetMonth = YearMonth.from(firstDate).plusMonths(monthOffset);
        return atRepaymentDay(targetMonth, firstDate.getDayOfMonth());
    }

    private static LocalDate atRepaymentDay(YearMonth month, int day) {
        return month.atDay(Math.min(day, month.lengthOfMonth()));
    }

    /**
     * 첫 회차 월부터 만기 월까지의 회차 수. 두 월을 모두 포함한다.
     */
    private static int countRemaining(LocalDate firstDate, LocalDate maturityDate) {
        long months = ChronoUnit.MONTHS.between(
                YearMonth.from(firstDate),
                YearMonth.from(maturityDate)
        );
        return months < 0 ? 0 : (int) months + 1;
    }
}
