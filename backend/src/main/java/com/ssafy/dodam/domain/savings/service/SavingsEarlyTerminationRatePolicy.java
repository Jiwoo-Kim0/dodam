package com.ssafy.dodam.domain.savings.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 적금 중도해지이율 산정 정책.
 *
 * <p><b>이 표는 서비스가 정한 기본안이며 상품별 공시 중도해지이율이 아니다.</b> 근거 자료를 찾지 못해
 * 추정으로 채운 부분이므로 응답의 {@code ratePolicy}·{@code rateTier}와 가정 코드로 함께 노출한다.
 *
 * <ul>
 *   <li>API 명세 11-2는 파라미터와 제한사항만 정하고 계산식을 정의하지 않는다.</li>
 *   <li>ERD의 {@code deposit_saving}에는 계약이율({@code interest_rate})만 있고 중도해지이율 컬럼이 없다.</li>
 *   <li>기획안 7-1은 "중도해지·추가납입 등 선택 비교"라고만 적혀 있다.</li>
 * </ul>
 *
 * <p>국내 시중은행이 정기적금에 널리 쓰는 <b>경과기간 구간별 계약이율 차등</b> 관행을 기본안으로 삼았다.
 * 예금(536)의 {@code DepositEarlyTerminationRatePolicy}와 같은 구간·비율이다. <b>같은 표를 두 도메인에
 * 따로 두는 이유는 두 이슈가 서로 다른 브랜치에서 병합될 수 있기 때문이다.</b> 둘 다 병합되면 공용
 * 위치로 합쳐야 하며 그 작업은 remaining-work에 남겼다.
 *
 * <p><b>이 정책을 넣기 전 구현은 중도해지 이율을 0%로 두었다.</b> 그러면 예상 수령액이 납입 원금과 같아져
 * "이자가 전혀 없다"는 계산된 답처럼 보인다. 실제 적금 중도해지는 낮더라도 이율이 붙으므로, 0을 두는 것이
 * 추정을 피하는 것이 아니라 더 틀린 추정을 하는 것이었다.
 */
final class SavingsEarlyTerminationRatePolicy {
    static final String POLICY_CODE = "TIERED_CONTRACT_RATE_RATIO";
    /** 어떤 구간에서도 보장하는 최저 연이율(%). 한 달을 못 채운 해지에는 이 값만 적용한다. */
    private static final BigDecimal FLOOR_RATE = new BigDecimal("0.1000");
    private static final int RATE_SCALE = 4;

    /** 경과 개월 하한(포함)과 계약이율에 곱할 비율. 내림차순으로 두어 첫 일치 구간을 쓴다. */
    private static final List<Tier> TIERS = List.of(
            new Tier(11, new BigDecimal("0.80")),
            new Tier(9, new BigDecimal("0.60")),
            new Tier(6, new BigDecimal("0.40")),
            new Tier(3, new BigDecimal("0.30")),
            new Tier(1, new BigDecimal("0.20"))
    );

    private SavingsEarlyTerminationRatePolicy() {
    }

    /**
     * @param contractRate 계약이율(연 %)
     * @param elapsedMonths 개설일부터 기준일까지 경과한 만(滿) 개월 수
     * @return 적용 중도해지이율(연 %). 소수 넷째 자리 HALF_UP이며 최저 0.1000 이상이다.
     */
    static BigDecimal resolve(BigDecimal contractRate, long elapsedMonths) {
        BigDecimal ratio = TIERS.stream()
                .filter(tier -> elapsedMonths >= tier.minMonths())
                .map(Tier::ratio)
                .findFirst()
                .orElse(null);
        if (ratio == null) {
            return FLOOR_RATE;
        }
        // 계약이율이 낮으면 비율 적용값이 최저이율보다 낮아진다. 관행대로 최저를 보장한다.
        return contractRate.multiply(ratio).setScale(RATE_SCALE, RoundingMode.HALF_UP).max(FLOOR_RATE);
    }

    /** 응답에 실을 구간 설명. 사용자가 어떤 표로 계산했는지 확인할 수 있게 한다. */
    static String describe(long elapsedMonths) {
        return TIERS.stream()
                .filter(tier -> elapsedMonths >= tier.minMonths())
                .map(tier -> "ELAPSED_MONTHS_GTE_" + tier.minMonths())
                .findFirst()
                .orElse("ELAPSED_MONTHS_LT_1");
    }

    private record Tier(int minMonths, BigDecimal ratio) {
    }
}
