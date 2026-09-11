package com.ssafy.dodam.domain.cashflow.service;

import com.ssafy.dodam.domain.cashflow.dto.CashflowBalanceProjection;
import com.ssafy.dodam.domain.cashflow.dto.CashflowForecastPeriod;
import com.ssafy.dodam.domain.cashflow.dto.CashflowShortage;
import com.ssafy.dodam.domain.cashflow.entity.CashflowDailyBalance;
import com.ssafy.dodam.domain.cashflow.entity.CashflowEvent;
import com.ssafy.dodam.domain.cashflow.entity.EventDirection;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 날짜별 예상잔액 계산. DB에 접근하지 않는 순수 계산이다.
 *
 * <p>기획안 "공통 현금흐름 예측 로직"의 순서를 따른다. 하루 안에서 <b>입금을 먼저, 출금을 나중에</b>
 * 반영한다. 순서를 바꾸면 같은 날 급여가 들어오는데도 카드대금이 잔액 부족으로 판정된다.
 *
 * <p>부족 사건({@link CashflowShortage})도 이 루프에서 함께 모은다. 별도 클래스가 같은 순회를 다시
 * 돌면 입금·출금 반영 순서가 두 곳에 생겨 한쪽만 바뀔 수 있다. 위험 유형·심각도 판단은 여기가 아니라
 * {@code CashflowRiskDetector}가 맡는다.
 *
 * <p><b>제한</b>: 기획안이 함께 제시한 요일별 평균 변동지출은 반영하지 않는다. 입력이 될 `transaction`
 * 테이블의 마이그레이션이 아직 없다. 확정 예정 이벤트만 반영하므로 예상잔액은 실제보다 낙관적이다.
 */
@Component
public class CashflowDailyBalanceCalculator {

    /**
     * @param startingBalance 기준일 시작 시점의 현금 잔액
     * @param events 예측 구간의 예정 이벤트. 구간 밖 이벤트는 무시한다
     * @return 구간 일수만큼의 일별 잔액과 파생 집계값
     */
    public CashflowBalanceProjection calculate(
            Long forecastId,
            CashflowForecastPeriod period,
            long startingBalance,
            List<CashflowEvent> events
    ) {
        Map<LocalDate, List<CashflowEvent>> byDate = events.stream()
                .filter(event -> withinPeriod(event.getEventDate(), period))
                .collect(Collectors.groupingBy(CashflowEvent::getEventDate));

        List<CashflowDailyBalance> dailyBalances = new ArrayList<>(period.horizonDays());
        long balance = startingBalance;
        // 최소값은 일별 마감 잔액 중에서만 고른다. 시작 잔액은 기준일 이벤트를 반영하기 전 값이라
        // 저장되는 어느 날짜의 잔액도 아니다.
        long minimumBalance = Long.MAX_VALUE;
        LocalDate firstShortageDate = null;
        List<CashflowShortage> shortages = new ArrayList<>();

        for (int day = 0; day < period.horizonDays(); day++) {
            LocalDate date = period.baseDate().plusDays(day);
            List<CashflowEvent> daily = byDate.getOrDefault(date, List.of());

            for (CashflowEvent event : daily) {
                if (event.getDirection() == EventDirection.INFLOW) {
                    balance = Math.addExact(balance, event.getAmount());
                }
            }
            for (CashflowEvent event : daily) {
                if (event.getDirection() != EventDirection.OUTFLOW) {
                    continue;
                }
                // 출금 직전 잔액으로 판정한다. 마감 잔액만 보면 같은 날 여러 건이 빠져나갈 때 어느
                // 출금에서 처음 막히는지 알 수 없다(기획안의 balance_before_payment).
                if (balance < event.getAmount()) {
                    shortages.add(new CashflowShortage(
                            date, event.getType(), event.getName(), event.getAmount(), balance));
                }
                balance = Math.subtractExact(balance, event.getAmount());
            }

            // 음수를 0으로 절삭하지 않는다. 부족 구간의 깊이가 남아야 위험 탐지(542)가 성립한다.
            dailyBalances.add(CashflowDailyBalance.of(forecastId, date, balance));
            if (balance < minimumBalance) {
                minimumBalance = balance;
            }
            if (firstShortageDate == null && balance < 0) {
                firstShortageDate = date;
            }
        }
        return new CashflowBalanceProjection(
                dailyBalances,
                dailyBalances.isEmpty() ? startingBalance : minimumBalance,
                firstShortageDate,
                shortages);
    }

    // 539가 이미 구간 밖 이벤트를 제외하지만, 계산기 단독으로도 행 수가 horizonDays와 어긋나지 않게 한다.
    private boolean withinPeriod(LocalDate date, CashflowForecastPeriod period) {
        return !date.isBefore(period.baseDate()) && !date.isAfter(period.endDate());
    }
}
