package com.ssafy.dodam.domain.cashflow.service;

import com.ssafy.dodam.domain.cashflow.dto.CashflowBalanceProjection;
import com.ssafy.dodam.domain.cashflow.dto.CashflowEventCollection;
import com.ssafy.dodam.domain.cashflow.dto.CashflowForecastPeriod;
import com.ssafy.dodam.domain.cashflow.dto.CashflowForecastResult;
import com.ssafy.dodam.domain.cashflow.dto.CashflowRiskAssessment;
import com.ssafy.dodam.domain.cashflow.dto.CashflowPlannedEvent;
import com.ssafy.dodam.domain.cashflow.entity.CashflowEvent;
import com.ssafy.dodam.domain.cashflow.entity.CashflowForecast;
import com.ssafy.dodam.domain.cashflow.entity.EventDirection;
import com.ssafy.dodam.domain.cashflow.entity.ForecastStatus;
import com.ssafy.dodam.domain.cashflow.repository.CashflowBalanceReader;
import com.ssafy.dodam.domain.cashflow.repository.CashflowForecastRepository;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * 예측 실행의 오케스트레이션. 캐시 판정 → 수집(539) → 계산 → 저장(538) 순서를 책임진다.
 *
 * <p>HTTP 계약은 S15P21D105-541의 Controller가 맡는다. 이 클래스는 인증된 `userId`를 받는다.
 *
 * <p>트랜잭션 경계는 {@link CashflowForecastStore}에 있다. 원본 수집과 계산은 수 초가 걸릴 수 있어
 * 하나의 트랜잭션으로 묶으면 커넥션을 계산하는 동안 붙잡는다. 대신 헤더를 먼저 `PENDING`으로 남겨
 * 진행 중인 계산을 다른 요청이 알아볼 수 있게 한다.
 */
@Service
public class CashflowForecastRunner {

    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    /**
     * 진행 중인 계산으로 인정하는 시간.
     *
     * <p>계산 도중 프로세스가 내려가면 헤더가 `PENDING`으로 남는다. 만료를 두지 않으면 그 요청 조합은
     * 영원히 "계산 중"으로 보여 `forceRefresh`로도 다시 돌릴 수 없다. 이 시간을 넘긴 행은 버려진 것으로
     * 보고 새로 계산한다. 수집·계산이 이보다 오래 걸리면 같은 예측이 두 번 돌 수 있으므로, 실제 소요
     * 시간을 확인한 뒤 조정한다.
     */
    private static final Duration IN_PROGRESS_TIMEOUT = Duration.ofMinutes(10);

    private final CashflowForecastPeriodPolicy periodPolicy;
    private final CashflowForecastRepository forecastRepository;
    private final CashflowBalanceReader balanceReader;
    private final CashflowEventCollector eventCollector;
    private final CashflowDailyBalanceCalculator calculator;
    private final CashflowRiskDetector riskDetector;
    private final CashflowForecastStore store;

    public CashflowForecastRunner(
            CashflowForecastPeriodPolicy periodPolicy,
            CashflowForecastRepository forecastRepository,
            CashflowBalanceReader balanceReader,
            CashflowEventCollector eventCollector,
            CashflowDailyBalanceCalculator calculator,
            CashflowRiskDetector riskDetector,
            CashflowForecastStore store
    ) {
        this.periodPolicy = periodPolicy;
        this.forecastRepository = forecastRepository;
        this.balanceReader = balanceReader;
        this.eventCollector = eventCollector;
        this.calculator = calculator;
        this.riskDetector = riskDetector;
        this.store = store;
    }

    public CashflowForecastResult run(
            Long userId,
            LocalDate baseDate,
            Integer horizonDays,
            boolean forceRefresh
    ) {
        return run(userId, baseDate, horizonDays, forceRefresh, LocalDateTime.now(SERVICE_ZONE));
    }

    CashflowForecastResult run(
            Long userId,
            LocalDate baseDate,
            Integer horizonDays,
            boolean forceRefresh,
            LocalDateTime now
    ) {
        if (userId == null || userId <= 0) {
            throw new InvalidRequestException("사용자를 지정해야 합니다.");
        }
        CashflowForecastPeriod period = periodPolicy.resolve(baseDate, horizonDays, now.toLocalDate());
        String requestHash = CashflowRequestHash.of(userId, period, CashflowCalculationVersion.CURRENT);

        // 진행 중인 계산은 forceRefresh로도 우회하지 않는다. 같은 예측을 두 번 돌리면 하위행 삭제와
        // 삽입이 교차해 결과가 깨진다.
        Optional<CashflowForecast> inProgress = findInProgress(userId, requestHash, now);
        if (inProgress.isPresent()) {
            return new CashflowForecastResult(inProgress.get(), true, List.of());
        }
        if (!forceRefresh) {
            Optional<CashflowForecast> cached = findReusableCompleted(userId, requestHash, now);
            if (cached.isPresent()) {
                return new CashflowForecastResult(cached.get(), true, List.of());
            }
        }
        return calculate(userId, period, requestHash, now);
    }

