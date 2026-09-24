package com.ssafy.dodam.domain.forecast.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ssafy.dodam.domain.forecast.dto.response.SpendingForecastResponse;
import com.ssafy.dodam.domain.forecast.repository.JdbcSpendingForecastReader;
import com.ssafy.dodam.domain.forecast.service.SpendingForecastCalculator.MonthlyPoint;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.time.Clock;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 조회 서비스(S15P21D105-706). Spring 컨텍스트를 새로 만들지 않는다. 이 저장소는 이미 서로 다른 H2 URL 33개를 쓰고 있어
 * 컨텍스트 캐시 한계(32)에 닿아 있고, 한 개를 더 만들면 밀려난 컨텍스트가 공유 H2에 create-drop을 돌려 무관한 테스트가
 * 무더기로 깨진다. SQL 자체는 MySQL에 적재한 데이터로 따로 확인했다(계획서 7.1).
 */
class SpendingForecastQueryServiceTest {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final Long USER_ID = 7L;

    private JdbcSpendingForecastReader reader;
    private SpendingForecastQueryService service;

    @BeforeEach
    void setUp() {
        reader = mock(JdbcSpendingForecastReader.class);
        SpendingForecastClock clock = new SpendingForecastClock(
                Clock.fixed(Instant.parse("2026-09-24T01:00:00Z"), SEOUL));
        service = new SpendingForecastQueryService(reader, clock);
    }

    /** 2024-09 ~ 2026-08, 매달 같은 금액에 완만한 증가 */
    private static List<MonthlyPoint> twentyFourMonths() {
        List<MonthlyPoint> points = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            points.add(new MonthlyPoint(YearMonth.of(2024, 9).plusMonths(i), 900_000L + i * 5_000L));
        }
        return points;
    }

    @Test
    @DisplayName("추정 입력은 완료된 달 24개다. 오늘이 속한 달은 넣지 않는다")
    void readsTwentyFourCompletedMonths() {
        given(reader.readMonthlyVariableSpending(eq(USER_ID), eq(YearMonth.of(2024, 9)), eq(YearMonth.of(2026, 8))))
                .willReturn(twentyFourMonths());

        SpendingForecastResponse response = service.forecast(USER_ID, null);

        ArgumentCaptor<YearMonth> from = ArgumentCaptor.forClass(YearMonth.class);
        ArgumentCaptor<YearMonth> to = ArgumentCaptor.forClass(YearMonth.class);
        org.mockito.Mockito.verify(reader).readMonthlyVariableSpending(eq(USER_ID), from.capture(), to.capture());
        assertThat(from.getValue()).isEqualTo(YearMonth.of(2024, 9));
        assertThat(to.getValue()).isEqualTo(YearMonth.of(2026, 8));
        assertThat(response.baseMonth()).isEqualTo("2026-09");
    }

    @Test
    @DisplayName("예측은 기준 달 다음 달부터 시작한다")
    void forecastsFromNextMonth() {
        given(reader.readMonthlyVariableSpending(eq(USER_ID), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).willReturn(twentyFourMonths());

        SpendingForecastResponse response = service.forecast(USER_ID, 3);

        assertThat(response.forecasts()).extracting(SpendingForecastResponse.ForecastPoint::month)
                .containsExactly("2026-10", "2026-11", "2026-12");
        assertThat(response.fallbackReason()).isNull();
    }

    @Test
    @DisplayName("추정에는 24개월을 쓰되 화면에는 최근 12개월만 돌려준다")
    void returnsTwelveMonthsOfHistory() {
        given(reader.readMonthlyVariableSpending(eq(USER_ID), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).willReturn(twentyFourMonths());

        SpendingForecastResponse response = service.forecast(USER_ID, null);

        assertThat(response.history()).hasSize(12);
        assertThat(response.history().get(0).month()).isEqualTo("2025-09");
        assertThat(response.history().get(11).month()).isEqualTo("2026-08");
    }

    @Test
    @DisplayName("이력이 없으면 예측하지 않고 사유를 싣는다")
    void reportsFallbackWhenHistoryIsEmpty() {
        given(reader.readMonthlyVariableSpending(eq(USER_ID), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).willReturn(List.of());

        SpendingForecastResponse response = service.forecast(USER_ID, null);

        assertThat(response.fallbackReason()).isEqualTo(ForecastFallbackReason.FEATURES_UNAVAILABLE.name());
        assertThat(response.forecasts()).isEmpty();
        assertThat(response.history()).isEmpty();
    }

    @Test
    @DisplayName("horizonMonths는 비어 있으면 3, 1~12 밖이면 400이다")
    void validatesHorizon() {
        assertThat(SpendingForecastQueryService.resolveHorizon(null)).isEqualTo(3);
        assertThat(SpendingForecastQueryService.resolveHorizon(1)).isEqualTo(1);
        assertThat(SpendingForecastQueryService.resolveHorizon(12)).isEqualTo(12);
        assertThatThrownBy(() -> SpendingForecastQueryService.resolveHorizon(0))
                .isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> SpendingForecastQueryService.resolveHorizon(13))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    @DisplayName("구간은 예측값을 감싸고 음수로 내려가지 않는다")
    void boundsAreUsable() {
        given(reader.readMonthlyVariableSpending(eq(USER_ID), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).willReturn(twentyFourMonths());

        SpendingForecastResponse response = service.forecast(USER_ID, 12);

        assertThat(response.forecasts()).hasSize(12).allSatisfy(point -> {
            assertThat(point.lowerBound()).isNotNegative().isLessThanOrEqualTo(point.predicted());
            assertThat(point.upperBound()).isGreaterThanOrEqualTo(point.predicted());
        });
    }
}
