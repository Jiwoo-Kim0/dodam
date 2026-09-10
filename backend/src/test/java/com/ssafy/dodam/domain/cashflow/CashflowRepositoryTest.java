package com.ssafy.dodam.domain.cashflow;

import static org.assertj.core.api.Assertions.assertThat;

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
import com.ssafy.dodam.domain.cashflow.service.CashflowForecastStore;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 조회 계약 테스트. 소유권 격리와 선택 필터, 빈 결과를 확인한다.
 *
 * <p>하위 3테이블에는 `user_id`가 없어 소유권이 상위 예측과의 조인에만 의존한다. 타인의
 * `forecastId`를 그대로 넘겼을 때 빈 결과가 나오는지가 이 테스트의 핵심이다.
 */
@ActiveProfiles("test")
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:cashflow538read;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class CashflowRepositoryTest {

    private static final Long OWNER = 5380101L;
    private static final Long OTHER = 5380102L;
    private static final LocalDate BASE_DATE = LocalDate.of(2026, 9, 10);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 10, 9, 0);

    @Autowired private CashflowForecastStore store;
    @Autowired private CashflowForecastRepository forecastRepository;
    @Autowired private CashflowDailyBalanceRepository dailyBalanceRepository;
    @Autowired private CashflowEventRepository eventRepository;
    @Autowired private CashflowRiskRepository riskRepository;

    private Long ownerForecastId;

    @BeforeEach
    void setUp() {
        // 하위행을 먼저 지운다. 실제 스키마에는 예측을 가리키는 외래키가 있어 순서를 뒤집으면 지워지지 않는다.
        dailyBalanceRepository.deleteAll();
        eventRepository.deleteAll();
        riskRepository.deleteAll();
        forecastRepository.deleteAll();
        ownerForecastId = fixture(OWNER, "hash-owner");
    }

    @Test
    void dailyBalancesAreOrderedAndLimitedToRequestedPeriod() {
        assertThat(dailyBalanceRepository.findOwnedByPeriod(
                        OWNER, ownerForecastId, BASE_DATE, BASE_DATE.plusDays(1)))
                .extracting(CashflowDailyBalance::getBalanceDate)
                .containsExactly(BASE_DATE, BASE_DATE.plusDays(1));
    }

    @Test
    void eventTypeFilterIsOptional() {
        assertThat(eventRepository.findOwnedByPeriod(
                        OWNER, ownerForecastId, BASE_DATE, BASE_DATE.plusDays(7), null))
                .hasSize(2);
        assertThat(eventRepository.findOwnedByPeriod(
                        OWNER, ownerForecastId, BASE_DATE, BASE_DATE.plusDays(7), "SALARY"))
                .extracting(CashflowEvent::getName)
                .containsExactly("급여");
        assertThat(eventRepository.findOwnedByPeriod(
                        OWNER, ownerForecastId, BASE_DATE, BASE_DATE.plusDays(7), "UNKNOWN_TYPE"))
                .isEmpty();
    }

    @Test
    void riskFiltersAreOptional() {
        assertThat(riskRepository.findOwned(OWNER, ownerForecastId, null, null)).hasSize(2);
        assertThat(riskRepository.findOwned(OWNER, ownerForecastId, null, RiskSeverity.CRITICAL))
                .extracting(CashflowRisk::getType)
                .containsExactly("NEGATIVE_BALANCE");
        assertThat(riskRepository.findOwned(OWNER, ownerForecastId, "LOW_BUFFER", null))
                .extracting(CashflowRisk::getSeverity)
                .containsExactly(RiskSeverity.LOW);
        assertThat(riskRepository.findOwned(OWNER, ownerForecastId, "LOW_BUFFER", RiskSeverity.CRITICAL))
                .isEmpty();
    }

    @Test
    void otherUsersForecastIsNotReadable() {
        Long otherForecastId = fixture(OTHER, "hash-other");

        assertThat(forecastRepository.findByForecastIdAndUserId(otherForecastId, OWNER)).isEmpty();
        assertThat(dailyBalanceRepository.findOwnedByPeriod(
                        OWNER, otherForecastId, BASE_DATE, BASE_DATE.plusDays(7)))
                .isEmpty();
        assertThat(eventRepository.findOwnedByPeriod(
                        OWNER, otherForecastId, BASE_DATE, BASE_DATE.plusDays(7), null))
                .isEmpty();
        assertThat(riskRepository.findOwned(OWNER, otherForecastId, null, null)).isEmpty();
    }

    @Test
    void latestForecastLookupsIgnoreOtherUsersAndIncompleteRuns() {
        fixture(OTHER, "hash-other");
        // 완료되지 않은 실행은 최신 조회 대상이 아니다.
        store.start(OWNER, BASE_DATE.plusDays(1), 30, "hash-pending", NOW.plusHours(2));

        assertThat(forecastRepository
                        .findFirstByUserIdAndStatusOrderByBaseDateDescCreatedAtDesc(
                                OWNER, ForecastStatus.COMPLETED)
                        .orElseThrow()
                        .getForecastId())
                .isEqualTo(ownerForecastId);
        assertThat(forecastRepository.findFirstByUserIdAndRequestHashAndStatusOrderByCreatedAtDesc(
                        OWNER, "hash-other", ForecastStatus.COMPLETED))
                .isEmpty();
        assertThat(forecastRepository.findFirstByUserIdAndRequestHashAndStatusOrderByCreatedAtDesc(
                        OWNER, "hash-owner", ForecastStatus.COMPLETED))
                .isPresent();
    }

    @Test
    void emptyPeriodReturnsEmptyList() {
        assertThat(dailyBalanceRepository.findOwnedByPeriod(
                        OWNER, ownerForecastId, BASE_DATE.plusYears(1), BASE_DATE.plusYears(1).plusDays(7)))
                .isEmpty();
    }

    private Long fixture(Long userId, String requestHash) {
        CashflowForecast forecast = store.start(userId, BASE_DATE, 3, requestHash, NOW);
        Long forecastId = forecast.getForecastId();
        store.complete(
                forecast,
                List.of(
                        CashflowDailyBalance.of(forecastId, BASE_DATE, 1_000_000L),
                        CashflowDailyBalance.of(forecastId, BASE_DATE.plusDays(1), 500_000L),
                        CashflowDailyBalance.of(forecastId, BASE_DATE.plusDays(2), -10_000L)),
                List.of(
                        CashflowEvent.of(forecastId, BASE_DATE, "SALARY", "급여",
                                EventDirection.INFLOW, 3_000_000L, "ACCOUNT", "1", requestHash + "-salary"),
                        CashflowEvent.of(forecastId, BASE_DATE.plusDays(2), "CARD_BILLING", "카드대금",
                                EventDirection.OUTFLOW, 510_000L, "CARD", "2", requestHash + "-card")),
                List.of(
                        CashflowRisk.of(forecastId, BASE_DATE.plusDays(2), "NEGATIVE_BALANCE",
                                RiskSeverity.CRITICAL, "잔액이 마이너스가 됩니다.", -10_000L),
                        CashflowRisk.of(forecastId, null, "LOW_BUFFER",
                                RiskSeverity.LOW, "여유 자금이 적습니다.", null)),
                1_000_000L, 900_000L, -10_000L, BASE_DATE.plusDays(2), NOW.plusMinutes(1));
        return forecastId;
    }
}
