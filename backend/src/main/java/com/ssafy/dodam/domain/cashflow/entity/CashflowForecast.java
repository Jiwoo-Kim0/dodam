package com.ssafy.dodam.domain.cashflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 현금흐름 예측 실행 1건.
 *
 * <p>하위 3테이블(`cashflow_daily_balance`, `cashflow_event`, `cashflow_risk`)은 이 행을 참조하지만
 * 연관관계로 매핑하지 않는다. horizon 일수만큼 생기는 일별 잔액을 컬렉션으로 들고 다니면 조회마다
 * 불필요한 로딩이 발생하고, 실제 조회는 언제나 `forecastId` 기준 배치이기 때문이다.
 *
 * <p>하위 테이블에는 `user_id`가 없다. 따라서 하위행 조회의 소유권 조건은 모두 이 행의 `user_id`를
 * 조인해서 건다({@code CashflowDailyBalanceRepository} 등 참고).
 *
 * <p>집계값(`currentBalance`, `availableToday`, `minimumBalance`, `riskDate`)은 계산이 끝난 뒤에만
 * 채워지므로 NULL을 허용한다. 계산 규칙은 S15P21D105-540·542에서 확정한다.
 */
@Getter
@Entity
@Table(name = "cashflow_forecast")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashflowForecast {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "forecast_id")
    private Long forecastId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "base_date", nullable = false)
    private LocalDate baseDate;

    @Column(name = "horizon_days", nullable = false)
    private Integer horizonDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ForecastStatus status;

    @Column(name = "current_balance")
    private Long currentBalance;

    @Column(name = "available_today")
    private Long availableToday;

    @Column(name = "minimum_balance")
    private Long minimumBalance;

    @Column(name = "risk_date")
    private LocalDate riskDate;

    @Column(name = "calculation_version", length = 50)
    private String calculationVersion;

    // 동일 요청 재실행을 캐시·멱등 처리하기 위한 지문. 생성 규칙은 S15P21D105-540 범위다.
    // `dedupe_key`와 같은 이유로 CHAR(64)를 명시한다.
    @Column(name = "request_hash", length = 64, columnDefinition = "CHAR(64)")
    private String requestHash;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    private CashflowForecast(
            Long userId,
            LocalDate baseDate,
            int horizonDays,
            String calculationVersion,
            String requestHash,
            LocalDateTime now
    ) {
        this.userId = userId;
        this.baseDate = baseDate;
        this.horizonDays = horizonDays;
        this.status = ForecastStatus.PENDING;
        this.calculationVersion = calculationVersion;
        this.requestHash = requestHash;
        this.createdAt = now;
    }

    /**
     * 예측을 접수한다. 계산 전 상태이므로 집계값은 비어 있다.
     *
     * @param horizonDays `chk_cashflow_forecast_horizon_days`를 만족하는 1 이상의 값
     */
    public static CashflowForecast start(
            Long userId,
            LocalDate baseDate,
            int horizonDays,
            String calculationVersion,
            String requestHash,
            LocalDateTime now
    ) {
        if (horizonDays <= 0) {
            throw new IllegalArgumentException("예측 기간은 1일 이상이어야 합니다.");
        }
        return new CashflowForecast(userId, baseDate, horizonDays, calculationVersion, requestHash, now);
    }

    public void markRunning() {
        requireStatus(ForecastStatus.PENDING);
        this.status = ForecastStatus.RUNNING;
    }

    /**
     * 계산 결과 집계값을 반영하고 완료 처리한다.
     *
     * <p>`riskDate`는 잔액이 처음 위험 수준으로 떨어지는 날이며 위험이 없으면 NULL이다.
     */
    public void complete(
            Long currentBalance,
            Long availableToday,
            Long minimumBalance,
            LocalDate riskDate,
            LocalDateTime now
    ) {
        requireStatus(ForecastStatus.PENDING, ForecastStatus.RUNNING);
        this.currentBalance = currentBalance;
        this.availableToday = availableToday;
        this.minimumBalance = minimumBalance;
        this.riskDate = riskDate;
        this.status = ForecastStatus.COMPLETED;
        this.completedAt = now;
    }

    /**
     * 계산에 실패했음을 기록한다. 실패 시점도 종료 시각으로 남겨 재시도 판단에 쓴다.
     */
    public void fail(LocalDateTime now) {
        requireStatus(ForecastStatus.PENDING, ForecastStatus.RUNNING);
        this.status = ForecastStatus.FAILED;
        this.completedAt = now;
    }

    public boolean isCompleted() {
        return status == ForecastStatus.COMPLETED;
    }

    // 완료된 예측을 덮어써 조회 결과가 조용히 바뀌는 것을 막는다. 재계산은 새 행으로 남긴다.
    private void requireStatus(ForecastStatus... allowed) {
        for (ForecastStatus candidate : allowed) {
            if (this.status == candidate) {
                return;
            }
        }
        throw new IllegalStateException("현재 상태에서 수행할 수 없는 전이입니다: " + this.status);
    }
}
