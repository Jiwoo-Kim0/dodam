package com.ssafy.dodam.domain.deposit.service;

import com.ssafy.dodam.domain.deposit.repository.DepositAccountRow;
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
 * 공시 기본금리 기준으로 비교군의 기준금리를 계산한다.
 *
 * <p>기준은 {@code DISCLOSED_BASE_RATE_MEDIAN}이며 <b>동일 조건에서 공시된 기본금리 수준</b>이라는 의미만
 * 가진다. 사용자가 실제로 받게 될 적용금리가 아니다.
 *
 * <p>대출 비교가 옵션 {@code min_rate}를 쓰는 것과 달리 예금은 옵션 {@code rate}를 쓴다. 데이터 구조가
 * 다르기 때문이다. 시연 데이터의 예금 옵션 56건은 <b>전부 rate가 채워져 있고 min_rate·max_rate는 전부
 * NULL</b>이다. 예금 공시는 기간별 단일 금리라 범위를 쓰지 않는다.
 *
 * <p>{@code preferred_rate}는 판정에 쓰지 않는다. 56건 중 값이 있는 것은 소수이고, 우대 조건 충족 여부는
 * 보유 예금 쪽에서 확인할 수 없다. 섞으면 조건을 채우지 못한 사용자의 금리를 우대금리와 비교하게 된다.
 * 근거 옵션의 우대금리는 후보에 그대로 실어 사용자가 상한을 볼 수 있게만 한다.
 */
@Component
public class DepositRateComparisonCalculator {
    static final String REFERENCE_METHOD = "DISCLOSED_BASE_RATE_MEDIAN";
    static final String UNMATCHED_TERM_BAND = "TERM_BAND_MATCH";
    static final String UNMATCHED_RATE_TYPE = "RATE_TYPE_NOT_MATCHED";
    private static final int RATE_SCALE = 4;

    /**
     * @param deposit 기준이 되는 보유 예금. 연결된 보유 상품은 모집단에서 제외한다.
     * @param products 요청당 한 번 읽은 예금 상품 전량
     * @param group 비교군 이름
     * @param band 기간 밴드
     * @param sector 업권. null이면 업권 조건을 적용하지 않는다.
     * @param contractMonths 보유 예금의 계약 개월 수. 밴드 일치 표시에만 쓴다.
     */
    DepositRateReference calculate(
            DepositAccountRow deposit, List<FinancialProduct> products, String group,
            DepositTermBand band, String sector, Integer contractMonths
    ) {
        Set<String> savingTypes = DepositComparisonPolicy.savingTypesOf(group);
        List<DepositRateCandidate> candidates = new ArrayList<>();
        for (FinancialProduct product : products) {
            // saving_type이 비어 있으면 어느 비교군에도 넣지 않는다. Set.of는 contains(null)에서 NPE다.
            if (product.getSavingType() == null || !savingTypes.contains(product.getSavingType())) {
                continue;
            }
            // 보유 상품 자신을 모집단에 넣으면 자기 금리와 비교하게 된다.
            if (deposit.financialProductId() != null
                    && deposit.financialProductId().equals(product.getId())) {
                continue;
            }
            if (sector != null && !sector.equals(product.getCompany().getSector())) {
                continue;
            }
            DepositRateCandidate candidate = representative(product, band, contractMonths);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        List<DepositRateCandidate> sorted = candidates.stream()
                .sorted(Comparator.comparing(DepositRateCandidate::representativeRate)
                        .thenComparing(DepositRateCandidate::productId))
                .toList();
        return reference(sorted);
    }

    /**
     * 한 상품의 대표값. 밴드에 드는 옵션 기본금리들의 중앙값이다.
     *
     * <p>옵션 수가 많은 상품이 기준금리를 더 끌어당기지 않도록 상품 단위로 한 번 접는다. 대표값을 만든 뒤
     * 그 값에 가장 가까운 옵션을 근거로 반환해 사용자가 어떤 조건을 본 것인지 확인할 수 있게 한다.
     */
    private DepositRateCandidate representative(
            FinancialProduct product, DepositTermBand band, Integer contractMonths
    ) {
        List<FinancialProductOption> matched = product.getOptions().stream()
                .filter(option -> option.getTerm() != null && band.contains(option.getTerm()))
                .filter(option -> option.getRate() != null && option.getRate().signum() >= 0)
                .toList();
        if (matched.isEmpty()) {
            return null;
        }
        BigDecimal median = median(matched.stream().map(FinancialProductOption::getRate).toList());
        FinancialProductOption basis = matched.stream()
                .min(Comparator.comparing((FinancialProductOption option) -> option.getRate().subtract(median).abs())
                        .thenComparing(FinancialProductOption::getId))
                .orElseThrow();
        // 미일치 조건은 후보마다 따로 모은다. 하나가 어긋났다고 나머지 후보까지 어긋난 것은 아니다.
        Set<String> unmatched = new TreeSet<>();
        if (!band.contains(contractMonths) || !basis.getTerm().equals(contractMonths)) {
            unmatched.add(UNMATCHED_TERM_BAND);
        }
        if (basis.getRateType() != null) {
            // 확정·변동 조건은 판정에 쓰지 않는다. 보유 예금에 대응 값이 없어 동질성을 확인할 수 없다.
            unmatched.add(UNMATCHED_RATE_TYPE);
        }
        return new DepositRateCandidate(product.getId(), product.getProductName(),
                product.getCompany().getCompanyName(), product.getSourceType().name(), median,
                basis.getRate(), basis.getPreferredRate(), basis.getTerm(), basis.getRateType(),
                product.getSyncedAt(), List.copyOf(unmatched));
    }

    /**
     * 후보 목록에서 기준금리와 응답 단위 미일치 조건을 만든다.
     *
     * <p>응답 단위 값은 <b>후보별 조건의 합집합</b>이다. topN으로 잘린 뒤가 아니라 전체 후보를 기준으로
     * 하므로 {@code comparableProductCount}와 같은 모집단을 가리킨다.
     */
    private DepositRateReference reference(List<DepositRateCandidate> candidates) {
        List<String> unmatched = candidates.stream()
                .flatMap(candidate -> candidate.unmatchedConditions().stream())
                .distinct()
                .sorted()
                .toList();
        LocalDateTime oldest = candidates.stream().map(DepositRateCandidate::syncedAt)
                .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
        LocalDateTime latest = candidates.stream().map(DepositRateCandidate::syncedAt)
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        if (candidates.size() < DepositRateReference.MINIMUM_PRODUCTS) {
            // 후보는 유지한다. 표본이 부족해 기준금리를 만들지 못한 것과 후보가 없는 것은 다른 상태다.
            return new DepositRateReference(null, null, candidates.size(), candidates, unmatched, oldest, latest);
        }
        BigDecimal median = median(candidates.stream().map(DepositRateCandidate::representativeRate).toList());
        String confidence = candidates.size() == DepositRateReference.MINIMUM_PRODUCTS
                ? DepositRateReference.CONFIDENCE_LOW : DepositRateReference.CONFIDENCE_NORMAL;
        return new DepositRateReference(median, confidence, candidates.size(), candidates, unmatched, oldest, latest);
    }

    /** 짝수 개면 가운데 두 값의 산술평균이다. 정렬은 반올림 전 값으로 하고 표시만 넷째 자리로 맞춘다. */
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
