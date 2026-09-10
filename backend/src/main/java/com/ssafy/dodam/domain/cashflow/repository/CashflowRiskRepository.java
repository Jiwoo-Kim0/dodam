package com.ssafy.dodam.domain.cashflow.repository;

import com.ssafy.dodam.domain.cashflow.entity.CashflowRisk;
import com.ssafy.dodam.domain.cashflow.entity.RiskSeverity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 현금흐름 위험 조회.
 *
 * <p>`type`과 `severity`는 명세의 선택 필터이며 NULL이면 조건에서 빠진다. 정렬은 심각도 우선이
 * 자연스럽지만 `severity`가 문자열 컬럼이라 사전순이 심각도순과 다르다. 정렬 규칙은 응답을 만드는
 * S15P21D105-542에서 정하고 여기서는 날짜 기준으로만 정렬한다.
 */
public interface CashflowRiskRepository extends JpaRepository<CashflowRisk, Long> {

    @Query("""
            select r
              from CashflowRisk r, CashflowForecast f
             where r.forecastId = f.forecastId
               and f.forecastId = :forecastId
               and f.userId = :userId
               and (:type is null or r.type = :type)
               and (:severity is null or r.severity = :severity)
             order by r.riskDate asc nulls last, r.riskId asc
            """)
    List<CashflowRisk> findOwned(
            @Param("userId") Long userId,
            @Param("forecastId") Long forecastId,
            @Param("type") String type,
            @Param("severity") RiskSeverity severity
    );

    /**
     * 재계산 시 이전 하위행을 지운다.
     *
     * <p>파생 삭제(`deleteByForecastId`)를 쓰면 Hibernate가 엔티티를 지연 삭제해 DELETE가 커밋
     * 시점에 나간다. 하위행 INSERT는 {@code CashflowResultWriter}가 JDBC로 바로 넣으므로 이전 행이
     * 남은 상태에서 삽입이 일어나 유니크 제약을 위반한다. 벌크 DML로 즉시 삭제한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CashflowRisk r where r.forecastId = :forecastId")
    void deleteByForecastId(@Param("forecastId") Long forecastId);
}
