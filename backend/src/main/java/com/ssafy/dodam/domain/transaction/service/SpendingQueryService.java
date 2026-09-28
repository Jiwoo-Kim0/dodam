package com.ssafy.dodam.domain.transaction.service;

import com.ssafy.dodam.domain.transaction.dto.response.CategorySpendingResponse;
import com.ssafy.dodam.domain.transaction.dto.response.CategorySpendingTrendResponse;
import com.ssafy.dodam.domain.transaction.dto.response.DailySpendingResponse;
import com.ssafy.dodam.domain.transaction.dto.response.PaymentMethodSpendingResponse;
import com.ssafy.dodam.domain.transaction.dto.response.SpendingMonthlySummaryResponse;
import com.ssafy.dodam.domain.transaction.dto.response.SpendingProgressResponse;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingReader;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingReader.CategorySpendingRow;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingReader.DailySpendingRow;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingReader.MonthlySpendingRow;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingReader.PaymentMethodSpendingRow;
import com.ssafy.dodam.domain.transaction.repository.TransactionCategoryRepository;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.policy.QueryPeriod;
import com.ssafy.dodam.global.policy.QueryPeriodPolicy;
import com.ssafy.dodam.global.policy.YearMonthPeriod;
import com.ssafy.dodam.global.policy.YearMonthPeriodPolicy;
import com.ssafy.dodam.global.policy.YearMonthPolicy;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API 6 소비 기간·카테고리 집계(S15P21D105-506). "소비"의 정의는 {@link JdbcSpendingReader}에 있다
 * (EXPENSE, NON_SPENDING 제외, 숨김 제외, 원장 전체).
 *
 * <p>월 파라미터는 카드 월 사용액과 같은 {@link YearMonthPolicy}, 월 기간은 {@link YearMonthPeriodPolicy},
 * 일 기간은 {@link QueryPeriodPolicy}를 그대로 쓴다. 기준일은 요청마다 {@link TransactionClock}에서 한 번 읽는다.
 */
@Service
@Transactional(readOnly = true)
public class SpendingQueryService {

    public static final String UNCATEGORIZED_CODE = "UNCATEGORIZED";
    public static final String UNCATEGORIZED_NAME = "미분류";
    public static final String UNKNOWN_PAYMENT_METHOD = "UNKNOWN";

    private final JdbcSpendingReader spendingReader;
    private final TransactionCategoryRepository transactionCategoryRepository;
    private final YearMonthPolicy yearMonthPolicy;
    private final YearMonthPeriodPolicy yearMonthPeriodPolicy;
    private final QueryPeriodPolicy queryPeriodPolicy;
    private final TransactionClock transactionClock;

    public SpendingQueryService(
            JdbcSpendingReader spendingReader,
            TransactionCategoryRepository transactionCategoryRepository,
            YearMonthPolicy yearMonthPolicy,
            YearMonthPeriodPolicy yearMonthPeriodPolicy,
            QueryPeriodPolicy queryPeriodPolicy,
            TransactionClock transactionClock
    ) {
        this.spendingReader = spendingReader;
        this.transactionCategoryRepository = transactionCategoryRepository;
        this.yearMonthPolicy = yearMonthPolicy;
        this.yearMonthPeriodPolicy = yearMonthPeriodPolicy;
        this.queryPeriodPolicy = queryPeriodPolicy;
        this.transactionClock = transactionClock;
    }

    // ---------- 월 소비 요약 ----------

    public SpendingMonthlySummaryResponse getMonthlySummary(Long userId, String yearMonth) {
        LocalDate today = transactionClock.now().toLocalDate();
        YearMonth month = yearMonthPolicy.resolveHistorical(yearMonth, today);
        YearMonth previous = month.minusMonths(1);

        Map<YearMonth, MonthlySpendingRow> rows = spendingReader
                .readMonthly(userId, previous.atDay(1), month.atEndOfMonth())
                .stream()
                .collect(Collectors.toMap(MonthlySpendingRow::yearMonth, Function.identity()));
        MonthlySpendingRow current = rows.getOrDefault(month, new MonthlySpendingRow(month, 0, 0));
        long previousTotal = rows.containsKey(previous) ? rows.get(previous).total() : 0;

        int daysElapsed = daysElapsed(month, today);
        return new SpendingMonthlySummaryResponse(
                month.toString(),
                current.total(),
                current.count(),
                daysElapsed,
                month.lengthOfMonth(),
                divide(current.total(), daysElapsed),
                divide(current.total(), current.count()),
                previousTotal,
                current.total() - previousTotal,
                changeRatePercent(current.total(), previousTotal)
        );
    }

