package com.ssafy.dodam.domain.loan.service;

import com.ssafy.dodam.domain.loan.entity.Loan;
import com.ssafy.dodam.domain.product.entity.FinancialProduct;
import com.ssafy.dodam.domain.product.entity.FinancialProductOption;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Component;

/**
 * 공시 최저금리 기준으로 비교군의 기준금리를 계산한다.
 *
 * <p>기준은 {@code DISCLOSED_MIN_RATE_MEDIAN}이며 <b>동일 조건에서 공시된 최저금리 수준</b>이라는 의미만
 * 가진다. 실제 평균 대출금리도, 사용자가 받을 승인금리도 아니다.
 *
 * <p>공시 범위 중간값 {@code (min + max) / 2}는 쓰지 않는다. 공시 상한이 15%까지 열려 있어 편차가
 * 구조적으로 음수가 되고, 시연 데이터에서 최대 편차가 +0.21%p에 그쳐 고금리 판정이 0건이 된다. 최저금리
 * 기준으로 바꾸면 같은 데이터에서 6건 중 4건이 비교 성립하고 편차가 +3.03 ~ -1.35%p로 분포한다.
 *
 * <p>옵션 {@code rate}는 사용하지 않는다. 319건 중 308건이 NULL이고, 값이 있는 11건 중 min/max와 다른
 * 6건(아낌e 보금자리론)은 repayment_type이 NULL이라 어차피 조건에서 제외되며 나머지 5건은 rate와 minRate가
 * 같은 값이다. rate를 우선하면 표본만 붕괴한다.
 */
@Component
public class LoanRateComparisonCalculator {
    static final String REFERENCE_METHOD = "DISCLOSED_MIN_RATE_MEDIAN";
    static final String UNMATCHED_TERM_BAND = "TERM_BAND_MATCH";
    static final String UNMATCHED_REPAYMENT_AMBIGUOUS = "REPAYMENT_TYPE_AMBIGUOUS";
    static final String UNMATCHED_RATE_TYPE = "RATE_TYPE_NOT_MATCHED";
    private static final int RATE_SCALE = 4;

