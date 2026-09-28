package com.ssafy.dodam.domain.report.service;

import com.ssafy.dodam.domain.report.dto.DailyReportResponse.CategoryAmount;
import com.ssafy.dodam.domain.report.dto.DailyReportResponse.Financial;
import com.ssafy.dodam.domain.report.dto.DailyReportResponse.NotableEvent;
import com.ssafy.dodam.domain.report.dto.MonthlyReportResponse.MonthAmounts;
import com.ssafy.dodam.domain.report.entity.ReportDataStatus;
import com.ssafy.dodam.domain.report.repository.JdbcReportReader;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingReader;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 데일리 브리핑 본문을 만든다. 기준일은 이미 끝난 하루이므로 집계는 그 하루에 대해 고정된다.
 *
 * <p>{@code notableEvents}는 <b>그날 실제로 관측된 사실</b>만 담는다. "어제 소비가 목표 달성일을 며칠 바꿨다"
 * 같은 인과 문장은 근거 계산이 따로 필요해 여기서 만들지 않는다(docs/report-backend-handoff.md 데일리 브리핑 항목).
 * 각 사실은 SQL 한 줄로 되짚을 수 있다.
 */
@Component
@Transactional(readOnly = true)
public class DailyReportGenerator {

    /** 상위 카테고리 노출 개수. 화면 카드가 담는 줄 수다. */
    static final int TOP_CATEGORY_LIMIT = 5;
    /** 큰 지출 판정의 비교 구간. 기준일 직전 30일의 일평균 소비와 견준다. */
    static final int LARGE_EXPENSE_BASELINE_DAYS = 30;
    static final String UNCATEGORIZED_CODE = "UNCATEGORIZED";
    static final String UNCATEGORIZED_NAME = "미분류";

    private final ReportAmountReader amounts;
    private final JdbcSpendingReader spending;
    private final JdbcReportReader reports;

    public DailyReportGenerator(ReportAmountReader amounts, JdbcSpendingReader spending, JdbcReportReader reports) {
        this.amounts = amounts;
        this.spending = spending;
        this.reports = reports;
    }

    public Content generate(Long userId, LocalDate date) {
        MonthAmounts totals = amounts.read(userId, date, date);
        long transactionCount = reports.countTransactions(userId, date, date);
        List<CategoryAmount> topCategories = spending.readByCategory(userId, date, date).stream()
                .limit(TOP_CATEGORY_LIMIT)
                .map(row -> new CategoryAmount(row.code() == null ? UNCATEGORIZED_CODE : row.code(),
                        row.name() == null ? UNCATEGORIZED_NAME : row.name(), row.total(), row.count()))
                .toList();
        long balanceChange = totals.totalIncome() - totals.totalExpense();
        Financial financial = new Financial(totals.totalIncome(), totals.totalExpense(), totals.totalSpending(),
                totals.totalSaving(), transactionCount, balanceChange, topCategories);
        ReportDataStatus status = transactionCount == 0 ? ReportDataStatus.NO_TRANSACTIONS : ReportDataStatus.READY;
        return new Content(status, summary(date, totals, transactionCount), financial,
                events(userId, date, totals, transactionCount));
    }

    /** 저장용 한 줄 요약. 관측한 수치만 옮긴 문장이며 해석을 붙이지 않는다. */
    private static String summary(LocalDate date, MonthAmounts totals, long transactionCount) {
        if (transactionCount == 0) {
            return "%s 기록된 거래가 없습니다.".formatted(date);
        }
        return "%s 소비 %,d원 · 수입 %,d원 · 거래 %d건".formatted(date, totals.totalSpending(),
                totals.totalIncome(), transactionCount);
    }

    private List<NotableEvent> events(Long userId, LocalDate date, MonthAmounts totals, long transactionCount) {
        List<NotableEvent> events = new ArrayList<>();
        if (transactionCount == 0) {
            return events;
        }
        if (totals.totalSpending() == 0) {
            events.add(new NotableEvent("NO_SPENDING", "소비가 없는 하루였습니다.", 0L));
        }
        if (totals.totalIncome() > 0) {
            events.add(new NotableEvent("INCOME_RECEIVED",
                    "수입 %,d원이 들어왔습니다.".formatted(totals.totalIncome()), totals.totalIncome()));
        }
        addLargeExpense(userId, date, events);
        addSubscriptionCharges(userId, date, events);
        return events;
    }

    /** 직전 30일 일평균 소비를 넘는 단일 지출만 "큰 지출"로 본다. 비교 대상이 없으면(일평균 0) 판정하지 않는다. */
    private void addLargeExpense(Long userId, LocalDate date, List<NotableEvent> events) {
        var largest = reports.findLargestExpense(userId, date);
        if (largest.isEmpty()) {
            return;
        }
        LocalDate baselineStart = date.minusDays(LARGE_EXPENSE_BASELINE_DAYS);
        long baselineTotal = amounts.read(userId, baselineStart, date.minusDays(1)).totalSpending();
        long dailyAverage = baselineTotal / LARGE_EXPENSE_BASELINE_DAYS;
        if (dailyAverage <= 0 || largest.get().amount() <= dailyAverage) {
            return;
        }
        String where = largest.get().merchantName() == null ? "" : largest.get().merchantName() + "에서 ";
        events.add(new NotableEvent("LARGE_EXPENSE",
                "%s%,d원을 썼습니다. 최근 %d일 일평균 소비 %,d원보다 많습니다."
                        .formatted(where, largest.get().amount(), LARGE_EXPENSE_BASELINE_DAYS, dailyAverage),
                largest.get().amount()));
    }

    private void addSubscriptionCharges(Long userId, LocalDate date, List<NotableEvent> events) {
        var charges = reports.findSubscriptionCharges(userId, date);
        if (charges.isEmpty()) {
            return;
        }
        long total = charges.stream().mapToLong(JdbcReportReader.SubscriptionCharge::amount).sum();
        events.add(new NotableEvent("SUBSCRIPTION_CHARGED",
                "정기결제 %d건이 결제되었습니다.".formatted(charges.size()), total));
    }

    public record Content(ReportDataStatus dataStatus, String summary, Financial financial,
            List<NotableEvent> notableEvents) {
    }
}
