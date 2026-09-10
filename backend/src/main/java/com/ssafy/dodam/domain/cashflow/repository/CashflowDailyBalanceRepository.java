package com.ssafy.dodam.domain.cashflow.repository;

import com.ssafy.dodam.domain.cashflow.entity.CashflowDailyBalance;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 날짜별 예상잔액 조회.
 *
 * <p>이 테이블에는 `user_id`가 없으므로 소유권은 상위 `CashflowForecast`를 조인해 건다. 파라미터로
 * 받은 `forecastId`만 믿으면 다른 사용자의 예측 잔액이 그대로 노출된다.
 */
public interface CashflowDailyBalanceRepository extends JpaRepository<CashflowDailyBalance, Long> {

    @Query("""
            select b
              from CashflowDailyBalance b, CashflowForecast f
             where b.forecastId = f.forecastId
               and f.forecastId = :forecastId
               and f.userId = :userId
               and b.balanceDate between :startDate and :endDate
             order by b.balanceDate asc
            """)
    List<CashflowDailyBalance> findOwnedByPeriod(
            @Param("userId") Long userId,
            @Param("forecastId") Long forecastId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    /**
     * 재계산 시 이전 하위행을 지운다.
     *
     * <p>파생 삭제(`deleteByForecastId`)를 쓰면 Hibernate가 엔티티를 지연 삭제해 DELETE가 커밋
     * 시점에 나간다. 하위행 INSERT는 {@code CashflowResultWriter}가 JDBC로 바로 넣으므로 이전 행이
     * 남은 상태에서 삽입이 일어나 유니크 제약을 위반한다. 벌크 DML로 즉시 삭제한다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CashflowDailyBalance b where b.forecastId = :forecastId")
    void deleteByForecastId(@Param("forecastId") Long forecastId);
}
