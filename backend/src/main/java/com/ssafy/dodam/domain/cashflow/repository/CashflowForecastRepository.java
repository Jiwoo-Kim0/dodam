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

    /** 기준일을 지정한 최신 예측. 같은 기준일로 여러 번 실행하면 마지막 실행이 유효하다. */
    Optional<CashflowForecast> findFirstByUserIdAndBaseDateAndStatusOrderByCreatedAtDesc(
            Long userId,
            LocalDate baseDate,
            ForecastStatus status
    );

    /** 기준일을 지정하지 않은 최신 예측. 기준일이 늦은 쪽을, 같으면 나중에 실행한 쪽을 고른다. */
    Optional<CashflowForecast> findFirstByUserIdAndStatusOrderByBaseDateDescCreatedAtDesc(
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
