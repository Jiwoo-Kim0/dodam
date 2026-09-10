package com.ssafy.dodam.domain.cashflow.repository;

import com.ssafy.dodam.domain.cashflow.entity.CashflowEvent;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 예정 금융이벤트 조회.
 *
 * <p>{@link CashflowDailyBalanceRepository}와 같은 이유로 소유권을 상위 예측과 조인해 건다.
 * `type`은 명세의 선택 필터이며 NULL이면 전체를 조회한다.
 */
public interface CashflowEventRepository extends JpaRepository<CashflowEvent, Long> {

    @Query("""
            select e
              from CashflowEvent e, CashflowForecast f
             where e.forecastId = f.forecastId
               and f.forecastId = :forecastId
               and f.userId = :userId
               and e.eventDate between :startDate and :endDate
               and (:type is null or e.type = :type)
             order by e.eventDate asc, e.eventId asc
            """)
    List<CashflowEvent> findOwnedByPeriod(
            @Param("userId") Long userId,
            @Param("forecastId") Long forecastId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("type") String type
    );

    /**
     * 재계산 시 이전 하위행을 지운다.
     *
     * <p>파생 삭제(`deleteByForecastId`)를 쓰면 Hibernate가 엔티티를 지연 삭제해 DELETE가 커밋
     * 시점에 나간다. 하위행 INSERT는 {@code CashflowResultWriter}가 JDBC로 바로 넣으므로 이전 행이
     * 남은 상태에서 삽입이 일어나 유니크 제약을 위반한다. 벌크 DML로 즉시 삭제한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CashflowEvent e where e.forecastId = :forecastId")
    void deleteByForecastId(@Param("forecastId") Long forecastId);
}
