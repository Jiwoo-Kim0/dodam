package com.ssafy.dodam.domain.cashflow.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 예측 기간에 예정된 입·출금 이벤트 1건.
 *
 * <p>`amount`는 방향과 무관하게 항상 양수이며 입·출금은 {@link EventDirection}으로만 구분한다
 * (ERD 주요 코드값 표). 부호를 금액에 섞으면 합계 쿼리에서 방향 조건이 빠져도 결과가 그럴듯하게
 * 나와 오류를 늦게 발견하게 된다.
 *
 * <p>`dedupeKey`는 같은 예정 이벤트가 여러 출처에서 들어와도 한 번만 반영되게 하는 키이며
 * `(forecastId, dedupeKey)`가 유니크다. 명세 `15 Cashflow`의 공통 제한인 "예정 이벤트 중복 반영
 * 금지"의 저장 단계 근거다. **키 생성 규칙은 S15P21D105-539에서 확정한다.**
 *
 * <p>`type`은 enum으로 두지 않았다. ERD 코드값 표에 `cashflow_event.type`의 값 목록이 없어
 * 이벤트를 실제로 만드는 539에서 집합을 확정해야 하기 때문이다.
 */
@Getter
@Entity
@Table(name = "cashflow_event", uniqueConstraints = @UniqueConstraint(
        name = "uk_cashflow_event_forecast_dedupe",
        columnNames = {"forecast_id", "dedupe_key"}))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CashflowEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "forecast_id", nullable = false)
    private Long forecastId;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private EventDirection direction;

    @Column(name = "amount", nullable = false)
    private Long amount;

    // 어떤 도메인에서 파생됐는지 남긴다. 원문 식별자는 마스킹 대상이 아닌 내부 PK만 저장한다.
    @Column(name = "source_type", length = 30)
    private String sourceType;

    @Column(name = "source_id", length = 100)
    private String sourceId;

    // DDL이 CHAR(64)다. columnDefinition을 빼면 Hibernate가 VARCHAR로 보고 validate에 실패한다.
    @Column(name = "dedupe_key", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String dedupeKey;

    private CashflowEvent(
            Long forecastId,
            LocalDate eventDate,
            String type,
            String name,
            EventDirection direction,
            long amount,
            String sourceType,
            String sourceId,
            String dedupeKey
    ) {
        this.forecastId = forecastId;
        this.eventDate = eventDate;
        this.type = type;
        this.name = name;
        this.direction = direction;
        this.amount = amount;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.dedupeKey = dedupeKey;
    }

    /**
     * @param amount `chk_cashflow_event_amount`를 만족하는 양수 금액
     * @param dedupeKey 같은 예측 안에서 이벤트를 식별하는 키. 비어 있으면 중복 차단이 무력화된다
     */
    public static CashflowEvent of(
            Long forecastId,
            LocalDate eventDate,
            String type,
            String name,
            EventDirection direction,
            long amount,
            String sourceType,
            String sourceId,
            String dedupeKey
    ) {
        if (amount <= 0) {
            throw new IllegalArgumentException("이벤트 금액은 양수여야 합니다.");
        }
        if (dedupeKey == null || dedupeKey.isBlank()) {
            throw new IllegalArgumentException("중복 차단 키가 필요합니다.");
        }
        return new CashflowEvent(
                forecastId, eventDate, type, name, direction, amount, sourceType, sourceId, dedupeKey);
    }

    /** 예상잔액 누적에 더할 부호 있는 금액. 저장값은 양수로 두고 계산 시점에만 부호를 붙인다. */
    public long signedAmount() {
        return direction == EventDirection.OUTFLOW ? -amount : amount;
    }
}
