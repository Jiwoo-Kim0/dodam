package com.ssafy.dodam.domain.savings.service;

import com.ssafy.dodam.domain.savings.dto.response.SavingsDetailResponse;
import com.ssafy.dodam.domain.savings.dto.response.SavingsForecastResponse;
import com.ssafy.dodam.domain.savings.dto.response.SavingsMonthlyAmountResponse;
import com.ssafy.dodam.domain.savings.dto.response.SavingsPaymentResponse;
import com.ssafy.dodam.domain.savings.dto.response.SavingsPaymentSchedulesResponse;
import com.ssafy.dodam.domain.savings.dto.response.SavingsScheduleItemResponse;
import com.ssafy.dodam.domain.savings.repository.SavingsAccountRow;
import com.ssafy.dodam.domain.savings.repository.SavingsPaymentRow;
import com.ssafy.dodam.domain.savings.repository.SavingsQueryRepository;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.policy.PaymentSchedulePeriodPolicy;
import com.ssafy.dodam.global.policy.QueryPeriod;
import com.ssafy.dodam.global.policy.QueryPeriodPolicy;
import com.ssafy.dodam.global.policy.YearMonthPolicy;
import com.ssafy.dodam.global.response.PageResponse;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.TreeSet;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 적금 상세·납입내역·납입 예정·월 저축액·예상금액.
 *
 * <p><b>데이터 출처는 로컬 시연 DB다.</b> 명세 11-2의 상세·납입내역·예상금액 출처는 SSAFY지만 535·536과
 * 같은 판단으로 외부 금융망 호출과 호출별 거래고유번호 생성을 하지 않는다. 시연 데이터가 이미 적재돼
 * 있고, 금융망 연결이 없는 사용자도 본인 데이터를 볼 수 있어야 하며, 금융망에는 예상금액을 돌려주는
 * 조회가 없어 어차피 서버에서 계산해야 한다.
 */
@Service
@Transactional(readOnly = true)
public class SavingsQueryService {
    private final SavingsQueryRepository repository;
    private final SensitiveNumberHasher hasher;
    private final SavingsClock clock;
    private final QueryPeriodPolicy historyPeriods;
    private final PaymentSchedulePeriodPolicy schedulePeriods;
    private final YearMonthPolicy months;
    private final SavingsScheduleCalculator schedules;
    private final SavingsForecastCalculator forecasts;

    public SavingsQueryService(SavingsQueryRepository repository, SensitiveNumberHasher hasher, SavingsClock clock,
            QueryPeriodPolicy historyPeriods, PaymentSchedulePeriodPolicy schedulePeriods, YearMonthPolicy months,
            SavingsScheduleCalculator schedules, SavingsForecastCalculator forecasts) {
        this.repository = repository;
        this.hasher = hasher;
        this.clock = clock;
        this.historyPeriods = historyPeriods;
        this.schedulePeriods = schedulePeriods;
        this.months = months;
        this.schedules = schedules;
        this.forecasts = forecasts;
    }

    public SavingsAccountRow owned(Long userId, String accountNo) {
        // 검증 오류에도 입력 원문을 넣지 않는다. 해시는 기존 HMAC 컴포넌트만 사용한다.
        if (accountNo == null || !accountNo.matches("[0-9]{10,20}")) {
            throw new InvalidRequestException("계좌번호 형식이 올바르지 않습니다.");
        }
        return repository.findOwned(userId, hasher.hash(accountNo))
                .orElseThrow(() -> new ResourceNotFoundException("적금을 찾을 수 없습니다."));
    }

    public SavingsDetailResponse detail(Long userId, String accountNo) {
        SavingsAccountRow account = owned(userId, accountNo);
        // 상세행이 없어도 계좌는 존재한다. 404로 감추면 535 목록에는 보이는 계좌가 상세에서 사라진다.
        return new SavingsDetailResponse(account.accountId(), account.accountName(),
                account.accountNoMasked(), account.bankCode(), account.status(), account.balance(),
                account.detailsAvailable(), account.interestRate(), account.monthlyAmount(),
                account.paymentDay(), account.nextPaymentDate(), account.openingDate(),
                account.expiryDate(), account.financialProductId(), clock.today());
    }

