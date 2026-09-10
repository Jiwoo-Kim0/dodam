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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 예측에서 탐지된 현금흐름 위험 1건.
 *
 * <p>`riskDate`와 `expectedBalance`는 NULL을 허용한다. 특정 날짜에 묶이지 않는 위험(예: 기간 전체의
 * 저축 여력 부족)이 있어 날짜를 강제할 수 없기 때문이다.
 *
 * <p>`type`은 enum으로 두지 않았다. ERD 코드값 표가 `severity`만 정의하고 `cashflow_risk.type`의
 * 값 목록은 두지 않아, 탐지 규칙을 만드는 S15P21D105-542에서 집합을 확정한다.
 */
@Getter
@Entity
@Table(name = "cashflow_risk")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashflowRisk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "risk_id")
    private Long riskId;

    @Column(name = "forecast_id", nullable = false)
    private Long forecastId;

    @Column(name = "risk_date")
    private LocalDate riskDate;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 30)
    private RiskSeverity severity;

    @Column(name = "message", length = 500)
    private String message;

    @Column(name = "expected_balance")
    private Long expectedBalance;

    private CashflowRisk(
            Long forecastId,
            LocalDate riskDate,
            String type,
            RiskSeverity severity,
            String message,
            Long expectedBalance
    ) {
        this.forecastId = forecastId;
        this.riskDate = riskDate;
        this.type = type;
        this.severity = severity;
        this.message = message;
        this.expectedBalance = expectedBalance;
    }

    public static CashflowRisk of(
            Long forecastId,
            LocalDate riskDate,
            String type,
            RiskSeverity severity,
            String message,
            Long expectedBalance
    ) {
        return new CashflowRisk(forecastId, riskDate, type, severity, message, expectedBalance);
    }
}
