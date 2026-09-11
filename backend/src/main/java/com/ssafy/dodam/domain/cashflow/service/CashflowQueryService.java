package com.ssafy.dodam.domain.cashflow.service;

import com.ssafy.dodam.domain.cashflow.dto.response.CashflowDailyBalanceListResponse;
import com.ssafy.dodam.domain.cashflow.dto.response.CashflowDailyBalanceResponse;
import com.ssafy.dodam.domain.cashflow.dto.response.CashflowEventListResponse;
import com.ssafy.dodam.domain.cashflow.dto.response.CashflowAvailableTodayResponse;
import com.ssafy.dodam.domain.cashflow.dto.response.CashflowEventResponse;
import com.ssafy.dodam.domain.cashflow.dto.response.CashflowRiskListResponse;
import com.ssafy.dodam.domain.cashflow.dto.response.CashflowRiskResponse;
import com.ssafy.dodam.domain.cashflow.dto.response.CashflowForecastResponse;
import com.ssafy.dodam.domain.cashflow.entity.CashflowForecast;
import com.ssafy.dodam.domain.cashflow.entity.CashflowRisk;
import com.ssafy.dodam.domain.cashflow.entity.ForecastStatus;
import com.ssafy.dodam.domain.cashflow.entity.RiskSeverity;
import com.ssafy.dodam.domain.cashflow.repository.CashflowDailyBalanceRepository;
import com.ssafy.dodam.domain.cashflow.repository.CashflowEventRepository;
import com.ssafy.dodam.domain.cashflow.repository.CashflowForecastRepository;
import com.ssafy.dodam.domain.cashflow.repository.CashflowRiskRepository;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.policy.PaymentSchedulePeriodPolicy;
import com.ssafy.dodam.global.policy.QueryPeriod;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 저장된 예측 결과의 조회.
 *
 * <p>명세에 `forecastId` 파라미터가 없으므로 세 조회 모두 **본인의 최신 완료 예측 1건**을 대상으로
 * 한다. `PENDING`·`RUNNING`·`FAILED` 예측은 하위행이 없거나 일부만 있는 상태라 확정값처럼 보여줄 수
 * 없어 제외한다.
 *
 * <p>소유권은 서비스가 아니라 쿼리에 있다(538). 여기서 얻은 `forecastId`도 하위행 조회에 `userId`와
 * 함께 넘겨 조인 조건으로 다시 확인한다.
 */
@Service
@Transactional(readOnly = true)
public class CashflowQueryService {


