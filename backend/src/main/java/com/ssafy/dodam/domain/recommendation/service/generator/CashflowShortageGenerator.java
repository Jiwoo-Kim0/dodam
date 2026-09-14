package com.ssafy.dodam.domain.recommendation.service.generator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.recommendation.dto.CandidateGenerationResult.SkipReason;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationCandidate;
import com.ssafy.dodam.domain.recommendation.dto.RecommendationInput;
import com.ssafy.dodam.domain.recommendation.entity.Recommendation;
import com.ssafy.dodam.domain.recommendation.service.RecommendationCandidateGenerator;
import java.time.LocalDate;
import java.util.List;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * CASHFLOW/BALANCE_SHORTAGE → CASHFLOW/SECURE_SHORTAGE_BUFFER. 예정 출금으로 잔액이 부족해지는 날 전에 부족액을 확보하라는 권고다.
 *
 * <p>입력은 진단이 저장한 {@code currentValue}(CashflowRiskResponse: riskId·riskDate·expectedBalance)와 {@code evidence.forecastId}다.
 * 부족액 {@code S = max(0, −expectedBalance)}. 금액 효과 컬럼은 비우고 {@code riskReduction = BALANCE_SHORTAGE_AVOIDED}로 위험 감소를 표시한다.
 * 후보 키는 예측별 위험 id라 같은 진단에서 멱등이다.
 */
@Component
@Order(30)
public class CashflowShortageGenerator implements RecommendationCandidateGenerator {
    public static final String VERSION = "548.1";
    public static final String DOMAIN = "CASHFLOW";
    public static final String TYPE = "SECURE_SHORTAGE_BUFFER";
    public static final String RISK_REDUCTION = "BALANCE_SHORTAGE_AVOIDED";
    static final String REASON_SHORTAGE = "CASHFLOW_SHORTAGE";
    private final ObjectMapper json;

    public CashflowShortageGenerator(ObjectMapper json) {
        this.json = json;
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public boolean supports(RecommendationInput.Issue issue) {
        return "CASHFLOW".equals(issue.domain()) && "BALANCE_SHORTAGE".equals(issue.type());
    }

    @Override
    public Outcome generate(RecommendationInput input, RecommendationInput.Issue issue) {
        JsonNode risk = issue.currentValue();
        Long riskId = GeneratorSupport.longOf(risk, "riskId");
        LocalDate riskDate = GeneratorSupport.dateOf(risk, "riskDate");
        Long expectedBalance = GeneratorSupport.longOf(risk, "expectedBalance");
        if (riskId == null || riskDate == null || expectedBalance == null) {
            return Outcome.skipped(SkipReason.INSUFFICIENT_DATA);
        }
        long shortage = Math.max(0L, -expectedBalance);
        ObjectNode evidence = json.createObjectNode();
        evidence.put("forecastId", GeneratorSupport.longOf(issue.evidence(), "forecastId")).put("riskId", riskId)
                .put("riskDate", riskDate.toString()).put("expectedBalance", expectedBalance).put("shortageAmount", shortage);
        Recommendation.Effect effect = new Recommendation.Effect(null, null, null, RISK_REDUCTION, null, null, null, null);
        return Outcome.of(new RecommendationCandidate(VERSION, TYPE + ":" + riskId, issue.issueId(), DOMAIN, TYPE,
                riskDate + " 전까지 " + GeneratorSupport.won(shortage) + " 확보하기",
                riskDate + "에 예정 출금으로 잔액이 " + GeneratorSupport.won(shortage) + " 부족할 것으로 예상됩니다. 그 전에 입출금 계좌로 자금을 옮기거나 지출을 조정해 두세요.",
                List.of(new RecommendationCandidate.Reason(REASON_SHORTAGE,
                        riskDate + " 예상 잔액 " + GeneratorSupport.won(expectedBalance) + ".", evidence)),
                effect));
    }
}
