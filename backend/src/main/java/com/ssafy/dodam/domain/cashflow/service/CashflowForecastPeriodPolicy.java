package com.ssafy.dodam.domain.cashflow.service;

import com.ssafy.dodam.domain.cashflow.dto.CashflowForecastPeriod;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.policy.PaymentSchedulePeriodPolicy;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Component;

/**
 * 예측 실행의 기준일·기간 정책.
 *
 * <p>기간 상·하한은 539가 예정 이벤트를 모을 때 쓰는 {@link PaymentSchedulePeriodPolicy}에서 파생한다.
 * 수집 가능한 범위보다 긴 예측을 허용하면 뒷부분이 이벤트 없는 평탄한 잔액이 되어 실제보다 안전해
 * 보인다.
 */
@Component
public class CashflowForecastPeriodPolicy {

    /** 기본 3개월. `PaymentSchedulePeriodPolicy.DEFAULT_PERIOD_MONTHS`와 같은 길이다. */
    public static final int DEFAULT_HORIZON_DAYS = 90;

    /** 상한 12개월. 수집 정책의 `MAX_PERIOD_MONTHS`를 일수로 옮긴 값이다. */
    public static final int MAX_HORIZON_DAYS = 365;

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    public CashflowForecastPeriod resolve(LocalDate baseDate, Integer horizonDays) {
        return resolve(baseDate, horizonDays, LocalDate.now(SERVICE_ZONE));
    }

    CashflowForecastPeriod resolve(LocalDate baseDate, Integer horizonDays, LocalDate today) {
        LocalDate resolvedBase = baseDate == null ? today : baseDate;
        int resolvedHorizon = horizonDays == null ? DEFAULT_HORIZON_DAYS : horizonDays;

        // 계산의 시작점은 저장된 '현재' 잔액이다. 과거 기준일을 허용하면 그 시점의 잔액을 복원한 것처럼
        // 보이지만 실제로는 오늘 잔액을 과거에 놓은 값이 나온다.
        if (resolvedBase.isBefore(today)) {
            throw new InvalidRequestException("기준일은 오늘 이후여야 합니다.");
        }
        if (resolvedHorizon < 1 || resolvedHorizon > MAX_HORIZON_DAYS) {
            throw new InvalidRequestException(
                    "예측 기간은 1일 이상 " + MAX_HORIZON_DAYS + "일 이하여야 합니다."
            );
        }
        return new CashflowForecastPeriod(resolvedBase, resolvedHorizon);
    }
}
