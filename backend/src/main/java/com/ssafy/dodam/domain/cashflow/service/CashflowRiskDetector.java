package com.ssafy.dodam.domain.cashflow.service;

import com.ssafy.dodam.domain.cashflow.dto.CashflowBalanceProjection;
import com.ssafy.dodam.domain.cashflow.dto.CashflowRiskAssessment;
import com.ssafy.dodam.domain.cashflow.dto.CashflowShortage;
import com.ssafy.dodam.domain.cashflow.entity.CashflowRisk;
import com.ssafy.dodam.domain.cashflow.entity.RiskSeverity;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 예측 결과에서 현금흐름 위험과 오늘 사용가능금액을 판정한다. DB에 접근하지 않는 순수 계산이다.
 *
 * <p>부족 사건 자체는 {@link CashflowDailyBalanceCalculator}가 잔액을 굴리면서 모은다. 이 클래스는
 * 그 사건에 <b>유형·심각도·문구</b>를 입히는 정책만 담당한다. 정책이 바뀌어도 잔액 계산은 건드리지
 * 않게 하려는 분리다.
 */
@Component
public class CashflowRiskDetector {

    /**
     * 예정 출금일에 잔액이 모자란 위험.
     *
     * <p>ERD 주요 코드값 표에 `cashflow_risk.type` 목록이 없어 S15P21D105-542에서 값을 정한다.
     * 현재 탐지 규칙이 이 하나뿐이므로 값 집합도 이 하나다. 유형이 늘면 여기에 상수를 추가하고
     * ERD 코드값 표를 함께 갱신한다.
     */
    public static final String TYPE_BALANCE_SHORTAGE = "BALANCE_SHORTAGE";

    // 심각도 경계. 부족액을 필요금액으로 나눈 비율이며 절대금액을 쓰지 않는다. 같은 10만원 부족도
    // 소득 규모에 따라 의미가 달라서 금액 임계값은 사용자마다 다시 정해야 하지만, "필요한 돈의 몇 %가
    // 비었나"는 사용자와 무관하게 해석이 같다. 경계값은 명세에 없어 정한 서비스 기본안이다.
    private static final double HIGH_SHORTAGE_RATIO = 0.5d;
    private static final double MEDIUM_SHORTAGE_RATIO = 0.2d;

    /**
     * @param projection 같은 예측의 일별 잔액 계산 결과
     */
    public CashflowRiskAssessment assess(Long forecastId, CashflowBalanceProjection projection) {
        List<CashflowRisk> risks = projection.shortages().stream()
                .map(shortage -> toRisk(forecastId, shortage))
                .toList();
        return new CashflowRiskAssessment(risks, availableToday(projection));
    }

    /**
     * 오늘 사용가능금액.
     *
     * <p>정의는 <b>`max(0, 예측 구간 최소 예상잔액)`</b>이다. 앞으로 확정된 출금을 모두 감당하고도
     * 오늘 꺼내 쓸 수 있는 금액이라는 뜻이다. 오늘 마감 잔액만 쓰면 내일 카드대금이 빠져나가도 오늘은
     * 여유가 있는 것처럼 보인다.
     *
     * <p>`min(오늘 마감 잔액, 구간 최소 잔액)`으로 쓸 필요는 없다. 최소 잔액은 기준일 마감 잔액을
     * 포함해 고른 값이라 둘의 최소값과 항상 같다({@code CashflowDailyBalanceCalculator}).
     *
     * <p>음수는 0으로 막는다. "사용가능금액 -30만원"은 쓸 수 있는 돈의 크기가 아니다. 부족 사실은
     * 위험 행과 `minimum_balance`가 따로 전달한다.
     *
     * <p><b>한계</b>: 예측 구간 전체(기본 90일)의 최소값이라 먼 미래의 큰 출금 한 건이 오늘 값을
     * 끌어내린다. 명세에 계산식이 없어 정한 기본안이며 구간 길이를 좁히는 안은 남은 작업에 기록한다.
     */
    private long availableToday(CashflowBalanceProjection projection) {
        return Math.max(0L, projection.minimumBalance());
    }

    private CashflowRisk toRisk(Long forecastId, CashflowShortage shortage) {
        return CashflowRisk.of(
                forecastId,
                shortage.date(),
                TYPE_BALANCE_SHORTAGE,
                severityOf(shortage),
                messageOf(shortage),
                shortage.balanceBeforePayment());
    }

    /**
     * 부족액이 필요금액에서 차지하는 비율로 심각도를 정한다.
     *
     * <p>출금 직전 잔액이 0 이하면 한 푼도 못 내는 상태라 `CRITICAL`이다. 비율만 보면 잔액이 음수일 때
     * 100%를 넘어 구간이 무의미해지므로 먼저 걸러 낸다.
     */
    private RiskSeverity severityOf(CashflowShortage shortage) {
        if (shortage.balanceBeforePayment() <= 0L) {
            return RiskSeverity.CRITICAL;
        }
        double ratio = (double) shortage.shortageAmount() / shortage.requiredAmount();
        if (ratio >= HIGH_SHORTAGE_RATIO) {
            return RiskSeverity.HIGH;
        }
        if (ratio >= MEDIUM_SHORTAGE_RATIO) {
            return RiskSeverity.MEDIUM;
        }
        return RiskSeverity.LOW;
    }

    /**
     * 위험 문구. 이벤트 이름과 금액만 쓰고 계좌번호·카드번호는 담지 않는다. 이 문구는 응답과 DB에
     * 그대로 남으므로 민감번호가 들어가면 회수할 수 없다.
     *
     * <p>`cashflow_risk.message`가 VARCHAR(500)이라 이벤트 이름을 잘라 상한을 넘지 않게 한다.
     */
    private String messageOf(CashflowShortage shortage) {
        return "%s %,d원 출금 예정이나 %,d원이 부족합니다."
                .formatted(
                        abbreviate(shortage.eventName()),
                        shortage.requiredAmount(),
                        shortage.shortageAmount());
    }

    private static final int MAX_EVENT_NAME_LENGTH = 100;

    private String abbreviate(String eventName) {
        if (eventName == null || eventName.isBlank()) {
            return "예정 출금";
        }
        return eventName.length() <= MAX_EVENT_NAME_LENGTH
                ? eventName
                : eventName.substring(0, MAX_EVENT_NAME_LENGTH) + "…";
    }
}
