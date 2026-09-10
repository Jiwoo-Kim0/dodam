package com.ssafy.dodam.domain.cashflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.dodam.domain.cashflow.entity.CashflowDailyBalance;
import com.ssafy.dodam.domain.cashflow.entity.CashflowEvent;
import com.ssafy.dodam.domain.cashflow.entity.CashflowForecast;
import com.ssafy.dodam.domain.cashflow.entity.CashflowRisk;
import com.ssafy.dodam.domain.cashflow.entity.EventDirection;
import com.ssafy.dodam.domain.cashflow.entity.ForecastStatus;
import com.ssafy.dodam.domain.cashflow.entity.RiskSeverity;
import com.ssafy.dodam.domain.cashflow.repository.CashflowDailyBalanceRepository;
import com.ssafy.dodam.domain.cashflow.repository.CashflowEventRepository;
import com.ssafy.dodam.domain.cashflow.repository.CashflowForecastRepository;
import com.ssafy.dodam.domain.cashflow.repository.CashflowRiskRepository;
import com.ssafy.dodam.domain.cashflow.service.CashflowCalculationVersion;
import com.ssafy.dodam.domain.cashflow.service.CashflowForecastStore;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 예측 저장 경로 테스트.
 *
 * <p>H2 프로필은 Flyway를 끄고 엔티티로 스키마를 만들기 때문에 MySQL CHECK와 유니크 제약은 여기서
 * 검증되지 않는다. 그 부분은 {@link CashflowMySqlIntegrationTest}가 맡는다.
 */
