package com.ssafy.dodam.domain.loan.service;

import static com.ssafy.dodam.domain.loan.repository.LoanSpecifications.ownedBy;

import com.ssafy.dodam.domain.loan.dto.response.LoanHighInterestItemResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanHighInterestResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanRateCandidateResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanRateComparisonResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanUnavailableResponse;
import com.ssafy.dodam.domain.loan.entity.Loan;
import com.ssafy.dodam.domain.loan.entity.LoanStatus;
import com.ssafy.dodam.domain.loan.entity.LoanType;
import com.ssafy.dodam.domain.loan.repository.LoanMarketProductReader;
import com.ssafy.dodam.domain.loan.repository.LoanRepository;
import com.ssafy.dodam.domain.product.entity.FinancialCompany;
import com.ssafy.dodam.domain.product.entity.FinancialProduct;
import com.ssafy.dodam.domain.product.repository.FinancialCompanyRepository;
import com.ssafy.dodam.global.exception.InvalidRequestException;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import java.math.BigDecimal;
import java.time.LocalDate;
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
 * 보유 대출과 상품 DB 공시금리를 비교하고 고금리 점검 대상을 판정한다.
 *
 * <p>두 API가 같은 계산을 공유한다. 판정 기준은 공시 자료 기반 참고 지표이며 대환 가능 여부나 절감액을
 * 만들지 않는다. 대환 계산은 명세 17의 시뮬레이션이, 목표 반영 우선순위는 명세 16이 소유한다.
 */
@Service
@Transactional(readOnly = true)
public class LoanRateComparisonService {
    /**
     * 초기 기준편차 1.0%p.
     *
     * <p>기획안의 α는 "0.0%p -> 추후 결정"으로만 적혀 있어 확정값으로 읽지 않았다. 확정 정책으로 계산한
     * 시연 데이터의 편차 분포는 +3.03 / +0.17 / -0.83 / -1.35%p이며 이 값이면 1건이 판정된다. 0.2%p까지
     * 낮추면 2건이 되지만 통계적 근거 없이 건수를 늘리는 선택이라 하지 않았다. **서비스 초기 임계값이며
     * 검증된 기준이 아니다.**
     */
    private static final BigDecimal RATE_SPREAD_THRESHOLD = new BigDecimal("1.0000");
    public static final String THRESHOLD_MODE = "MARKET_SPREAD";

    private static final String STATUS_COMPLETE = "COMPLETE";
    private static final String STATUS_PARTIAL = "PARTIAL";
    private static final String STATUS_UNAVAILABLE = "UNAVAILABLE";
    private static final String REASON_NOT_OUTSTANDING = "NOT_OUTSTANDING";
    private static final String REASON_GROUP_UNKNOWN = "COMPARISON_GROUP_UNKNOWN";
    private static final String REASON_UNSUPPORTED_TYPE = "UNSUPPORTED_LOAN_TYPE";
    private static final String REASON_INSUFFICIENT = "INSUFFICIENT_COMPARABLE_PRODUCTS";
    private static final String REASON_INDUSTRY_UNKNOWN = "INDUSTRY_UNKNOWN";
    private static final String REASON_MISSING_LOAN_PERIOD = "MISSING_LOAN_PERIOD";
    private static final String REASON_INVALID_TERMS = "INVALID_TERMS";
    private static final String REASON_INCOMPLETE_LOAN_DATA = "INCOMPLETE_LOAN_DATA";
    private static final String UNMATCHED_OTHER_INDUSTRY = "OTHER_INDUSTRY_REFERENCE";

    private final LoanRepository loans;
    private final LoanMarketProductReader marketProducts;
    private final FinancialCompanyRepository companies;
    private final LoanRateComparisonCalculator calculator;
    private final SensitiveNumberHasher hasher;
    private final LoanAnalysisClock clock;

    public LoanRateComparisonService(
            LoanRepository loans, LoanMarketProductReader marketProducts, FinancialCompanyRepository companies,
            LoanRateComparisonCalculator calculator, SensitiveNumberHasher hasher, LoanAnalysisClock clock
    ) {
        this.loans = loans;
        this.marketProducts = marketProducts;
        this.companies = companies;
        this.calculator = calculator;
        this.hasher = hasher;
        this.clock = clock;
    }

