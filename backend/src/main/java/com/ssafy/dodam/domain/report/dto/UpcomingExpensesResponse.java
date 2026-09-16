package com.ssafy.dodam.domain.report.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 예정 비용(API 20 #7). <b>이미 확정된 스케줄만</b> 담고 추정하지 않는다.
 *
 * <p>이 엔드포인트만 리포트 저장본을 쓰지 않고 조회 시점에 계산한다. 미래 스케줄을 생성 시점에 굳히면
 * 다음 날부터 낡은 값이 된다. 원본은 현금흐름 예측이 쓰는 것과 같은 수집기({@code CashflowEventCollector})라
 * 두 화면의 예정 금액이 어긋나지 않는다.
 *
 * <p>{@code yearMonth}는 <b>집계할 월 그 자체</b>이며 생략하면 오늘의 다음 달이다.
 *
 * <h2>"못 만든 것"과 "덜 모은 것"을 구분한다</h2>
 * <ul>
 *   <li>{@code unavailableReason != null} — 합계를 만들 수 없다. {@code totalAmount}가 null이고 수치 대신
 *       안내 문구를 띄운다. {@code PERIOD_ELAPSED}(이미 지난 월이라 남은 예정이 없다),
 *       {@code SOURCE_CONFLICT}(같은 예정이 원본마다 금액·날짜가 달라 확정할 수 없다),
 *       {@code SOURCE_UNAVAILABLE}(원본 조회 자체가 실패했다)</li>
 *   <li>{@code partial == true} — 합계는 있지만 <b>과소집계</b>다. 수치를 보여 주되 "일부 빠졌을 수 있음"을
 *       함께 표시한다. 사유는 {@code warnings}에 원본 코드 그대로 담는다(예: 이번 달을 집계해 오늘 이전 구간이
 *       빠진 {@code PARTIALLY_ELAPSED}, 카드 청구서와 대사가 필요한 구독 등)</li>
 * </ul>
 *
 * @param sourceType CARD / LOAN / SUBSCRIPTION / SAVINGS / MINUS_ACCOUNT
 * @param dueDate    그 달 안에 여러 건이 흩어져 있으면 null이다(합계만 의미 있다)
 */
public record UpcomingExpensesResponse(
        String yearMonth,
        LocalDate collectedFrom,
        LocalDate collectedTo,
        Long totalAmount,
        List<Item> items,
        boolean partial,
        List<String> warnings,
        String unavailableReason
) {

    public static final String PARTIALLY_ELAPSED = "PARTIALLY_ELAPSED";

    public UpcomingExpensesResponse {
        items = items == null ? List.of() : List.copyOf(items);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public static UpcomingExpensesResponse unavailable(String yearMonth, LocalDate from, LocalDate to, String reason) {
        return new UpcomingExpensesResponse(yearMonth, from, to, null, List.of(), false, List.of(), reason);
    }

    public record Item(String sourceType, String label, long amount, LocalDate dueDate) {
    }
}