    // ---------- 월 누적 소비 ----------

    public SpendingProgressResponse getProgress(Long userId, String yearMonth) {
        LocalDate today = transactionClock.now().toLocalDate();
        YearMonth month = yearMonthPolicy.resolveHistorical(yearMonth, today);
        LocalDate asOf = month.equals(YearMonth.from(today)) ? today : month.atEndOfMonth();

        Map<LocalDate, DailySpendingRow> rows = spendingReader
                .readDaily(userId, month.atDay(1), asOf)
                .stream()
                .collect(Collectors.toMap(DailySpendingRow::date, Function.identity()));

        List<SpendingProgressResponse.Day> days = new ArrayList<>();
        long cumulative = 0;
        for (LocalDate date = month.atDay(1); !date.isAfter(asOf); date = date.plusDays(1)) {
            DailySpendingRow row = rows.get(date);
            long daily = row == null ? 0 : row.total();
            cumulative += daily;
            days.add(new SpendingProgressResponse.Day(date, daily, row == null ? 0 : row.count(), cumulative));
        }

        int daysElapsed = days.size();
        // 반올림한 일평균에 일수를 곱하면 지난 달 합계와 어긋나므로, 총액 × 그 달 일수 / 경과 일수로 한 번에 계산한다.
        long projected = daysElapsed == 0
                ? 0
                : BigDecimal.valueOf(cumulative)
                        .multiply(BigDecimal.valueOf(month.lengthOfMonth()))
                        .divide(BigDecimal.valueOf(daysElapsed), 0, RoundingMode.HALF_UP)
                        .longValue();
        return new SpendingProgressResponse(
                month.toString(),
                asOf,
                daysElapsed,
                month.lengthOfMonth(),
                cumulative,
                divide(cumulative, daysElapsed),
                projected,
                days
        );
    }

    // ---------- 일별 소비 ----------

    public DailySpendingResponse getDaily(Long userId, LocalDate startDate, LocalDate endDate) {
        QueryPeriod period = queryPeriodPolicy.resolve(startDate, endDate, transactionClock.now().toLocalDate());
        Map<LocalDate, DailySpendingRow> rows = spendingReader
                .readDaily(userId, period.startDate(), period.endDate())
                .stream()
                .collect(Collectors.toMap(DailySpendingRow::date, Function.identity()));

        List<DailySpendingResponse.Day> days = new ArrayList<>();
        for (LocalDate date = period.startDate(); !date.isAfter(period.endDate()); date = date.plusDays(1)) {
            DailySpendingRow row = rows.get(date);
            days.add(new DailySpendingResponse.Day(date, row == null ? 0 : row.total(), row == null ? 0 : row.count()));
        }
        long total = days.stream().mapToLong(DailySpendingResponse.Day::totalSpending).sum();
        long count = days.stream().mapToLong(DailySpendingResponse.Day::transactionCount).sum();
        return new DailySpendingResponse(period.startDate(), period.endDate(), total, count, divide(total, days.size()), days);
    }

    // ---------- 카테고리별 소비 ----------

    public CategorySpendingResponse getByCategory(Long userId, String yearMonth) {
        YearMonth month = yearMonthPolicy.resolveHistorical(yearMonth, transactionClock.now().toLocalDate());
        List<CategorySpendingRow> rows = spendingReader.readByCategory(userId, month.atDay(1), month.atEndOfMonth());
        long total = rows.stream().mapToLong(CategorySpendingRow::total).sum();
        long count = rows.stream().mapToLong(CategorySpendingRow::count).sum();

        List<CategorySpendingResponse.Item> items = rows.stream()
                .map(row -> new CategorySpendingResponse.Item(
                        row.categoryId(),
                        codeOf(row),
                        nameOf(row),
                        row.total(),
                        row.count(),
                        sharePercent(row.total(), total)
                ))
                .toList();
        return new CategorySpendingResponse(month.toString(), total, count, items);
    }

    // ---------- 카테고리 소비 추이 ----------

