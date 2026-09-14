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

    public DiagnosisEvaluator(GoalRepository goals, GoalCalculationService goalCalculation,
            CashflowQueryService cashflow, ObjectMapper json, SavingGoalRepository savingGoals,
            SpendingGoalRepository spendingGoals) {
        this.goals = goals;
        this.goalCalculation = goalCalculation;
        this.cashflow = cashflow;
        this.json = json;
        this.savingGoals = savingGoals;
        this.spendingGoals = spendingGoals;
    }

    public Evaluation evaluate(Diagnosis diagnosis) {
        List<DiagnosisIssue> issues = new ArrayList<>();
        ObjectNode snapshot = json.createObjectNode();
        snapshot.put("schemaVersion", 1).put("ruleVersion", DiagnosisRuleVersion.CURRENT)
                .put("asOf", diagnosis.getCreatedAt().toString()).put("timezone", "Asia/Seoul");
        ArrayNode coverage = snapshot.putArray("coverage");
        snapshot.putArray("assumptions").add("LOCAL_DATA_ONLY").add("PARTIAL_RULE_COVERAGE")
                .add("GOAL_RULES_USE_CURRENT_ACCOUNT_BALANCES");
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
        for (String rule : List.of("DORMANT_ACCOUNT", "FIXED_SPENDING_INCREASE", "VARIABLE_SPENDING_INCREASE",
                "HIGH_RATE_LOAN", "LOAN_INTEREST_BURDEN", "LOAN_REPAYMENT_BURDEN", "LOW_USAGE_CARD",
                "CARD_PAYMENT_BURDEN", "SAVING_CASHFLOW_BURDEN")) {
            coverage.addObject().put("ruleKey", rule).put("status", "UNAVAILABLE").put("reason", "RULE_POLICY_NOT_DEFINED");
        }
        coverage.addObject().put("ruleKey", "CLUSTER").put("status", "UNAVAILABLE").put("reason", "CLUSTER_NOT_CONNECTED");
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

    public record Evaluation(List<DiagnosisIssue> issues, DiagnosisStatus status, JsonNode snapshot) { }
}
