package com.ssafy.dodam.domain.cashflow.service;

import com.ssafy.dodam.domain.cashflow.entity.CashflowDailyBalance;
import com.ssafy.dodam.domain.cashflow.entity.CashflowEvent;
import com.ssafy.dodam.domain.cashflow.entity.CashflowForecast;
import com.ssafy.dodam.domain.cashflow.entity.CashflowRisk;
import com.ssafy.dodam.domain.cashflow.repository.CashflowDailyBalanceRepository;
import com.ssafy.dodam.domain.cashflow.repository.CashflowEventRepository;
import com.ssafy.dodam.domain.cashflow.repository.CashflowForecastRepository;
import com.ssafy.dodam.domain.cashflow.repository.CashflowResultWriter;
import com.ssafy.dodam.domain.cashflow.repository.CashflowRiskRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 예측 헤더와 하위행의 저장 경로.
 *
 * <p>이 클래스는 저장 순서와 트랜잭션 경계만 책임진다. 기준일·horizon 결정과 캐시 판단은
 * S15P21D105-540, 이벤트 생성은 539, 위험 탐지와 가용금액 계산은 542에서 이 경로를 호출한다.
 */
@Service
public class CashflowForecastStore {

    private final CashflowForecastRepository forecastRepository;
    private final CashflowDailyBalanceRepository dailyBalanceRepository;
    private final CashflowEventRepository eventRepository;
    private final CashflowRiskRepository riskRepository;
    private final CashflowResultWriter resultWriter;

    public CashflowForecastStore(
            CashflowForecastRepository forecastRepository,
            CashflowDailyBalanceRepository dailyBalanceRepository,
            CashflowEventRepository eventRepository,
            CashflowRiskRepository riskRepository,
            CashflowResultWriter resultWriter
    ) {
        this.forecastRepository = forecastRepository;
        this.dailyBalanceRepository = dailyBalanceRepository;
        this.eventRepository = eventRepository;
        this.riskRepository = riskRepository;
        this.resultWriter = resultWriter;
    }

    /**
     * 예측을 접수해 `PENDING` 상태의 헤더를 만든다. 하위행의 외래키가 될 `forecastId`를 얻기 위해
     * 계산 시작 전에 먼저 저장한다.
     */
    @Transactional
    public CashflowForecast start(
            Long userId,
            LocalDate baseDate,
            int horizonDays,
            String requestHash,
            LocalDateTime now
    ) {
        CashflowForecast forecast = CashflowForecast.start(
                userId, baseDate, horizonDays, CashflowCalculationVersion.CURRENT, requestHash, now);
        return forecastRepository.save(forecast);
    }

    /**
     * 계산 결과를 저장하고 예측을 완료 처리한다.
     *
     * <p>하위행을 먼저 지우고 다시 넣는다. 같은 예측을 재계산할 때 이전 결과가 남으면
     * `(forecast_id, balance_date)` 유니크 제약에 걸리거나 이벤트가 중복 반영되기 때문이다.
     * 삭제·삽입·상태 전이는 한 트랜잭션이어야 부분 반영된 예측이 조회되지 않는다.
     */
    @Transactional
    public void complete(
            CashflowForecast forecast,
            List<CashflowDailyBalance> dailyBalances,
            List<CashflowEvent> events,
            List<CashflowRisk> risks,
            Long currentBalance,
            Long availableToday,
            Long minimumBalance,
            LocalDate riskDate,
            LocalDateTime now
    ) {
        Long forecastId = forecast.getForecastId();
        dailyBalanceRepository.deleteByForecastId(forecastId);
        eventRepository.deleteByForecastId(forecastId);
        riskRepository.deleteByForecastId(forecastId);

        resultWriter.insertDailyBalances(dailyBalances);
        resultWriter.insertEvents(events);
        resultWriter.insertRisks(risks);

        forecast.complete(currentBalance, availableToday, minimumBalance, riskDate, now);
        forecastRepository.save(forecast);
    }

    /**
     * 계산에 실패했음을 기록한다. 하위행은 지운다. 완료되지 않은 예측의 부분 결과가 남으면
     * 조회 API가 실제보다 낙관적인 잔액을 보여줄 수 있다.
     */
    @Transactional
    public void fail(CashflowForecast forecast, LocalDateTime now) {
        Long forecastId = forecast.getForecastId();
        dailyBalanceRepository.deleteByForecastId(forecastId);
        eventRepository.deleteByForecastId(forecastId);
        riskRepository.deleteByForecastId(forecastId);

        forecast.fail(now);
        forecastRepository.save(forecast);
    }
}
