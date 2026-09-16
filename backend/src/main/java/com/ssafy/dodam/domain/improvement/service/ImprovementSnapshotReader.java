package com.ssafy.dodam.domain.improvement.service;

import static com.ssafy.dodam.domain.loan.repository.LoanSpecifications.ownedBy;

import com.ssafy.dodam.domain.goal.service.GoalCalculationService;
import com.ssafy.dodam.domain.improvement.dto.ImprovementSnapshot;
import com.ssafy.dodam.domain.improvement.service.ImprovementMeasurementPolicy.Window;
import com.ssafy.dodam.domain.loan.entity.Loan;
import com.ssafy.dodam.domain.loan.repository.LoanRepository;
import com.ssafy.dodam.domain.transaction.repository.JdbcSpendingAnalysisReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개선 행동의 재무 관측치를 만든다. 실행 전(수락 시점)과 실행 후(측정 시점)가 <b>같은 코드</b>로 계산되어야
 * 비교가 성립하므로 한 곳에 둔다.
 *
 * <ul>
 *   <li>소비·저축은 기간 합을 개월 수로 나눈 <b>월평균</b>이다. 정의는 506 소비 집계를 그대로 쓴다
 *       ({@link JdbcSpendingAnalysisReader}) — 리포트·소비 화면과 같은 숫자가 나와야 한다.</li>
 *   <li>부채는 <b>잔액</b>이라 기간 개념이 없다. 스냅샷을 만드는 시점의 상환 중(ACTIVE·OVERDUE) 대출 합이다.</li>
 *   <li>월 이자는 잔액 x 연이율 / 1200을 원 단위로 반올림한 값의 합이다(상환일정 계산기와 같은 월이율 정의).</li>
 *   <li>목표 예상 달성일은 목표 도메인의 계산을 그대로 부른다. 여기서 다시 만들지 않는다.</li>
 * </ul>
 *
 * <p>값을 만들지 못하면 null로 두고 사유 코드를 {@code unavailable}에 남긴다. 0으로 채우면 "부채가 없다"와
 * "부채를 못 읽었다"가 같아진다.
 */
@Component
@Transactional(readOnly = true)
public class ImprovementSnapshotReader {

    static final String LOAN_RATE_UNAVAILABLE = "LOAN_RATE_UNAVAILABLE";
    static final String GOAL_FORECAST_UNAVAILABLE = "GOAL_FORECAST_UNAVAILABLE";

    private static final BigDecimal MONTHLY_PERCENT_DIVISOR = BigDecimal.valueOf(1200);

    private final JdbcSpendingAnalysisReader spending;
    private final LoanRepository loans;
    private final GoalCalculationService goals;

    public ImprovementSnapshotReader(JdbcSpendingAnalysisReader spending, LoanRepository loans,
            GoalCalculationService goals) {
        this.spending = spending;
        this.loans = loans;
        this.goals = goals;
    }

    /**
     * @param window 소비·저축 월평균을 낼 집계 구간
     * @param goalId 연결된 모으기 목표. null이면 목표 항목은 비운다
     * @param now    스냅샷 시각. 부채 잔액·목표 예상일의 기준이기도 하다
     */
    public ImprovementSnapshot read(Long userId, Window window, Long goalId, LocalDateTime now) {
        List<String> unavailable = new ArrayList<>();
        long spendingTotal = spending.readSpendingTotal(userId, window.startDate(), window.endDate());
        long savingTotal = spending.readSavingTransferTotal(userId, window.startDate(), window.endDate());
        int months = Math.max(1, window.months());

        long debtBalance = 0L;
        long monthlyInterest = 0L;
        for (Loan loan : loans.findAll(ownedBy(userId))) {
            if (!loan.getStatus().isOutstanding() || loan.getBalance() == null || loan.getBalance() <= 0) {
                continue;
            }
            debtBalance += loan.getBalance();
            if (loan.getInterestRate() == null || loan.getInterestRate().signum() < 0) {
                unavailable.add(LOAN_RATE_UNAVAILABLE);
                continue;
            }
            monthlyInterest += BigDecimal.valueOf(loan.getBalance())
                    .multiply(loan.getInterestRate())
                    .divide(MONTHLY_PERCENT_DIVISOR, 0, RoundingMode.HALF_UP)
                    .longValue();
        }

        return new ImprovementSnapshot(now, window.startDate(), window.endDate(), months,
                spendingTotal / months, savingTotal / months, debtBalance,
                unavailable.contains(LOAN_RATE_UNAVAILABLE) ? null : monthlyInterest,
                goalId, forecastDate(userId, goalId, now, unavailable), unavailable);
    }

    private java.time.LocalDate forecastDate(Long userId, Long goalId, LocalDateTime now, List<String> unavailable) {
        if (goalId == null) {
            return null;
        }
        try {
            var forecast = goals.getForecast(userId, goalId, now.toLocalDate());
            if (forecast.expectedAchievementDate() == null) {
                unavailable.add(GOAL_FORECAST_UNAVAILABLE);
            }
            return forecast.expectedAchievementDate();
        } catch (RuntimeException failure) {
            // 목표가 삭제됐거나 줄이기 목표라 예상 달성일 개념이 없다. 스냅샷 전체를 실패시키지 않는다.
            unavailable.add(GOAL_FORECAST_UNAVAILABLE);
            return null;
        }
    }
}
