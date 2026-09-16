package com.ssafy.dodam.domain.diagnosis.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisIssue;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import com.ssafy.dodam.domain.loan.dto.response.LoanHighInterestItemResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanHighInterestResponse;
import com.ssafy.dodam.domain.loan.dto.response.LoanUnavailableResponse;
import com.ssafy.dodam.domain.loan.entity.Loan;
import com.ssafy.dodam.domain.loan.entity.LoanStatus;
import com.ssafy.dodam.domain.loan.repository.LoanMarketProductReader;
import com.ssafy.dodam.domain.loan.repository.LoanRepository;
import com.ssafy.dodam.domain.loan.service.LoanComparisonPolicy;
import com.ssafy.dodam.domain.loan.service.LoanRateComparisonService;
import com.ssafy.dodam.domain.loan.service.LoanRepaymentSchedule;
import com.ssafy.dodam.domain.loan.service.LoanRepaymentScheduleCalculator;
import com.ssafy.dodam.domain.loan.service.LoanRepaymentTerms;
import com.ssafy.dodam.domain.product.entity.FinancialCompany;
import com.ssafy.dodam.domain.product.entity.FinancialProduct;
import com.ssafy.dodam.domain.product.repository.FinancialCompanyRepository;
import com.ssafy.dodam.domain.simulation.dto.ProductSnapshot;
import com.ssafy.dodam.domain.simulation.service.ProductOptionSelector;
import com.ssafy.dodam.domain.simulation.service.SimulationSnapshotReader;
import com.ssafy.dodam.global.exception.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * LOAN/HIGH_INTEREST_RATE 규칙(S15P21D105-627): 보유 대출 금리가 비교상품 공시금리보다 높은지 판정하고 대환 절감액을 추정한다.
 *
 * <p>판정은 API 11 고금리 점검({@link LoanRateComparisonService#highInterest})을 그대로 쓴다 — 같은 비교군·기간 밴드·상환방식의
 * 공시 최저금리 중앙값보다 1.0%p 이상 높으면 대상이다. 기준금리는 실제 평균 대출금리도 승인금리도 아니므로 제목·설명은
 * "비교상품 공시금리보다 높다"로만 표현한다. 판정 불가 대출(연결 상품 없음·표본 부족 등)은 문제로 만들지 않고 스냅샷에만 남긴다.
 *
 * <p><b>대환 절감액</b>은 명세 17 대환 시뮬레이션({@code LoanRefinanceCalculator})과 같은 규칙으로 추정한다. 잔여 회차 수·기존
 * 상환방식·잔액 한도를 만족하는 옵션({@link ProductOptionSelector#selectLoan})을 가진 비교군 상품 중 공시 최저금리가 가장 낮은
 * 것을 대상으로, 기존 일정과 같은 날짜·회차·방식에서 금리만 바꿔 만기까지 총이자 차이를 계산한다. 비교 대상은 판정에 쓴 모집단과
 * 같이 보유 대출과 같은 업권으로 한정하고 보유 상품 자신은 제외한다. 조건에 맞는 상품이 없거나(금리 비교 후보가 모두 대환 조건을
 * 충족하는 것은 아니다) 일정을 만들 수 없으면(연체·만기 누락·360회 초과) 절감액 없이 사유만 남긴다.
 * 중도상환수수료·부대비용은 계산하지 않는다({@code feesIncluded = false}).
 *
 * <p>심각도는 편차가 {@link #HIGH_SEVERITY_RATE_SPREAD}%p 이상이거나 연체 중이면 HIGH, 그 외 MEDIUM이다. 서비스 초기값이며
 * 팀에서 검증한 기준이 아니다.
 */
@Component
@Transactional(readOnly = true)
public class HighInterestLoanRule {
    public static final String DOMAIN = "LOAN";
    public static final String TYPE = "HIGH_INTEREST_RATE";
    public static final BigDecimal HIGH_SEVERITY_RATE_SPREAD = new BigDecimal("2.0000");
    public static final String UNAVAILABLE_LOAN_OVERDUE = "LOAN_OVERDUE";
    public static final String UNAVAILABLE_SCHEDULE = "SCHEDULE_NOT_COMPUTABLE";
    public static final String UNAVAILABLE_NO_PRODUCT = "NO_ELIGIBLE_PRODUCT";
    static final String COVERAGE_INCOMPLETE = "INCOMPLETE_LOAN_DATA";

    private final LoanRateComparisonService comparison;
    private final LoanRepository loans;
    private final LoanMarketProductReader marketProducts;
    private final FinancialCompanyRepository companies;
    private final LoanRepaymentScheduleCalculator scheduleCalculator;
    private final ObjectMapper json;

    public HighInterestLoanRule(LoanRateComparisonService comparison, LoanRepository loans, LoanMarketProductReader marketProducts,
            FinancialCompanyRepository companies, LoanRepaymentScheduleCalculator scheduleCalculator, ObjectMapper json) {
        this.comparison = comparison;
        this.loans = loans;
        this.marketProducts = marketProducts;
        this.companies = companies;
        this.scheduleCalculator = scheduleCalculator;
        this.json = json;
    }

    public Result evaluate(Long userId, LocalDate baseDate) {
        LoanHighInterestResponse judged = comparison.highInterest(userId, null, null);
        List<Item> items = new ArrayList<>();
        if (!judged.loans().isEmpty()) {
            List<FinancialProduct> products = marketProducts.findAllLoanProducts();
            Map<String, String> sectors = companies.findAll().stream()
                    .filter(company -> company.getSector() != null)
                    .collect(Collectors.toMap(FinancialCompany::getCompanyCode, FinancialCompany::getSector, (first, second) -> first));
            for (LoanHighInterestItemResponse item : judged.loans()) {
                // highInterest가 소유권을 이미 걸렀지만, 다른 사용자의 대출 id가 섞이는 경로를 만들지 않도록 다시 확인한다
                Loan loan = loans.findById(item.loanId()).filter(l -> userId.equals(l.getUserId())).orElse(null);
                if (loan == null) {
                    continue;
                }
                RefinanceEstimate estimate = estimate(loan, item.comparisonGroup(), products, sectors, baseDate);
                boolean high = item.rateDifference().compareTo(HIGH_SEVERITY_RATE_SPREAD) >= 0 || item.requiresAttention();
                items.add(new Item(loan.getId(), loan.getAccountName(), loan.getInstitutionName(), loan.getLoanType().name(),
                        loan.getRepaymentType().name(), loan.getStatus().name(), item.balance(), item.interestRate(),
                        item.comparisonGroup(), item.referenceRate(), item.referenceConfidence(), item.rateDifference(),
                        item.comparableProductCount(), item.requiresAttention(), loan.getMaturityDate(), estimate,
                        high ? IssueSeverity.HIGH : IssueSeverity.MEDIUM));
            }
        }
        return new Result(judged.evaluatedCount() + judged.unavailableCount() > 0, judged.thresholdMode(),
                judged.rateSpreadThreshold(), judged.referenceMethod(), judged.evaluatedCount(), judged.unavailableCount(),
                judged.unavailableLoans(), List.copyOf(items));
    }

    private RefinanceEstimate estimate(Loan loan, String group, List<FinancialProduct> products, Map<String, String> sectors,
            LocalDate baseDate) {
        if (loan.getStatus() != LoanStatus.ACTIVE) {
            // 연체는 연체이자·미납 회차를 확정할 수 없어 대환 시뮬레이션도 거부한다
            return RefinanceEstimate.unavailable(UNAVAILABLE_LOAN_OVERDUE);
        }
        LoanRepaymentSchedule before = scheduleCalculator.calculate(LoanRepaymentTerms.from(loan), baseDate);
        if (before.items().isEmpty() || before.truncated()) {
            return RefinanceEstimate.unavailable(UNAVAILABLE_SCHEDULE);
        }
        int installments = before.items().size();
        LocalDate firstDate = before.items().get(0).repaymentDate();
        Set<String> loanTypes = LoanComparisonPolicy.loanTypesOf(group);
        String sector = sectors.get(loan.getInstitutionCode());
        FinancialProduct bestProduct = null;
        ProductOptionSelector.Selection best = null;
        for (FinancialProduct product : products) {
            if (!loanTypes.contains(product.getLoanType()) || product.getId().equals(loan.getFinancialProductId())
                    || (sector != null && !sector.equals(product.getCompany().getSector()))) {
                continue;
            }
            ProductOptionSelector.Selection selection;
            try {
                selection = ProductOptionSelector.selectLoan(SimulationSnapshotReader.toSnapshot(product), installments,
                        loan.getRepaymentType(), loan.getBalance());
            } catch (BusinessException notEligible) {
                // 기간·상환방식·한도가 맞는 옵션이 없는 상품은 대환 대상이 아니다
                continue;
            }
            if (selection.appliedRate().compareTo(loan.getInterestRate()) >= 0) {
                continue;
            }
            // 상품 목록은 id 오름차순이므로 같은 금리면 먼저 나온 상품이 남는다
            if (best == null || selection.appliedRate().compareTo(best.appliedRate()) < 0) {
                best = selection;
                bestProduct = product;
            }
        }
        if (best == null) {
            return RefinanceEstimate.unavailable(UNAVAILABLE_NO_PRODUCT);
        }
        LoanRepaymentSchedule after = scheduleCalculator.calculate(new LoanRepaymentTerms(loan.getBalance(), best.appliedRate(),
                loan.getRepaymentType(), loan.getRepaymentDay(), firstDate, loan.getMaturityDate()), baseDate);
        long beforeInterest = before.totalInterest();
        long afterInterest = after.totalInterest();
        long saving = Math.max(0L, beforeInterest - afterInterest);
        return new RefinanceEstimate(true, null, bestProduct.getId(), bestProduct.getProductName(),
                bestProduct.getCompany().getCompanyName(), best.appliedRate(), best.option().term(), installments,
                beforeInterest, afterInterest, saving, saving / installments, false, best.assumptions());
    }

    public DiagnosisIssue toIssue(Diagnosis diagnosis, Result result, Item item) {
        ObjectNode current = json.valueToTree(item);
        current.remove("severity");
        ObjectNode benchmark = json.createObjectNode();
        benchmark.put("thresholdMode", result.thresholdMode()).put("rateSpreadThreshold", result.rateSpreadThreshold())
                .put("referenceMethod", result.referenceMethod()).put("referenceRate", item.referenceRate())
                .put("highSeverityRateSpread", HIGH_SEVERITY_RATE_SPREAD);
        ObjectNode evidence = json.createObjectNode();
        evidence.put("ruleKey", TYPE).put("targetId", item.loanId()).put("loanId", item.loanId())
                .put("comparisonGroup", item.comparisonGroup()).put("comparableProductCount", item.comparableProductCount());
        evidence.set("refinance", json.valueToTree(item.refinance()));
        String rate = item.interestRate().stripTrailingZeros().toPlainString();
        String reference = item.referenceRate().stripTrailingZeros().toPlainString();
        String difference = item.rateDifference().stripTrailingZeros().toPlainString();
        StringBuilder description = new StringBuilder("연 " + rate + "%로 같은 조건 상품의 공시 최저금리 중앙값 " + reference + "%보다 "
                + difference + "%p 높아요.");
        if (item.refinance().available()) {
            description.append(" 공시 최저 연 ").append(item.refinance().appliedRate().stripTrailingZeros().toPlainString())
                    .append("% 상품으로 대환하면 만기까지 이자 약 ").append(won(item.refinance().interestSaving()))
                    .append("을 아낄 수 있어요(중도상환수수료·부대비용 제외).");
        } else {
            description.append(" 잔여 기간·상환방식에 맞는 대환 상품이 없어 절감액은 계산하지 못했어요.");
        }
        return DiagnosisIssue.ofRule(diagnosis.getDiagnosisId(), DOMAIN, TYPE, item.severity(),
                "'" + item.accountName() + "' 금리가 비교상품 공시금리보다 높아요", description.toString(),
                current, benchmark, evidence, diagnosis.getCreatedAt());
    }

    private static String won(long amount) {
        return String.format("%,d원", amount);
    }

    /**
     * @param applicable 평가 대상 대출(잔액이 있는 ACTIVE·OVERDUE)이 하나라도 있었는지
     * @param unavailableLoans 비교 조건을 만들지 못한 대출과 사유(API 11과 같은 코드)
     */
    public record Result(boolean applicable, String thresholdMode, BigDecimal rateSpreadThreshold, String referenceMethod,
            int evaluatedCount, int unavailableCount, List<LoanUnavailableResponse> unavailableLoans, List<Item> items) { }

    /** 계좌번호 원문·해시·마스킹값은 싣지 않는다 — 진단 스냅샷·문제 JSON에 그대로 저장되기 때문이다. */
    public record Item(Long loanId, String accountName, String institutionName, String loanType, String repaymentType, String status,
            Long balance, BigDecimal interestRate, String comparisonGroup, BigDecimal referenceRate, String referenceConfidence,
            BigDecimal rateDifference, int comparableProductCount, boolean overdue, LocalDate maturityDate,
            RefinanceEstimate refinance, IssueSeverity severity) { }

    /**
     * @param optionTerm            채택한 옵션의 기간(= 잔여 회차 수)
     * @param monthlySaving         회차당 평균 절감액 {@code interestSaving / remainingInstallments}(내림)
     * @param feesIncluded          항상 false. 중도상환수수료·부대비용을 반영하지 않았음을 응답에 남긴다
     * @param assumptions           옵션 선택 가정({@code ProductOptionSelector})
     */
    public record RefinanceEstimate(boolean available, String unavailableReason, Long targetProductId, String targetProductName,
            String targetCompanyName, BigDecimal appliedRate, Integer optionTerm, Integer remainingInstallments, Long beforeInterest,
            Long afterInterest, Long interestSaving, Long monthlySaving, boolean feesIncluded, List<String> assumptions) {
        static RefinanceEstimate unavailable(String reason) {
            return new RefinanceEstimate(false, reason, null, null, null, null, null, null, null, null, null, null, false, List.of());
        }
    }
}
