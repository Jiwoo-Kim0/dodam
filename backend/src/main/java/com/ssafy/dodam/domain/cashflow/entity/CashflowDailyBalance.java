package com.ssafy.dodam.domain.cashflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 예측 기간의 날짜별 예상잔액.
 *
 * <p>`expectedBalance`는 음수가 될 수 있다. 마이너스통장 사용 구간과 잔액 부족 구간을 그대로
 * 표현해야 위험 탐지(S15P21D105-542)가 성립하기 때문에 0으로 절삭하지 않는다.
 *
 * <p>`(forecastId, balanceDate)`는 유니크다. 같은 예측에서 같은 날짜가 두 번 나오면 어느 쪽이
 * 맞는지 판단할 수 없으므로 저장 단계에서 막는다.
 */
@Getter
@Entity
@Table(name = "cashflow_daily_balance", uniqueConstraints = @UniqueConstraint(
        name = "uk_cashflow_daily_balance_forecast_date",
        columnNames = {"forecast_id", "balance_date"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashflowDailyBalance {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "daily_balance_id")
    private Long dailyBalanceId;

    @Column(name = "forecast_id", nullable = false)
    private Long forecastId;

    @Column(name = "balance_date", nullable = false)
    private LocalDate balanceDate;

    @Column(name = "expected_balance", nullable = false)
    private Long expectedBalance;

    private CashflowDailyBalance(Long forecastId, LocalDate balanceDate, long expectedBalance) {
        this.forecastId = forecastId;
        this.balanceDate = balanceDate;
        this.expectedBalance = expectedBalance;
    }

    public static CashflowDailyBalance of(Long forecastId, LocalDate balanceDate, long expectedBalance) {
        return new CashflowDailyBalance(forecastId, balanceDate, expectedBalance);
    }
}
