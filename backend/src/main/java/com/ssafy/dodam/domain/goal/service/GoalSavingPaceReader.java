package com.ssafy.dodam.domain.goal.service;

import com.ssafy.dodam.domain.account.entity.AccountType;
import com.ssafy.dodam.domain.goal.repository.JdbcGoalPaceReader;
import com.ssafy.dodam.domain.goal.repository.JdbcGoalPaceReader.DepositSavingTerms;
import com.ssafy.dodam.domain.goal.repository.SafeboxAccountRepository;
import com.ssafy.dodam.domain.goal.repository.SafeboxMovementRepository;
import java.math.BigInteger;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
 *   <li>입출금(전액이든 담기든): 목표에 연결된 입출금 계좌의 순현금흐름(수입 − 지출·이체) 중 <b>이 목표 몫</b>(703, {@code shareOf})
 *       / 거래 이력이 있는 개월 수.
 *       그 계좌에서 대금이 빠지는 카드의 승인 거래도 포함한다({@link JdbcGoalPaceReader}). 연결하지 않은 계좌·카드의 거래는
 *       포함하지 않는다. 연결된 입출금 계좌에서 연결된 예·적금으로 이체하면 입출금 출금과 PAID 납입이 상쇄되므로 같은 저축을
 *       중복 계산하지 않는다. 음수면 예·적금 납입을 상쇄한다.</li>
 * </ul>
 * 세이프박스(도담 계좌에 담은 금액)는 담기·꺼내기 기록({@code safebox_movement})의 순증감으로 잰다. 아래
 * {@link #safeboxPace} 참고.
 * 합이 음수면 0(현재 속도로는 달성 불가). 창을 완료 월로만 잡는 이유는 급여일 전의 이번 달을 넣으면 수입 없이 지출만 잡혀
 * 속도가 매달 초에 떨어졌다 올라오기 때문이다.
 */
@Component
public class GoalSavingPaceReader {

    public static final int WINDOW_MONTHS = 3;

    private final JdbcGoalPaceReader reader;
    private final SafeboxAccountRepository safeboxAccounts;
    private final SafeboxMovementRepository movements;

    public GoalSavingPaceReader(JdbcGoalPaceReader reader, SafeboxAccountRepository safeboxAccounts,
            SafeboxMovementRepository movements) {
        this.reader = reader;
        this.safeboxAccounts = safeboxAccounts;
        this.movements = movements;
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
        Long dedicatedId = safeboxAccounts.findById(userId).map(ref -> ref.getAccountId()).orElse(null);

        List<Long> exclusive = allocation.accounts().stream()
                .filter(item -> GoalAllocationReader.isExclusiveType(item.account().getAccountType()))
                .map(item -> item.account().getId())
                .toList();
        List<Long> demandDeposits = allocation.accounts().stream()
                .filter(item -> item.account().getAccountType() == AccountType.DEMAND_DEPOSIT)
                .filter(item -> !item.account().getId().equals(dedicatedId))
                .map(item -> item.account().getId())
                .toList();

        long pace = 0L;
        if (!exclusive.isEmpty()) {
            Map<Long, Long> paid = reader.readPaidPaymentTotals(exclusive, from, to);
            Map<Long, DepositSavingTerms> terms = reader.readTerms(exclusive);
            for (Long accountId : exclusive) {
                pace += depositPace(paid.get(accountId), terms.get(accountId), first, last, today);
            }
        }
        if (allocation.accounts().stream().anyMatch(item -> item.account().getId().equals(dedicatedId))) {
            pace += safeboxPace(userId, allocation.goalId(), first, last, today);
        }
        if (!demandDeposits.isEmpty()) {
            int months = monthsCovered(first, last, reader.readFirstTransactionDate(userId, demandDeposits).orElse(null));
            if (months > 0) {
                Map<Long, Long> netByAccount = reader.readNetCashflowByAccount(userId, demandDeposits, from, to);
                long attributed = 0L;
                for (GoalAllocationReader.LinkedAccount item : allocation.accounts()) {
                    if (item.account().getAccountType() != AccountType.DEMAND_DEPOSIT) {
                        continue;
                    }
                    Long net = netByAccount.get(item.account().getId());
                    if (net != null) {
                        attributed += shareOf(net, item);
                    }
                }
                // 계좌별로 나누지 않고 합한 뒤 한 번만 나눈다. 몫이 모두 1이면 702까지와 같은 값이 된다.
                pace += attributed / months;
            }
        }
        return Math.max(0L, pace);
    }

    /**
     * 도담 계좌 안에서 이 목표에 담은 금액의 월 순증감(705). 도담 계좌 입금 자체는 어느 목표의 저축인지 알 수 없어 세지 않는다.
     *
     * <p>담기 기록은 사용자가 직접 만들므로 입출금 계좌처럼 급여일 전 이번 달이 속도를 끌어내릴 이유가 없다. 그래서
     * <ul>
     *   <li>첫 담기가 창 안이나 그 전이면 창의 순증감 / 첫 담기부터 덮인 완료 월 수. 예전처럼 늘 3으로 나누면 지난달 만든
     *       목표의 속도가 3분의 1로 깎였다.</li>
     *   <li>첫 담기가 이번 달이면(이번 달 만든 목표) 완료 월이 없어 늘 0이 되고 예상 달성일이 "계산 불가"로 남았다.
     *       이번 달 1일부터 기준일까지의 순증감을 한 달 치 속도로 본다. 다음 달이 되면 완료 월 기준으로 넘어간다.</li>
     * </ul>
     */
    private long safeboxPace(Long userId, Long goalId, YearMonth first, YearMonth last, LocalDate today) {
        LocalDate firstMovement = movements.findFirstOccurredAt(userId, goalId)
                .map(LocalDateTime::toLocalDate)
                .orElse(null);
        if (firstMovement == null) {
            return 0L;
        }
        int months = monthsCovered(first, last, firstMovement);
        if (months == 0) {
            return movements.sumDelta(userId, goalId, YearMonth.from(today).atDay(1).atStartOfDay(),
                    today.plusDays(1).atStartOfDay());
        }
        return movements.sumDelta(userId, goalId, first.atDay(1).atStartOfDay(),
                last.atEndOfMonth().plusDays(1).atStartOfDay()) / months;
    }

    /**
     * 한 입출금 계좌를 여러 목표에 담았을 때 그 계좌의 순현금흐름 중 이 목표 몫(703).
     *
     * <p>626은 확보액에서 같은 돈이 두 목표에 중복 합산되지 않게 막았지만, 694에서 따로 생긴 저축속도에는 그 장치가 없어
     * 두 목표가 같은 증가분을 각자 전액 세고 있었다. 몫은 확보액과 같은 기준인 {@code 이 목표의 기여액 / 그 계좌의 총 할당액}이다.
     *
     * <p>담은 금액이 아직 없으면({@code contribution_amount} NULL) 각 목표의 기여액이 잔액 전체라 몫이 {@code 1/연결 목표 수},
     * 즉 균등 분할이 된다. 총 할당액이 0이거나(잔액 0) 이 목표가 전부를 차지하면 나눌 것이 없어 전액으로 본다.
     *
     * <p>{@code 순현금흐름 × 기여액}이 long 범위를 넘을 수 있어 중간 곱만 {@link BigInteger}로 계산한다. 몫이 1 이하라 결과는
     * 다시 long에 들어간다.
     */
    private static long shareOf(long netCashflow, GoalAllocationReader.LinkedAccount item) {
        long allocatedTotal = item.allocation().allocatedTotal();
        if (allocatedTotal <= 0 || item.contribution() >= allocatedTotal) {
            return netCashflow;
        }
        return BigInteger.valueOf(netCashflow)
                .multiply(BigInteger.valueOf(item.contribution()))
                .divide(BigInteger.valueOf(allocatedTotal))
                .longValue();
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
