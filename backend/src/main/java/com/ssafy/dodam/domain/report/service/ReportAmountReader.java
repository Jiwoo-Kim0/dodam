package com.ssafy.dodam.domain.report.service;

import com.ssafy.dodam.domain.report.dto.MonthlyReportResponse.MonthAmounts;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingAnalysisReader;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * 리포트 합계의 단일 정의. 데일리와 월간이 같은 코드를 쓰므로 "어제 소비"와 "그 달 소비"가 어긋날 수 없다.
 *
 * <ul>
 *   <li>{@code totalSpending} — 506 소비 정의(EXPENSE에서 {@code NON_SPENDING}·숨긴 거래 제외)</li>
 *   <li>{@code totalSaving} — 저축성 지출({@code NON_SPENDING} EXPENSE)</li>
 *   <li>{@code totalExpense} — 둘의 합. 즉 숨기지 않은 EXPENSE 전부다</li>
 * </ul>
 *
 * <p>세 값을 모두 내려 주는 이유: 화면마다 필요한 "지출"이 다르다. 현금흐름은 저축성 지출도 나간 돈이지만
 * 소비 분석은 저축을 소비로 세지 않는다. 한쪽만 주면 FE가 나머지를 추정하게 된다.
 */
@Component
public class ReportAmountReader {

    private final JdbcSpendingAnalysisReader amounts;

    public ReportAmountReader(JdbcSpendingAnalysisReader amounts) {
        this.amounts = amounts;
    }

    public MonthAmounts read(Long userId, LocalDate startDate, LocalDate endDate) {
        long income = amounts.readIncomeTotal(userId, startDate, endDate);
        long spending = amounts.readSpendingTotal(userId, startDate, endDate);
        long saving = amounts.readSavingTransferTotal(userId, startDate, endDate);
        return new MonthAmounts(income, spending + saving, spending, saving);
    }
}
