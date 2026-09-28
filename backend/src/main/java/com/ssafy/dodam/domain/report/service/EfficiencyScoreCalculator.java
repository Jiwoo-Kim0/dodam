package com.ssafy.dodam.domain.report.service;

import com.ssafy.dodam.domain.loan.repository.LoanRepaymentReader;
import com.ssafy.dodam.domain.report.dto.EfficiencyScoreResponse;
import com.ssafy.dodam.domain.report.dto.EfficiencyScoreResponse.Dimension;
import com.ssafy.dodam.domain.report.dto.MonthlyReportResponse.MonthAmounts;
import com.ssafy.dodam.domain.transaction.entity.SpendingType;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingAnalysisReader;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingReader;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 금융 효율 점수 입력을 모아 {@link EfficiencyScorePolicy}로 점수를 만든다(S15P21D105-635).
 *
 * <p>입력 출처는 전부 리포트가 이미 쓰는 저장값이다. 소비·저축·소득은 {@code ReportAmountReader}의 합계(리포트 합계와 같은
 * 숫자), 고정지출은 소비패턴의 {@code bySpendingType.FIXED}, 신용은 {@code user.credit_score}(680)를 우선하고 없으면
 * {@code user.credit_rating}(629)을 호환 입력으로 쓴다. 둘 다 현재 값이라 과거 달 리포트에도 조회 시점 값이 들어간다.
 * 부채 상환은 그 달 {@code loan_repayment}의 PAID 상환액 합계다(712, v4).
 *
 * <p>v4부터 모든 항목이 그 달 원천 데이터만 읽는다. v3까지의 현금흐름·비효율 항목은 사용자가 먼저 실행한 예측·진단이 있어야
 * 계산돼, 실행 전에 처음 연 리포트에는 "계산 불가"로 굳었다.
 */
@Component
@Transactional(readOnly = true)
public class EfficiencyScoreCalculator {

    private final JdbcSpendingAnalysisReader spendingTypes;
    private final UserRepository users;
    private final LoanRepaymentReader loanRepayments;

    public EfficiencyScoreCalculator(JdbcSpendingAnalysisReader spendingTypes, UserRepository users,
            LoanRepaymentReader loanRepayments) {
        this.spendingTypes = spendingTypes;
        this.users = users;
        this.loanRepayments = loanRepayments;
    }

    /**
     * @param totals 그 달 합계(리포트 합계와 같은 값)
     */
    public EfficiencyScoreResponse calculate(Long userId, YearMonth month, MonthAmounts totals) {
        LocalDate start = month.atDay(1);
        LocalDate end = month.atEndOfMonth();

        long fixed = spendingTypes.readMonthlyBySpendingType(userId, SpendingType.FIXED.name(), start, end).stream()
                .mapToLong(JdbcSpendingReader.MonthlySpendingRow::total).sum();
        User user = users.findById(userId).orElse(null);
        Integer creditScore = user == null ? null : user.getCreditScore();
        String creditRating = user == null ? null : user.getCreditRating();
        long paidRepayment = loanRepayments.sumPaidAmount(userId, start, end);

        List<Dimension> dimensions = EfficiencyScorePolicy.ordered(
                EfficiencyScorePolicy.spending(totals.totalSpending(), totals.totalIncome()),
                EfficiencyScorePolicy.saving(totals.totalSaving(), totals.totalIncome()),
                EfficiencyScorePolicy.fixedExpense(fixed, totals.totalSpending()),
                EfficiencyScorePolicy.credit(creditScore, creditRating),
                EfficiencyScorePolicy.debtRepayment(paidRepayment, totals.totalIncome()));
        return EfficiencyScorePolicy.overall(dimensions);
    }
}
