package com.ssafy.dodam.domain.simulation.service;

import com.ssafy.dodam.domain.loan.entity.RepaymentType;
import com.ssafy.dodam.domain.loan.service.LoanComparisonPolicy;
import com.ssafy.dodam.domain.product.entity.ProductType;
import com.ssafy.dodam.domain.simulation.dto.ProductSnapshot;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * 상품 옵션 선택 정책(585 §10.2). 순수 함수이며 데이터 없이 테스트한다.
 *
 * <p>처리 순서: 유형 → 기간·명시 상환방식 → 유효 금리·한도 자료 → 요청 금액의 한도 충족 → 생략 상환방식 확정 → 정렬·선택.
 * 순서가 중요하다 — 한도 때문에 탈락한 옵션의 상환방식 때문에 "방식 모호" 오류를 내면 안 되고, 유효 옵션이 있는데 한도로
 * 모두 탈락했으면 "옵션 없음"이 아니라 "한도 초과"여야 한다.
 *
 * <p>우대금리·인접 기간·상품 최상위 금리로 대체하지 않는다. 0% 금리는 유효하고 null·음수는 자료 오류로 부적합이다.
 */
public final class ProductOptionSelector {
    static final String FIXED_DEPOSIT = "FIXED_DEPOSIT";
    static final String FIXED_RATE_TYPE = "FIXED";

    private ProductOptionSelector() {
    }

    /**
     * @param resolvedRepaymentType 대출만. 요청(또는 기존 대출)의 방식이거나 AUTO에서 확정된 방식
     * @param appliedRate           예적금은 option.rate, 대출은 option.minRate
     * @param effectiveLimit        대출만. 옵션·상품 한도 중 작은 값. 둘 다 없으면 null
     */
    public record Selection(ProductSnapshot.Option option, RepaymentType resolvedRepaymentType, BigDecimal appliedRate,
            Long effectiveLimit, List<String> assumptions) {
        public Selection {
            assumptions = List.copyOf(assumptions);
        }

        public boolean ambiguousRepaymentCode() {
            return option.repaymentType() != null && mappedTypes(option.repaymentType()).size() > 1;
        }
    }

