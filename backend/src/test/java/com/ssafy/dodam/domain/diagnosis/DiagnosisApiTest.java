package com.ssafy.dodam.domain.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.diagnosis.entity.Diagnosis;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisIssue;
import com.ssafy.dodam.domain.diagnosis.entity.DiagnosisStatus;
import com.ssafy.dodam.domain.diagnosis.entity.DetectionSource;
import com.ssafy.dodam.domain.diagnosis.entity.IssueSeverity;
import com.ssafy.dodam.domain.diagnosis.repository.DiagnosisRepository;
import com.ssafy.dodam.domain.diagnosis.service.DiagnosisClock;
import com.ssafy.dodam.domain.diagnosis.service.DiagnosisEvaluator;
import com.ssafy.dodam.domain.diagnosis.service.DiagnosisExecutionStore;
import com.ssafy.dodam.domain.diagnosis.service.DiagnosisStore;
import com.ssafy.dodam.domain.goal.entity.Goal;
import com.ssafy.dodam.domain.goal.entity.GoalType;
import com.ssafy.dodam.domain.goal.entity.SavingGoal;
import com.ssafy.dodam.domain.goal.repository.GoalRepository;
import com.ssafy.dodam.domain.goal.repository.SavingGoalRepository;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:diagnosis544;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class DiagnosisApiTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 13, 12, 0);
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired GoalRepository goals;
    @Autowired SavingGoalRepository savings;
    @Autowired DiagnosisRepository diagnoses;
    @Autowired DiagnosisExecutionStore execution;
    @Autowired DiagnosisStore store;
    @Autowired JdbcTemplate jdbc;
    @Autowired SensitiveNumberHasher hasher;
    @MockitoBean DiagnosisClock clock;
    private Long owner;

    @BeforeEach
    void setup() {
        given(clock.now()).willReturn(NOW);
        owner = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "진단 테스트")).getId();
    }

    @Test
    void emptyDataRemainsUnknownAndCacheCanBeRefreshed() throws Exception {
        String body = mvc.perform(post("/api/v1/diagnoses").with(user(owner.toString())))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.diagnosis.overallStatus").isEmpty())
                .andExpect(jsonPath("$.data.diagnosis.coverage").isArray()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).at("/data/diagnosis/diagnosisId").asLong();
        mvc.perform(post("/api/v1/diagnoses").with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.cached").value(true))
                .andExpect(jsonPath("$.data.diagnosis.diagnosisId").value(id));
        mvc.perform(post("/api/v1/diagnoses").with(user(owner.toString())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"forceRefresh\":true}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.cached").value(false));
        mvc.perform(get("/api/v1/diagnoses/{id}/issues", id).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/v1/diagnoses").with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    void goalRunAndSummaryMatchAndPastResultsDoNotChange() throws Exception {
        Goal goal = goals.save(Goal.create(owner, "목표", GoalType.SAVING, NOW.minusMonths(2)));
        savings.save(SavingGoal.create(goal.getId(), 1_000_000L, NOW.toLocalDate().plusMonths(1)));
        String body = mvc.perform(post("/api/v1/diagnoses").with(user(owner.toString())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"goalId\":" + goal.getId() + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.diagnosis.issueCount").value(1))
                .andExpect(jsonPath("$.data.diagnosis.overallStatus").value("RISK"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).at("/data/diagnosis/diagnosisId").asLong();
        mvc.perform(get("/api/v1/diagnoses/{id}/summary", id).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.byDomain.GOAL").value(1));
        String before = mvc.perform(get("/api/v1/diagnoses/{id}/issues", id).with(user(owner.toString())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        savings.save(SavingGoal.create(goal.getId(), 1L, NOW.toLocalDate().plusYears(10)));
        String after = mvc.perform(get("/api/v1/diagnoses/{id}/issues", id).with(user(owner.toString())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(after).isEqualTo(before);
    }

    @Test
    void ownershipAuthenticationAndValidationAreEnforced() throws Exception {
        Diagnosis diagnosis = store.start(owner, null, NOW);
        store.complete(diagnosis, List.of(), null, json.createObjectNode(), NOW);
        Long other = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "다른 사용자")).getId();
        Goal otherGoal = goals.save(Goal.create(other, "다른 목표", GoalType.SAVING, NOW));
        for (String suffix : List.of("", "/summary", "/issues", "/issues/1")) {
            mvc.perform(get("/api/v1/diagnoses/" + diagnosis.getDiagnosisId() + suffix).with(user(other.toString())))
                    .andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/diagnoses/" + diagnosis.getDiagnosisId() + suffix)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/diagnoses")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/diagnoses")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/diagnoses/latest")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/diagnoses").with(user(owner.toString())).contentType(MediaType.APPLICATION_JSON)
                .content("{\"goalId\":" + otherGoal.getId() + "}" )).andExpect(status().isNotFound());
        for (String query : List.of("page=-1", "size=0", "size=101")) {
            mvc.perform(get("/api/v1/diagnoses?" + query).with(user(owner.toString()))).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/diagnoses/{id}/issues?severity=UNKNOWN", diagnosis.getDiagnosisId()).with(user(owner.toString())))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/diagnoses/{id}/issues?domain=lowercase", diagnosis.getDiagnosisId()).with(user(owner.toString())))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/diagnoses/latest").with(user(other.toString()))).andExpect(status().isNotFound());
    }

    @Test
    void incompleteIsHiddenAndFailureAllowsImmediateRetry() throws Exception {
        var admitted = execution.admit(owner, null, false);
        assertThatThrownBy(() -> execution.admit(owner, null, true)).isInstanceOf(BusinessException.class);
        mvc.perform(get("/api/v1/diagnoses/{id}", admitted.diagnosis().getDiagnosisId()).with(user(owner.toString())))
                .andExpect(status().isNotFound());
        execution.abandon(owner, admitted.diagnosis().getDiagnosisId());
        assertThat(execution.admit(owner, null, false).cached()).isFalse();
    }

    @Test
    void expiredWorkerCannotCompleteOrDeleteNewWorker() {
        var old = execution.admit(owner, null, true);
        given(clock.now()).willReturn(NOW.plusMinutes(5));
        var next = execution.admit(owner, null, true);
        var empty = new DiagnosisEvaluator.Evaluation(List.of(), null, json.createObjectNode());
        assertThatThrownBy(() -> execution.complete(owner, old.diagnosis().getDiagnosisId(), empty))
                .isInstanceOf(BusinessException.class);
        execution.abandon(owner, old.diagnosis().getDiagnosisId());
        assertThat(execution.complete(owner, next.diagnosis().getDiagnosisId(), empty).isCompleted()).isTrue();
        execution.abandon(owner, next.diagnosis().getDiagnosisId());
        assertThat(diagnoses.findById(next.diagnosis().getDiagnosisId())).isPresent();
    }

    @Test
    void severityPagingClusterEvidenceAndNestedOwnership() throws Exception {
        Diagnosis d = store.start(owner, null, NOW);
        var low = DiagnosisIssue.ofRule(d.getDiagnosisId(), "SPENDING", "INCREASE", IssueSeverity.LOW,
                "점검", "근거", null, null, null, NOW);
        var high = DiagnosisIssue.ofCluster(d.getDiagnosisId(), "SPENDING", "INCREASE", IssueSeverity.CRITICAL,
                "점검", "근거", null, null, null, DetectionSource.RULE_AND_CLUSTER, "test-model",
                json.createObjectNode().put("sampleSize", 100).put("modelVersion", "test-model"), NOW);
        store.complete(d, List.of(low, high), DiagnosisStatus.RISK, json.createObjectNode(), NOW);
        String body = mvc.perform(get("/api/v1/diagnoses/{id}/issues?size=1", d.getDiagnosisId()).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content[0].severity").value("CRITICAL"))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].clusterContext.sampleSize").value(100))
                .andReturn().getResponse().getContentAsString();
        long issueId = json.readTree(body).at("/data/content/0/issueId").asLong();
        mvc.perform(get("/api/v1/diagnoses/{id}/issues/{issueId}", d.getDiagnosisId(), issueId).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.detectionSource").value("RULE_AND_CLUSTER"));
        Diagnosis another = store.start(owner, null, NOW);
        store.complete(another, List.of(), null, null, NOW);
        mvc.perform(get("/api/v1/diagnoses/{id}/issues/{issueId}", another.getDiagnosisId(), issueId).with(user(owner.toString())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/diagnoses/{id}/issues?domain=UNKNOWN", d.getDiagnosisId()).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
    }

    /**
     * 627: 카테고리 소비 증가·고금리 대출 규칙. 기준일 2026-09-13 → 최근 6~8월 vs 이전 3~5월. 식비 100,000 → 120,000/140,000/160,000
     * (+40,000, 40%, 3개월 연속). 대출은 잔액 1,200,000·12%·만기일시·10/1·11/1·12/1 3회 → 이자 36,000. 비교군 상품 A(6.0)·B(8.0)
     * 중앙값 7.0 대비 +5.0%p. 대환 후보는 잔여 3회 옵션이 있는 A뿐이고 6%면 이자 18,000 → 절감 18,000.
     */
    @Test
    void spendingIncreaseAndHighInterestLoanRulesCreateIssuesWithEstimates() throws Exception {
        long food = category("FOOD", "식비");
        for (int month = 3; month <= 5; month++) {
            expense(owner, LocalDate.of(2026, month, 5), 100_000L, food);
        }
        expense(owner, LocalDate.of(2026, 6, 5), 120_000L, food);
        expense(owner, LocalDate.of(2026, 7, 5), 140_000L, food);
        expense(owner, LocalDate.of(2026, 8, 5), 160_000L, food);
        expense(owner, LocalDate.of(2026, 9, 3), 5_000_000L, food); // 진행 중인 달은 제외
        expense(owner, LocalDate.of(2026, 8, 20), 9_000_000L, null); // 미분류는 판정하지 않음
        long company = company();
        long own = product(company, "내 신용대출", "PERSONAL_CREDIT");
        long productA = product(company, "A 신용대출", "PERSONAL_CREDIT");
        option(productA, 3, "6", "BULLET");
        long productB = product(company, "B 갈아타기", "CREDIT_REFINANCE");
        option(productB, 12, "8", "BULLET");
        long loanId = loan(owner, "9900000000001", own, "ACTIVE", 1_200_000L, "12");
        long overdue = loan(owner, "9900000000002", own, "OVERDUE", 500_000L, "9.5");

        String body = mvc.perform(post("/api/v1/diagnoses").with(user(owner.toString())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forceRefresh\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.diagnosis.ruleVersion").value("v6-last-month-vs-3m"))
                .andExpect(jsonPath("$.data.diagnosis.issueCount").value(3))
                .andExpect(jsonPath("$.data.diagnosis.overallStatus").value("RISK"))
                .andExpect(jsonPath("$.data.diagnosis.coverage[?(@.ruleKey == 'CATEGORY_SPENDING_INCREASE')].status").value("EVALUATED"))
                .andExpect(jsonPath("$.data.diagnosis.coverage[?(@.ruleKey == 'HIGH_INTEREST_RATE')].status").value("EVALUATED"))
                .andExpect(jsonPath("$.data.diagnosis.coverage[?(@.ruleKey == 'HIGH_RATE_LOAN')]").isEmpty())
                .andExpect(jsonPath("$.data.diagnosis.assumptions").value(org.hamcrest.Matchers.hasItem("REFINANCE_ESTIMATE_EXCLUDES_FEES")))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).at("/data/diagnosis/diagnosisId").asLong();

        mvc.perform(get("/api/v1/diagnoses/{id}/issues?domain=SPENDING", id).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].type").value("CATEGORY_SPENDING_INCREASE"))
                .andExpect(jsonPath("$.data.content[0].severity").value("MEDIUM"))
                .andExpect(jsonPath("$.data.content[0].title").value("식비 소비가 3개월째 늘고 있어요"))
                // 631: 지난달(8월 160,000) vs 직전 3개월(5~7월) 평균 120,000 → +40,000, 33.3%
                .andExpect(jsonPath("$.data.content[0].description").value("지난달 소비가 직전 3개월 평균보다 33.3% 높아요."))
                .andExpect(jsonPath("$.data.content[0].currentValue.categoryCode").value("FOOD"))
                .andExpect(jsonPath("$.data.content[0].currentValue.monthlyAverage").value(160_000))
                .andExpect(jsonPath("$.data.content[0].currentValue.lastMonthAmount").value(160_000))
                .andExpect(jsonPath("$.data.content[0].currentValue.increaseAmount").value(40_000))
                .andExpect(jsonPath("$.data.content[0].currentValue.increaseRatePercent").value(33.3))
                .andExpect(jsonPath("$.data.content[0].currentValue.consecutiveMonths").value(3))
                .andExpect(jsonPath("$.data.content[0].currentValue.recentPeriod.months").value(1))
                .andExpect(jsonPath("$.data.content[0].currentValue.recentPeriod.startMonth").value("2026-08"))
                .andExpect(jsonPath("$.data.content[0].benchmarkValue.previousMonthlyAverage").value(120_000))
                .andExpect(jsonPath("$.data.content[0].benchmarkValue.baselinePeriod.months").value(3))
                .andExpect(jsonPath("$.data.content[0].benchmarkValue.baselinePeriod.startMonth").value("2026-05"))
                .andExpect(jsonPath("$.data.content[0].benchmarkValue.baselinePeriod.endMonth").value("2026-07"))
                .andExpect(jsonPath("$.data.content[0].evidence.targetId").value(food));

        String loans = mvc.perform(get("/api/v1/diagnoses/{id}/issues?domain=LOAN", id).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
                .andReturn().getResponse().getContentAsString();
        var byLoan = new java.util.HashMap<Long, com.fasterxml.jackson.databind.JsonNode>();
        for (var issue : json.readTree(loans).at("/data/content")) {
            byLoan.put(issue.at("/currentValue/loanId").asLong(), issue);
        }
        var active = byLoan.get(loanId);
        assertThat(active.get("type").asText()).isEqualTo("HIGH_INTEREST_RATE");
        assertThat(active.get("severity").asText()).isEqualTo("HIGH");
        assertThat(active.get("title").asText()).isEqualTo("'신용대출' 금리가 비교상품 공시금리보다 높아요");
        assertThat(active.at("/currentValue/interestRate").decimalValue()).isEqualByComparingTo("12");
        assertThat(active.at("/currentValue/referenceRate").decimalValue()).isEqualByComparingTo("7");
        assertThat(active.at("/currentValue/rateDifference").decimalValue()).isEqualByComparingTo("5");
        assertThat(active.at("/currentValue/balance").asLong()).isEqualTo(1_200_000L);
        assertThat(active.at("/currentValue/refinance/available").asBoolean()).isTrue();
        assertThat(active.at("/currentValue/refinance/targetProductId").asLong()).isEqualTo(productA);
        assertThat(active.at("/currentValue/refinance/appliedRate").decimalValue()).isEqualByComparingTo("6");
        assertThat(active.at("/currentValue/refinance/remainingInstallments").asInt()).isEqualTo(3);
        assertThat(active.at("/currentValue/refinance/beforeInterest").asLong()).isEqualTo(36_000L);
        assertThat(active.at("/currentValue/refinance/afterInterest").asLong()).isEqualTo(18_000L);
        assertThat(active.at("/currentValue/refinance/interestSaving").asLong()).isEqualTo(18_000L);
        assertThat(active.at("/currentValue/refinance/feesIncluded").asBoolean()).isFalse();
        assertThat(active.at("/benchmarkValue/rateSpreadThreshold").decimalValue()).isEqualByComparingTo("1");
        assertThat(active.at("/evidence/targetId").asLong()).isEqualTo(loanId);
        assertThat(active.toString()).doesNotContain("9900000000001").doesNotContain("accountNoMasked");
        var overdueIssue = byLoan.get(overdue);
        assertThat(overdueIssue.get("severity").asText()).isEqualTo("HIGH");
        assertThat(overdueIssue.at("/currentValue/refinance/available").asBoolean()).isFalse();
        assertThat(overdueIssue.at("/currentValue/refinance/unavailableReason").asText()).isEqualTo("LOAN_OVERDUE");
        assertThat(overdueIssue.get("description").asText()).contains("절감액은 계산하지 못했어요");
    }

    private long category(String code, String name) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM transaction_category WHERE code = ?", Integer.class, code) == 0) {
            jdbc.update("INSERT INTO transaction_category (code, name, default_spending_type, active) VALUES (?, ?, 'VARIABLE', TRUE)", code, name);
        }
        return jdbc.queryForObject("SELECT category_id FROM transaction_category WHERE code = ?", Long.class, code);
    }

    private void expense(Long userId, LocalDate date, long amount, Long categoryId) {
        jdbc.update("""
                INSERT INTO `transaction` (transaction_id, user_id, account_id, card_id, category_id, transaction_date,
                    transaction_time, transaction_type, amount, balance_after, merchant_name, payment_method, spending_type,
                    summary, memo, hidden, source_type, created_at, updated_at)
                VALUES (?, ?, NULL, NULL, ?, ?, NULL, 'EXPENSE', ?, NULL, NULL, 'CARD', 'VARIABLE', 'test', NULL, FALSE, 'SEED', ?, NULL)
                """, UUID.randomUUID().toString(), userId, categoryId, date, amount, NOW);
    }

    private long company() {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM financial_company WHERE company_code = 'TESTBANK'", Integer.class) == 0) {
            jdbc.update("INSERT INTO financial_company (company_code, company_name, sector) VALUES ('TESTBANK', '테스트은행', 'BANK')");
        }
        return jdbc.queryForObject("SELECT company_id FROM financial_company WHERE company_code = 'TESTBANK'", Long.class);
    }

    private long product(long companyId, String name, String loanType) {
        jdbc.update("""
                INSERT INTO financial_product (company_id, source_type, external_product_code, product_type, product_name, join_way,
                    saving_type, loan_type, special_condition, join_member, min_rate, max_rate, max_limit, baseline_performance,
                    max_benefit_limit, description, benefits, synced_at)
                VALUES (?, 'SEED', NULL, 'LOAN', ?, NULL, NULL, ?, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, ?)
                """, companyId, name, loanType, NOW.minusDays(1));
        return jdbc.queryForObject("SELECT MAX(product_id) FROM financial_product", Long.class);
    }

    private void option(long productId, int term, String minRate, String repaymentType) {
        jdbc.update("""
                INSERT INTO financial_product_option (product_id, term, rate, preferred_rate, rate_type, min_rate, max_rate, repayment_type, limit_amount)
                VALUES (?, ?, NULL, NULL, 'FIXED', ?, 10, ?, NULL)
                """, productId, term, new java.math.BigDecimal(minRate), repaymentType);
    }

    /** 만기일시 12개월 약정, 다음 상환일 2026-10-01, 만기 2026-12-01 → 기준일 이후 3회. */
    private long loan(Long userId, String accountNo, long productId, String status, long balance, String rate) {
        jdbc.update("""
                INSERT INTO loan (user_id, withdrawal_account_id, withdrawal_account_type, financial_product_id, institution_code,
                    institution_name, account_no_hash, account_no_masked, account_name, loan_type, repayment_type, status, loan_period,
                    start_date, maturity_date, original_principal, balance, interest_rate, repayment_day, next_repayment_date, created_at, updated_at)
                VALUES (?, 1, 'DEMAND_DEPOSIT', ?, 'TESTBANK', '테스트은행', ?, '9900-***-**001', '신용대출', 'CREDIT', 'BULLET', ?, 12,
                    ?, ?, ?, ?, ?, 1, ?, ?, NULL)
                """, userId, productId, hasher.hash(accountNo), status, LocalDate.of(2025, 12, 1), LocalDate.of(2026, 12, 1), balance, balance,
                new java.math.BigDecimal(rate), LocalDate.of(2026, 10, 1), NOW);
        return jdbc.queryForObject("SELECT loan_id FROM loan WHERE user_id = ? AND account_no_hash = ?", Long.class, userId, hasher.hash(accountNo));
    }

    @Test
    void simultaneousAdmissionCreatesOnlyOneExecution() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var task = (java.util.concurrent.Callable<Boolean>) () -> {
                start.await();
                try { execution.admit(owner, null, true); return true; }
                catch (BusinessException conflict) { return false; }
            };
            var first = pool.submit(task);
            var second = pool.submit(task);
            start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
        }
    }
}
