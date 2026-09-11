package com.ssafy.dodam.domain.cashflow.repository;

import com.ssafy.dodam.domain.cashflow.entity.CashflowForecast;
import com.ssafy.dodam.domain.cashflow.entity.ForecastStatus;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 예측 헤더 조회.
 *
 * <p>모든 조회에 `userId` 조건을 포함한다. 조건을 서비스에 두면 타인 예측의 존재 여부가 응답
 * 차이로 드러날 수 있어 쿼리 단계에서 막는다.
 */
public interface CashflowForecastRepository extends JpaRepository<CashflowForecast, Long> {

    Optional<CashflowForecast> findByForecastIdAndUserId(Long forecastId, Long userId);

    /** 기준일을 지정한 최신 예측. 같은 기준일로 여러 번 실행하면 마지막에 완료된 실행이 유효하다. */
    Optional<CashflowForecast> findFirstByUserIdAndBaseDateAndStatusOrderByCompletedAtDescForecastIdDesc(
            Long userId,
            LocalDate baseDate,
            ForecastStatus status
    );

    /**
     * 기준일을 지정하지 않은 최신 예측. **가장 마지막에 완료된 실행**을 고르며 기준일은 보지 않는다.
     *
     * <p>기준일 내림차순으로 고르면 미래 기준일로 한 번 실행해 둔 예측이 계속 최신이 되어, 오늘부터의
     * 흐름을 조회할 수 없다. 완료 시각이 같으면 나중에 저장된 쪽(`forecastId`가 큰 쪽)을 고른다.
     *
     * <p>`COMPLETED`만 조회 대상이므로 `completed_at`은 항상 채워져 있다.
     */
    Optional<CashflowForecast> findFirstByUserIdAndStatusOrderByCompletedAtDescForecastIdDesc(
            Long userId,
            ForecastStatus status
    );

    /**
     * 캐시·멱등 처리용 조회. 같은 요청 지문의 완료된 예측이 있으면 재계산 없이 재사용한다.
     * 지문 생성 규칙은 S15P21D105-540에서 정한다.
     */
    Optional<CashflowForecast> findFirstByUserIdAndRequestHashAndStatusOrderByCreatedAtDesc(
            Long userId,
            String requestHash,
            ForecastStatus status
    );
}
