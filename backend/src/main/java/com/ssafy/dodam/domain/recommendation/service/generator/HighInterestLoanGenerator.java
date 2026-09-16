package com.ssafy.dodam.domain.recommendation.service.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.recommendation.dto.CandidateGenerationResult.SkipReason;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationCandidate;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationInput;
import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import com.ssafy.dodam.domain.recommendation.service.RecommendationCandidateGenerator;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * LOAN/HIGH_INTEREST_RATE → LOAN/REFINANCE_LOAN(S15P21D105-627). 비교상품 공시금리보다 높은 대출을 대환 검토하라는 권고다.
 *
 * <p>입력은 진단이 저장한 {@code currentValue}(loanId·accountName·interestRate·referenceRate·rateDifference·balance)와
 * {@code currentValue.refinance}(진단이 추정한 대환 절감액)다. 추정이 있으면 {@code interestSaving}(만기까지 총이자 차이),
 * {@code monthlyCostSaving}(회차당 평균 절감액), {@code annualCostSaving = monthly × min(12, 잔여 회차)}를 채운다 — 잔여 회차가
 * 12회 미만인 대출에 ×12를 적용하면 절감액을 부풀리므로 {@code SpendingGoalRiskGenerator}의 ×12와 다르다.
 * 추정이 없으면(조건에 맞는 상품 없음·연체·일정 불가) 금리 비교 근거만으로 "대환 조건 비교" 후보를 만들고 효과는 비운다.
 *
 * <p>근거 {@code REFINANCE_INTEREST_SAVING}의 evidence에 {@code loanId}·{@code targetProductId}를 남긴다. 시뮬레이션 프리셋이
 * 대환 요청을 만들 때 이 값을 읽는다.
 */
@Component
@Order(50)
public class HighInterestLoanGenerator implements RecommendationCandidateGenerator {
    public static final String VERSION = "627.2";
    public static final String DOMAIN = "LOAN";
    public static final String TYPE = "REFINANCE_LOAN";
    public static final String REASON_ABOVE_MARKET = "LOAN_RATE_ABOVE_MARKET";
    public static final String REASON_INTEREST_SAVING = "REFINANCE_INTEREST_SAVING";
    public static final String REASON_ESTIMATE_UNAVAILABLE = "REFINANCE_ESTIMATE_UNAVAILABLE";
    public static final String TARGET_PRODUCT_ID_FIELD = "targetProductId";
    static final int MONTHS_PER_YEAR = 12;
    private final ObjectMapper json;

    public HighInterestLoanGenerator(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public boolean supports(RecommendationInput.Issue issue) {
        return "LOAN".equals(issue.domain()) && "HIGH_INTEREST_RATE".equals(issue.type());
    }

    @Override
    public Outcome generate(RecommendationInput input, RecommendationInput.Issue issue) {
        JsonNode current = issue.currentValue();
        Long loanId = GeneratorSupport.longOf(current, "loanId");
        BigDecimal interestRate = decimalOf(current, "interestRate");
        BigDecimal referenceRate = decimalOf(current, "referenceRate");
        BigDecimal difference = decimalOf(current, "rateDifference");
        Long balance = GeneratorSupport.longOf(current, "balance");
        if (loanId == null || interestRate == null || referenceRate == null || difference == null || balance == null) {
            return Outcome.skipped(SkipReason.INSUFFICIENT_DATA);
        }
        String accountName = GeneratorSupport.textOf(current, "accountName");
        String label = accountName == null ? "대출" : "'" + accountName + "'";
        List<RecommendationCandidate.Reason> reasons = new ArrayList<>();
        ObjectNode market = json.createObjectNode();
        market.put("loanId", loanId).put("interestRate", interestRate).put("referenceRate", referenceRate)
                .put("rateDifference", difference).put("balance", balance)
                .put("comparisonGroup", GeneratorSupport.textOf(current, "comparisonGroup"));
        reasons.add(new RecommendationCandidate.Reason(REASON_ABOVE_MARKET,
                "연 " + plain(interestRate) + "%로 같은 조건 상품의 공시 최저금리 중앙값 " + plain(referenceRate) + "%보다 "
                        + plain(difference) + "%p 높아요.", market));

        JsonNode refinance = current.get("refinance");
        Long saving = GeneratorSupport.longOf(refinance, "interestSaving");
        Long targetProductId = GeneratorSupport.longOf(refinance, TARGET_PRODUCT_ID_FIELD);
        Long installments = GeneratorSupport.longOf(refinance, "remainingInstallments");
        boolean available = refinance != null && refinance.path("available").asBoolean(false)
                && saving != null && targetProductId != null && installments != null && installments > 0;
        if (!available) {
            ObjectNode evidence = json.createObjectNode();
            evidence.put("loanId", loanId).put("reason", GeneratorSupport.textOf(refinance, "unavailableReason"));
            reasons.add(new RecommendationCandidate.Reason(REASON_ESTIMATE_UNAVAILABLE,
                    "잔여 기간·상환방식에 맞는 대환 상품이 없어 절감액은 추정하지 못했어요.", evidence));
            return Outcome.of(new RecommendationCandidate(VERSION, TYPE + ":" + loanId, issue.issueId(), DOMAIN, TYPE,
                    label + " 대환 조건 비교해보기",
                    "지금 금리가 비슷한 조건의 상품보다 " + plain(difference) + "%p 높아요. 대환 조건을 한번 비교해 보세요.",
                    reasons, Recommendation.Effect.NONE));
        }
        long monthly = saving / installments;
        long annual = monthly * Math.min(MONTHS_PER_YEAR, installments);
        BigDecimal appliedRate = decimalOf(refinance, "appliedRate");
        String productName = GeneratorSupport.textOf(refinance, "targetProductName");
        ObjectNode evidence = json.createObjectNode();
        evidence.put("loanId", loanId).put(TARGET_PRODUCT_ID_FIELD, targetProductId).put("targetProductName", productName)
                .put("appliedRate", appliedRate).put("remainingInstallments", installments)
                .put("beforeInterest", GeneratorSupport.longOf(refinance, "beforeInterest"))
                .put("afterInterest", GeneratorSupport.longOf(refinance, "afterInterest"))
                .put("interestSaving", saving).put("feesIncluded", false);
        reasons.add(new RecommendationCandidate.Reason(REASON_INTEREST_SAVING,
                "공시 최저 연 " + plain(appliedRate) + "% 상품으로 대환하면 만기까지 이자 " + GeneratorSupport.won(saving)
                        + "을 아낄 수 있어요(중도상환수수료·부대비용 제외).", evidence));
        Recommendation.Effect effect = new Recommendation.Effect(monthly, annual, saving, null, null, null, null, null);
        return Outcome.of(new RecommendationCandidate(VERSION, TYPE + ":" + loanId, issue.issueId(), DOMAIN, TYPE,
                label + " 대환 알아보기",
                "대환하면 만기까지 이자 " + GeneratorSupport.won(saving) + "을 아낄 수 있어요(수수료 제외).",
                reasons, effect));
    }

    private static BigDecimal decimalOf(JsonNode node, String field) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(field);
        return value == null || !value.isNumber() ? null : value.decimalValue();
    }

    private static String plain(BigDecimal value) {
        return value == null ? "?" : value.stripTrailingZeros().toPlainString();
    }
}
