package com.ssafy.dodam.domain.cashflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ssafy.dodam.domain.cashflow.dto.CashflowEventCollection;
import com.ssafy.dodam.domain.cashflow.dto.CashflowForecastResult;
import com.ssafy.dodam.domain.cashflow.dto.CashflowPlannedEvent;
import com.ssafy.dodam.domain.cashflow.entity.CashflowForecast;
import com.ssafy.dodam.domain.cashflow.entity.ForecastStatus;
import com.ssafy.dodam.domain.cashflow.repository.CashflowBalanceReader;
import com.ssafy.dodam.domain.cashflow.repository.CashflowDailyBalanceRepository;
import com.ssafy.dodam.domain.cashflow.repository.CashflowEventRepository;
import com.ssafy.dodam.domain.cashflow.repository.CashflowForecastRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 캐시·멱등 판정과 저장 경로를 함께 확인한다.
 *
 * <p>원본 수집(539)과 잔액 조회는 대역으로 바꾼다. 이 테스트의 대상은 언제 다시 계산하는지에 대한
 * 판정이지 원본 변환 규칙이 아니다. 저장은 실제 Repository를 쓴다.
 */
@ActiveProfiles("test")
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:cashflow540runner;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class CashflowForecastRunnerTest {

    private static final LocalDate BASE_DATE = LocalDate.of(2026, 9, 10);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 10, 9, 0);
    private static final AtomicLong USER_SEQUENCE = new AtomicLong(5_400_100L);

    @Autowired private CashflowForecastStore store;
    @Autowired private CashflowForecastRepository forecastRepository;
    @Autowired private CashflowDailyBalanceRepository dailyBalanceRepository;
    @Autowired private CashflowEventRepository eventRepository;

    private final CashflowBalanceReader balanceReader = mock(CashflowBalanceReader.class);
    private final CashflowEventCollector eventCollector = mock(CashflowEventCollector.class);
    private CashflowForecastRunner runner;
    private Long userId;

    @BeforeEach
    void setUp() {
        userId = USER_SEQUENCE.incrementAndGet();
        runner = new CashflowForecastRunner(
                new CashflowForecastPeriodPolicy(),
                forecastRepository,
                balanceReader,
                eventCollector,
                new CashflowDailyBalanceCalculator(),
                store);
        when(balanceReader.readStartingBalance(any())).thenReturn(1_000_000L);
        when(eventCollector.collect(any(), any(), any()))
                .thenReturn(new CashflowEventCollection(List.of(), List.of()));
    }

    @Test
    void storesDailyBalancesAndAggregatesForEachHorizonDay() {
        when(eventCollector.collect(any(), any(), any())).thenReturn(new CashflowEventCollection(
                List.of(plannedEvent(BASE_DATE.plusDays(1), 1_500_000L)), List.of()));

        CashflowForecastResult result = runner.run(userId, BASE_DATE, 3, false, NOW);
        CashflowForecast forecast = result.forecast();

        assertThat(result.cached()).isFalse();
        assertThat(forecast.getStatus()).isEqualTo(ForecastStatus.COMPLETED);
        assertThat(forecast.getCurrentBalance()).isEqualTo(1_000_000L);
        assertThat(forecast.getMinimumBalance()).isEqualTo(-500_000L);
        assertThat(forecast.getRiskDate()).isEqualTo(BASE_DATE.plusDays(1));
        // available_today 계산은 S15P21D105-542 범위다.
        assertThat(forecast.getAvailableToday()).isNull();
        assertThat(dailyBalanceRepository.findOwnedByPeriod(
                userId, forecast.getForecastId(), BASE_DATE, BASE_DATE.plusDays(2))).hasSize(3);
        assertThat(eventRepository.findOwnedByPeriod(
                userId, forecast.getForecastId(), BASE_DATE, BASE_DATE.plusDays(2), null)).hasSize(1);
    }

    @Test
    void reusesCompletedForecastCalculatedOnTheSameDay() {
        CashflowForecastResult first = runner.run(userId, BASE_DATE, 30, false, NOW);
        CashflowForecastResult second = runner.run(userId, BASE_DATE, 30, false, NOW.plusHours(5));

        assertThat(second.cached()).isTrue();
        assertThat(second.forecast().getForecastId()).isEqualTo(first.forecast().getForecastId());
        assertThat(forecastRepository.findAll()).filteredOn(f -> f.getUserId().equals(userId)).hasSize(1);
    }

    /**
     * 잔액과 예정 내역은 수시로 바뀐다. 하루가 지난 결과를 확정값처럼 재사용하지 않는다.
     *
     * <p>기준일을 미래로 둔다. 기준일이 오늘이면 다음 날 같은 요청 자체가 과거 기준일이 되어 거부되므로
     * 캐시 만료를 확인할 수 없다.
     */
    @Test
    void recalculatesWhenCachedForecastIsFromAnotherDay() {
        LocalDate futureBase = BASE_DATE.plusDays(10);
        runner.run(userId, futureBase, 30, false, NOW);
        CashflowForecastResult next = runner.run(userId, futureBase, 30, false, NOW.plusDays(1));

        assertThat(next.cached()).isFalse();
        assertThat(forecastRepository.findAll()).filteredOn(f -> f.getUserId().equals(userId)).hasSize(2);
    }

    @Test
    void forceRefreshCreatesNewForecastInsteadOfOverwritingTheCachedOne() {
        CashflowForecastResult first = runner.run(userId, BASE_DATE, 30, false, NOW);
        CashflowForecastResult refreshed = runner.run(userId, BASE_DATE, 30, true, NOW.plusMinutes(1));

        assertThat(refreshed.cached()).isFalse();
        assertThat(refreshed.forecast().getForecastId()).isNotEqualTo(first.forecast().getForecastId());
        assertThat(forecastRepository.findByForecastIdAndUserId(first.forecast().getForecastId(), userId))
                .get()
                .satisfies(kept -> assertThat(kept.getStatus()).isEqualTo(ForecastStatus.COMPLETED));
    }

    @Test
    void differentHorizonIsNotServedFromCache() {
        runner.run(userId, BASE_DATE, 30, false, NOW);
        CashflowForecastResult other = runner.run(userId, BASE_DATE, 60, false, NOW);

        assertThat(other.cached()).isFalse();
    }

    /** 명세의 중복 요청 방지. 진행 중인 계산이 있으면 forceRefresh로도 새로 돌리지 않는다. */
    @Test
    void inProgressForecastIsReusedEvenWithForceRefresh() {
        CashflowForecast pending = startPendingForecast();

        CashflowForecastResult result = runner.run(userId, BASE_DATE, 30, true, NOW);

        assertThat(result.cached()).isTrue();
        assertThat(result.forecast().getForecastId()).isEqualTo(pending.getForecastId());
        verify(eventCollector, never()).collect(eq(userId), any(), any());
    }

    /**
     * 계산 도중 프로세스가 내려가면 헤더가 `PENDING`으로 남는다. 만료가 없으면 그 요청 조합을 영원히
     * 다시 계산할 수 없다.
     */
    @Test
    void abandonedInProgressForecastDoesNotBlockRecalculationForever() {
        CashflowForecast abandoned = startPendingForecast();

        CashflowForecastResult result = runner.run(userId, BASE_DATE, 30, false, NOW.plusHours(1));

        assertThat(result.cached()).isFalse();
        assertThat(result.forecast().getForecastId()).isNotEqualTo(abandoned.getForecastId());
        assertThat(result.forecast().getStatus()).isEqualTo(ForecastStatus.COMPLETED);
    }

    @Test
    void marksForecastFailedWhenCollectionFails() {
        when(eventCollector.collect(any(), any(), any())).thenThrow(new IllegalStateException("원본 조회 실패"));

        assertThatThrownBy(() -> runner.run(userId, BASE_DATE, 30, false, NOW))
                .isInstanceOf(IllegalStateException.class);

        assertThat(forecastRepository.findAll()).filteredOn(f -> f.getUserId().equals(userId))
                .singleElement()
                .satisfies(failed -> {
                    assertThat(failed.getStatus()).isEqualTo(ForecastStatus.FAILED);
                    assertThat(dailyBalanceRepository.findOwnedByPeriod(
                            userId, failed.getForecastId(), BASE_DATE, BASE_DATE.plusDays(30))).isEmpty();
                });
    }

    @Test
    void collectionReasonsAreReportedButDoNotFailTheForecast() {
        when(eventCollector.collect(any(), any(), any())).thenReturn(new CashflowEventCollection(
                List.of(), List.of("ACCOUNT_SCHEDULE_SOURCE_UNAVAILABLE")));

        CashflowForecastResult result = runner.run(userId, BASE_DATE, 30, false, NOW);

        assertThat(result.forecast().getStatus()).isEqualTo(ForecastStatus.COMPLETED);
        assertThat(result.reasons()).containsExactly("ACCOUNT_SCHEDULE_SOURCE_UNAVAILABLE");
    }

    private CashflowForecast startPendingForecast() {
        String requestHash = CashflowRequestHash.of(
                userId,
                new CashflowForecastPeriodPolicy().resolve(BASE_DATE, 30, NOW.toLocalDate()),
                CashflowCalculationVersion.CURRENT);
        return store.start(userId, BASE_DATE, 30, requestHash, NOW);
    }

    private CashflowPlannedEvent plannedEvent(LocalDate date, long amount) {
        return new CashflowPlannedEvent(date, "CARD_PAYMENT", "카드대금",
                CashflowPlannedEvent.Direction.OUTFLOW, amount, "CARD_BILLING", "1",
                CashflowEventKeys.key(userId, "CARD_BILLING", "1", date.toString()));
    }
}
