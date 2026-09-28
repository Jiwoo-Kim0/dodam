package com.ssafy.dodam.domain.depositsaving.service;

import com.ssafy.dodam.domain.depositsaving.dto.response.DepositSavingMaturityResponse;
import com.ssafy.dodam.domain.depositsaving.dto.response.DepositSavingRateResponse;
import com.ssafy.dodam.domain.depositsaving.dto.response.DepositSavingResponse;
import com.ssafy.dodam.domain.depositsaving.dto.response.DepositSavingSummaryResponse;
import com.ssafy.dodam.domain.depositsaving.repository.DepositSavingQueryRepository;
import com.ssafy.dodam.domain.depositsaving.repository.DepositSavingQueryRepository.BalanceAggregate;
import com.ssafy.dodam.domain.user.entity.FinancialProfile;
import com.ssafy.dodam.domain.user.repository.FinancialProfileRepository;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.policy.YearMonthPolicy;
import com.ssafy.dodam.global.response.PageResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class DepositSavingQueryService {
    private final DepositSavingQueryRepository repository;
    private final FinancialProfileRepository profiles;
    private final YearMonthPolicy monthPolicy;
    private final DepositSavingClock clock;

    public DepositSavingQueryService(
            DepositSavingQueryRepository repository, FinancialProfileRepository profiles,
            YearMonthPolicy monthPolicy, DepositSavingClock clock
    ) {
        this.repository = repository;
        this.profiles = profiles;
        this.monthPolicy = monthPolicy;
        this.clock = clock;
    }

    public PageResponse<DepositSavingResponse> list(Long userId, String type, String status, int page, int size) {
        validateCode(type, Set.of("DEPOSIT", "SAVINGS"), "type");
        validateCode(status, Set.of("ACTIVE", "INACTIVE", "CLOSED"), "status");
        return PageResponse.from(repository.findAccounts(
                userId, type, status, null, null, pageRequest(page, size)), row -> row);
    }

    public DepositSavingSummaryResponse summary(Long userId) {
        var totals = repository.summarize(userId);
        var deposits = totals.getOrDefault("DEPOSIT", new BalanceAggregate("DEPOSIT", 0, BigDecimal.ZERO));
        var savings = totals.getOrDefault("SAVINGS", new BalanceAggregate("SAVINGS", 0, BigDecimal.ZERO));
        Long depositBalance = amountOrNull(deposits.balance());
        Long savingsBalance = amountOrNull(savings.balance());
        Long total = amountOrNull(deposits.balance().add(savings.balance()));
        return new DepositSavingSummaryResponse(
                deposits.count() + savings.count(), total,
                deposits.count(), depositBalance, savings.count(), savingsBalance,
                total == null || depositBalance == null || savingsBalance == null
                        ? "AMOUNT_OUT_OF_RANGE" : null);
    }

    public PageResponse<DepositSavingMaturityResponse> maturities(
            Long userId, int withinDays, int page, int size
    ) {
        if (withinDays < 1 || withinDays > 365) {
            throw new InvalidRequestException("withinDays는 1~365여야 합니다.");
        }
        LocalDate today = clock.today();
        return PageResponse.from(repository.findAccounts(
                userId, null, null, today, today.plusDays(withinDays), pageRequest(page, size)),
                row -> new DepositSavingMaturityResponse(
                        row, today, ChronoUnit.DAYS.between(today, row.expiryDate())));
    }

    public DepositSavingRateResponse rate(Long userId, String yearMonth) {
        // 한 요청의 기본 월·검증·조회 종료일은 모두 같은 기준일을 사용한다.
        LocalDate today = clock.today();
        YearMonth month = monthPolicy.resolveHistorical(yearMonth, today);
        LocalDate start = month.atDay(1);
        LocalDate end = month.equals(YearMonth.from(today)) ? today : month.atEndOfMonth();
        Long paid = amountOrNull(repository.sumPaid(userId, start, end));
        Long income = profiles.findById(userId).map(FinancialProfile::getMonthlyIncome).orElse(null);
        String reason = paid == null ? "AMOUNT_OUT_OF_RANGE"
                : income == null ? "INCOME_NOT_REGISTERED"
                : income == 0 ? "ZERO_INCOME" : null;
        BigDecimal rate = reason == null
                ? BigDecimal.valueOf(paid).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(income), 2, RoundingMode.HALF_UP)
                : null;
        return new DepositSavingRateResponse(
                month, today, start, end, paid, income, rate, reason,
                "SAVINGS_PAID_TO_PROFILE_INCOME", "CURRENT_PROFILE_ASSUMED_CONSTANT");
    }

    private static Long amountOrNull(BigDecimal value) {
        try {
            return value.longValueExact();
        } catch (ArithmeticException exception) {
            return null;
        }
    }

    private static PageRequest pageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidRequestException("page는 0 이상, size는 1~100이어야 합니다.");
        }
        return PageRequest.of(page, size);
    }

    private static void validateCode(String value, Set<String> allowed, String name) {
        if (value != null && !allowed.contains(value)) {
            throw new InvalidRequestException(name + " 값이 올바르지 않습니다.");
        }
    }
}
