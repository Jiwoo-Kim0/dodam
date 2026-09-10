package com.ssafy.dodam.domain.deposit.service;

import com.ssafy.dodam.domain.deposit.dto.response.DepositRateCandidateResponse;
import com.ssafy.dodam.domain.deposit.dto.response.DepositRateComparisonResponse;
import com.ssafy.dodam.domain.deposit.repository.DepositAccountRow;
import com.ssafy.dodam.domain.deposit.repository.DepositMarketProductReader;
import com.ssafy.dodam.domain.product.entity.FinancialCompany;
import com.ssafy.dodam.domain.product.entity.FinancialProduct;
import com.ssafy.dodam.domain.product.repository.FinancialCompanyRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 보유 예금과 상품 DB 공시금리를 비교한다.
 *
 * <p>대출 금리비교(533)와 같은 계층 구조·판정 어휘를 쓴다. 다른 점은 두 가지다. 예금은 옵션의 기본금리
 * {@code rate}를 쓰고, <b>금리가 높을수록 유리하므로 편차의 부호 해석이 반대</b>다. 후자는
 * {@code comparisonDirection}으로 응답에 명시한다.
 *
 * <p>이 API는 참고 지표만 만든다. 갈아타기 가능 여부나 예상 이자 차액을 계산하지 않는다.
 */
@Service
@Transactional(readOnly = true)
public class DepositRateComparisonService {
    private static final String STATUS_COMPLETE = "COMPLETE";
    private static final String STATUS_PARTIAL = "PARTIAL";
    private static final String STATUS_UNAVAILABLE = "UNAVAILABLE";
    private static final String COMPARISON_DIRECTION = "HIGHER_IS_BETTER";
    private static final String STATUS_CLOSED = "CLOSED";

    private static final String REASON_NOT_COMPARABLE_STATUS = "NOT_COMPARABLE_STATUS";
    private static final String REASON_MISSING_DEPOSIT_DETAIL = "MISSING_DEPOSIT_DETAIL";
    private static final String REASON_GROUP_UNKNOWN = "COMPARISON_GROUP_UNKNOWN";
    private static final String REASON_UNSUPPORTED_TYPE = "UNSUPPORTED_DEPOSIT_TYPE";
    private static final String REASON_INSUFFICIENT = "INSUFFICIENT_COMPARABLE_PRODUCTS";
    private static final String REASON_INDUSTRY_UNKNOWN = "INDUSTRY_UNKNOWN";
    private static final String REASON_MISSING_TERM = "MISSING_DEPOSIT_TERM";
    private static final String REASON_TERM_OUT_OF_RANGE = "DEPOSIT_TERM_OUT_OF_COMPARABLE_RANGE";
    private static final String REASON_INVALID_TERMS = "INVALID_TERMS";
    private static final String UNMATCHED_OTHER_INDUSTRY = "OTHER_INDUSTRY_REFERENCE";

    /** 판정 근거를 숨기지 않기 위한 고정 가정 코드. 코드명 오름차순으로 반환한다. */
    private static final List<String> ASSUMPTIONS = List.of(
            "DISCLOSED_SNAPSHOT_NOT_REALTIME",
            "EXCLUDES_PREFERENTIAL_RATE",
            "REFERENCE_IS_NOT_APPLIED_RATE");

    private final DepositAccountLookup lookup;
    private final DepositMarketProductReader marketProducts;
    private final FinancialCompanyRepository companies;
    private final DepositRateComparisonCalculator calculator;
    private final DepositClock clock;

    public DepositRateComparisonService(
            DepositAccountLookup lookup, DepositMarketProductReader marketProducts,
            FinancialCompanyRepository companies, DepositRateComparisonCalculator calculator,
            DepositClock clock
    ) {
        this.lookup = lookup;
        this.marketProducts = marketProducts;
        this.companies = companies;
        this.calculator = calculator;
        this.clock = clock;
    }

    public DepositRateComparisonResponse rateComparison(
            Long userId, String accountNo, Integer term, String industry, int topN
    ) {
        DepositAccountRow deposit = lookup.require(userId, accountNo);
        LocalDate today = clock.today();

        // 해지된 예금에는 견줄 현재 금리가 없다. 잔액 0은 만기 후 미인출(INACTIVE)일 수 있어 조건에 넣지 않는다.
        if (STATUS_CLOSED.equals(deposit.status())) {
            return unavailable(today, deposit, List.of(REASON_NOT_COMPARABLE_STATUS));
        }
        if (!deposit.detailsAvailable()) {
            return unavailable(today, deposit, List.of(REASON_MISSING_DEPOSIT_DETAIL));
        }
        Evaluation evaluation = evaluate(deposit, term, industry,
                marketProducts.findAllDepositProducts(), sectors());
        if (evaluation.reference() == null) {
            return unavailable(today, deposit, evaluation.reasons());
        }
        DepositRateReference reference = evaluation.reference();
        List<String> unmatched = new ArrayList<>(reference.unmatchedConditions());
        unmatched.addAll(evaluation.unmatched());
        List<DepositRateCandidateResponse> candidates = reference.candidates().stream()
                .limit(topN)
                .map(DepositRateComparisonService::candidate)
                .toList();
        BigDecimal difference = reference.sufficient()
                ? deposit.interestRate().subtract(reference.referenceRate()) : null;
        return new DepositRateComparisonResponse(today, ASSUMPTIONS, deposit.accountId(),
                deposit.accountName(), deposit.accountNoMasked(), deposit.bankName(),
                deposit.balance(), deposit.interestRate(),
                reference.sufficient() ? STATUS_COMPLETE : STATUS_PARTIAL,
                reference.sufficient() ? List.of() : List.of(REASON_INSUFFICIENT),
                evaluation.group(), evaluation.band().label(), evaluation.industry(),
                DepositRateComparisonCalculator.REFERENCE_METHOD, reference.referenceRate(),
                reference.confidence(), difference, COMPARISON_DIRECTION,
                reference.comparableProductCount(), sorted(unmatched),
                reference.oldestSyncedAt(), reference.latestSyncedAt(), candidates);
    }

