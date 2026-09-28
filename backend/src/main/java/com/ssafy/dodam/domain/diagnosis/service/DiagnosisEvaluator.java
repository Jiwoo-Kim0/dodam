package com.ssafy.dodam.domain.diagnosis.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisIssue;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisStatus;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import com.ssafy.dodam.domain.goal.entity.Goal;
import com.ssafy.dodam.domain.goal.entity.GoalStatus;
import com.ssafy.dodam.domain.goal.repository.GoalRepository;
import com.ssafy.dodam.domain.goal.repository.SavingGoalRepository;
import com.ssafy.dodam.domain.goal.repository.SpendingGoalRepository;
import com.ssafy.dodam.domain.goal.service.GoalCalculationService;
import com.ssafy.dodam.domain.cashflow.service.CashflowQueryService;
import com.ssafy.dodam.global.exception.ResourceNotFoundException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** 기존 도메인 판정을 스냅샷으로 보존한다. 미확정 규칙을 정상으로 간주하지 않는다. */
@Service
public class DiagnosisEvaluator {
    private final GoalRepository goals;
    private final GoalCalculationService goalCalculation;
    private final CashflowQueryService cashflow;
    private final ObjectMapper json;
    private final SavingGoalRepository savingGoals;
    private final SpendingGoalRepository spendingGoals;
    private final SpendingIncreaseRule spendingIncrease;
    private final HighInterestLoanRule highInterestLoan;
    private final ClusterDeviationRule clusterDeviation;

    public DiagnosisEvaluator(GoalRepository goals, GoalCalculationService goalCalculation,
            CashflowQueryService cashflow, ObjectMapper json, SavingGoalRepository savingGoals,
            SpendingGoalRepository spendingGoals, SpendingIncreaseRule spendingIncrease, HighInterestLoanRule highInterestLoan,
            ClusterDeviationRule clusterDeviation) {
        this.goals = goals;
        this.goalCalculation = goalCalculation;
        this.cashflow = cashflow;
        this.json = json;
        this.savingGoals = savingGoals;
        this.spendingGoals = spendingGoals;
        this.spendingIncrease = spendingIncrease;
        this.highInterestLoan = highInterestLoan;
        this.clusterDeviation = clusterDeviation;
    }