    public LoanRateComparisonResponse rateComparison(Long userId, String accountNo, Integer term,
            String industry, int topN) {
        Loan loan = loans.findByUserIdAndAccountNoHash(userId, hasher.hash(accountNo))
                .orElseThrow(() -> new ResourceNotFoundException("대출을 찾을 수 없습니다."));
        LocalDate today = clock.today();
        List<String> assumptions = assumptions(List.of(loan));

        if (!loan.getStatus().isOutstanding() || loan.getBalance() == null || loan.getBalance() <= 0) {
            return unavailableComparison(today, assumptions, loan, List.of(REASON_NOT_OUTSTANDING));
        }
        Evaluation evaluation = evaluate(loan, term, industry, marketProducts.findAllLoanProducts(), sectors());
        if (evaluation.reference() == null) {
            return unavailableComparison(today, assumptions, loan, evaluation.reasons());
        }
        LoanRateReference reference = evaluation.reference();
        List<String> unmatched = new ArrayList<>(reference.unmatchedConditions());
        unmatched.addAll(evaluation.unmatched());
        List<LoanRateCandidateResponse> candidates = reference.candidates().stream()
                .limit(topN)
                .map(LoanRateComparisonService::candidate)
                .toList();
        BigDecimal difference = reference.sufficient()
                ? loan.getInterestRate().subtract(reference.referenceRate()) : null;
        return new LoanRateComparisonResponse(today, assumptions, loan.getId(), loan.getAccountName(),
                loan.getAccountNoMasked(), loan.getInstitutionName(), loan.getBalance(), loan.getInterestRate(),
                reference.sufficient() ? STATUS_COMPLETE : STATUS_PARTIAL,
                reference.sufficient() ? List.of() : List.of(REASON_INSUFFICIENT),
                evaluation.group(), evaluation.band().label(), evaluation.industry(),
                LoanRateComparisonCalculator.REFERENCE_METHOD, reference.referenceRate(), reference.confidence(),
                difference, reference.comparableProductCount(), sorted(unmatched),
                reference.oldestSyncedAt(), reference.latestSyncedAt(), candidates);
    }

    public LoanHighInterestResponse highInterest(Long userId, String loanType, String industry) {
        LocalDate today = clock.today();
        LoanType requested = loanType == null ? null : parseLoanType(loanType);
        List<Loan> outstanding = loans.findAll(ownedBy(userId)).stream()
                .filter(loan -> loan.getStatus().isOutstanding())
                .filter(loan -> loan.getBalance() != null && loan.getBalance() > 0)
                .filter(loan -> requested == null || loan.getLoanType() == requested)
                .sorted(Comparator.comparing(Loan::getId))
                .toList();

        List<FinancialProduct> products = outstanding.isEmpty()
                ? List.of() : marketProducts.findAllLoanProducts();
        Map<String, String> sectors = outstanding.isEmpty() ? Map.of() : sectors();
        List<LoanHighInterestItemResponse> items = new ArrayList<>();
        List<LoanUnavailableResponse> unavailableLoans = new ArrayList<>();
        int evaluated = 0;
        for (Loan loan : outstanding) {
            // 업권 필터는 보유 대출의 업권에 적용한다. 비교 API의 다른 업권 참고 비교와 다른 개념이다.
            String sector = sectors.get(loan.getInstitutionCode());
            if (industry != null && !industry.equals(sector)) {
                continue;
            }
            Evaluation evaluation = evaluate(loan, null, industry, products, sectors);
            if (evaluation.reference() == null || !evaluation.reference().sufficient()) {
                List<String> reasons = evaluation.reference() == null
                        ? evaluation.reasons() : List.of(REASON_INSUFFICIENT);
                unavailableLoans.add(new LoanUnavailableResponse(loan.getId(), sorted(reasons)));
                continue;
            }
            evaluated++;
            LoanRateReference reference = evaluation.reference();
            BigDecimal difference = loan.getInterestRate().subtract(reference.referenceRate());
            if (difference.compareTo(RATE_SPREAD_THRESHOLD) < 0) {
                continue;
            }
            items.add(new LoanHighInterestItemResponse(loan.getId(), loan.getAccountName(),
                    loan.getAccountNoMasked(), loan.getInstitutionName(), loan.getBalance(), loan.getInterestRate(),
                    loan.getStatus() == LoanStatus.OVERDUE, evaluation.group(),
                    LoanRateComparisonCalculator.REFERENCE_METHOD, reference.referenceRate(),
                    reference.confidence(), difference, reference.comparableProductCount(),
                    STATUS_COMPLETE, List.of(THRESHOLD_MODE)));
        }
        List<LoanHighInterestItemResponse> sorted = items.stream()
                .sorted(Comparator.comparing(LoanHighInterestItemResponse::rateDifference, Comparator.reverseOrder())
                        .thenComparing(LoanHighInterestItemResponse::interestRate, Comparator.reverseOrder())
                        .thenComparing(LoanHighInterestItemResponse::loanId))
                .toList();
        return new LoanHighInterestResponse(today, assumptions(outstanding), unavailableLoans.isEmpty(),
                unavailableLoans.isEmpty() ? List.of() : List.of(REASON_INCOMPLETE_LOAN_DATA),
                List.copyOf(unavailableLoans), THRESHOLD_MODE, RATE_SPREAD_THRESHOLD,
                LoanRateComparisonCalculator.REFERENCE_METHOD, evaluated, sorted.size(),
                unavailableLoans.size(), sorted);
    }