    public PageResponse<SavingsPaymentResponse> payments(
            Long userId, String accountNo, LocalDate start, LocalDate end, int page, int size
    ) {
        SavingsAccountRow account = owned(userId, accountNo);
        // 컨트롤러가 이미 같은 범위를 검증한다. 서비스를 직접 부르는 경로에서도 계약을 지키기 위한 이중 확인이다.
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidRequestException("page는 0 이상, size는 1~100이어야 합니다.");
        }
        QueryPeriod period = historyPeriods.resolve(start, end, clock.today());
        return PageResponse.from(repository.findPayments(userId, account.accountId(),
                period.startDate(), period.endDate(), PageRequest.of(page, size)), row ->
                new SavingsPaymentResponse(row.paymentId(), row.paymentDate(), row.amount(),
                        row.installmentNumber(), row.status()));
    }

    public SavingsPaymentSchedulesResponse paymentSchedules(Long userId, LocalDate start, LocalDate end) {
        LocalDate today = clock.today();
        // 공개 정책의 날짜 기본값은 주입 Clock으로 구체화한다. 정책의 기간 검증을 그대로 재사용한다.
        if (start == null && end == null) {
            start = today;
            end = today.plusMonths(PaymentSchedulePeriodPolicy.DEFAULT_PERIOD_MONTHS).minusDays(1);
        }
        QueryPeriod period = schedulePeriods.resolve(start, end);
        return schedules.calculate(repository.findAllOwned(userId), repository.findPayments(userId),
                period.startDate(), period.endDate(), today);
    }

    public SavingsMonthlyAmountResponse monthlyAmount(Long userId, String value) {
        return monthlyAmount(userId, value, clock.today());
    }

    SavingsMonthlyAmountResponse monthlyAmount(Long userId, String value, LocalDate today) {
        YearMonth month = resolveMonth(value, today);
        LocalDate through = month.isBefore(YearMonth.from(today)) ? month.atEndOfMonth() : today;
        List<SavingsAccountRow> accounts = repository.findAllOwned(userId);
        List<SavingsPaymentRow> payments = repository.findPayments(userId);
        List<SavingsPaymentRow> paid = payments.stream().filter(row -> "PAID".equals(row.status()))
                .filter(row -> YearMonth.from(row.paymentDate()).equals(month) && !row.paymentDate().isAfter(through))
                .toList();
        Long paidAmount = SavingsAmounts.sum(paid, SavingsPaymentRow::amount);
        boolean historical = month.isBefore(YearMonth.from(today));
        SavingsPaymentSchedulesResponse schedule =
                schedules.calculate(accounts, payments, month.atDay(1), month.atEndOfMonth(), today);
        Long stored = historical ? 0L : SavingsAmounts.sum(schedule.schedules().stream()
                .filter(row -> "STORED_SCHEDULED".equals(row.source())).toList(), SavingsScheduleItemResponse::amount);
        Long estimated = historical ? 0L : SavingsAmounts.sum(schedule.schedules().stream()
                .filter(row -> "ESTIMATED_MONTHLY".equals(row.source())).toList(), SavingsScheduleItemResponse::amount);
        TreeSet<String> reasons = new TreeSet<>();
        if (!historical) {
            reasons.addAll(schedule.reasons());
            if (payments.stream().anyMatch(row -> "PAID".equals(row.status())
                    && row.paymentDate().isAfter(today) && YearMonth.from(row.paymentDate()).equals(month))) {
                reasons.add("FUTURE_PAID_PAYMENT");
            }
        }
        Long expected = null;
        if (paidAmount != null && stored != null && estimated != null) {
            expected = SavingsAmounts.amount(BigDecimal.valueOf(paidAmount)
                    .add(BigDecimal.valueOf(stored)).add(BigDecimal.valueOf(estimated)));
        }
        if (expected == null) {
            reasons.add("AMOUNT_OUT_OF_RANGE");
        }
        return new SavingsMonthlyAmountResponse(month, today, through, paidAmount, stored, estimated,
                reasons.isEmpty() ? expected : null, reasons.isEmpty(), List.copyOf(reasons),
                historical ? "STORED_PAID_ONLY" : "PAID_PLUS_REMAINING_MONTHLY_SCHEDULE");
    }

    YearMonth resolveMonth(String value, LocalDate today) {
        YearMonth month = months.resolveBillingMonth(value == null ? YearMonth.from(today).toString() : value);
        if (month.isAfter(YearMonth.from(today).plusMonths(11))) {
            throw new InvalidRequestException("조회 연월은 현재 월부터 12개월 이내여야 합니다.");
        }
        return month;
    }

    public SavingsForecastResponse forecast(Long userId, String accountNo, boolean early) {
        SavingsAccountRow account = owned(userId, accountNo);
        return forecasts.calculate(account, repository.findPayments(userId), clock.today(), early);
    }
}