    /**
     * 명세의 "중복 요청 방지". 아직 끝나지 않은 같은 요청이 있으면 그 예측을 그대로 돌려준다.
     *
     * <p>{@link #IN_PROGRESS_TIMEOUT}을 넘긴 행은 버려진 계산으로 보고 무시한다.
     */
    private Optional<CashflowForecast> findInProgress(Long userId, String requestHash, LocalDateTime now) {
        LocalDateTime oldestAlive = now.minus(IN_PROGRESS_TIMEOUT);
        for (ForecastStatus status : List.of(ForecastStatus.RUNNING, ForecastStatus.PENDING)) {
            Optional<CashflowForecast> running = forecastRepository
                    .findFirstByUserIdAndRequestHashAndStatusOrderByCreatedAtDesc(userId, requestHash, status)
                    .filter(forecast -> !forecast.getCreatedAt().isBefore(oldestAlive));
            if (running.isPresent()) {
                return running;
            }
        }
        return Optional.empty();
    }

    /**
     * 재사용 가능한 완료 예측을 찾는다.
     *
     * <p><b>같은 서비스일에 계산된 것만</b> 재사용한다. 잔액과 예정 내역은 수시로 바뀌므로 무기한
     * 재사용하면 낡은 잔액을 확정값처럼 보여준다. 하루를 넘기면 기준일의 의미 자체가 달라진다.
     * 당일 안에서 최신 데이터가 필요하면 `forceRefresh`를 쓴다.
     */
    private Optional<CashflowForecast> findReusableCompleted(
            Long userId, String requestHash, LocalDateTime now) {
        return forecastRepository
                .findFirstByUserIdAndRequestHashAndStatusOrderByCreatedAtDesc(
                        userId, requestHash, ForecastStatus.COMPLETED)
                .filter(forecast -> forecast.getCompletedAt() != null
                        && forecast.getCompletedAt().toLocalDate().equals(now.toLocalDate()));
    }

    private CashflowForecastResult calculate(
            Long userId,
            CashflowForecastPeriod period,
            String requestHash,
            LocalDateTime now
    ) {
        CashflowForecast forecast = store.start(
                userId, period.baseDate(), period.horizonDays(), requestHash, now);
        try {
            long startingBalance = balanceReader.readStartingBalance(userId);
            CashflowEventCollection collected = eventCollector.collect(
                    userId, period.baseDate(), period.endDate());
            List<CashflowEvent> events = collected.events().stream()
                    .map(event -> toEntity(forecast.getForecastId(), event))
                    .toList();
            CashflowBalanceProjection projection = calculator.calculate(
                    forecast.getForecastId(), period, startingBalance, events);

            CashflowRiskAssessment assessment = riskDetector.assess(forecast.getForecastId(), projection);

            // `risk_date`는 잔액이 처음 음수가 되는 날이다. 첫 부족 사건의 날짜와 언제나 같으므로
            // (잔액은 출금으로만 줄고, 음수로 넘어가는 출금은 반드시 부족으로 잡힌다) 계산기가 이미
            // 구한 값을 그대로 쓴다.
            store.complete(
                    forecast,
                    projection.dailyBalances(),
                    events,
                    assessment.risks(),
                    startingBalance,
                    assessment.availableToday(),
                    projection.minimumBalance(),
                    projection.firstShortageDate(),
                    now);
            return new CashflowForecastResult(forecast, false, collected.reasons());
        } catch (RuntimeException exception) {
            // 접수만 된 채 남은 예측이 조회되면 계산이 끝난 것처럼 보인다. 실패를 기록하고 전파한다.
            store.fail(forecast, now);
            throw exception;
        }
    }

    private CashflowEvent toEntity(Long forecastId, CashflowPlannedEvent event) {
        return CashflowEvent.of(
                forecastId,
                event.eventDate(),
                event.type(),
                event.name(),
                event.direction() == CashflowPlannedEvent.Direction.INFLOW
                        ? EventDirection.INFLOW : EventDirection.OUTFLOW,
                event.amount(),
                event.sourceType(),
                event.sourceId(),
                event.dedupeKey());
    }
}
