package com.ssafy.dodam.domain.deposit.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 보유 예금과 상품 DB를 같은 조건으로 묶는 비교 정책.
 *
 * <p>비교군은 <b>연결 상품의 saving_type 문자열</b>로 정한다. 보유 예금 쪽에는 상품 유형을 나타내는 컬럼이
 * 없어({@code deposit_saving}에는 금리·기간만 있다) 연결 상품 없이는 동질 집단을 만들 수 없다. 대출 비교가
 * loan_type을 쓰는 것과 같은 구조다.
 *
 * <p>확정금리와 지수·시장 연동을 같은 모집단에 넣지 않는다. 연동형의 공시 {@code rate}는 최저 보장 수준이라
 * 확정금리와 나란히 두면 기준금리를 끌어내린다. 연금예금도 자격·수령 구조가 달라 분리한다.
 *
 * <p><b>현재 시연 데이터에서는 FIXED만 비교가 성립한다.</b> 예금 상품 15개 중 FIXED_DEPOSIT이 10개이고
 * MARKET_LINKED는 3개, PENSION과 ROTATING은 각각 1개다. 표본이 모자란 군은 조용히 다른 군에 섞지 않고
 * {@code INSUFFICIENT_COMPARABLE_PRODUCTS}로 남긴다.
 */
final class DepositComparisonPolicy {
    private static final Map<String, Set<String>> GROUPS = groups();

    private DepositComparisonPolicy() {
    }

    private static Map<String, Set<String>> groups() {
        Map<String, Set<String>> groups = new LinkedHashMap<>();
        groups.put("FIXED", Set.of("FIXED_DEPOSIT"));
        groups.put("MARKET_LINKED", Set.of("INDEX_LINKED_DEPOSIT", "MARKET_LINKED_DEPOSIT"));
        groups.put("PENSION", Set.of("PENSION_DEPOSIT"));
        // 회전식은 회전주기마다 금리가 갱신돼 계약 전 기간의 확정금리가 아니다.
        groups.put("ROTATING", Set.of("ROTATING_DEPOSIT"));
        return Map.copyOf(groups);
    }

    /** 상품 saving_type이 속한 비교군 이름. 대응이 없으면 null이다. */
    static String groupOf(String productSavingType) {
        if (productSavingType == null) {
            return null;
        }
        return GROUPS.entrySet().stream()
                .filter(entry -> entry.getValue().contains(productSavingType))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    static Set<String> savingTypesOf(String group) {
        return GROUPS.getOrDefault(group, Set.of());
    }
}