    /**
     * 한 예금의 비교 조건을 확정하고 기준금리를 계산한다.
     *
     * <p>비교군은 연결 상품의 saving_type으로 정한다. {@code deposit_saving}에는 예금 유형 컬럼이 없어
     * 연결 상품 없이는 확정금리와 지수연동을 구분할 수 없다.
     */
    private Evaluation evaluate(DepositAccountRow deposit, Integer requestedTerm, String requestedIndustry,
            List<FinancialProduct> products, Map<String, String> sectors) {
        if (deposit.interestRate() == null || deposit.interestRate().signum() < 0) {
            return Evaluation.unavailable(List.of(REASON_INVALID_TERMS));
        }
        Integer contractMonths = contractMonths(deposit);
        Integer months = requestedTerm != null ? requestedTerm : contractMonths;
        DepositTermBand band = DepositTermBand.of(months);
        if (band == null) {
            // 기간을 모르는 것과 아는 기간이 비교 범위 밖인 것은 다른 상태다. 사유를 뭉뚱그리면
            // 클라이언트가 "만기일을 채워 달라"와 "비교할 상품이 없다"를 구분해 안내할 수 없다.
            // 요청 term은 컨트롤러가 1~120으로 이미 걸러내므로 범위 밖은 계약 기간에서만 나온다.
            return Evaluation.unavailable(
                    List.of(months == null ? REASON_MISSING_TERM : REASON_TERM_OUT_OF_RANGE));
        }
        Optional<String> savingType = deposit.financialProductId() == null
                ? Optional.empty()
                : products.stream()
                        .filter(product -> product.getId().equals(deposit.financialProductId()))
                        .map(FinancialProduct::getSavingType)
                        .filter(Objects::nonNull)
                        .findFirst();
        if (savingType.isEmpty()) {
            // 연결 상품이 없거나 상품의 saving_type이 비어 세부 유형을 확정할 수 없다.
            return Evaluation.unavailable(List.of(REASON_GROUP_UNKNOWN));
        }
        String group = DepositComparisonPolicy.groupOf(savingType.get());
        if (group == null) {
            return Evaluation.unavailable(List.of(REASON_UNSUPPORTED_TYPE));
        }
        String ownSector = sectors.get(deposit.bankCode());
        String industry = requestedIndustry != null ? requestedIndustry : ownSector;
        if (industry == null) {
            // 보유 업권을 모르면 요청 업권을 보유 업권으로 간주하지 않는다.
            return Evaluation.unavailable(List.of(REASON_INDUSTRY_UNKNOWN));
        }
        List<String> unmatched = new ArrayList<>();
        if (ownSector != null && !industry.equals(ownSector)) {
            unmatched.add(UNMATCHED_OTHER_INDUSTRY);
        }
        DepositRateReference reference =
                calculator.calculate(deposit, products, group, band, industry, contractMonths);
        return new Evaluation(group, band, industry, reference, List.of(), unmatched);
    }

    private static Integer contractMonths(DepositAccountRow deposit) {
        if (deposit.openingDate() == null || deposit.expiryDate() == null) {
            return null;
        }
        return (int) ChronoUnit.MONTHS.between(deposit.openingDate(), deposit.expiryDate());
    }

    /** account.bank_code는 financial_company.company_code와 같은 값을 쓴다. 별도 대응표가 없다. */
    private Map<String, String> sectors() {
        return companies.findAll().stream()
                .filter(company -> company.getSector() != null)
                .collect(Collectors.toMap(
                        FinancialCompany::getCompanyCode, FinancialCompany::getSector, (first, second) -> first));
    }

    private DepositRateComparisonResponse unavailable(
            LocalDate today, DepositAccountRow deposit, List<String> reasons
    ) {
        return new DepositRateComparisonResponse(today, ASSUMPTIONS, deposit.accountId(),
                deposit.accountName(), deposit.accountNoMasked(), deposit.bankName(),
                deposit.balance(), deposit.interestRate(), STATUS_UNAVAILABLE, sorted(reasons),
                null, null, null, DepositRateComparisonCalculator.REFERENCE_METHOD, null, null,
                null, COMPARISON_DIRECTION, 0, List.of(), null, null, List.of());
    }

    private static DepositRateCandidateResponse candidate(DepositRateCandidate candidate) {
        return new DepositRateCandidateResponse(candidate.productId(), candidate.productName(),
                candidate.institutionName(), candidate.sourceType(), candidate.representativeRate(),
                candidate.disclosedRate(), candidate.disclosedPreferredRate(), candidate.term(),
                candidate.rateType(), candidate.syncedAt(), candidate.unmatchedConditions());
    }

    private static List<String> sorted(List<String> values) {
        return List.copyOf(new TreeSet<>(values));
    }

    /** 비교 조건 확정 결과. reference가 null이면 조건 자체를 만들지 못한 상태다. */
    private record Evaluation(String group, DepositTermBand band, String industry,
                              DepositRateReference reference, List<String> reasons, List<String> unmatched) {
        static Evaluation unavailable(List<String> reasons) {
            return new Evaluation(null, null, null, null, reasons, List.of());
        }
    }
}
