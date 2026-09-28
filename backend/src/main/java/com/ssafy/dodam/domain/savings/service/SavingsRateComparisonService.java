package com.ssafy.dodam.domain.savings.service;

import com.ssafy.dodam.domain.product.entity.FinancialCompany;
import com.ssafy.dodam.domain.product.entity.FinancialProduct;
import com.ssafy.dodam.domain.product.entity.FinancialProductOption;
import com.ssafy.dodam.domain.product.entity.ProductType;
import com.ssafy.dodam.domain.product.repository.FinancialCompanyRepository;
import com.ssafy.dodam.domain.product.repository.FinancialProductRepository;
import com.ssafy.dodam.domain.savings.dto.response.SavingsRateCandidateResponse;
import com.ssafy.dodam.domain.savings.dto.response.SavingsRateComparisonResponse;
import com.ssafy.dodam.domain.savings.repository.SavingsAccountRow;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보유 적금과 상품 DB 공시금리를 비교한다.
 *
 * <p>예금 비교(536)와 같은 계층 구조·판정 어휘·응답 필드를 쓴다. 두 API가 다른 모양이면 화면이 같은
 * 비교를 두 번 구현해야 한다. 적금 고유의 차이는 비교군 판정 방식({@link SavingsComparisonPolicy})과
 * 기간 밴드 폭뿐이다.
 *
 * <p>기준금리는 <b>기본금리(option.rate) 중앙값</b>이다. 우대금리를 쓰지 않는 이유는 적금에서 더 중요하다.
 * 시연 적금 옵션의 우대금리는 기본금리의 두세 배까지 벌어지는데(최대 10.25%) 급여이체·카드실적 같은
 * 조건 충족 여부를 보유 적금 쪽에서 확인할 수 없다. 우대금리로 비교하면 조건을 채우지 못한 사용자에게
 * 도달 불가능한 기준을 보여 준다. 근거 옵션의 우대금리는 후보에 실어 상한만 알린다.
 */
@Service
@Transactional(readOnly = true)
public class SavingsRateComparisonService {
    static final String REFERENCE_METHOD = "DISCLOSED_BASE_RATE_MEDIAN";
    private static final String STATUS_COMPLETE = "COMPLETE";
    private static final String STATUS_PARTIAL = "PARTIAL";
    private static final String STATUS_UNAVAILABLE = "UNAVAILABLE";
    private static final String COMPARISON_DIRECTION = "HIGHER_IS_BETTER";
    private static final String STATUS_CLOSED = "CLOSED";
    private static final String CONFIDENCE_LOW = "LOW";
    private static final String CONFIDENCE_NORMAL = "NORMAL";
    /** 서로 다른 상품이 2개 미만이면 중앙값을 만들지 않는다. 예금·대출 비교와 같은 기준이다. */
    private static final int MINIMUM_PRODUCTS = 2;
    private static final int RATE_SCALE = 4;

    private static final String REASON_NOT_COMPARABLE_STATUS = "NOT_COMPARABLE_STATUS";
    private static final String REASON_MISSING_SAVINGS_DETAIL = "MISSING_SAVINGS_DETAIL";
    private static final String REASON_GROUP_UNKNOWN = "COMPARISON_GROUP_UNKNOWN";
    private static final String REASON_INSUFFICIENT = "INSUFFICIENT_COMPARABLE_PRODUCTS";
    private static final String REASON_INDUSTRY_UNKNOWN = "INDUSTRY_UNKNOWN";
    private static final String REASON_MISSING_TERM = "MISSING_SAVINGS_TERM";
    private static final String REASON_TERM_OUT_OF_RANGE = "SAVINGS_TERM_OUT_OF_COMPARABLE_RANGE";
    private static final String REASON_INVALID_TERMS = "INVALID_TERMS";
    private static final String UNMATCHED_TERM_BAND = "TERM_BAND_MATCH";
    private static final String UNMATCHED_RATE_TYPE = "RATE_TYPE_NOT_MATCHED";
    private static final String UNMATCHED_OTHER_INDUSTRY = "OTHER_INDUSTRY_REFERENCE";

    private static final List<String> ASSUMPTIONS = List.of(
            "DISCLOSED_SNAPSHOT_NOT_REALTIME",
            "EXCLUDES_PREFERENTIAL_RATE",
            "REFERENCE_IS_NOT_APPLIED_RATE");

    private final SavingsQueryService savings;
    private final FinancialProductRepository products;
    private final FinancialCompanyRepository companies;
    private final SavingsClock clock;

    public SavingsRateComparisonService(
            SavingsQueryService savings, FinancialProductRepository products,
            FinancialCompanyRepository companies, SavingsClock clock
    ) {
        this.savings = savings;
        this.products = products;
        this.companies = companies;
        this.clock = clock;
    }

