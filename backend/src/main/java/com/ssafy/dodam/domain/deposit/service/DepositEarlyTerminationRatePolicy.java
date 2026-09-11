package com.ssafy.dodam.domain.deposit.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 중도해지이율 산정 정책.
 *
 * <p><b>이 표는 서비스가 정한 기본안이며 상품별 공시 중도해지이율이 아니다.</b> 근거 자료를 찾지 못해
 * 추정으로 채운 부분이므로 숨기지 않고 응답과 문서에 정책 코드를 함께 노출한다.
 *
 * <ul>
 *   <li>API 명세 11-1은 파라미터와 제한사항만 정하고 계산식을 정의하지 않는다.</li>
 *   <li>ERD의 {@code deposit_saving}에는 계약이율({@code interest_rate})만 있고 중도해지이율 컬럼이 없다.
 *       {@code financial_product_option}에도 대응 항목이 없다.</li>
 *   <li>기획안 7-1은 "중도해지·추가납입 등 선택 비교"라고만 적혀 있다.</li>
 * </ul>
 *
 * <p>그래서 국내 시중은행이 정기예금에 널리 쓰는 <b>경과기간 구간별 계약이율 차등</b> 관행을 기본안으로
 * 삼았다. 경과기간이 길수록 계약이율에 가까워지고, 어떤 구간에서도 최저 연 0.10%는 보장한다.
 *
 * <p>실제 상품의 공시 중도해지이율이 확보되면 이 표 대신 상품별 값을 쓴다. 그 전까지는 <b>참고 추정치</b>다.
 */
final class DepositEarlyTerminationRatePolicy {
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

    private DepositEarlyTerminationRatePolicy() {
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
        BigDecimal applied = contractRate.multiply(ratio).setScale(RATE_SCALE, RoundingMode.HALF_UP);
        // 계약이율이 0.125% 같은 저금리면 비율 적용값이 최저이율보다 낮아진다. 관행대로 최저를 보장한다.
        return applied.max(FLOOR_RATE);
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
