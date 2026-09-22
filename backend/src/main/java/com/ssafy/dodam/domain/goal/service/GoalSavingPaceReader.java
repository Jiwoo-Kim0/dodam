package com.ssafy.dodam.domain.goal.service;

import com.ssafy.dodam.domain.account.entity.AccountType;
import com.ssafy.dodam.domain.goal.repository.JdbcGoalPaceReader;
import com.ssafy.dodam.domain.goal.repository.JdbcGoalPaceReader.DepositSavingTerms;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 모으기 목표의 현재 저축속도(월 저축액) 단일 정의(S15P21D105-694). Goal API 510의 {@code averageMonthlySaving}, 511 이력의
 * {@code forecast_date}, 시뮬레이션 기준선({@code SimulationBaseline.SavingGoal.averageMonthlySaving})이 모두 여기를 쓴다.
 *
 * <p><b>예전 정의 {@code 확보 금액 / 목표 생성 후 경과 개월(최소 1)}을 버린 이유.</b> 잔액이 이미 있는 계좌를 새 목표에 연결하면
 * 그 잔액 전체가 "한 달에 모은 돈"이 되어 예상 달성일이 다음 달로 당겨졌다(1,746만 원 계좌를 연결한 3,000만 원 목표가
 * "373일 빨라요"). 목표를 언제 만들었는지는 돈이 모이는 속도와 무관하다.
 *
 * <p><b>새 정의: 연결 계좌의 최근 순증가.</b> 기준일이 든 달을 뺀 직전 {@value #WINDOW_MONTHS}개 완료 월을 창으로 잡고,
 * <ul>
 *   <li>예·적금(전액 연결): 창 안의 PAID 납입 합 / 납입 이력이 있는 개월 수. 창 안에 납입이 없고 창 이후에 가입한 계좌는
 *       약정 월 납입액(만기 전)으로 본다. 예금·만기 적금은 0.</li>
 *   <li>입출금(전액이든 담기든): 사용자 전체의 순현금흐름(수입 − 지출·이체) / 거래 이력이 있는 개월 수. 계좌별 잔액 이력이 없어
 *       계좌 단위로 재구성할 수 없고({@link JdbcGoalPaceReader}), 담은 금액은 사용자 여윳돈에서 옮겨지므로 여윳돈이 곧 속도다.
 *       음수면 예·적금 납입을 상쇄한다(적금은 붓지만 입출금이 줄고 있는 상태). 입출금 하나를 여러 목표에 담으면 같은 여윳돈이
 *       각 목표 속도에 들어간다 — 목표별 예상일은 "그 목표에만 여윳돈을 쓰면"의 뜻이다.</li>
 * </ul>
 * 합이 음수면 0(현재 속도로는 달성 불가). 창을 완료 월로만 잡는 이유는 급여일 전의 이번 달을 넣으면 수입 없이 지출만 잡혀
 * 속도가 매달 초에 떨어졌다 올라오기 때문이다.
 */
@Component
public class GoalSavingPaceReader {

    public static final int WINDOW_MONTHS = 3;

    private final JdbcGoalPaceReader reader;

    public GoalSavingPaceReader(JdbcGoalPaceReader reader) {
        this.reader = reader;
    }

    /**
     * @param allocation 목표의 연결 계좌(626 {@link GoalAllocationReader#readGoal}). null이거나 비어 있으면 0
     * @param today      기준일. 이 날이 든 달은 창에서 뺀다
     */
    public long monthlyPace(Long userId, GoalAllocationReader.GoalAllocation allocation, LocalDate today) {
        if (allocation == null || allocation.accounts().isEmpty()) {
            return 0L;
        }
        YearMonth last = YearMonth.from(today).minusMonths(1);
        YearMonth first = last.minusMonths(WINDOW_MONTHS - 1L);
        LocalDate from = first.atDay(1);
        LocalDate to = last.atEndOfMonth();

        List<Long> exclusive = allocation.accounts().stream()
                .filter(item -> GoalAllocationReader.isExclusiveType(item.account().getAccountType()))
                .map(item -> item.account().getId())
                .toList();
        boolean hasDemandDeposit = allocation.accounts().stream()
                .anyMatch(item -> item.account().getAccountType() == AccountType.DEMAND_DEPOSIT);

        long pace = 0L;
        if (!exclusive.isEmpty()) {
            Map<Long, Long> paid = reader.readPaidPaymentTotals(exclusive, from, to);
            Map<Long, DepositSavingTerms> terms = reader.readTerms(exclusive);
            for (Long accountId : exclusive) {
                pace += depositPace(paid.get(accountId), terms.get(accountId), first, last, today);
            }
        }
        if (hasDemandDeposit) {
            int months = monthsCovered(first, last, reader.readFirstTransactionDate(userId).orElse(null));
            if (months > 0) {
                pace += reader.readNetCashflow(userId, from, to) / months;
            }
        }
        return Math.max(0L, pace);
    }

    private static long depositPace(Long paidInWindow, DepositSavingTerms terms, YearMonth first, YearMonth last, LocalDate today) {
        LocalDate opening = terms == null ? null : terms.openingDate();
        if (paidInWindow != null && paidInWindow > 0) {
            return paidInWindow / Math.max(1, monthsCovered(first, last, opening));
        }
        // 창 이후에 가입해 아직 납입이 한 번도 없는 적금: 약정액으로 본다. 창 안에 가입했는데 납입이 없으면 실제로 0이다
        boolean openedAfterWindow = opening != null && YearMonth.from(opening).isAfter(last);
        boolean beforeExpiry = terms != null && (terms.expiryDate() == null || !terms.expiryDate().isBefore(today));
        if (openedAfterWindow && beforeExpiry && terms.monthlyAmount() != null) {
            return Math.max(0L, terms.monthlyAmount());
        }
        return 0L;
    }

    /** 창 [first, last] 중 {@code sourceStart}가 든 달부터 세어 몇 개월이 이력에 덮이는지. 시작을 모르면 창 전체. */
    static int monthsCovered(YearMonth first, YearMonth last, LocalDate sourceStart) {
        if (sourceStart == null) {
            return WINDOW_MONTHS;
        }
        YearMonth start = YearMonth.from(sourceStart);
        if (start.isAfter(last)) {
            return 0;
        }
        YearMonth effective = start.isAfter(first) ? start : first;
        return (int) ChronoUnit.MONTHS.between(effective, last) + 1;
    }
}
