package com.ssafy.dodam.domain.improvement.dto;

import java.time.LocalDate;

/**
 * 금액 집계(API 19 #5 절감 · #6 부채감소 · #7 소비감소 · #8 저축증가). 네 API가 같은 형식을 쓴다 —
 * 화면이 4개 타일을 같은 컴포넌트로 그릴 수 있게 한 것이다.
 *
 * <p>측정된 행동이 없으면 {@code totalAmount: 0}, {@code contributingActions: 0},
 * {@code unavailableReason: null}이다. 데이터가 없어서 0인 것이지 계산 불가가 아니다.
 *
 * @param metric             SAVINGS / DEBT_REDUCTION / SPENDING_REDUCTION / SAVING_INCREASE
 * @param totalAmount        기간 누적. 월 단위 지표(절감·소비감소·저축증가)는 월 금액의 연환산이다
 * @param monthlyAmount      월 금액. 부채감소는 잔액 차이라 월 개념이 없어 null이다
 * @param measuredFrom       측정에 포함된 행동 중 가장 이른 측정일. 측정된 행동이 없으면 null
 * @param measuredTo         가장 늦은 측정일. 측정된 행동이 없으면 null
 * @param contributingActions 합계에 기여한(측정이 끝난) 행동 수
 */
public record AmountTotalResponse(
        String metric,
        long totalAmount,
        Long monthlyAmount,
        String currency,
        LocalDate measuredFrom,
        LocalDate measuredTo,
        long contributingActions,
        String unavailableReason
) {

    public static final String CURRENCY = "KRW";
}
