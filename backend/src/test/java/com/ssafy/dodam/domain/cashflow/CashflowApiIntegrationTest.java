package com.ssafy.dodam.domain.cashflow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.ssafy.dodam.domain.cashflow.dto.CashflowEventCollection;
import com.ssafy.dodam.domain.cashflow.dto.CashflowPlannedEvent;
import com.ssafy.dodam.domain.cashflow.repository.CashflowBalanceReader;
import com.ssafy.dodam.domain.cashflow.repository.CashflowDailyBalanceRepository;
import com.ssafy.dodam.domain.cashflow.repository.CashflowEventRepository;
import com.ssafy.dodam.domain.cashflow.repository.CashflowForecastRepository;
import com.ssafy.dodam.domain.cashflow.repository.CashflowRiskRepository;
import com.ssafy.dodam.domain.cashflow.service.CashflowEventCollector;
import com.ssafy.dodam.global.policy.PaymentSchedulePeriodPolicy;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 실행·조회 API의 HTTP 계약 테스트.
 *
 * <p>원본 수집(539)과 시작 잔액 조회는 대역으로 바꾼다. 변환 규칙은 539·540 테스트가 검증하며,
 * 여기서 확인할 것은 인증·소유권·입력 검증·기간 기본값과 응답 계약이다.
 *
 * <p>기준일이 오늘 이후여야 하므로 고정 날짜를 쓸 수 없다. 모든 날짜는 서비스 타임존의 오늘을
 * 기준으로 만든다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:cashflow541api;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class CashflowApiIntegrationTest {

    private static final String BASE = "/api/v1/cashflow";
    private static final String OWNER = "5410101";
    private static final String OTHER = "5410102";
    private static final long STARTING_BALANCE = 1_000_000L;
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("Asia/Seoul"));

    @Autowired private MockMvc mvc;
    @Autowired private CashflowForecastRepository forecastRepository;
    @Autowired private CashflowDailyBalanceRepository dailyBalanceRepository;
    @Autowired private CashflowEventRepository eventRepository;
    @Autowired private CashflowRiskRepository riskRepository;

    @MockitoBean private CashflowBalanceReader balanceReader;
    @MockitoBean private CashflowEventCollector eventCollector;

    @BeforeEach
    void setUp() {
        // 하위행을 먼저 지운다. 실제 스키마에는 예측을 가리키는 외래키가 있어 순서를 뒤집으면 지워지지 않는다.
        dailyBalanceRepository.deleteAll();
        eventRepository.deleteAll();
        riskRepository.deleteAll();
        forecastRepository.deleteAll();

        when(balanceReader.readStartingBalance(anyLong())).thenReturn(STARTING_BALANCE);
        when(eventCollector.collect(anyLong(), any(), any())).thenReturn(new CashflowEventCollection(
                List.of(
                        plannedEvent(TODAY.plusDays(1), "CARD_PAYMENT", "카드대금", 300_000L, 1),
                        plannedEvent(TODAY.plusDays(2), "SAVINGS_PAYMENT", "적금 납입", 200_000L, 2)
                ),
                List.of("SUBSCRIPTION_SOURCE_UNAVAILABLE")
        ));
    }

    @Test
    @WithMockUser(username = OWNER)
    void runThenQueriesShareTheSameForecast() throws Exception {
        int forecastId = runForecast(OWNER, "{}", false);

        mvc.perform(get(BASE + "/forecasts/latest")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.forecastId").value(forecastId))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.baseDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.data.horizonDays").value(90))
                .andExpect(jsonPath("$.data.endDate").value(TODAY.plusDays(89).toString()))
                .andExpect(jsonPath("$.data.currentBalance").value(STARTING_BALANCE))
                .andExpect(jsonPath("$.data.minimumBalance").value(500_000));

        // 기간을 지정하지 않으면 공통 결제예정 정책의 기본값(오늘부터 3개월)이며, 값이 있는 범위는
        // 예측 구간(90일)까지다.
        mvc.perform(get(BASE + "/daily-balances")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.forecast.forecastId").value(forecastId))
                .andExpect(jsonPath("$.data.startDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.data.balances", hasSize(90)))
                .andExpect(jsonPath("$.data.balances[0].balanceDate").value(TODAY.toString()))
                .andExpect(jsonPath("$.data.balances[0].expectedBalance").value(STARTING_BALANCE))
                .andExpect(jsonPath("$.data.balances[1].expectedBalance").value(700_000))
                .andExpect(jsonPath("$.data.balances[2].expectedBalance").value(500_000));

        mvc.perform(get(BASE + "/events")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.forecast.forecastId").value(forecastId))
                .andExpect(jsonPath("$.data.events", hasSize(2)))
                .andExpect(jsonPath("$.data.events[0].type").value("CARD_PAYMENT"))
                .andExpect(jsonPath("$.data.events[0].direction").value("OUTFLOW"))
                .andExpect(jsonPath("$.data.events[0].amount").value(300_000))
                .andExpect(jsonPath("$.data.events[1].type").value("SAVINGS_PAYMENT"));
    }

    @Test
    @WithMockUser(username = OWNER)
    void rerunReusesCacheAndForceRefreshCreatesNewForecast() throws Exception {
        int first = runForecast(OWNER, "{}", false);
        int cached = runForecast(OWNER, "{}", true);
        int refreshed = runForecast(OWNER, "{\"forceRefresh\":true}", false);

        assertThat(cached).isEqualTo(first);
        assertThat(refreshed).isNotEqualTo(first);

        // 같은 기준일로 여러 번 실행하면 마지막 실행이 최신이다.
        mvc.perform(get(BASE + "/forecasts/latest")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.forecastId").value(refreshed));
        mvc.perform(get(BASE + "/forecasts/latest").param("baseDate", TODAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.forecastId").value(refreshed));
    }

    @Test
    @WithMockUser(username = OWNER)
    void latestFollowsCompletionTimeNotBaseDate() throws Exception {
        int future = runForecast(OWNER, "{\"baseDate\":\"" + TODAY.plusDays(5) + "\"}", false);
        int today = runForecast(OWNER, "{}", false);

        // 미래 기준일 예측을 먼저 돌려 두어도 나중에 완료된 오늘 기준 예측이 최신이다.
        mvc.perform(get(BASE + "/forecasts/latest")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.forecastId").value(today))
                .andExpect(jsonPath("$.data.baseDate").value(TODAY.toString()));
        mvc.perform(get(BASE + "/daily-balances")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.forecast.forecastId").value(today))
                .andExpect(jsonPath("$.data.balances[0].balanceDate").value(TODAY.toString()));

        // 특정 기준일의 결과가 필요하면 baseDate로 지정한다.
        mvc.perform(get(BASE + "/forecasts/latest").param("baseDate", TODAY.plusDays(5).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.forecastId").value(future));
    }

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mvc.perform(get(BASE + "/forecasts/latest")).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/daily-balances")).andExpect(status().isUnauthorized());
        mvc.perform(get(BASE + "/events")).andExpect(status().isUnauthorized());
        mvc.perform(post(BASE + "/forecasts").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = OTHER)
    void otherUsersCannotSeeAnotherForecastAndGetTheSameNotFound() throws Exception {
        runForecast(OWNER, "{}", false);

        // 예측을 한 번도 실행하지 않은 사용자와 타인 예측만 존재하는 사용자의 응답이 같아야 한다.
        mvc.perform(get(BASE + "/forecasts/latest")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(get(BASE + "/daily-balances")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(get(BASE + "/events")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = OWNER)
    void invalidInputsAreRejectedBeforeCalculation() throws Exception {
        mvc.perform(post(BASE + "/forecasts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horizonDays\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post(BASE + "/forecasts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"horizonDays\":366}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(post(BASE + "/forecasts").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"baseDate\":\"" + TODAY.minusDays(1) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

        runForecast(OWNER, "{}", false);
        mvc.perform(get(BASE + "/daily-balances")
                        .param("startDate", TODAY.plusDays(5).toString())
                        .param("endDate", TODAY.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get(BASE + "/events").param("type", "card-payment"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @WithMockUser(username = OWNER)
    void filterAndOutOfRangePeriodReturnEmptyLists() throws Exception {
        int forecastId = runForecast(OWNER, "{\"horizonDays\":10}", false);

        mvc.perform(get(BASE + "/events").param("type", "CARD_PAYMENT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("CARD_PAYMENT"))
                .andExpect(jsonPath("$.data.events", hasSize(1)))
                .andExpect(jsonPath("$.data.events[0].type").value("CARD_PAYMENT"));

        // 형식이 맞지만 저장된 적 없는 유형은 오류가 아니라 빈 결과다. 계좌 어댑터가 자체 유형을
        // 지정하는 계약이라 값 집합을 서버가 고정하지 않는다.
        mvc.perform(get(BASE + "/events").param("type", "UNKNOWN_TYPE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events", hasSize(0)));

        // 예측 구간(10일) 밖은 계산된 적이 없는 날짜다. 404가 아니라 빈 목록이며 예측 헤더는 남는다.
        mvc.perform(get(BASE + "/daily-balances")
                        .param("startDate", TODAY.plusDays(30).toString())
                        .param("endDate", TODAY.plusDays(40).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.forecast.forecastId").value(forecastId))
                .andExpect(jsonPath("$.data.forecast.endDate").value(TODAY.plusDays(9).toString()))
                .andExpect(jsonPath("$.data.balances", hasSize(0)));

        // 상한을 넘는 기간은 공통 정책이 막는다.
        mvc.perform(get(BASE + "/events")
                        .param("startDate", TODAY.toString())
                        .param("endDate",
                                TODAY.plusMonths(PaymentSchedulePeriodPolicy.MAX_PERIOD_MONTHS).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    /** 실행 사용자를 명시한다. 타인 격리 테스트에서는 클래스 기본 사용자와 다른 사용자로 실행해야 한다. */
    private int runForecast(String username, String body, boolean expectedCached) throws Exception {
        String response = mvc.perform(post(BASE + "/forecasts").with(user(username))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cached").value(expectedCached))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.data.forecast.forecastId");
    }

    private CashflowPlannedEvent plannedEvent(
            LocalDate date, String type, String name, long amount, int keySeed) {
        return new CashflowPlannedEvent(
                date, type, name, CashflowPlannedEvent.Direction.OUTFLOW, amount,
                "TEST", Integer.toString(keySeed), String.format("%064x", keySeed));
    }
}