    public SavingsRateComparisonResponse rateComparison(
            Long userId, String accountNo, Integer term, String industry, int topN
    ) {
        SavingsAccountRow account = savings.owned(userId, accountNo);
        LocalDate today = clock.today();

        if (STATUS_CLOSED.equals(account.status())) {
            return unavailable(today, account, List.of(REASON_NOT_COMPARABLE_STATUS));
        }
        if (!account.detailsAvailable()) {
            return unavailable(today, account, List.of(REASON_MISSING_SAVINGS_DETAIL));
        }
        if (account.interestRate() == null || account.interestRate().signum() < 0) {
            return unavailable(today, account, List.of(REASON_INVALID_TERMS));
        }
        Integer contractMonths = contractMonths(account);
        Integer months = term != null ? term : contractMonths;
        SavingsTermBand band = SavingsTermBand.of(months);
        if (band == null) {
            // 기간을 모르는 것과 아는 기간이 비교 범위 밖인 것은 다른 상태다. 요청 term은 컨트롤러가
            // 1~120으로 이미 걸러내므로 범위 밖은 계약 기간에서만 나온다.
            return unavailable(today, account,
                    List.of(months == null ? REASON_MISSING_TERM : REASON_TERM_OUT_OF_RANGE));
        }

        List<FinancialProduct> catalog = savingsProducts();
        Optional<String> savingType = account.financialProductId() == null ? Optional.empty()
                : catalog.stream()
                        .filter(product -> product.getId().equals(account.financialProductId()))
                        .map(FinancialProduct::getSavingType)
                        .filter(Objects::nonNull)
                        .findFirst();
        if (savingType.isEmpty()) {
            // 연결 상품이 없거나, 적금 상품이 아니거나, saving_type이 비어 비교군을 확정할 수 없다.
            return unavailable(today, account, List.of(REASON_GROUP_UNKNOWN));
        }
        String group = SavingsComparisonPolicy.groupOf(savingType.get());

        Map<String, String> sectors = sectors();
        String ownSector = sectors.get(account.bankCode());
        String resolvedIndustry = industry != null ? industry : ownSector;
        if (resolvedIndustry == null) {
            return unavailable(today, account, List.of(REASON_INDUSTRY_UNKNOWN));
        }
        List<String> extraUnmatched = new ArrayList<>();
        if (ownSector != null && !resolvedIndustry.equals(ownSector)) {
            extraUnmatched.add(UNMATCHED_OTHER_INDUSTRY);
        }

        List<Candidate> candidates = candidates(account, catalog, group, band, resolvedIndustry,
                contractMonths, sectors);
        boolean sufficient = candidates.size() >= MINIMUM_PRODUCTS;
        BigDecimal referenceRate = sufficient
                ? median(candidates.stream().map(Candidate::representativeRate).toList()) : null;
        List<String> unmatched = new ArrayList<>(extraUnmatched);
        candidates.forEach(candidate -> unmatched.addAll(candidate.unmatchedConditions()));

        return new SavingsRateComparisonResponse(today, ASSUMPTIONS, account.accountId(),
                account.accountName(), account.accountNoMasked(), account.bankName(),
                account.balance(), account.interestRate(),
                sufficient ? STATUS_COMPLETE : STATUS_PARTIAL,
                sufficient ? List.of() : List.of(REASON_INSUFFICIENT),
                group, band.label(), resolvedIndustry, REFERENCE_METHOD, referenceRate,
                sufficient ? (candidates.size() == MINIMUM_PRODUCTS ? CONFIDENCE_LOW : CONFIDENCE_NORMAL) : null,
                referenceRate == null ? null : account.interestRate().subtract(referenceRate),
                COMPARISON_DIRECTION, candidates.size(), sorted(unmatched),
                syncedAt(candidates, true), syncedAt(candidates, false),
                candidates.stream().limit(topN).map(SavingsRateComparisonService::response).toList());
    }