@ActiveProfiles("test")
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:cashflow538;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class CashflowForecastStoreTest {

    private static final Long USER_ID = 5380001L;
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 9, 10);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 10, 9, 0);

    @Autowired private CashflowForecastStore store;
    @Autowired private CashflowForecastRepository forecastRepository;
    @Autowired private CashflowDailyBalanceRepository dailyBalanceRepository;
    @Autowired private CashflowEventRepository eventRepository;
    @Autowired private CashflowRiskRepository riskRepository;

    @Test
    void startCreatesPendingForecastWithCalculationVersion() {
        CashflowForecast forecast = store.start(USER_ID, BASE_DATE, 30, "hash-start", NOW);

        assertThat(forecast.getForecastId()).isNotNull();
        assertThat(forecast.getStatus()).isEqualTo(ForecastStatus.PENDING);
        assertThat(forecast.getCalculationVersion()).isEqualTo(CashflowCalculationVersion.CURRENT);
        assertThat(forecast.getCompletedAt()).isNull();
        assertThat(forecast.getCurrentBalance()).isNull();
    }

    @Test
    void completeStoresChildRowsAndAggregates() {
        CashflowForecast forecast = store.start(USER_ID, BASE_DATE, 3, "hash-complete", NOW);
        Long forecastId = forecast.getForecastId();

        store.complete(
                forecast,
                List.of(
                        CashflowDailyBalance.of(forecastId, BASE_DATE, 1_000_000L),
                        CashflowDailyBalance.of(forecastId, BASE_DATE.plusDays(1), -50_000L)),
                List.of(event(forecastId, BASE_DATE.plusDays(1), "CARD_BILLING", "카드대금", "dedupe-1")),
                List.of(CashflowRisk.of(
                        forecastId, BASE_DATE.plusDays(1), "NEGATIVE_BALANCE",
                        RiskSeverity.HIGH, "잔액이 마이너스가 됩니다.", -50_000L)),
                1_000_000L, 900_000L, -50_000L, BASE_DATE.plusDays(1), NOW.plusMinutes(1));

        CashflowForecast saved = forecastRepository.findByForecastIdAndUserId(forecastId, USER_ID).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ForecastStatus.COMPLETED);
        assertThat(saved.getCompletedAt()).isEqualTo(NOW.plusMinutes(1));
        assertThat(saved.getMinimumBalance()).isEqualTo(-50_000L);
        assertThat(saved.getRiskDate()).isEqualTo(BASE_DATE.plusDays(1));

        // 음수 예상잔액을 0으로 절삭하지 않는지 함께 확인한다.
        assertThat(dailyBalanceRepository.findOwnedByPeriod(
                        USER_ID, forecastId, BASE_DATE, BASE_DATE.plusDays(7)))
                .extracting(CashflowDailyBalance::getExpectedBalance)
                .containsExactly(1_000_000L, -50_000L);
        assertThat(eventRepository.findOwnedByPeriod(
                        USER_ID, forecastId, BASE_DATE, BASE_DATE.plusDays(7), null))
                .hasSize(1);
        assertThat(riskRepository.findOwned(USER_ID, forecastId, null, null)).hasSize(1);
    }

    @Test
    void completeKeepsOnlyFirstEventPerDedupeKey() {
        CashflowForecast forecast = store.start(USER_ID, BASE_DATE, 3, "hash-dedupe", NOW);
        Long forecastId = forecast.getForecastId();

        store.complete(
                forecast,
                List.of(),
                List.of(
                        event(forecastId, BASE_DATE, "SUBSCRIPTION", "구독료", "same-key"),
                        event(forecastId, BASE_DATE, "SUBSCRIPTION", "같은 구독료 재유입", "same-key"),
                        event(forecastId, BASE_DATE, "SUBSCRIPTION", "다른 구독료", "other-key")),
                List.of(), null, null, null, null, NOW);

        assertThat(eventRepository.findOwnedByPeriod(
                        USER_ID, forecastId, BASE_DATE, BASE_DATE.plusDays(7), null))
                .extracting(CashflowEvent::getName)
                .containsExactly("구독료", "다른 구독료");
    }

    @Test
    void recalculationStartsNewForecastAndCompletedOneIsImmutable() {
        CashflowForecast forecast = store.start(USER_ID, BASE_DATE, 3, "hash-replace", NOW);
        Long forecastId = forecast.getForecastId();
        store.complete(
                forecast,
                List.of(CashflowDailyBalance.of(forecastId, BASE_DATE, 100L)),
                List.of(event(forecastId, BASE_DATE, "CARD_BILLING", "이전 결과", "dedupe-old")),
                List.of(), 100L, 100L, 100L, null, NOW);

        // 완료된 예측을 덮어써 조회 결과가 조용히 바뀌지 않는지 확인한다.
        CashflowForecast reloaded = forecastRepository.findByForecastIdAndUserId(forecastId, USER_ID).orElseThrow();
        assertThatThrownBy(() -> reloaded.complete(200L, 200L, 200L, null, NOW))
                .isInstanceOf(IllegalStateException.class);

        CashflowForecast retry = store.start(USER_ID, BASE_DATE, 3, "hash-replace", NOW.plusHours(1));
        store.complete(
                retry,
                List.of(CashflowDailyBalance.of(retry.getForecastId(), BASE_DATE, 200L)),
                List.of(event(retry.getForecastId(), BASE_DATE, "CARD_BILLING", "새 결과", "dedupe-old")),
                List.of(), 200L, 200L, 200L, null, NOW.plusHours(1));

        assertThat(dailyBalanceRepository.findOwnedByPeriod(
                        USER_ID, retry.getForecastId(), BASE_DATE, BASE_DATE.plusDays(7)))
                .extracting(CashflowDailyBalance::getExpectedBalance)
                .containsExactly(200L);
        // 기준일이 같아도 최신 실행이 조회된다.
        assertThat(forecastRepository
                        .findFirstByUserIdAndBaseDateAndStatusOrderByCreatedAtDesc(
                                USER_ID, BASE_DATE, ForecastStatus.COMPLETED)
                        .orElseThrow()
                        .getForecastId())
                .isEqualTo(retry.getForecastId());
    }

    @Test
    void completeReplacesChildRowsOfSameForecast() {
        CashflowForecast forecast = store.start(USER_ID, BASE_DATE, 3, "hash-retry", NOW);
        Long forecastId = forecast.getForecastId();
        dailyBalanceRepository.save(CashflowDailyBalance.of(forecastId, BASE_DATE, 111L));

        // 계산 중간에 남은 하위행이 있어도 완료 저장이 이를 교체한다.
        store.complete(
                forecast,
                List.of(CashflowDailyBalance.of(forecastId, BASE_DATE, 222L)),
                List.of(), List.of(), 222L, 222L, 222L, null, NOW);

        assertThat(dailyBalanceRepository.findOwnedByPeriod(
                        USER_ID, forecastId, BASE_DATE, BASE_DATE.plusDays(7)))
                .extracting(CashflowDailyBalance::getExpectedBalance)
                .containsExactly(222L);
    }

    @Test
    void failRemovesPartialChildRows() {
        CashflowForecast forecast = store.start(USER_ID, BASE_DATE, 3, "hash-fail", NOW);
        Long forecastId = forecast.getForecastId();
        dailyBalanceRepository.save(CashflowDailyBalance.of(forecastId, BASE_DATE, 999L));

        store.fail(forecast, NOW.plusMinutes(5));

        CashflowForecast saved = forecastRepository.findByForecastIdAndUserId(forecastId, USER_ID).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ForecastStatus.FAILED);
        assertThat(saved.getCompletedAt()).isEqualTo(NOW.plusMinutes(5));
        assertThat(dailyBalanceRepository.findOwnedByPeriod(
                        USER_ID, forecastId, BASE_DATE, BASE_DATE.plusDays(7)))
                .isEmpty();
    }

    @Test
    void horizonDaysMustBePositive() {
        assertThatThrownBy(() -> store.start(USER_ID, BASE_DATE, 0, null, NOW))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void eventRejectsNonPositiveAmountAndBlankDedupeKey() {
        assertThatThrownBy(() -> CashflowEvent.of(
                        1L, BASE_DATE, "CARD_BILLING", "카드대금", EventDirection.OUTFLOW, 0L, null, null, "key"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CashflowEvent.of(
                        1L, BASE_DATE, "CARD_BILLING", "카드대금", EventDirection.OUTFLOW, 1L, null, null, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void signedAmountAppliesDirectionOnly() {
        CashflowEvent outflow = event(1L, BASE_DATE, "CARD_BILLING", "카드대금", "key-out");
        CashflowEvent inflow = CashflowEvent.of(
                1L, BASE_DATE, "SALARY", "급여", EventDirection.INFLOW, 3_000_000L, null, null, "key-in");

        assertThat(outflow.getAmount()).isPositive();
        assertThat(outflow.signedAmount()).isEqualTo(-500_000L);
        assertThat(inflow.signedAmount()).isEqualTo(3_000_000L);
    }

    private CashflowEvent event(Long forecastId, LocalDate date, String type, String name, String dedupeKey) {
        return CashflowEvent.of(
                forecastId, date, type, name, EventDirection.OUTFLOW, 500_000L, "CARD", "1", dedupeKey);
    }
}
