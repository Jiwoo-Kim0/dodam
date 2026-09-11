package com.ssafy.dodam.domain.cashflow.repository;

import com.ssafy.dodam.domain.cashflow.entity.CashflowDailyBalance;
import com.ssafy.dodam.domain.cashflow.entity.CashflowEvent;
import com.ssafy.dodam.domain.cashflow.entity.CashflowRisk;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Repository;

/**
 * 예측 하위행(일별 잔액·이벤트·위험) 일괄 저장.
 *
 * <p>JPA `saveAll` 대신 JDBC 배치를 쓴다. 세 테이블 모두 AUTO_INCREMENT PK라 Hibernate가 INSERT를
 * 배치로 묶지 못하고, 일별 잔액은 예측 1건당 horizon 일수(예: 90행)만큼 생긴다. 하위행은 조회 시
 * 항상 `forecastId` 기준 배치로 읽으므로 생성된 PK를 돌려받을 필요도 없다.
 */
@Repository
public class CashflowResultWriter {

    private static final String INSERT_DAILY_BALANCE = """
            INSERT INTO cashflow_daily_balance (forecast_id, balance_date, expected_balance)
            VALUES (:forecastId, :balanceDate, :expectedBalance)
            """;

    private static final String INSERT_EVENT = """
            INSERT INTO cashflow_event
                   (forecast_id, event_date, type, name, direction, amount,
                    source_type, source_id, dedupe_key)
            VALUES (:forecastId, :eventDate, :type, :name, :direction, :amount,
                    :sourceType, :sourceId, :dedupeKey)
            """;

    private static final String INSERT_RISK = """
            INSERT INTO cashflow_risk
                   (forecast_id, risk_date, type, severity, message, expected_balance)
            VALUES (:forecastId, :riskDate, :type, :severity, :message, :expectedBalance)
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public CashflowResultWriter(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertDailyBalances(List<CashflowDailyBalance> balances) {
        if (balances.isEmpty()) {
            return;
        }
        SqlParameterSource[] parameters = balances.stream()
                .map(balance -> new MapSqlParameterSource()
                        .addValue("forecastId", balance.getForecastId())
                        .addValue("balanceDate", balance.getBalanceDate())
                        .addValue("expectedBalance", balance.getExpectedBalance()))
                .toArray(SqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(INSERT_DAILY_BALANCE, parameters);
    }

    /**
     * 이벤트를 저장한다. 같은 `dedupeKey`가 여러 번 들어오면 첫 건만 남긴다.
     *
     * <p>명세 `15 Cashflow`의 공통 제한이 "예정 이벤트 중복 반영 금지"다. 같은 예정 출금이 두 번
     * 반영되면 예상잔액이 실제보다 낮아져 없는 위험을 보고하게 된다. DB의
     * `uk_cashflow_event_forecast_dedupe`가 최종 방어선이지만, 배치 INSERT는 한 건만 걸려도 전체가
     * 실패하므로 여기서 먼저 걸러 예측 저장이 통째로 무산되지 않게 한다.
     */
    public void insertEvents(List<CashflowEvent> events) {
        List<CashflowEvent> distinct = distinctByDedupeKey(events);
        if (distinct.isEmpty()) {
            return;
        }
        SqlParameterSource[] parameters = distinct.stream()
                .map(event -> new MapSqlParameterSource()
                        .addValue("forecastId", event.getForecastId())
                        .addValue("eventDate", event.getEventDate())
                        .addValue("type", event.getType())
                        .addValue("name", event.getName())
                        .addValue("direction", event.getDirection().name())
                        .addValue("amount", event.getAmount())
                        .addValue("sourceType", event.getSourceType())
                        .addValue("sourceId", event.getSourceId())
                        .addValue("dedupeKey", event.getDedupeKey()))
                .toArray(SqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(INSERT_EVENT, parameters);
    }

    public void insertRisks(List<CashflowRisk> risks) {
        if (risks.isEmpty()) {
            return;
        }
        SqlParameterSource[] parameters = risks.stream()
                .map(risk -> new MapSqlParameterSource()
                        .addValue("forecastId", risk.getForecastId())
                        .addValue("riskDate", risk.getRiskDate())
                        .addValue("type", risk.getType())
                        .addValue("severity", risk.getSeverity().name())
                        .addValue("message", risk.getMessage())
                        .addValue("expectedBalance", risk.getExpectedBalance()))
                .toArray(SqlParameterSource[]::new);
        jdbcTemplate.batchUpdate(INSERT_RISK, parameters);
    }

    // 중복 차단은 예측 단위이므로 키를 `forecastId`와 함께 본다.
    private List<CashflowEvent> distinctByDedupeKey(List<CashflowEvent> events) {
        Set<String> seen = new HashSet<>();
        List<CashflowEvent> distinct = new ArrayList<>(events.size());
        for (CashflowEvent event : events) {
            if (seen.add(event.getForecastId() + ":" + event.getDedupeKey())) {
                distinct.add(event);
            }
        }
        return distinct;
    }
}