    public Evaluation evaluate(Diagnosis diagnosis) {
        List<DiagnosisIssue> issues = new ArrayList<>();
        ObjectNode snapshot = json.createObjectNode();
        snapshot.put("schemaVersion", 1).put("ruleVersion", DiagnosisRuleVersion.CURRENT)
                .put("asOf", diagnosis.getCreatedAt().toString()).put("timezone", "Asia/Seoul");
        ArrayNode coverage = snapshot.putArray("coverage");
        snapshot.putArray("assumptions").add("LOCAL_DATA_ONLY").add("PARTIAL_RULE_COVERAGE")
                .add("GOAL_RULES_USE_CURRENT_ACCOUNT_BALANCES").add("SPENDING_INCREASE_EXCLUDES_CURRENT_MONTH")
                .add("REFINANCE_ESTIMATE_EXCLUDES_FEES").add("CLUSTER_DEVIATION_USES_STORED_PATTERN_ANALYSIS");
        ArrayNode inputs = snapshot.putArray("goals");
        List<Goal> selected = diagnosis.getGoalId() == null
                ? goals.findAllByUserIdAndDeletedAtIsNull(diagnosis.getUserId()).stream()
                        .filter(g -> g.getStatus() == GoalStatus.ACTIVE).toList()
                : List.of(goals.findByIdAndUserIdAndDeletedAtIsNull(diagnosis.getGoalId(), diagnosis.getUserId())
                        .orElseThrow(() -> new ResourceNotFoundException("목표를 찾을 수 없습니다.")));
        for (Goal goal : selected) {
            var risk = goalCalculation.getRisk(diagnosis.getUserId(), goal.getId(), diagnosis.getCreatedAt().toLocalDate());
            ObjectNode input = json.valueToTree(risk);
            input.set("goalSnapshot", json.valueToTree(goal));
            savingGoals.findById(goal.getId()).ifPresent(g -> {
                input.set("savingGoalSnapshot", json.valueToTree(g));
                // 546 goal-impact가 조회 시점에 재계산하지 않도록 예상 달성일·남은 금액을 진단 시점 값으로 보존한다.
                input.set("forecast", json.valueToTree(
                        goalCalculation.getForecast(diagnosis.getUserId(), goal.getId(), diagnosis.getCreatedAt().toLocalDate())));
            });
            spendingGoals.findById(goal.getId()).ifPresent(g -> input.set("spendingGoalSnapshot", json.valueToTree(g)));
            inputs.add(input);
            if (!risk.factors().isEmpty()) {
                ObjectNode evidence = input.deepCopy();
                evidence.put("ruleKey", "GOAL_" + risk.type()).put("targetId", goal.getId());
                issues.add(DiagnosisIssue.ofRule(diagnosis.getDiagnosisId(), "GOAL", risk.type() + "_GOAL_RISK",
                        IssueSeverity.valueOf(risk.riskLevel()), "목표 달성 계획 점검", "현재 목표 계산 기준으로 점검이 필요한 요인이 있습니다.",
                        json.valueToTree(risk.metrics()), json.valueToTree(goalCalculation.riskPolicySnapshot()), evidence,
                        diagnosis.getCreatedAt()));
            }
        }
        coverage.addObject().put("ruleKey", "GOAL").put("status", selected.isEmpty() ? "NOT_APPLICABLE" : "EVALUATED");
        evaluateCashflow(diagnosis, issues, snapshot, coverage);
        evaluateSpendingIncrease(diagnosis, issues, snapshot, coverage);
        evaluateHighInterestLoan(diagnosis, issues, snapshot, coverage);
        // 627이 카테고리 소비 증가·고금리 대출을 붙였다. 고정/변동지출 유형별 증가와 이자·상환 부담 규칙은 아직 정책이 없다.
        for (String rule : List.of("DORMANT_ACCOUNT", "FIXED_SPENDING_INCREASE", "VARIABLE_SPENDING_INCREASE",
                "LOAN_INTEREST_BURDEN", "LOAN_REPAYMENT_BURDEN", "LOW_USAGE_CARD",
                "CARD_PAYMENT_BURDEN", "SAVING_CASHFLOW_BURDEN")) {
            coverage.addObject().put("ruleKey", rule).put("status", "UNAVAILABLE").put("reason", "RULE_POLICY_NOT_DEFINED");
        }
        // 641: 규칙 문제가 모두 모인 뒤에 실행한다 — 병합 대상(같은 성격의 규칙 문제)을 찾아 근거만 붙여야 하기 때문이다.
        evaluateClusterDeviation(diagnosis, issues, snapshot, coverage);
        DiagnosisStatus status = issues.stream().anyMatch(DiagnosisIssue::isHighSeverity) ? DiagnosisStatus.RISK
                : issues.isEmpty() ? null : DiagnosisStatus.CAUTION;
        return new Evaluation(List.copyOf(issues), status, snapshot);
    }

    private void evaluateCashflow(Diagnosis diagnosis, List<DiagnosisIssue> issues, ObjectNode snapshot, ArrayNode coverage) {
        try {
            var result = cashflow.getRisks(diagnosis.getUserId(), null, null);
            var forecast = result.forecast();
            snapshot.set("cashflow", json.valueToTree(result));
            if (!forecast.baseDate().equals(diagnosis.getCreatedAt().toLocalDate())) {
                coverage.addObject().put("ruleKey", "CASHFLOW").put("status", "UNAVAILABLE").put("reason", "STALE_FORECAST");
                return;
            }
            for (var risk : result.risks()) {
                ObjectNode evidence = json.valueToTree(risk);
                evidence.put("ruleKey", "CASHFLOW_" + risk.type()).put("forecastId", forecast.forecastId())
                        .put("targetId", risk.riskId());
                issues.add(DiagnosisIssue.ofRule(diagnosis.getDiagnosisId(), "CASHFLOW", risk.type(),
                        IssueSeverity.valueOf(risk.severity().name()), "예정 출금 잔액 부족", risk.message(),
                        json.valueToTree(risk), null, evidence, diagnosis.getCreatedAt()));
            }
            coverage.addObject().put("ruleKey", "CASHFLOW").put("status", "EVALUATED")
                    .put("reason", "STORED_FORECAST_INPUT_COMPLETENESS_UNKNOWN");
        } catch (ResourceNotFoundException missing) {
            coverage.addObject().put("ruleKey", "CASHFLOW").put("status", "UNAVAILABLE").put("reason", "FORECAST_NOT_FOUND");
        }
    }