    /** 예금은 DEPOSIT + FIXED_DEPOSIT, 적금은 SAVINGS 전부. 다른 예금 유형(연동·회전·연금)은 확정금리 단리로 계산할 수 없다. */
    public static Selection selectSaving(ProductSnapshot product, int term) {
        boolean deposit = product.productType() == ProductType.DEPOSIT && FIXED_DEPOSIT.equals(product.savingType());
        if (!deposit && product.productType() != ProductType.SAVINGS) {
            throw new BusinessException(ErrorCode.SIMULATION_PRODUCT_NOT_SUPPORTED);
        }
        List<ProductSnapshot.Option> candidates = product.options().stream()
                .filter(o -> o.term() != null && o.term() == term)
                .filter(o -> o.rate() != null && o.rate().signum() >= 0)
                .sorted(Comparator.comparing(ProductSnapshot.Option::rate).thenComparing(ProductSnapshot.Option::optionId))
                .toList();
        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.SIMULATION_PRODUCT_OPTION_UNAVAILABLE);
        }
        List<String> assumptions = new ArrayList<>();
        if (candidates.size() > 1) {
            assumptions.add(SimulationAssumptions.MULTIPLE_OPTIONS_LOWEST_RATE_SELECTED);
        }
        ProductSnapshot.Option selected = candidates.get(0);
        return new Selection(selected, null, selected.rate(), null, assumptions);
    }

    /** 상품 loan_type이 533 비교군(CREDIT/CREDIT_MID/MORTGAGE/JEONSE/AUTO)에 속하는지. 한도대출·자격 한정 상품은 null이다. */
    public static String loanGroupOf(ProductSnapshot product) {
        return product.productType() == ProductType.LOAN ? LoanComparisonPolicy.groupOf(product.loanType()) : null;
    }

    /**
     * @param term      요청 기간(신규) 또는 잔여 회차 수(대환)
     * @param requested 명시 상환방식. null이면 AUTO(후보 방식의 합집합이 한 종류일 때만 확정)
     * @param amount    한도와 비교할 대출분
     */
    public static Selection selectLoan(ProductSnapshot product, int term, RepaymentType requested, long amount) {
        if (loanGroupOf(product) == null) {
            throw new BusinessException(ErrorCode.SIMULATION_PRODUCT_NOT_SUPPORTED);
        }
        // 상품 공통 한도가 0·음수면 자료 오류라 모든 옵션이 부적합하다
        boolean productLimitBroken = product.maxLimit() != null && product.maxLimit() <= 0;
        List<ProductSnapshot.Option> valid = product.options().stream()
                .filter(o -> o.term() != null && o.term() == term)
                .filter(o -> requested == null ? !mappedTypes(o.repaymentType()).isEmpty()
                        : LoanComparisonPolicy.matchesRepaymentType(requested, o.repaymentType()))
                .filter(o -> o.minRate() != null && o.minRate().signum() >= 0
                        && (o.maxRate() == null || o.minRate().compareTo(o.maxRate()) <= 0))
                .filter(o -> !productLimitBroken && (o.limitAmount() == null || o.limitAmount() > 0))
                .toList();
        if (valid.isEmpty()) {
            throw new BusinessException(ErrorCode.SIMULATION_PRODUCT_OPTION_UNAVAILABLE);
        }
        List<ProductSnapshot.Option> withinLimit = valid.stream()
                .filter(o -> {
                    Long limit = effectiveLimit(product, o);
                    return limit == null || amount <= limit;
                })
                .toList();
        if (withinLimit.isEmpty()) {
            throw new BusinessException(ErrorCode.SIMULATION_PRODUCT_LIMIT_EXCEEDED);
        }
        RepaymentType resolved = requested;
        if (resolved == null) {
            Set<RepaymentType> union = EnumSet.noneOf(RepaymentType.class);
            withinLimit.forEach(o -> union.addAll(mappedTypes(o.repaymentType())));
            if (union.size() != 1) {
                throw new BusinessException(ErrorCode.SIMULATION_REPAYMENT_TYPE_REQUIRED);
            }
            resolved = union.iterator().next();
        }
        // 모호하지 않은 상환 코드 우선, 그다음 최저금리 오름차순, 마지막으로 optionId로 결정적 선택
        ProductSnapshot.Option selected = withinLimit.stream()
                .sorted(Comparator.comparing((ProductSnapshot.Option o) -> mappedTypes(o.repaymentType()).size() > 1)
                        .thenComparing(ProductSnapshot.Option::minRate)
                        .thenComparing(ProductSnapshot.Option::optionId))
                .findFirst().orElseThrow();
        List<String> assumptions = new ArrayList<>();
        assumptions.add(SimulationAssumptions.LOAN_RATE_PUBLISHED_MINIMUM);
        if (withinLimit.size() > 1) {
            assumptions.add(SimulationAssumptions.MULTIPLE_OPTIONS_LOWEST_RATE_SELECTED);
        }
        Long limit = effectiveLimit(product, selected);
        if (limit == null) {
            assumptions.add(SimulationAssumptions.PRODUCT_LIMIT_UNKNOWN);
        }
        if (!FIXED_RATE_TYPE.equals(selected.rateType())) {
            assumptions.add(SimulationAssumptions.VARIABLE_RATE_HELD_CONSTANT);
        }
        if (mappedTypes(selected.repaymentType()).size() > 1) {
            assumptions.add(SimulationAssumptions.REPAYMENT_CODE_AMBIGUOUS_MAPPED);
        }
        return new Selection(selected, resolved, selected.minRate(), limit, assumptions);
    }

    /** 양수 옵션 한도와 양수 상품 한도 중 작은 값. 하나만 있으면 그 값, 둘 다 없으면 null(검증 생략). */
    static Long effectiveLimit(ProductSnapshot product, ProductSnapshot.Option option) {
        Long optionLimit = option.limitAmount() != null && option.limitAmount() > 0 ? option.limitAmount() : null;
        Long productLimit = product.maxLimit() != null && product.maxLimit() > 0 ? product.maxLimit() : null;
        if (optionLimit == null) {
            return productLimit;
        }
        return productLimit == null ? optionLimit : Math.min(optionLimit, productLimit);
    }

    /** 옵션 상환 코드에 대응하는 Loan 방식 집합. 미상 코드·null은 빈 집합. AMORTIZING은 두 균등 방식. */
    static Set<RepaymentType> mappedTypes(String optionRepaymentType) {
        Set<RepaymentType> types = EnumSet.noneOf(RepaymentType.class);
        for (RepaymentType type : RepaymentType.values()) {
            if (LoanComparisonPolicy.matchesRepaymentType(type, optionRepaymentType)) {
                types.add(type);
            }
        }
        return types;
    }
}