    /**
     * 위험 정렬: 심각도 내림차순 → 날짜 오름차순 → `riskId` 오름차순.
     *
     * <p>DB에서 정렬하지 않고 메모리에서 정렬한다. `severity`가 문자열 컬럼이라 SQL의 사전순
     * (`CRITICAL` < `HIGH` < `LOW` < `MEDIUM`)이 심각도순과 다르기 때문이다. enum 선언 순서를 쓰면
     * 정렬 기준이 코드값 정의 한 곳에만 남는다(538이 542로 미뤄 둔 결정).
     *
     * <p>`riskDate`는 NULL을 허용하므로 날짜 없는 위험을 뒤로 보낸다.
     */
    private static final Comparator<CashflowRisk> SEVERITY_FIRST =
            Comparator.comparing(CashflowRisk::getSeverity, Comparator.reverseOrder())
                    .thenComparing(
                            CashflowRisk::getRiskDate,
                            Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(CashflowRisk::getRiskId);

    private final CashflowForecastRepository forecastRepository;
    private final CashflowDailyBalanceRepository dailyBalanceRepository;
    private final CashflowEventRepository eventRepository;
    private final CashflowRiskRepository riskRepository;
    private final PaymentSchedulePeriodPolicy periodPolicy;

    public CashflowQueryService(
            CashflowForecastRepository forecastRepository,
            CashflowDailyBalanceRepository dailyBalanceRepository,
            CashflowEventRepository eventRepository,
            CashflowRiskRepository riskRepository,
            PaymentSchedulePeriodPolicy periodPolicy
    ) {
        this.forecastRepository = forecastRepository;
        this.dailyBalanceRepository = dailyBalanceRepository;
        this.eventRepository = eventRepository;
        this.riskRepository = riskRepository;
        this.periodPolicy = periodPolicy;
    }

    public CashflowForecastResponse getLatestForecast(Long userId, LocalDate baseDate) {
        return CashflowForecastResponse.from(findLatestCompleted(userId, baseDate));
    }

    public CashflowDailyBalanceListResponse getDailyBalances(
            Long userId, LocalDate startDate, LocalDate endDate) {
        CashflowForecast forecast = findLatestCompleted(userId, null);
        QueryPeriod period = periodPolicy.resolve(startDate, endDate);
        List<CashflowDailyBalanceResponse> balances = dailyBalanceRepository
                .findOwnedByPeriod(userId, forecast.getForecastId(), period.startDate(), period.endDate())
                .stream()
                .map(CashflowDailyBalanceResponse::from)
                .toList();
        return new CashflowDailyBalanceListResponse(
                CashflowForecastResponse.from(forecast),
                period.startDate(),
                period.endDate(),
                balances
        );
    }

    public CashflowEventListResponse getEvents(
            Long userId, LocalDate startDate, LocalDate endDate, String type) {
        CashflowForecast forecast = findLatestCompleted(userId, null);
        QueryPeriod period = periodPolicy.resolve(startDate, endDate);
        List<CashflowEventResponse> events = eventRepository
                .findOwnedByPeriod(
                        userId, forecast.getForecastId(), period.startDate(), period.endDate(), type)
                .stream()
                .map(CashflowEventResponse::from)
                .toList();
        return new CashflowEventListResponse(
                CashflowForecastResponse.from(forecast),
                period.startDate(),
                period.endDate(),
                type,
                events
        );
    }


    /**
     * 위험 목록. 명세의 선택 필터 `type`·`severity`는 null이면 조건에서 빠진다.
     *
     * <p>조회 기간 파라미터가 명세에 없으므로 <b>예측 구간 전체</b>의 위험을 돌려준다. 위험 행은 예측
     * 1건당 예정 출금 건수를 넘지 않아(대부분 수십 건) 페이지를 나누지 않는다.
     */
    public CashflowRiskListResponse getRisks(Long userId, String type, RiskSeverity severity) {
        CashflowForecast forecast = findLatestCompleted(userId, null);
        List<CashflowRiskResponse> risks = riskRepository
                .findOwned(userId, forecast.getForecastId(), type, severity)
                .stream()
                .sorted(SEVERITY_FIRST)
                .map(CashflowRiskResponse::from)
                .toList();
        return new CashflowRiskListResponse(
                CashflowForecastResponse.from(forecast), type, severity, risks);
    }

    /**
     * 오늘 사용가능금액. 저장된 집계값을 그대로 읽고 여기서 다시 계산하지 않는다. 조회 시점에 계산하면
     * 같은 예측을 두 번 조회했을 때 값이 달라질 수 있고, `daily-balances`와도 어긋난다.
     */
    public CashflowAvailableTodayResponse getAvailableToday(Long userId, LocalDate baseDate) {
        return CashflowAvailableTodayResponse.from(
                CashflowForecastResponse.from(findLatestCompleted(userId, baseDate)));
    }

    /**
     * 조회 대상 예측을 고른다.
     *
     * <p>기준일을 지정하지 않으면 **가장 마지막에 완료된 예측**을 고른다. 기준일 내림차순으로 고르면
     * 미래 기준일로 한 번 실행해 둔 예측이 계속 최신이 되어 오늘부터의 흐름을 조회할 수 없다. 특정
     * 기준일의 결과가 필요하면 `latest`에 `baseDate`를 지정한다.
     *
     * <p>예측이 없을 때와 타인 예측일 때를 같은 `RESOURCE_NOT_FOUND`로 처리해 존재 여부를
     * 구분해 노출하지 않는다.
     */
    private CashflowForecast findLatestCompleted(Long userId, LocalDate baseDate) {
        return (baseDate == null
                ? forecastRepository.findFirstByUserIdAndStatusOrderByCompletedAtDescForecastIdDesc(
                        userId, ForecastStatus.COMPLETED)
                : forecastRepository
                        .findFirstByUserIdAndBaseDateAndStatusOrderByCompletedAtDescForecastIdDesc(
                                userId, baseDate, ForecastStatus.COMPLETED))
                .orElseThrow(() -> new ResourceNotFoundException("완료된 현금흐름 예측이 없습니다."));
    }
}