    /**
     * 비교 조건에 맞는 상품별 대표값을 만든다.
     *
     * <p>상품마다 옵션 수가 달라도 동일 가중이 되도록 상품 단위로 한 번 접는다. 대표값에 가장 가까운
     * 옵션을 근거로 남겨 사용자가 어떤 조건을 본 것인지 확인할 수 있게 한다.
     */
    private List<Candidate> candidates(
            SavingsAccountRow account, List<FinancialProduct> catalog, String group, SavingsTermBand band,
            String industry, Integer contractMonths, Map<String, String> sectors
    ) {
        List<Candidate> candidates = new ArrayList<>();
        for (FinancialProduct product : catalog) {
            if (product.getSavingType() == null
                    || !SavingsComparisonPolicy.sameGroup(group, product.getSavingType())) {
                continue;
            }
            // 보유 상품 자신을 모집단에 넣으면 자기 금리와 비교하게 된다.
            if (account.financialProductId() != null && account.financialProductId().equals(product.getId())) {
                continue;
            }
            if (!industry.equals(product.getCompany().getSector())) {
                continue;
            }
            List<FinancialProductOption> matched = product.getOptions().stream()
                    .filter(option -> option.getTerm() != null && band.contains(option.getTerm()))
                    .filter(option -> option.getRate() != null && option.getRate().signum() >= 0)
                    .toList();
            if (matched.isEmpty()) {
                continue;
            }
            BigDecimal representative = median(matched.stream()
                    .map(FinancialProductOption::getRate).toList());
            FinancialProductOption basis = matched.stream()
                    .min(Comparator.comparing((FinancialProductOption option) ->
                                    option.getRate().subtract(representative).abs())
                            .thenComparing(FinancialProductOption::getId))
                    .orElseThrow();
            // 미일치 조건은 후보마다 따로 모은다. 하나가 어긋났다고 나머지 후보까지 어긋난 것은 아니다.
            TreeSet<String> unmatched = new TreeSet<>();
            if (!band.contains(contractMonths) || !basis.getTerm().equals(contractMonths)) {
                unmatched.add(UNMATCHED_TERM_BAND);
            }
            if (basis.getRateType() != null) {
                // 확정·변동 조건은 판정에 쓰지 않는다. 보유 적금에 대응 값이 없어 동질성을 확인할 수 없다.
                unmatched.add(UNMATCHED_RATE_TYPE);
            }
            candidates.add(new Candidate(product.getId(), product.getProductName(),
                    product.getCompany().getCompanyName(), product.getSourceType().name(), representative,
                    basis.getRate(), basis.getPreferredRate(), basis.getTerm(), product.getSavingType(),
                    product.getSyncedAt(), List.copyOf(unmatched)));
        }
        return candidates.stream()
                .sorted(Comparator.comparing(Candidate::representativeRate)
                        .thenComparing(Candidate::productId))
                .toList();
    }

    /**
     * 요청당 적금 상품 전량을 한 번 읽는다.
     *
     * <p>시연 데이터의 적금 상품은 53개, 옵션은 81건이라 전량 로드가 조건별 조회보다 단순하다.
     * {@code financial_product_option}에는 product_id 인덱스만 있어 term을 DB에서 거르는 이점도 없다.
     * 지연 로딩으로 옵션을 건별 조회하면 N+1이 되므로 id를 먼저 모아 한 번에 채운다.
     */
    private List<FinancialProduct> savingsProducts() {
        List<Long> ids = products.findAllByProductTypeOrderByIdAsc(ProductType.SAVINGS).stream()
                .map(FinancialProduct::getId)
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return products.findDetailsByIdIn(ids).stream()
                .sorted(Comparator.comparing(FinancialProduct::getId))
                .toList();
    }

    /** account.bank_code는 financial_company.company_code와 같은 값을 쓴다. 별도 대응표가 없다. */
    private Map<String, String> sectors() {
        return companies.findAll().stream()
                .filter(company -> company.getSector() != null)
                .collect(Collectors.toMap(
                        FinancialCompany::getCompanyCode, FinancialCompany::getSector, (first, second) -> first));
    }

    private static Integer contractMonths(SavingsAccountRow account) {
        if (account.openingDate() == null || account.expiryDate() == null) {
            return null;
        }
        return (int) ChronoUnit.MONTHS.between(account.openingDate(), account.expiryDate());
    }

    private static LocalDateTime syncedAt(List<Candidate> candidates, boolean oldest) {
        Comparator<LocalDateTime> order = Comparator.naturalOrder();
        return candidates.stream().map(Candidate::syncedAt).filter(Objects::nonNull)
                .min(oldest ? order : order.reversed()).orElse(null);
    }

    private SavingsRateComparisonResponse unavailable(
            LocalDate today, SavingsAccountRow account, List<String> reasons
    ) {
        return new SavingsRateComparisonResponse(today, ASSUMPTIONS, account.accountId(),
                account.accountName(), account.accountNoMasked(), account.bankName(),
                account.balance(), account.interestRate(), STATUS_UNAVAILABLE, sorted(reasons),
                null, null, null, REFERENCE_METHOD, null, null, null, COMPARISON_DIRECTION,
                0, List.of(), null, null, List.of());
    }

    private static SavingsRateCandidateResponse response(Candidate candidate) {
        return new SavingsRateCandidateResponse(candidate.productId(), candidate.productName(),
                candidate.institutionName(), candidate.sourceType(), candidate.representativeRate(),
                candidate.disclosedRate(), candidate.disclosedPreferredRate(), candidate.term(),
                candidate.savingType(), candidate.syncedAt(), candidate.unmatchedConditions());
    }

    private static List<String> sorted(List<String> values) {
        return List.copyOf(new TreeSet<>(values));
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

    private record Candidate(
            Long productId, String productName, String institutionName, String sourceType,
            BigDecimal representativeRate, BigDecimal disclosedRate, BigDecimal disclosedPreferredRate,
            Integer term, String savingType, LocalDateTime syncedAt, List<String> unmatchedConditions
    ) {
    }
}
