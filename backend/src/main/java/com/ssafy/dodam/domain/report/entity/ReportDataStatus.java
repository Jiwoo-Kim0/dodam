package com.ssafy.dodam.domain.report.entity;

/**
 * 리포트가 만들어졌지만 안에 무엇이 들었는지 구분하는 상태. FE 협의안(docs/report-backend-handoff.md 3번)의
 * "리포트 미생성 / 거래 0건 / 일부 지표 계산 불가"를 404 하나로 표현하지 않기 위해 둔다.
 *
 * <ul>
 *   <li>{@link #READY} — 정상 생성됐고 수치를 보여줄 수 있다</li>
 *   <li>{@link #NO_TRANSACTIONS} — 기간은 끝났지만 거래가 0건이다. 합계는 0원이며 "계산 불가"가 아니다</li>
 *   <li>{@link #INSUFFICIENT_DATA} — 핵심 집계를 만들지 못했다. 수치 대신 안내 문구를 띄운다</li>
 * </ul>
 *
 * <p>READY가 아닌 리포트는 다음 조회 때 다시 계산한다. 금융망 동기화가 늦게 도착해 거래가 나중에 생기는
 * 경우가 있어서다. READY는 저장본을 고정해 같은 기간을 볼 때마다 숫자가 달라지지 않게 한다.
 */
public enum ReportDataStatus {
    READY, NO_TRANSACTIONS, INSUFFICIENT_DATA;

    public boolean isRegenerable() {
        return this != READY;
    }

    /** 응답의 `unavailableReason`. 정상이면 null이다. */
    public String unavailableReason() {
        return this == READY ? null : name();
    }
}