    /**
     * @param category 카테고리 코드(대소문자 무시). 주면 그 카테고리만, 없는 코드는 400. `UNCATEGORIZED`는 미분류만
     */
    public CategorySpendingTrendResponse getCategoryTrend(Long userId, String startMonth, String endMonth, String category) {
        YearMonthPeriod period = yearMonthPeriodPolicy.resolve(startMonth, endMonth);
        String categoryCode = resolveCategoryFilter(category);

        Map<YearMonth, List<CategorySpendingRow>> byMonth = spendingReader
                .readByMonthAndCategory(userId, period.startMonth().atDay(1), period.endMonth().atEndOfMonth())
                .stream()
                .filter(row -> categoryCode == null || categoryCode.equals(codeOf(row)))
                .collect(Collectors.groupingBy(CategorySpendingRow::yearMonth));

        List<CategorySpendingTrendResponse.Month> months = new ArrayList<>();
        for (YearMonth month = period.startMonth(); !month.isAfter(period.endMonth()); month = month.plusMonths(1)) {
            List<CategorySpendingRow> rows = byMonth.getOrDefault(month, List.of());
            months.add(new CategorySpendingTrendResponse.Month(
                    month.toString(),
                    rows.stream().mapToLong(CategorySpendingRow::total).sum(),
                    rows.stream().mapToLong(CategorySpendingRow::count).sum(),
                    rows.stream()
                            .map(row -> new CategorySpendingTrendResponse.Category(
                                    row.categoryId(), codeOf(row), nameOf(row), row.total(), row.count()))
                            .toList()
            ));
        }
        long total = months.stream().mapToLong(CategorySpendingTrendResponse.Month::totalSpending).sum();
        return new CategorySpendingTrendResponse(
                period.startMonth().toString(),
                period.endMonth().toString(),
                categoryCode,
                total,
                divide(total, months.size()),
                months
        );
    }

    // ---------- 결제수단별 소비 ----------

    public PaymentMethodSpendingResponse getByPaymentMethod(Long userId, String yearMonth) {
        YearMonth month = yearMonthPolicy.resolveHistorical(yearMonth, transactionClock.now().toLocalDate());
        List<PaymentMethodSpendingRow> rows = spendingReader.readByPaymentMethod(userId, month.atDay(1), month.atEndOfMonth());
        long total = rows.stream().mapToLong(PaymentMethodSpendingRow::total).sum();
        long count = rows.stream().mapToLong(PaymentMethodSpendingRow::count).sum();

        List<PaymentMethodSpendingResponse.Item> items = rows.stream()
                .map(row -> new PaymentMethodSpendingResponse.Item(
                        row.paymentMethod() == null || row.paymentMethod().isBlank() ? UNKNOWN_PAYMENT_METHOD : row.paymentMethod(),
                        row.total(),
                        row.count(),
                        sharePercent(row.total(), total)
                ))
                .toList();
        return new PaymentMethodSpendingResponse(month.toString(), total, count, items);
    }

    // ---------- helpers ----------

    private String resolveCategoryFilter(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        String code = category.strip().toUpperCase(Locale.ROOT);
        if (UNCATEGORIZED_CODE.equals(code)) {
            return code;
        }
        return transactionCategoryRepository.findByCode(code)
                .orElseThrow(() -> new InvalidRequestException(TransactionQueryService.CATEGORY_CODE_NOT_FOUND_MESSAGE))
                .getCode();
    }

    private static String codeOf(CategorySpendingRow row) {
        return row.categoryId() == null ? UNCATEGORIZED_CODE : row.code();
    }

    private static String nameOf(CategorySpendingRow row) {
        return row.categoryId() == null ? UNCATEGORIZED_NAME : row.name();
    }

    /**
     * 이번 달이면 오늘까지, 지난 달이면 그 달 전체 일수.
     */
    private static int daysElapsed(YearMonth month, LocalDate today) {
        return month.equals(YearMonth.from(today)) ? today.getDayOfMonth() : month.lengthOfMonth();
    }

    private static long divide(long amount, long divisor) {
        if (divisor <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(amount).divide(BigDecimal.valueOf(divisor), 0, RoundingMode.HALF_UP).longValue();
    }

    private static Double changeRatePercent(long current, long previous) {
        if (previous <= 0) {
            return null;
        }
        return BigDecimal.valueOf(current - previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(previous), 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private static double sharePercent(long amount, long total) {
        if (total <= 0 || amount <= 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(amount)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)
                .doubleValue();
    }
}
