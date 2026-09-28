package com.ssafy.dodam.domain.report.service;

import com.ssafy.dodam.domain.report.dto.MonthlyReportResponse.MonthAmounts;
import com.ssafy.dodam.domain.report.dto.MonthlySpendingPatternResponse;
import com.ssafy.dodam.domain.report.dto.MonthlySpendingPatternResponse.Category;
import com.ssafy.dodam.domain.report.dto.MonthlySpendingPatternResponse.Compared;
import com.ssafy.dodam.domain.report.dto.MonthlySpendingPatternResponse.WeekdayWeekend;
import com.ssafy.dodam.domain.transaction.entity.SpendingType;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingAnalysisReader;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 월간 소비패턴을 만든다. 월간 리포트 생성 시점에 한 번 계산해 저장하고, 조회는 저장본을 읽는다.
 *
 * <p>비율은 0~1 소수 4자리다(패턴 분석 API 3과 같은 규칙). 분모를 섞지 않도록 카테고리 비율의 분모는
 * 응답의 {@code ratioBase}로 명시한다 — 카테고리 집계는 소비만 담으므로 분모도 소비여야 합이 1이 된다.
 */
@Component
@Transactional(readOnly = true)
public class MonthlySpendingPatternAssembler {

    private static final int RATIO_SCALE = 4;

    private final JdbcSpendingReader spending;
    private final JdbcSpendingAnalysisReader analysis;

    public MonthlySpendingPatternAssembler(JdbcSpendingReader spending, JdbcSpendingAnalysisReader analysis) {
        this.spending = spending;
        this.analysis = analysis;
    }

    public MonthlySpendingPatternResponse assemble(Long userId, YearMonth month, MonthAmounts totals,
            MonthAmounts previous) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();
        List<Category> byCategory = spending.readByCategory(userId, start, end).stream()
                .map(row -> new Category(
                        row.code() == null ? DailyReportGenerator.UNCATEGORIZED_CODE : row.code(),
                        row.name() == null ? DailyReportGenerator.UNCATEGORIZED_NAME : row.name(),
                        row.total(), ratio(row.total(), totals.totalSpending()), row.count()))
                .toList();

        // 3개 키를 항상 채운다. FIXED·VARIABLE은 소비의 하위 분류이고 NON_SPENDING은 저축성 지출이라
        // 세 값의 합이 곧 totalExpense다.
        Map<String, Long> bySpendingType = new LinkedHashMap<>();
        bySpendingType.put(SpendingType.FIXED.name(), sumOf(userId, SpendingType.FIXED, start, end));
        bySpendingType.put(SpendingType.VARIABLE.name(), sumOf(userId, SpendingType.VARIABLE, start, end));
        bySpendingType.put(SpendingType.NON_SPENDING.name(), totals.totalSaving());

        long weekday = 0L;
        long weekend = 0L;
        for (var row : spending.readDaily(userId, start, end)) {
            if (isWeekend(row.date())) {
                weekend += row.total();
            } else {
                weekday += row.total();
            }
        }

        long amountDelta = totals.totalSpending() - previous.totalSpending();
        Double ratioDelta = previous.totalSpending() == 0 ? null : ratio(amountDelta, previous.totalSpending());
        return new MonthlySpendingPatternResponse(month.toString(), totals.totalExpense(), totals.totalSpending(),
                MonthlySpendingPatternResponse.RATIO_BASE, byCategory, bySpendingType,
                new WeekdayWeekend(weekday, weekend), new Compared(amountDelta, ratioDelta));
    }

    private long sumOf(Long userId, SpendingType type, LocalDate start, LocalDate end) {
        return analysis.readMonthlyBySpendingType(userId, type.name(), start, end).stream()
                .mapToLong(JdbcSpendingReader.MonthlySpendingRow::total).sum();
    }

    /** 분모가 0이면 0이다. 0으로 나누지 않고, 비율을 만들 수 없는 경우는 호출자가 null로 처리한다. */
    private static double ratio(long amount, long total) {
        if (total == 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(amount)
                .divide(BigDecimal.valueOf(total), RATIO_SCALE, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** 주말 판정. 소비 화면의 평일·주말 구분과 같은 기준이다. */
    private static boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }
}
