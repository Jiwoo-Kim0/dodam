package com.ssafy.dodam.domain.loan.service;

import com.ssafy.dodam.domain.loan.entity.RepaymentType;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * 보유 대출과 상품 DB를 같은 조건으로 묶는 비교 정책.
 *
 * <p>상품 조회 API의 {@code ProductQueryService.*_LOAN_TYPES}를 재사용하지 않는다. 그 집합은 화면 목록용
 * 넓은 분류이고 여기서는 금리를 견줄 수 있는 동질 집단만 필요하다. 예를 들어 상품 조회의 개인신용대출에는
 * 정책·사업자·한도대출이 함께 들어 있어 일반 신용대출의 기준금리를 왜곡한다.
 *
 * <p>비교군은 보유 대출의 {@code LoanType}이 아니라 <b>연결 상품의 loan_type 문자열</b>로 결정한다. 같은
 * CREDIT 대출이라도 일반 신용과 중금리는 금리 수준이 달라 같은 모집단으로 둘 수 없다.
 */
final class LoanComparisonPolicy {
    /** 상품 조회의 MORTGAGE 분류와 달리 POLICY_HOUSING과 SOHO_MORTGAGE를 넣지 않는다. 자격 요건이 다르다. */
    private static final Map<String, Set<String>> GROUPS = groups();

    /**
     * Loan enum과 상품 옵션 문자열의 대응.
     *
     * <p>문자열 동일성만 비교하면 EQUAL_PAYMENT의 후보가 0건이 된다. 상품 옵션에는 그 이름이 없다.
     * AMORTIZING은 원금균등·원리금균등을 구분할 수 없어 양쪽에 넣고 후보에 모호함을 표시한다.
     * MORTGAGE 옵션 다수가 이 값이라 제외하면 담보대출 비교군이 성립하지 않는다.
     */
    private static final Map<RepaymentType, Set<String>> REPAYMENT_TYPES = Map.of(
            RepaymentType.EQUAL_PAYMENT, Set.of("EQUAL_PRINCIPAL_INTEREST", "AMORTIZING"),
            RepaymentType.EQUAL_PRINCIPAL, Set.of("EQUAL_PRINCIPAL", "AMORTIZING"),
            RepaymentType.BULLET, Set.of("BULLET")
    );

    static final String AMBIGUOUS_REPAYMENT_TYPE = "AMORTIZING";

    private LoanComparisonPolicy() {
    }

    private static Map<String, Set<String>> groups() {
        Map<String, Set<String>> groups = new LinkedHashMap<>();
        groups.put("CREDIT", Set.of("PERSONAL_CREDIT", "CREDIT_REFINANCE"));
        groups.put("CREDIT_MID", Set.of("POLICY_CREDIT", "MID_CREDIT", "MID_CREDIT_EMERGENCY",
                "MID_CREDIT_REFINANCE", "MID_CREDIT_POLICY", "MID_EMERGENCY_REFINANCE"));
        groups.put("MORTGAGE", Set.of("MORTGAGE", "MORTGAGE_REFINANCE", "POLICY_MORTGAGE"));
        groups.put("JEONSE", Set.of("JEONSE", "LEASE_HF", "LEASE_YOUTH", "LEASE_SGI",
                "LEASE_REFI_HF", "LEASE_REFI_YOUTH", "LEASE_REFI_SGI"));
        groups.put("AUTO", Set.of("AUTO_NEW", "AUTO_LOAN", "USED_CAR"));
        return Map.copyOf(groups);
    }

    /**
     * 상품 loan_type이 속한 비교군 이름. 대응이 없으면 null이다.
     *
     * <p>자격 한정 상품(SOHO_*, MILITARY_*, PUBLIC_OFFICIAL 등)과 한도·마이너스 방식(CREDIT_LINE,
     * LINE_REFINANCE, EMERGENCY_CREDIT)은 어느 군에도 넣지 않는다. 후자는 분할상환 대출의 비교 대상이
     * 아니며 마이너스통장은 534 범위다.
     */
    static String groupOf(String productLoanType) {
        if (productLoanType == null) {
            return null;
        }
        return GROUPS.entrySet().stream()
                .filter(entry -> entry.getValue().contains(productLoanType))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    static Set<String> loanTypesOf(String group) {
        return GROUPS.getOrDefault(group, Set.of());
    }

    static boolean matchesRepaymentType(RepaymentType loanRepaymentType, String optionRepaymentType) {
        if (loanRepaymentType == null || optionRepaymentType == null) {
            return false;
        }
        return REPAYMENT_TYPES.getOrDefault(loanRepaymentType, Set.of()).contains(optionRepaymentType);
    }
}
