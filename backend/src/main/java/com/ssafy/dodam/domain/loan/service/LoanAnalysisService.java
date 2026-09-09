package com.ssafy.dodam.domain.loan.service;

import static com.ssafy.dodam.domain.loan.repository.LoanSpecifications.ownedBy;
import static com.ssafy.dodam.domain.loan.service.LoanAnalysisAmounts.OUT_OF_RANGE;
import static com.ssafy.dodam.domain.loan.service.LoanAnalysisAmounts.sum;

import com.ssafy.dodam.domain.loan.dto.response.LoanCostComparisonResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanCostItemResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanInterestForecastResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanInterestMonthItemResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanInterestSummaryResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanMonthlyRepaymentItemResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanMonthlyRepaymentsResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanMonthlyTotalResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanRemainingAmountsResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanRemainingItemResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanRepaymentBurdenResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanUnavailableResponse;
import com.ssafy.dodam.domain.loan.entity.Loan;
import com.ssafy.dodam.domain.loan.entity.LoanStatus;
import com.ssafy.dodam.domain.loan.repository.LoanRepository;
import com.ssafy.dodam.domain.user.entity.FinancialProfile;
import com.ssafy.dodam.domain.user.repository.FinancialProfileRepository;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.policy.YearMonthPeriod;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class LoanAnalysisService {
    private final LoanRepository loans;
    private final FinancialProfileRepository profiles;
    private final SensitiveNumberHasher hasher;
    private final LoanForecastCalculator calculator;
    private final LoanAnalysisPeriodPolicy periods;
    private final LoanAnalysisClock clock;

    public LoanAnalysisService(
            LoanRepository loans, FinancialProfileRepository profiles, SensitiveNumberHasher hasher,
            LoanForecastCalculator calculator, LoanAnalysisPeriodPolicy periods, LoanAnalysisClock clock
    ) {
        this.loans = loans;
        this.profiles = profiles;
        this.hasher = hasher;
        this.calculator = calculator;
        this.periods = periods;
        this.clock = clock;
    }

    public LoanMonthlyRepaymentsResponse monthlyRepayments(Long userId, String start, String end) {
        LocalDate today = clock.today();
        YearMonthPeriod period = periods.resolve(start, end, YearMonth.from(today));
        List<Loan> owned = ownedLoans(userId);
        List<LoanForecast> forecasts = forecast(owned, today, period);
        List<LoanMonthlyRepaymentItemResponse> months = months(period).stream()
                .map(month -> monthly(month, today, owned.isEmpty(), forecasts)).toList();
        List<String> reasons = sorted(months.stream()
                .flatMap(month -> combined(month.monthlyTotal().unavailableReasons(),
                        month.remaining().unavailableReasons()).stream()).toList());
        return new LoanMonthlyRepaymentsResponse(today, assumptions(owned, false), reasons.isEmpty(),
                reasons, unavailable(forecasts), "MONTH_TOTAL_AND_REMAINING",
                period.startMonth(), period.endMonth(), months);
    }

    public LoanRepaymentBurdenResponse repaymentBurden(Long userId, String value) {
        LocalDate today = clock.today();
        YearMonth month = periods.single(value, YearMonth.from(today));
        List<Loan> owned = ownedLoans(userId);
        List<LoanForecast> forecasts = forecast(owned, today, new YearMonthPeriod(month, month));
        LoanMonthlyRepaymentItemResponse monthly = monthly(month, today, owned.isEmpty(), forecasts);
        Long income = profiles.findById(userId).map(FinancialProfile::getMonthlyIncome).orElse(null);
        List<String> reasons = new ArrayList<>(combined(
                monthly.monthlyTotal().unavailableReasons(), monthly.remaining().unavailableReasons()));
        if (income == null) {
            reasons.add("INCOME_MISSING");
        } else if (income <= 0) {
            reasons.add("INCOME_ZERO");
        }
        Long amount = monthly.monthlyTotal().monthlyRepaymentAmount();
        BigDecimal rate = amount == null || income == null || income <= 0 ? null : percent(amount, income);
        return new LoanRepaymentBurdenResponse(today, assumptions(owned, true), rate != null,
                sorted(reasons), unavailable(forecasts), "MONTH_TOTAL", month, income, "CURRENT_PROFILE",
                monthly.monthlyTotal(), monthly.remaining(), rate);
    }

    public LoanInterestForecastResponse interestForecast(Long userId, String accountNo) {
        LocalDate today = clock.today();
        Loan loan = loans.findByUserIdAndAccountNoHash(userId, hasher.hash(accountNo))
                .orElseThrow(() -> new ResourceNotFoundException("대출을 찾을 수 없습니다."));
        LoanForecast forecast = calculator.calculate(loan, today, LoanRepaymentScheduleCalculator.MAX_SCHEDULE_SIZE);
        if (!forecast.available()) {
            return new LoanInterestForecastResponse(today, assumptions(List.of(loan), false),
                    "REMAINING_SCHEDULE", loan.getId(), loan.getAccountNoMasked(), "UNAVAILABLE",
                    forecast.reasons(), loan.getMaturityDate(), null, false, null, null, List.of());
        }
        List<LoanRepaymentScheduleItem> items = forecast.items();
        Long total = sum(items, LoanRepaymentScheduleItem::interest);
        List<String> reasons = new ArrayList<>();
        if (forecast.truncated()) {
            reasons.add("SCHEDULE_TRUNCATED");
        }
        if (total == null) {
            reasons.add(OUT_OF_RANGE);
        }
        LocalDate covered = items.isEmpty() ? null : items.getLast().repaymentDate();
        List<LoanInterestMonthItemResponse> months = items.stream()
                .map(item -> YearMonth.from(item.repaymentDate())).distinct()
                .map(month -> interestMonth(month, List.of(forecast))).toList();
        return new LoanInterestForecastResponse(today, assumptions(List.of(loan), false),
                "REMAINING_SCHEDULE", loan.getId(), loan.getAccountNoMasked(),
                reasons.isEmpty() ? "COMPLETE" : "PARTIAL", sorted(reasons),
                loan.getMaturityDate(), covered, forecast.truncated(), total,
                forecast.truncated() ? null : total, months);
    }

    public LoanInterestSummaryResponse interestSummary(Long userId, int count) {
        LocalDate today = clock.today();
        YearMonthPeriod period = periods.forecast(count, YearMonth.from(today));
        List<Loan> owned = ownedLoans(userId);
        List<LoanForecast> forecasts = forecast(owned, today, period);
        InterestTotal total = interestTotal(forecasts, period);
        return new LoanInterestSummaryResponse(today, assumptions(owned, false), total.reasons().isEmpty(),
                total.reasons(), unavailable(forecasts), "REMAINING_SCHEDULE",
                period.startMonth(), period.endMonth(), total.known(), total.total(),
                months(period).stream().map(month -> interestMonth(month, forecasts)).toList());
    }

    public LoanCostComparisonResponse costComparison(Long userId, int count) {
        LocalDate today = clock.today();
        YearMonthPeriod period = periods.forecast(count, YearMonth.from(today));
        List<Loan> owned = ownedLoans(userId);
        List<LoanForecast> forecasts = forecast(owned, today, period);
        InterestTotal total = interestTotal(forecasts, period);
        List<LoanCostItemResponse> items = forecasts.stream().map(forecast -> {
            Loan loan = forecast.loan();
            Long amount = forecast.available() ? sum(inPeriod(forecast, period), LoanRepaymentScheduleItem::interest) : null;
            List<String> reasons = forecast.available() && amount == null ? List.of(OUT_OF_RANGE) : forecast.reasons();
            BigDecimal share = amount != null && total.total() != null && total.total() > 0
                    ? percent(amount, total.total()) : null;
            return new LoanCostItemResponse(loan.getId(), loan.getAccountName(), loan.getAccountNoMasked(),
                    loan.getInstitutionName(), loan.getBalance(), loan.getInterestRate(), amount, share,
                    !forecast.available() ? "UNAVAILABLE" : amount == null ? "PARTIAL" : "COMPLETE", reasons);
        }).sorted(Comparator.comparing(LoanCostItemResponse::forecastInterest,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(LoanCostItemResponse::loanId)).toList();
        return new LoanCostComparisonResponse(today, assumptions(owned, false), total.reasons().isEmpty(),
                total.reasons(), unavailable(forecasts), "REMAINING_SCHEDULE",
                period.startMonth(), period.endMonth(), total.known(), total.total(), items);
    }

    private List<Loan> ownedLoans(Long userId) {
        // 현재 월 전체 금액의 판단에는 종료 대출도 필요하다. 예측 대상은 조회 후 별도로 제한한다.
        return loans.findAll(ownedBy(userId)).stream().sorted(Comparator.comparing(Loan::getId)).toList();
    }

    private List<LoanForecast> forecast(List<Loan> owned, LocalDate today, YearMonthPeriod period) {
        int limit = (int) ChronoUnit.MONTHS.between(YearMonth.from(today), period.endMonth()) + 1;
        return owned.stream().filter(loan -> loan.getStatus().isOutstanding())
                .filter(loan -> loan.getBalance() == null || loan.getBalance() != 0)
                .map(loan -> calculator.calculate(loan, today, limit, period.endMonth().atEndOfMonth())).toList();
    }

    private LoanMonthlyRepaymentItemResponse monthly(
            YearMonth month, LocalDate today, boolean noLoans, List<LoanForecast> forecasts
    ) {
        YearMonthPeriod period = new YearMonthPeriod(month, month);
        List<LoanRepaymentScheduleItem> rows = forecasts.stream()
                .filter(LoanForecast::available).flatMap(forecast -> inPeriod(forecast, period).stream()).toList();
        Long principal = sum(rows, LoanRepaymentScheduleItem::principal);
        Long interest = sum(rows, LoanRepaymentScheduleItem::interest);
        Long amount = sum(rows, LoanRepaymentScheduleItem::amount);
        List<LoanUnavailableResponse> unavailable = unavailable(forecasts);
        List<String> reasons = new ArrayList<>();
        if (!unavailable.isEmpty()) {
            reasons.add("INCOMPLETE_LOAN_DATA");
        }
        if (principal == null || interest == null || amount == null) {
            reasons.add(OUT_OF_RANGE);
        }
        LoanRemainingAmountsResponse remaining = new LoanRemainingAmountsResponse(reasons.isEmpty(),
                sorted(reasons), unavailable, principal, interest, amount,
                unavailable.isEmpty() ? principal : null, unavailable.isEmpty() ? interest : null,
                unavailable.isEmpty() ? amount : null);
        // 납부 후의 잔액만으로 월 전체 상환액을 복원하지 않는다.
        boolean unknownMonth = month.equals(YearMonth.from(today)) && !noLoans;
        List<String> monthReasons = unknownMonth
                ? combined(reasons, List.of("MONTH_TOTAL_UNAVAILABLE")) : sorted(reasons);
        Long monthlyAmount = unknownMonth ? null : remaining.remainingRepaymentAmount();
        LoanMonthlyTotalResponse total = new LoanMonthlyTotalResponse(monthlyAmount != null, monthReasons, monthlyAmount);
        List<LoanRemainingItemResponse> items = forecasts.stream().map(forecast -> {
            Loan loan = forecast.loan();
            List<LoanRepaymentScheduleItem> selected = inPeriod(forecast, period);
            return new LoanRemainingItemResponse(loan.getId(), loan.getAccountName(), loan.getAccountNoMasked(),
                    forecast.available() ? "COMPLETE" : "UNAVAILABLE", forecast.reasons(),
                    forecast.available() ? sum(selected, LoanRepaymentScheduleItem::principal) : null,
                    forecast.available() ? sum(selected, LoanRepaymentScheduleItem::interest) : null,
                    forecast.available() ? sum(selected, LoanRepaymentScheduleItem::amount) : null);
        }).toList();
        return new LoanMonthlyRepaymentItemResponse(month, total, remaining, items);
    }

    private InterestTotal interestTotal(List<LoanForecast> forecasts, YearMonthPeriod period) {
        List<LoanRepaymentScheduleItem> rows = forecasts.stream().filter(LoanForecast::available)
                .flatMap(forecast -> inPeriod(forecast, period).stream()).toList();
        Long known = sum(rows, LoanRepaymentScheduleItem::interest);
        List<String> reasons = new ArrayList<>();
        if (forecasts.stream().anyMatch(forecast -> !forecast.available())) {
            reasons.add("INCOMPLETE_LOAN_DATA");
        }
        if (known == null) {
            reasons.add(OUT_OF_RANGE);
        }
        return new InterestTotal(known, reasons.isEmpty() ? known : null, sorted(reasons));
    }

    private LoanInterestMonthItemResponse interestMonth(YearMonth month, List<LoanForecast> forecasts) {
        InterestTotal total = interestTotal(forecasts, new YearMonthPeriod(month, month));
        return new LoanInterestMonthItemResponse(month, total.reasons().isEmpty(), total.reasons(),
                unavailable(forecasts), total.known(), total.total());
    }

    private List<LoanRepaymentScheduleItem> inPeriod(LoanForecast forecast, YearMonthPeriod period) {
        return forecast.items().stream().filter(item -> {
            YearMonth month = YearMonth.from(item.repaymentDate());
            return !month.isBefore(period.startMonth()) && !month.isAfter(period.endMonth());
        }).toList();
    }

    private List<LoanUnavailableResponse> unavailable(List<LoanForecast> forecasts) {
        return forecasts.stream().filter(forecast -> !forecast.available())
                .map(forecast -> new LoanUnavailableResponse(forecast.loan().getId(), forecast.reasons())).toList();
    }

    private List<YearMonth> months(YearMonthPeriod period) {
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth month = period.startMonth(); !month.isAfter(period.endMonth()); month = month.plusMonths(1)) {
            months.add(month);
        }
        return List.copyOf(months);
    }

    private List<String> assumptions(List<Loan> owned, boolean income) {
        List<String> assumptions = new ArrayList<>(List.of("FIXED_CURRENT_RATE",
                "NO_EARLY_REPAYMENT_OR_NEW_BORROWING", "EXCLUDES_FEES", "MONTHLY_RATE_NOT_DAILY_PRORATED"));
        if (income) {
            assumptions.add("CURRENT_INCOME_UNCHANGED");
        }
        if (owned.stream().anyMatch(loan -> loan.getStatus() == LoanStatus.OVERDUE)) {
            assumptions.add("EXCLUDES_OVERDUE_INTEREST_AND_ARREARS");
        }
        return sorted(assumptions);
    }

    private static BigDecimal percent(long numerator, long denominator) {
        return BigDecimal.valueOf(numerator).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private static List<String> sorted(List<String> values) {
        return List.copyOf(new TreeSet<>(values));
    }

    private static List<String> combined(List<String> first, List<String> second) {
        List<String> values = new ArrayList<>(first);
        values.addAll(second);
        return sorted(values);
    }

    private record InterestTotal(Long known, Long total, List<String> reasons) {
    }
}