    /**
     * 한 대출의 비교 조건을 확정하고 기준금리를 계산한다.
     *
     * <p>비교군은 연결 상품의 loan_type으로 정한다. 보유 대출의 LoanType 5종은 세부 조건을 구분하지 못해
     * 일반 신용과 중금리를 같은 모집단으로 만든다.
     */
    private Evaluation evaluate(Loan loan, Integer requestedTerm, String requestedIndustry,
            List<FinancialProduct> products, Map<String, String> sectors) {
        if (loan.getInterestRate() == null || loan.getInterestRate().signum() < 0) {
            return Evaluation.unavailable(List.of(REASON_INVALID_TERMS));
        }
        Integer months = requestedTerm != null ? requestedTerm : loan.getLoanPeriod();
        LoanTermBand band = LoanTermBand.of(months);
        if (band == null) {
            return Evaluation.unavailable(List.of(REASON_MISSING_LOAN_PERIOD));
        }
        Optional<String> productLoanType = productLoanType(loan, products);
        if (productLoanType.isEmpty()) {
            // 연결 상품이 없거나 상품의 loan_type이 비어 세부 유형을 확정할 수 없다.
            return Evaluation.unavailable(List.of(REASON_GROUP_UNKNOWN));
        }
        String group = LoanComparisonPolicy.groupOf(productLoanType.get());
        if (group == null) {
            return Evaluation.unavailable(List.of(REASON_UNSUPPORTED_TYPE));
        }
        String ownSector = sectors.get(loan.getInstitutionCode());
        List<String> unmatched = new ArrayList<>();
        String industry = requestedIndustry != null ? requestedIndustry : ownSector;
        if (industry == null) {
            // 보유 업권을 모르면 요청 업권을 보유 업권으로 간주하지 않는다.
            return Evaluation.unavailable(List.of(REASON_INDUSTRY_UNKNOWN));
        }
        if (ownSector != null && !industry.equals(ownSector)) {
            unmatched.add(UNMATCHED_OTHER_INDUSTRY);
        }
        LoanRateReference reference = calculator.calculate(loan, products, group, band, industry);
        return new Evaluation(group, band, industry, reference, List.of(), unmatched);
    }

    private Optional<String> productLoanType(Loan loan, List<FinancialProduct> products) {
        if (loan.getFinancialProductId() == null) {
            return Optional.empty();
        }
        return products.stream()
                .filter(product -> product.getId().equals(loan.getFinancialProductId()))
                .map(FinancialProduct::getLoanType)
                .filter(Objects::nonNull)
                .findFirst();
    }

    /** 대출의 institution_code는 financial_company.company_code와 같은 값을 쓴다. 별도 대응표가 없다. */
    private Map<String, String> sectors() {
        return companies.findAll().stream()
                .filter(company -> company.getSector() != null)
                .collect(Collectors.toMap(
                        FinancialCompany::getCompanyCode, FinancialCompany::getSector, (first, second) -> first));
    }

    private LoanRateComparisonResponse unavailableComparison(
            LocalDate today, List<String> assumptions, Loan loan, List<String> reasons
    ) {
        return new LoanRateComparisonResponse(today, assumptions, loan.getId(), loan.getAccountName(),
                loan.getAccountNoMasked(), loan.getInstitutionName(), loan.getBalance(), loan.getInterestRate(),
                STATUS_UNAVAILABLE, sorted(reasons), null, null, null,
                LoanRateComparisonCalculator.REFERENCE_METHOD, null, null, null, 0, List.of(),
                null, null, List.of());
    }

    private static LoanRateCandidateResponse candidate(LoanRateCandidate candidate) {
        return new LoanRateCandidateResponse(candidate.productId(), candidate.productName(),
                candidate.institutionName(), candidate.sourceType(), candidate.representativeRate(),
                candidate.disclosedMinRate(), candidate.disclosedMaxRate(), candidate.term(),
                candidate.repaymentType(), candidate.rateType(), candidate.syncedAt());
    }

    private static LoanType parseLoanType(String value) {
        try {
            return LoanType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("loanType 값이 올바르지 않습니다.");
        }
    }

    /**
     * 기존 이자 분석과 같은 가정 코드를 재사용한다. 기준금리가 승인금리가 아니라는 점과 상품 자료가 저장
     * 스냅샷이라는 점은 533 고유 가정이다.
     */
    private static List<String> assumptions(List<Loan> loanList) {
        List<String> assumptions = new ArrayList<>(List.of("EXCLUDES_FEES",
                "DISCLOSED_SNAPSHOT_NOT_REALTIME", "REFERENCE_IS_NOT_APPROVAL_RATE"));
        if (loanList.stream().anyMatch(loan -> loan.getStatus() == LoanStatus.OVERDUE)) {
            assumptions.add("EXCLUDES_OVERDUE_INTEREST_AND_ARREARS");
        }
        return sorted(assumptions);
    }

    private static List<String> sorted(List<String> values) {
        return List.copyOf(new TreeSet<>(values));
    }

    /** 비교 조건 확정 결과. reference가 null이면 조건 자체를 만들지 못한 상태다. */
    private record Evaluation(String group, LoanTermBand band, String industry, LoanRateReference reference,
                              List<String> reasons, List<String> unmatched) {
        static Evaluation unavailable(List<String> reasons) {
            return new Evaluation(null, null, null, null, reasons, List.of());
        }
    }
}