    /**
     * @param loan 기준이 되는 보유 대출. 연결된 보유 상품은 모집단에서 제외한다.
     * @param products 요청당 한 번 읽은 대출 상품 전량
     * @param group 비교군 이름
     * @param band 기간 밴드
     * @param sector 업권. null이면 업권 조건을 적용하지 않는다.
     */
    LoanRateReference calculate(
            Loan loan, List<FinancialProduct> products, String group, LoanTermBand band, String sector
    ) {
        Set<String> loanTypes = LoanComparisonPolicy.loanTypesOf(group);
        Set<String> unmatched = new TreeSet<>();
        List<LoanRateCandidate> candidates = new ArrayList<>();
        for (FinancialProduct product : products) {
            if (!loanTypes.contains(product.getLoanType())) {
                continue;
            }
            // 보유 상품 자신을 모집단에 넣으면 자기 금리와 비교하게 된다.
            if (loan.getFinancialProductId() != null && loan.getFinancialProductId().equals(product.getId())) {
                continue;
            }
            if (sector != null && !sector.equals(product.getCompany().getSector())) {
                continue;
            }
            LoanRateCandidate candidate = representative(loan, product, band, unmatched);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        List<LoanRateCandidate> sorted = candidates.stream()
                .sorted(Comparator.comparing(LoanRateCandidate::representativeRate)
                        .thenComparing(LoanRateCandidate::productId))
                .toList();
        return reference(sorted, List.copyOf(unmatched));
    }

    /**
     * 한 상품의 대표값. 조건에 맞는 옵션 minRate들의 중앙값이다.
     *
     * <p>옵션 수가 많은 상품이 기준금리를 더 끌어당기지 않도록 상품 단위로 한 번 접는다. 대표값을 만든 뒤
     * 그 값에 가장 가까운 옵션을 근거로 반환해 사용자가 어떤 조건을 본 것인지 확인할 수 있게 한다.
     */
    private LoanRateCandidate representative(
            Loan loan, FinancialProduct product, LoanTermBand band, Set<String> unmatched
    ) {
        List<FinancialProductOption> matched = product.getOptions().stream()
                .filter(option -> option.getTerm() != null && band.contains(option.getTerm()))
                .filter(option -> LoanComparisonPolicy.matchesRepaymentType(
                        loan.getRepaymentType(), option.getRepaymentType()))
                .filter(option -> option.getMinRate() != null && option.getMinRate().signum() >= 0)
                .filter(option -> option.getMaxRate() == null
                        || option.getMinRate().compareTo(option.getMaxRate()) <= 0)
                .toList();
        if (matched.isEmpty()) {
            return null;
        }
        BigDecimal median = median(matched.stream().map(FinancialProductOption::getMinRate).toList());
        FinancialProductOption basis = matched.stream()
                .min(Comparator.comparing((FinancialProductOption option) -> option.getMinRate().subtract(median).abs())
                        .thenComparing(FinancialProductOption::getId))
                .orElseThrow();
        if (!band.contains(loan.getLoanPeriod()) || !basis.getTerm().equals(loan.getLoanPeriod())) {
            unmatched.add(UNMATCHED_TERM_BAND);
        }
        if (LoanComparisonPolicy.AMBIGUOUS_REPAYMENT_TYPE.equals(basis.getRepaymentType())) {
            unmatched.add(UNMATCHED_REPAYMENT_AMBIGUOUS);
        }
        if (basis.getRateType() != null) {
            // 고정·변동 조건은 판정에 쓰지 않는다. 보유 대출에 대응 값이 없어 동질성을 확인할 수 없다.
            unmatched.add(UNMATCHED_RATE_TYPE);
        }
        return new LoanRateCandidate(product.getId(), product.getProductName(),
                product.getCompany().getCompanyName(), product.getSourceType().name(), median,
                basis.getMinRate(), basis.getMaxRate(), basis.getTerm(), basis.getRepaymentType(),
                basis.getRateType(), product.getSyncedAt());
    }

    private LoanRateReference reference(List<LoanRateCandidate> candidates, List<String> unmatched) {
        LocalDateTime oldest = candidates.stream().map(LoanRateCandidate::syncedAt)
                .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
        LocalDateTime latest = candidates.stream().map(LoanRateCandidate::syncedAt)
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        if (candidates.size() < LoanRateReference.MINIMUM_PRODUCTS) {
            // 후보는 유지한다. 표본이 부족해 기준금리를 만들지 못한 것과 후보가 없는 것은 다른 상태다.
            return new LoanRateReference(null, null, candidates.size(), candidates, unmatched, oldest, latest);
        }
        BigDecimal median = median(candidates.stream().map(LoanRateCandidate::representativeRate).toList());
        String confidence = candidates.size() == LoanRateReference.MINIMUM_PRODUCTS
                ? LoanRateReference.CONFIDENCE_LOW : LoanRateReference.CONFIDENCE_NORMAL;
        return new LoanRateReference(median, confidence, candidates.size(), candidates, unmatched, oldest, latest);
    }

    /** 짝수 개면 가운데 두 값의 산술평균이다. 판정과 정렬은 반올림 전 값으로 하고 표시만 넷째 자리로 맞춘다. */
    private static BigDecimal median(List<BigDecimal> values) {
        List<BigDecimal> sorted = values.stream().sorted().toList();
        int size = sorted.size();
        int middle = size / 2;
        if (size % 2 == 1) {
            return sorted.get(middle).setScale(RATE_SCALE, RoundingMode.HALF_UP);
        }
        return sorted.get(middle - 1).add(sorted.get(middle))
                .divide(BigDecimal.valueOf(2), RATE_SCALE, RoundingMode.HALF_UP);
    }
}
