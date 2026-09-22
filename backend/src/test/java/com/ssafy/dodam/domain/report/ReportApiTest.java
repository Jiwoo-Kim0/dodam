package com.ssafy.dodam.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.cashflow.entity.CashflowForecast;
import com.ssafy.dodam.domain.cashflow.entity.CashflowRisk;
import com.ssafy.dodam.domain.cashflow.entity.RiskSeverity;
import com.ssafy.dodam.domain.cashflow.repository.CashflowForecastRepository;
import com.ssafy.dodam.domain.cashflow.repository.CashflowRiskRepository;
import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisIssue;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisStatus;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisIssueRepository;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisRepository;
import com.ssafy.dodam.domain.goal.entity.Goal;
import com.ssafy.dodam.domain.goal.entity.GoalType;
import com.ssafy.dodam.domain.goal.entity.SavingGoal;
import com.ssafy.dodam.domain.goal.repository.GoalRepository;
import com.ssafy.dodam.domain.goal.repository.SavingGoalRepository;
import com.ssafy.dodam.domain.improvement.service.ImprovementClock;
import com.ssafy.dodam.domain.report.service.ReportClock;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API 20 Report(S15P21D105-590).
 *
 * <p>리포트는 조회 시점에 생성된다. 그래서 이 테스트의 관심사는 "무엇을 집계했나"만큼이나 "두 번 봐도 같은가",
 * "끝나지 않은 기간을 거부하는가", "하위 응답이 저장된 리포트와 같은 숫자인가"다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:report590;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class ReportApiTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 16, 5, 0);
    /** 기본 기준월(직전 완료 월)과 기본 기준일(어제). */
    private static final String BASE_MONTH = "2026-08";
    private static final LocalDate YESTERDAY = LocalDate.of(2026, 9, 15);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired GoalRepository goals;
    @Autowired SavingGoalRepository savingGoals;
    @Autowired JdbcTemplate jdbc;
    @Autowired CashflowForecastRepository forecasts;
    @Autowired CashflowRiskRepository risks;
    @Autowired DiagnosisRepository diagnoses;
    @Autowired DiagnosisIssueRepository issues;
    @MockitoBean ReportClock reportClock;
    @MockitoBean ImprovementClock improvementClock;

    private Long owner;
    private Long other;
    private Long goalId;

    @BeforeEach
    void setup() {
        given(reportClock.now()).willReturn(NOW);
        given(reportClock.today()).willReturn(NOW.toLocalDate());
        given(improvementClock.now()).willReturn(NOW);
        given(improvementClock.today()).willReturn(NOW.toLocalDate());
        // 카드 청구서·대출 상환 내역은 JPA 엔티티가 없어 H2 기본 스키마에 만들어지지 않는다(운영 스키마는
        // Flyway가 만든다). 예정 비용 수집기가 두 테이블을 읽으므로 비어 있는 채로 만들어 둔다 —
        // 확정된 예정이 하나도 없는 상태를 재현한다.
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS card_billing_statement (
                    card_id BIGINT NOT NULL, billing_month CHAR(7) NOT NULL, billing_date DATE NOT NULL,
                    total_balance BIGINT NOT NULL, status VARCHAR(30) NOT NULL, payment_date DATE,
                    PRIMARY KEY (card_id, billing_month))
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS loan_repayment (
                    loan_repayment_id BIGINT NOT NULL, loan_id BIGINT NOT NULL, repayment_date DATE NOT NULL,
                    amount BIGINT NOT NULL, status VARCHAR(30) NOT NULL, PRIMARY KEY (loan_repayment_id))
                """);
        owner = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "리포트")).getId();
        other = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "다른 사용자")).getId();
        goalId = goals.save(Goal.create(owner, "비상금 1,000만원 만들기", GoalType.SAVING, NOW.minusMonths(6))).getId();
        savingGoals.save(SavingGoal.create(goalId, 10_000_000L, LocalDate.of(2028, 3, 31)));

        // 2026-07: 소비 1,560,000 · 저축성 350,000 · 수입 3,200,000
        income(LocalDate.of(2026, 7, 25), 3_200_000);
        expense(LocalDate.of(2026, 7, 10), 1_560_000, "VARIABLE", null);
        expense(LocalDate.of(2026, 7, 26), 350_000, "NON_SPENDING", null);
        // 2026-08: 소비 1,450,000(고정 900,000 + 변동 550,000) · 저축성 400,000 · 수입 3,200,000
        income(LocalDate.of(2026, 8, 25), 3_200_000);
        expense(LocalDate.of(2026, 8, 5), 900_000, "FIXED", null);
        expense(LocalDate.of(2026, 8, 12), 550_000, "VARIABLE", category("DINING", "외식"));
        expense(LocalDate.of(2026, 8, 26), 400_000, "NON_SPENDING", null);
    }

    // ---------- 월간 리포트 ----------

    @Test
    void monthlyReportSeparatesSpendingFromSavingInsideTotalExpense() throws Exception {
        mvc.perform(get("/api/v1/reports/monthly").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("MONTHLY"))
                .andExpect(jsonPath("$.data.period").value(BASE_MONTH))
                .andExpect(jsonPath("$.data.dataStatus").value("READY"))
                .andExpect(jsonPath("$.data.unavailableReason").doesNotExist())
                .andExpect(jsonPath("$.data.totalIncome").value(3_200_000))
                // totalExpense = 소비 + 저축성 지출. 저축성 지출을 두 번 빼지 않는다.
                .andExpect(jsonPath("$.data.totalExpense").value(1_850_000))
                .andExpect(jsonPath("$.data.totalSpending").value(1_450_000))
                .andExpect(jsonPath("$.data.totalSaving").value(400_000))
                .andExpect(jsonPath("$.data.netCashflow").value(1_350_000))
                .andExpect(jsonPath("$.data.previousMonth.totalExpense").value(1_910_000))
                .andExpect(jsonPath("$.data.changeFromPreviousMonth.totalExpense").value(-60_000))
                .andExpect(jsonPath("$.data.changeFromPreviousMonth.totalSaving").value(50_000));
    }

    // ---------- 635 금융 효율 점수 · 카드 사용액 ----------

    @Test
    void monthlyReportScoresSixDimensionsFromStoredInputs() throws Exception {
        // 신용등급 B, 8월 안의 현금흐름 예측에 HIGH 위험 1건, 8월 완료 진단에 LOAN HIGH 1건 + GOAL(제외) 1건
        jdbc.update("UPDATE `user` SET credit_rating = 'B', credit_rating_updated_at = ? WHERE user_id = ?", NOW, owner);
        CashflowForecast forecast = forecasts.save(CashflowForecast.start(owner, LocalDate.of(2026, 8, 20), 30, "v1", null,
                LocalDateTime.of(2026, 8, 20, 9, 0)));
        forecast.complete(500_000L, 300_000L, -50_000L, LocalDate.of(2026, 8, 28), LocalDateTime.of(2026, 8, 20, 9, 1));
        forecasts.save(forecast);
        risks.save(CashflowRisk.of(forecast.getForecastId(), LocalDate.of(2026, 8, 28), "BALANCE_SHORTAGE", RiskSeverity.HIGH,
                "잔액 부족", -50_000L));
        Diagnosis diagnosis = diagnoses.save(Diagnosis.start(owner, null, LocalDateTime.of(2026, 8, 21, 9, 0)));
        diagnosis.complete(2, 1, DiagnosisStatus.RISK, "v6-last-month-vs-3m", null, LocalDateTime.of(2026, 8, 21, 9, 1));
        diagnoses.save(diagnosis);
        issues.save(DiagnosisIssue.ofRule(diagnosis.getDiagnosisId(), "LOAN", "HIGH_INTEREST_RATE", IssueSeverity.HIGH,
                "금리", "높아요", null, null, null, LocalDateTime.of(2026, 8, 21, 9, 1)));
        issues.save(DiagnosisIssue.ofRule(diagnosis.getDiagnosisId(), "GOAL", "SAVING_GOAL_RISK", IssueSeverity.MEDIUM,
                "목표", "위험", null, null, null, LocalDateTime.of(2026, 8, 21, 9, 1)));

        mvc.perform(get("/api/v1/reports/monthly").with(user(owner.toString())))
                .andExpect(status().isOk())
                // 카드 사용액: 소비 정의(저축성 지출 제외) 중 카드 결제. setup의 거래는 전부 payment_method = CARD다
                .andExpect(jsonPath("$.data.cardSpending").value(1_450_000))
                .andExpect(jsonPath("$.data.cardSpendingPreviousMonth").value(1_560_000))
                .andExpect(jsonPath("$.data.cardSpendingDifferenceFromPreviousMonth").value(-110_000))
                .andExpect(jsonPath("$.data.efficiency.calculationVersion").value("efficiency-v3"))
                .andExpect(jsonPath("$.data.efficiency.maxScore").value(100))
                .andExpect(jsonPath("$.data.efficiency.availableDimensionCount").value(6))
                .andExpect(jsonPath("$.data.efficiency.dimensions.length()").value(6))
                // 소비 1,450,000 / 소득 3,200,000 = 0.4531 → 100
                .andExpect(jsonPath("$.data.efficiency.dimensions[0].key").value("SPENDING"))
                .andExpect(jsonPath("$.data.efficiency.dimensions[0].score").value(100))
                .andExpect(jsonPath("$.data.efficiency.dimensions[0].status").value("GOOD"))
                .andExpect(jsonPath("$.data.efficiency.dimensions[0].value").value(0.4531))
                // 저축 400,000 / 3,200,000 = 0.125 → 42
                .andExpect(jsonPath("$.data.efficiency.dimensions[1].key").value("SAVING"))
                .andExpect(jsonPath("$.data.efficiency.dimensions[1].score").value(42))
                // 고정 900,000 / 소비 1,450,000 = 0.6207 → (0.8 − 0.6207) / 0.4 = 45 (NORMAL)
                .andExpect(jsonPath("$.data.efficiency.dimensions[2].key").value("FIXED_EXPENSE"))
                .andExpect(jsonPath("$.data.efficiency.dimensions[2].score").value(45))
                .andExpect(jsonPath("$.data.efficiency.dimensions[2].status").value("NORMAL"))
                // 신용등급 B → 85
                .andExpect(jsonPath("$.data.efficiency.dimensions[3].key").value("CREDIT"))
                .andExpect(jsonPath("$.data.efficiency.dimensions[3].score").value(85))
                .andExpect(jsonPath("$.data.efficiency.dimensions[3].grade").value("B"))
                // HIGH 위험 1건 → 70
                .andExpect(jsonPath("$.data.efficiency.dimensions[4].key").value("CASHFLOW"))
                .andExpect(jsonPath("$.data.efficiency.dimensions[4].score").value(70))
                .andExpect(jsonPath("$.data.efficiency.dimensions[4].description").value("예정 잔액 위험 1건이 있어요."))
                // GOAL 문제는 세지 않는다 → HIGH 1건 → 75
                .andExpect(jsonPath("$.data.efficiency.dimensions[5].key").value("INEFFICIENCY"))
                .andExpect(jsonPath("$.data.efficiency.dimensions[5].score").value(75))
                .andExpect(jsonPath("$.data.efficiency.dimensions[5].value").value(1))
                // 전체 = (100 + 42 + 45 + 85 + 70 + 75) / 6 = 69.5 → 70
                .andExpect(jsonPath("$.data.efficiency.score").value(70))
                // 7월: 100 · 36 · 100(고정지출 0) · 85, 7월 말까지 예측·진단이 없어 생성 시점 최신(8월 실행분 70·75)으로 폴백
                // → (100+36+100+85+70+75)/6 = 77.7 → 78 → 70 − 78
                .andExpect(jsonPath("$.data.efficiency.previousMonthDifference").value(-8))
                .andExpect(jsonPath("$.data.efficiency.summary").value("지출 관리는 안정적이에요. 저축을 조금 더 챙겨 보세요."))
                .andExpect(jsonPath("$.data.diagnosisId").value(diagnosis.getDiagnosisId()));
    }

    @Test
    void unavailableDimensionsAreNullNotZeroAndOverallSkipsThem() throws Exception {
        // 신용등급·예측·진단이 없는 기본 상태: 3개만 계산되고 나머지는 사유가 붙는다
        mvc.perform(get("/api/v1/reports/monthly").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.efficiency.availableDimensionCount").value(3))
                // (100 + 42 + 45) / 3 = 62.3 → 62
                .andExpect(jsonPath("$.data.efficiency.score").value(62))
                .andExpect(jsonPath("$.data.efficiency.unavailableReason").doesNotExist())
                // 계산 불가 항목의 score는 키를 빼지 않고 null로 내려간다. 키가 빠지면 FE(=== null 판정)가 undefined를 그린다(694)
                .andExpect(jsonPath("$.data.efficiency.dimensions[3].score", nullValue()))
                .andExpect(jsonPath("$.data.efficiency.dimensions[3].value").doesNotExist())
                .andExpect(jsonPath("$.data.efficiency.dimensions[3].grade").doesNotExist())
                .andExpect(jsonPath("$.data.efficiency.dimensions[3].status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.efficiency.dimensions[3].unavailableReason").value("CREDIT_SCORE_MISSING"))
                .andExpect(jsonPath("$.data.efficiency.dimensions[4].unavailableReason").value("FORECAST_NOT_FOUND"))
                .andExpect(jsonPath("$.data.efficiency.dimensions[5].unavailableReason").value("DIAGNOSIS_NOT_FOUND"))
                // 7월은 3개 계산(100·36·100) → 79 → 62 − 79
                .andExpect(jsonPath("$.data.efficiency.previousMonthDifference").value(-17));

        // 소득이 없는 달은 소득 기반 2개가 빠져 계산 항목이 1개 → 전체 점수 없음
        expense(LocalDate.of(2026, 6, 10), 700_000, "VARIABLE", null);
        mvc.perform(get("/api/v1/reports/monthly").param("yearMonth", "2026-06").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.efficiency.score").doesNotExist())
                .andExpect(jsonPath("$.data.efficiency.unavailableReason").value("INSUFFICIENT_DIMENSIONS"))
                .andExpect(jsonPath("$.data.efficiency.dimensions[0].unavailableReason").value("INCOME_MISSING"))
                .andExpect(jsonPath("$.data.efficiency.previousMonthDifference").doesNotExist())
                // 6월은 사용자 첫 거래가 있는 달이라 5월 비교 대상이 없다 → 카드 전월 필드도 null
                .andExpect(jsonPath("$.data.cardSpending").value(700_000))
                .andExpect(jsonPath("$.data.cardSpendingPreviousMonth").doesNotExist());
    }

    @Test
    void cashflowAndInefficiencyUseLatestForecastAndDiagnosisUpToMonthEnd() throws Exception {
        // 8월 안에는 예측·진단이 없고, 7월 말 실행분과 9월 실행분만 있다(694).
        // 7월 것은 8월 말일 시점에 사용자가 알던 마지막 상태라 8월 리포트에 들어가고, 9월 것은 8월 리포트가 보지 않는다.
        CashflowForecast july = forecasts.save(CashflowForecast.start(owner, LocalDate.of(2026, 7, 30), 30, "v1", null,
                LocalDateTime.of(2026, 7, 30, 9, 0)));
        july.complete(500_000L, 300_000L, 100_000L, null, LocalDateTime.of(2026, 7, 30, 9, 1));
        forecasts.save(july);
        CashflowForecast september = forecasts.save(CashflowForecast.start(owner, LocalDate.of(2026, 9, 1), 30, "v1", null,
                LocalDateTime.of(2026, 9, 1, 9, 0)));
        september.complete(500_000L, 300_000L, -50_000L, LocalDate.of(2026, 9, 10), LocalDateTime.of(2026, 9, 1, 9, 1));
        forecasts.save(september);
        risks.save(CashflowRisk.of(september.getForecastId(), LocalDate.of(2026, 9, 10), "BALANCE_SHORTAGE", RiskSeverity.CRITICAL,
                "잔액 부족", -50_000L));
        Diagnosis julyDiagnosis = diagnoses.save(Diagnosis.start(owner, null, LocalDateTime.of(2026, 7, 31, 23, 0)));
        julyDiagnosis.complete(1, 0, DiagnosisStatus.CAUTION, "v6-last-month-vs-3m", null, LocalDateTime.of(2026, 7, 31, 23, 1));
        diagnoses.save(julyDiagnosis);
        issues.save(DiagnosisIssue.ofRule(julyDiagnosis.getDiagnosisId(), "LOAN", "HIGH_INTEREST_RATE", IssueSeverity.MEDIUM,
                "금리", "높아요", null, null, null, LocalDateTime.of(2026, 7, 31, 23, 1)));
        Diagnosis septemberDiagnosis = diagnoses.save(Diagnosis.start(owner, null, LocalDateTime.of(2026, 9, 2, 9, 0)));
        septemberDiagnosis.complete(3, 2, DiagnosisStatus.RISK, "v6-last-month-vs-3m", null, LocalDateTime.of(2026, 9, 2, 9, 1));
        diagnoses.save(septemberDiagnosis);

        mvc.perform(get("/api/v1/reports/monthly").with(user(owner.toString())))
                .andExpect(status().isOk())
                // 7월 예측은 위험 0건 → 100. 9월 예측의 CRITICAL은 반영되지 않는다
                .andExpect(jsonPath("$.data.efficiency.dimensions[4].score").value(100))
                .andExpect(jsonPath("$.data.efficiency.dimensions[4].value").value(0))
                // 7월 진단의 MEDIUM 1건 → 85
                .andExpect(jsonPath("$.data.efficiency.dimensions[5].score").value(85))
                .andExpect(jsonPath("$.data.efficiency.availableDimensionCount").value(5))
                // 리포트의 진단 참조도 같은 진단이다
                .andExpect(jsonPath("$.data.diagnosisId").value(julyDiagnosis.getDiagnosisId()))
                .andExpect(jsonPath("$.data.overallStatus").value("CAUTION"));

        // 7월 리포트(전월 점수 즉시 계산 경로 포함)는 7월 말일까지 것을 본다 — 같은 7월 실행분이 잡힌다
        mvc.perform(get("/api/v1/reports/monthly").param("yearMonth", "2026-07").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.efficiency.dimensions[4].score").value(100))
                .andExpect(jsonPath("$.data.efficiency.dimensions[5].score").value(85))
                .andExpect(jsonPath("$.data.diagnosisId").value(julyDiagnosis.getDiagnosisId()));
    }

    @Test
    void fallsBackToLatestRunWhenNothingIsCompletedByMonthEnd() throws Exception {
        // 8월 말일까지 예측·진단이 하나도 없고 9월 실행분만 있다 → 8월 리포트는 그것을 쓰되 근거 문장에 밝힌다(694).
        // 가입 첫 달 사용자처럼 이력이 없는 경우 두 항목이 영원히 계산 불가로 남지 않게 한다
        CashflowForecast september = forecasts.save(CashflowForecast.start(owner, LocalDate.of(2026, 9, 1), 30, "v1", null,
                LocalDateTime.of(2026, 9, 1, 9, 0)));
        september.complete(500_000L, 300_000L, -50_000L, LocalDate.of(2026, 9, 10), LocalDateTime.of(2026, 9, 1, 9, 1));
        forecasts.save(september);
        risks.save(CashflowRisk.of(september.getForecastId(), LocalDate.of(2026, 9, 10), "BALANCE_SHORTAGE", RiskSeverity.CRITICAL,
                "잔액 부족", -50_000L));
        Diagnosis septemberDiagnosis = diagnoses.save(Diagnosis.start(owner, null, LocalDateTime.of(2026, 9, 2, 9, 0)));
        septemberDiagnosis.complete(0, 0, DiagnosisStatus.HEALTHY, "v6-last-month-vs-3m", null, LocalDateTime.of(2026, 9, 2, 9, 1));
        diagnoses.save(septemberDiagnosis);

        mvc.perform(get("/api/v1/reports/monthly").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.efficiency.dimensions[4].score").value(60))
                .andExpect(jsonPath("$.data.efficiency.dimensions[4].description").value("가장 최근 예측 기준으로 예정 잔액 위험 1건이 있어요."))
                .andExpect(jsonPath("$.data.efficiency.dimensions[5].score").value(100))
                .andExpect(jsonPath("$.data.efficiency.dimensions[5].description").value("가장 최근 진단 기준으로 점검이 필요한 항목이 없어요."))
                .andExpect(jsonPath("$.data.diagnosisId").value(septemberDiagnosis.getDiagnosisId()))
                .andExpect(jsonPath("$.data.overallStatus").value("HEALTHY"));
    }

    @Test
    void reportsFrozenBefore635GetScoreAndCardSpendingWithoutChangingTotals() throws Exception {
        String first = mvc.perform(get("/api/v1/reports/monthly").with(user(owner.toString())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long reportId = json.readTree(first).at("/data/reportId").asLong();
        // 635 이전 저장본을 재현한다: 효율 점수 컬럼이 비어 있다
        jdbc.update("UPDATE monthly_report SET efficiency_summary = NULL WHERE report_id = ?", reportId);

        String second = mvc.perform(get("/api/v1/reports/monthly").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportId").value(reportId))
                .andExpect(jsonPath("$.data.efficiency.score").value(62))
                .andExpect(jsonPath("$.data.cardSpending").value(1_450_000))
                .andReturn().getResponse().getContentAsString();
        // 합계·생성 시각은 그대로다(READY 리포트는 고정)
        assertThat(json.readTree(second).at("/data/generatedAt")).isEqualTo(json.readTree(first).at("/data/generatedAt"));
        assertThat(json.readTree(second).at("/data/totalExpense")).isEqualTo(json.readTree(first).at("/data/totalExpense"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM monthly_report WHERE report_id = ? AND efficiency_summary IS NOT NULL",
                Integer.class, reportId)).isEqualTo(1);
    }

    @Test
    void generatedReportIsStableAcrossRequests() throws Exception {
        String first = mvc.perform(get("/api/v1/reports/monthly").with(user(owner.toString())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        // 두 번째 조회가 다시 계산하면 같은 달 숫자가 조회할 때마다 달라진다
        String second = mvc.perform(get("/api/v1/reports/monthly").with(user(owner.toString())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(second).at("/data/reportId"))
                .isEqualTo(json.readTree(first).at("/data/reportId"));
        assertThat(json.readTree(second).at("/data/generatedAt"))
                .isEqualTo(json.readTree(first).at("/data/generatedAt"));
    }

    @Test
    void monthWithoutTransactionsIsZeroNotUnknown() throws Exception {
        mvc.perform(get("/api/v1/reports/monthly").param("yearMonth", "2026-05").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataStatus").value("NO_TRANSACTIONS"))
                .andExpect(jsonPath("$.data.unavailableReason").value("NO_TRANSACTIONS"))
                .andExpect(jsonPath("$.data.totalExpense").value(0))
                // 사용자 첫 거래(2026-07)보다 앞선 달이라 비교 대상 자체가 없다 → 0원이 아니라 null이다
                .andExpect(jsonPath("$.data.previousMonth").doesNotExist())
                .andExpect(jsonPath("$.data.changeFromPreviousMonth").doesNotExist());
    }

    @Test
    void unfinishedPeriodIsRejected() throws Exception {
        mvc.perform(get("/api/v1/reports/monthly").param("yearMonth", "2026-09").with(user(owner.toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/v1/reports/monthly").param("yearMonth", "2026-13").with(user(owner.toString())))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reports/daily").param("date", "2026-09-16").with(user(owner.toString())))
                .andExpect(status().isBadRequest());
    }

    // ---------- 하위 응답이 저장된 리포트와 같은 숫자인가 ----------

    @Test
    void spendingPatternComesFromTheStoredReport() throws Exception {
        mvc.perform(get("/api/v1/reports/monthly/spending-pattern").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearMonth").value(BASE_MONTH))
                .andExpect(jsonPath("$.data.totalExpense").value(1_850_000))
                .andExpect(jsonPath("$.data.totalSpending").value(1_450_000))
                // 카테고리 비율의 분모를 응답이 직접 밝힌다
                .andExpect(jsonPath("$.data.ratioBase").value("TOTAL_SPENDING"))
                // 3개 키가 항상 있고 합이 totalExpense와 같다
                .andExpect(jsonPath("$.data.bySpendingType.FIXED").value(900_000))
                .andExpect(jsonPath("$.data.bySpendingType.VARIABLE").value(550_000))
                .andExpect(jsonPath("$.data.bySpendingType.NON_SPENDING").value(400_000))
                .andExpect(jsonPath("$.data.comparedToPrevious.amountDelta").value(-110_000));

        String body = mvc.perform(get("/api/v1/reports/monthly/spending-pattern").with(user(owner.toString())))
                .andReturn().getResponse().getContentAsString();
        var pattern = json.readTree(body).at("/data");
        long byType = pattern.at("/bySpendingType/FIXED").asLong() + pattern.at("/bySpendingType/VARIABLE").asLong()
                + pattern.at("/bySpendingType/NON_SPENDING").asLong();
        assertThat(byType).isEqualTo(pattern.at("/totalExpense").asLong());
        // 카테고리는 금액 내림차순이고, 비율은 0~1 소수이며 분모는 소비(1,450,000)다
        assertThat(pattern.at("/byCategory/0/amount").asLong()).isEqualTo(900_000);
        assertThat(pattern.at("/byCategory/0/ratio").asDouble()).isEqualTo(900_000 / 1_450_000.0, within());
        assertThat(pattern.at("/byCategory/1/categoryCode").asText()).isEqualTo("DINING");
        assertThat(pattern.at("/byCategory/1/ratio").asDouble()).isEqualTo(550_000 / 1_450_000.0, within());
    }

    @Test
    void monthlyGoalImpactReadsRecordedHistoryNotTodayBalance() throws Exception {
        goalHistory(LocalDate.of(2026, 7, 31), 3_000_000, LocalDate.of(2028, 3, 31));
        goalHistory(LocalDate.of(2026, 8, 31), 3_400_000, LocalDate.of(2027, 12, 31));

        mvc.perform(get("/api/v1/reports/monthly/goal-impact").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearMonth").value(BASE_MONTH))
                .andExpect(jsonPath("$.data.goals[0].goalId").value(goalId))
                .andExpect(jsonPath("$.data.goals[0].startAmount").value(3_000_000))
                .andExpect(jsonPath("$.data.goals[0].endAmount").value(3_400_000))
                .andExpect(jsonPath("$.data.goals[0].contributed").value(400_000))
                .andExpect(jsonPath("$.data.goals[0].progressRatio").value(0.34))
                .andExpect(jsonPath("$.data.goals[0].advancedDays").value(91));
    }

    @Test
    void monthlyGoalImpactHidesOtherUsersGoal() throws Exception {
        long othersGoal = goals.save(Goal.create(other, "남의 목표", GoalType.SAVING, NOW.minusMonths(6))).getId();
        savingGoals.save(SavingGoal.create(othersGoal, 1_000_000L, LocalDate.of(2028, 3, 31)));

        mvc.perform(get("/api/v1/reports/monthly/goal-impact").param("goalId", String.valueOf(othersGoal))
                        .with(user(owner.toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void monthlyImprovementsIsEmptyWithoutActions() throws Exception {
        mvc.perform(get("/api/v1/reports/monthly/improvements").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearMonth").value(BASE_MONTH))
                .andExpect(jsonPath("$.data.completedActions").value(0))
                .andExpect(jsonPath("$.data.monthlySaving").value(0))
                .andExpect(jsonPath("$.data.actions").isEmpty());
    }

    // ---------- 데일리 브리핑 ----------

    @Test
    void dailyBriefingSummarisesYesterday() throws Exception {
        expense(YESTERDAY, 32_000, "VARIABLE", category("DINING", "외식"));
        expense(YESTERDAY, 15_800, "VARIABLE", null);

        mvc.perform(get("/api/v1/reports/daily").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("DAILY"))
                .andExpect(jsonPath("$.data.period").value(YESTERDAY.toString()))
                .andExpect(jsonPath("$.data.dataStatus").value("READY"))
                .andExpect(jsonPath("$.data.totalExpense").value(47_800))
                .andExpect(jsonPath("$.data.totalSpending").value(47_800))
                .andExpect(jsonPath("$.data.transactionCount").value(2))
                .andExpect(jsonPath("$.data.balanceChange").value(-47_800))
                .andExpect(jsonPath("$.data.topCategories[0].categoryCode").value("DINING"))
                .andExpect(jsonPath("$.data.topCategories[0].amount").value(32_000))
                .andExpect(jsonPath("$.data.topCategories[1].categoryCode").value("UNCATEGORIZED"))
                // AI 문장은 리포트에 끼워 넣지 않는다(API 21을 따로 부른다)
                .andExpect(jsonPath("$.data.coachMessage").doesNotExist());
    }

    @Test
    void dailyBriefingWithoutTransactionsSaysSoInsteadOf404() throws Exception {
        mvc.perform(get("/api/v1/reports/daily").param("date", "2026-09-10").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dataStatus").value("NO_TRANSACTIONS"))
                .andExpect(jsonPath("$.data.unavailableReason").value("NO_TRANSACTIONS"))
                .andExpect(jsonPath("$.data.totalExpense").value(0))
                .andExpect(jsonPath("$.data.notableEvents").isEmpty());
    }

    // ---------- 목록·상세 ----------

    @Test
    void listShowsGeneratedReportsAndDetailHidesOtherUsers() throws Exception {
        mvc.perform(get("/api/v1/reports/monthly").with(user(owner.toString()))).andExpect(status().isOk());
        String body = mvc.perform(get("/api/v1/reports/daily").with(user(owner.toString())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long dailyId = json.readTree(body).at("/data/reportId").asLong();

        mvc.perform(get("/api/v1/reports").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
        mvc.perform(get("/api/v1/reports").param("type", "MONTHLY").with(user(owner.toString())))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].period").value(BASE_MONTH));

        mvc.perform(get("/api/v1/reports/" + dailyId).with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("DAILY"));
        // 남의 리포트와 없는 리포트를 구분하지 않는다
        mvc.perform(get("/api/v1/reports/" + dailyId).with(user(other.toString())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/reports/999999").with(user(owner.toString())))
                .andExpect(status().isNotFound());
    }

    // ---------- 예정 비용 ----------

    @Test
    void upcomingExpensesTellsPartialApartFromUnavailable() throws Exception {
        // 기본값은 오늘의 다음 달. 확정 스케줄이 없으니 0원이며, 읽지 못한 원본은 warnings로 드러낸다.
        mvc.perform(get("/api/v1/reports/monthly/upcoming-expenses").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.yearMonth").value("2026-10"))
                .andExpect(jsonPath("$.data.totalAmount").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.unavailableReason").doesNotExist())
                // 계좌 예정 거래 원본이 아직 없다 → 합계는 주되 과소집계임을 알린다
                .andExpect(jsonPath("$.data.partial").value(true))
                .andExpect(jsonPath("$.data.warnings[0]").value("ACCOUNT_SCHEDULE_SOURCE_UNAVAILABLE"));

        // 이미 지난 달은 남은 예정이 없다. 0원으로 꾸미지 않고 합계를 null로 둔다.
        mvc.perform(get("/api/v1/reports/monthly/upcoming-expenses").param("yearMonth", "2026-08")
                        .with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unavailableReason").value("PERIOD_ELAPSED"))
                .andExpect(jsonPath("$.data.totalAmount").doesNotExist())
                .andExpect(jsonPath("$.data.items").isEmpty());
    }

    // ---------- helpers ----------

    private static org.assertj.core.data.Offset<Double> within() {
        return org.assertj.core.data.Offset.offset(0.0001);
    }

    private void income(LocalDate date, long amount) {
        transaction(date, "INCOME", amount, null, null);
    }

    private void expense(LocalDate date, long amount, String spendingType, Long categoryId) {
        transaction(date, "EXPENSE", amount, spendingType, categoryId);
    }

    private void transaction(LocalDate date, String type, long amount, String spendingType, Long categoryId) {
        jdbc.update("""
                INSERT INTO `transaction` (transaction_id, user_id, account_id, card_id, category_id, transaction_date,
                    transaction_time, transaction_type, amount, balance_after, merchant_name, payment_method, spending_type,
                    summary, memo, hidden, source_type, created_at, updated_at)
                VALUES (?, ?, NULL, NULL, ?, ?, NULL, ?, ?, NULL, NULL, 'CARD', ?, 'test', NULL, FALSE, 'SEED', ?, NULL)
                """, UUID.randomUUID().toString(), owner, categoryId, date, type, amount, spendingType, NOW);
    }

    private void goalHistory(LocalDate recordedDate, long currentAmount, LocalDate forecastDate) {
        jdbc.update("""
                INSERT INTO goal_history (goal_id, recorded_date, current_amount, progress_rate, forecast_date, event, created_at)
                VALUES (?, ?, ?, NULL, ?, 'AMOUNT_UPDATED', ?)
                """, goalId, recordedDate, currentAmount, forecastDate, NOW);
    }

    private long category(String code, String name) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM transaction_category WHERE code = ?", Integer.class, code) == 0) {
            jdbc.update("INSERT INTO transaction_category (code, name, default_spending_type, active) VALUES (?, ?, 'VARIABLE', TRUE)",
                    code, name);
        }
        return jdbc.queryForObject("SELECT category_id FROM transaction_category WHERE code = ?", Long.class, code);
    }
}