    /**
     * 진행 중인 달을 뺀 최근 3개월 vs 이전 3개월. 이전 기간에 소비가 있는 카테고리가 하나도 없으면 비교 자체가 성립하지 않으므로
     * 문제 0건과 구분해 {@code NO_BASELINE_SPENDING}으로 남긴다.
     */
    private void evaluateSpendingIncrease(Diagnosis diagnosis, List<DiagnosisIssue> issues, ObjectNode snapshot, ArrayNode coverage) {
        SpendingIncreaseRule.Result result = spendingIncrease.evaluate(diagnosis.getUserId(), diagnosis.getCreatedAt().toLocalDate());
        snapshot.set("spendingIncrease", json.valueToTree(result));
        for (SpendingIncreaseRule.Item item : result.items()) {
            issues.add(spendingIncrease.toIssue(diagnosis, result, item));
        }
        ObjectNode entry = coverage.addObject().put("ruleKey", SpendingIncreaseRule.TYPE);
        if (result.evaluatedCategories() == 0) {
            entry.put("status", "UNAVAILABLE").put("reason", SpendingIncreaseRule.COVERAGE_NO_BASELINE);
        } else {
            entry.put("status", "EVALUATED");
        }
    }

    /** 잔액이 있는 대출이 없으면 NOT_APPLICABLE. 일부 대출의 비교 조건을 만들지 못했으면 EVALUATED에 사유를 덧붙인다. */
    private void evaluateHighInterestLoan(Diagnosis diagnosis, List<DiagnosisIssue> issues, ObjectNode snapshot, ArrayNode coverage) {
        HighInterestLoanRule.Result result = highInterestLoan.evaluate(diagnosis.getUserId(), diagnosis.getCreatedAt().toLocalDate());
        snapshot.set("highInterestLoan", json.valueToTree(result));
        for (HighInterestLoanRule.Item item : result.items()) {
            issues.add(highInterestLoan.toIssue(diagnosis, result, item));
        }
        ObjectNode entry = coverage.addObject().put("ruleKey", HighInterestLoanRule.TYPE);
        if (!result.applicable()) {
            entry.put("status", "NOT_APPLICABLE");
        } else {
            entry.put("status", "EVALUATED");
            if (result.unavailableCount() > 0) {
                entry.put("reason", HighInterestLoanRule.COVERAGE_INCOMPLETE);
            }
        }
    }

    /**
     * 군집 이탈(641). 저장된 최신 패턴 분석만 읽고, 병합 대상이 있으면 그 규칙 문제에 근거를 붙이고 없으면 단독 문제를 만든다.
     * 분석이 없거나 낡았거나 조건 미달이면 coverage에 사유만 남긴다(문제 0건과 구분).
     */
    private void evaluateClusterDeviation(Diagnosis diagnosis, List<DiagnosisIssue> issues, ObjectNode snapshot, ArrayNode coverage) {
        ClusterDeviationRule.Result result = clusterDeviation.evaluate(diagnosis.getUserId(), diagnosis.getCreatedAt().toLocalDate());
        ObjectNode stored = json.valueToTree(result);
        int created = result.available() ? clusterDeviation.apply(diagnosis, result, issues) : 0;
        stored.put("standaloneIssueCount", created).put("mergedCandidateCount", result.items().size() - created);
        snapshot.set("clusterDeviation", stored);
        ObjectNode entry = coverage.addObject().put("ruleKey", ClusterDeviationRule.COVERAGE_KEY).put("status", result.coverageStatus());
        if (result.coverageReason() != null) {
            entry.put("reason", result.coverageReason());
        }
    }

    public record Evaluation(List<DiagnosisIssue> issues, DiagnosisStatus status, JsonNode snapshot) { }
}
