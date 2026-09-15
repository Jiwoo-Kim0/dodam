package com.ssafy.dodam.domain.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.goal.entity.Goal;
import com.ssafy.dodam.domain.goal.entity.GoalAccount;
import com.ssafy.dodam.domain.goal.entity.GoalType;
import com.ssafy.dodam.domain.goal.entity.SavingGoal;
import com.ssafy.dodam.domain.goal.repository.GoalAccountRepository;
import com.ssafy.dodam.domain.goal.repository.GoalRepository;
import com.ssafy.dodam.domain.goal.repository.SavingGoalRepository;
import com.ssafy.dodam.domain.goal.service.GoalClock;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import com.ssafy.dodam.domain.simulation.service.SimulationClock;
import com.ssafy.dodam.domain.user.entity.FinancialProfile;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.FinancialProfileRepository;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 587 통합 회귀: 명세 17 실행 10종을 한 fixture에서 실행하고 공통 조회 8경로·삭제와의 계약을 유형별로 같은 기준으로 확인한다.
 * 유형별 계산값은 550·551·585·586 테스트가 검증하므로 여기서는 실행 응답과 조회 응답의 동일성, 멱등, 인증·소유권, 빈 결과, 경계값만 본다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
// 586 테스트와 같은 URL·MockitoBean 구성을 써서 Spring 컨텍스트를 공유한다. 컨텍스트가 늘면 캐시 축출로 닫힌 컨텍스트의
// create-drop이 공유 H2 테이블을 지워 다른 테스트가 깨진다(2026-09-15 전체 빌드에서 확인).
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:simulation586;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class SimulationIntegrationApiTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 15, 12, 0);
    private static final String LOAN_NO = "1234567890123";
    private static final String GOAL_ACCOUNT_NO = "1111222233334444";
    private static final List<String> QUERY_PATHS = List.of("", "/comparison", "/cashflow", "/goal-impact", "/assets", "/debts", "/costs");
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired FinancialProfileRepository profiles;
    @Autowired GoalRepository goals;
    @Autowired SavingGoalRepository savingGoals;
    @Autowired GoalAccountRepository goalAccounts;
    @Autowired SensitiveNumberHasher hasher;
    @MockitoBean SimulationClock clock;
    @MockitoBean GoalClock goalClock;
    private Long owner;
    private Long other;
    private Long goalId;
    /** 실행 경로 → 요청 본문. 10종 전부이며 순서는 명세 17 순서다. */
    private Map<String, String> requests;

    @BeforeEach
    void setup() {
        given(clock.now()).willReturn(NOW);
        given(goalClock.now()).willReturn(NOW);
        given(goalClock.today()).willReturn(NOW.toLocalDate());
        jdbc.update("DELETE FROM simulation_goal_impact");
        jdbc.update("DELETE FROM simulation_scenario");
        jdbc.update("DELETE FROM simulation");
        jdbc.update("DELETE FROM card");
        jdbc.update("DELETE FROM loan");
        jdbc.update("DELETE FROM financial_product_option");
        jdbc.update("DELETE FROM financial_product");
        jdbc.update("DELETE FROM financial_company");
        owner = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "시뮬레이션")).getId();
        other = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "다른 사용자")).getId();
        FinancialProfile profile = FinancialProfile.empty(owner);
        profile.update(3_000_000L, null, null, null, null);
        profiles.save(profile);
        jdbc.update("DELETE FROM `transaction` WHERE user_id = ?", owner);
        for (int month = 6; month <= 8; month++) {
            expense(LocalDate.of(2026, month, 20), 600_000L);
        }
        Long accountId = insertAccount(owner, GOAL_ACCOUNT_NO, 1_000_000L);
        Goal goal = goals.save(Goal.create(owner, "비상금", GoalType.SAVING, NOW.minusMonths(3)));
        goalId = goal.getId();
        savingGoals.save(SavingGoal.create(goalId, 3_000_000L, NOW.toLocalDate().plusYears(1)));
        goalAccounts.save(GoalAccount.link(owner, goalId, accountId, "DEMAND_DEPOSIT"));
        Long cardId = insertCard(owner, accountId);

        jdbc.update("INSERT INTO financial_company (company_code, company_name, sector) VALUES ('TESTBANK', '테스트은행', 'BANK')");
        long company = jdbc.queryForObject("SELECT company_id FROM financial_company WHERE company_code = 'TESTBANK'", Long.class);
        long deposit = insertProduct(company, "DEPOSIT", "정기예금", "FIXED_DEPOSIT", null);
        option(deposit, 12, "3.65", null);
        long credit = insertProduct(company, "LOAN", "신용대출", null, "PERSONAL_CREDIT");
        option(credit, 2, "6", "BULLET");
        option(credit, 12, "6", "EQUAL_PRINCIPAL");
        insertLoan(owner, LOAN_NO, credit, 1_200_000L, LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 15));

        requests = new LinkedHashMap<>();
        requests.put("/spending", "{\"monthlyReduction\":100000}");
        requests.put("/saving", "{\"monthlySavingAmount\":500000}");
        requests.put("/saving-product", "{\"productId\":" + deposit + ",\"monthlyAmount\":1000000,\"term\":12}");
        requests.put("/loan/prepayment", "{\"accountNo\":\"" + LOAN_NO + "\",\"prepaymentAmount\":200000}");
        requests.put("/loan/refinance", "{\"accountNo\":\"" + LOAN_NO + "\",\"targetProductId\":" + credit + "}");
        requests.put("/loan/new", "{\"productId\":" + credit + ",\"principal\":1200000,\"term\":12}");
        requests.put("/installment", "{\"purchaseAmount\":1200000,\"months\":2,\"feeRate\":12}");
        requests.put("/revolving", "{\"cardId\":" + cardId + ",\"balance\":1000000,\"paymentRatio\":50,\"rate\":12}");
        requests.put("/cash-advance", "{\"amount\":1000000,\"rate\":12}");
        requests.put("/combined", "{\"scenarios\":[{\"type\":\"SPENDING\",\"parameters\":{\"monthlyReduction\":100000}},"
                + "{\"type\":\"INSTALLMENT\",\"parameters\":{\"purchaseAmount\":1200000,\"months\":2,\"feeRate\":12}}]}");
    }

    @Test
    void everyTypeRunsAndCommonQueriesReturnTheStoredResultUnchanged() throws Exception {
        assertThat(requests).hasSize(SimulationType.values().length);
        for (Map.Entry<String, String> entry : requests.entrySet()) {
            String path = entry.getKey();
            String body = run(owner, path, entry.getValue()).andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.created").value(true))
                    .andExpect(jsonPath("$.data.simulation.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.data.simulation.request.requestHash").doesNotExist())
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).as(path).doesNotContain(LOAN_NO).doesNotContain(GOAL_ACCOUNT_NO).doesNotContain(hasher.hash(LOAN_NO));
            JsonNode simulation = json.readTree(body).at("/data/simulation");
            long id = simulation.path("simulationId").asLong();

            // 조회 7경로는 실행 응답을 저장값 그대로 돌려준다(재계산 없음)
            assertThat(data(get("/api/v1/simulations/{id}", id))).as(path + " detail").isEqualTo(simulation);
            assertThat(data(get("/api/v1/simulations/{id}/comparison", id))).as(path + " comparison").isEqualTo(simulation.path("comparison"));
            assertThat(data(get("/api/v1/simulations/{id}/cashflow", id))).as(path + " cashflow").isEqualTo(simulation.path("cashflow"));
            assertThat(data(get("/api/v1/simulations/{id}/goal-impact", id))).as(path + " goal-impact").isEqualTo(simulation.path("goalImpacts"));
            for (String section : List.of("assets", "debts", "costs")) {
                JsonNode page = data(get("/api/v1/simulations/{id}/" + section + "?size=100", id));
                assertThat(page.path("content")).as(path + " " + section).isEqualTo(simulation.path("comparison").path(section));
                assertThat(page.path("totalElements").asInt()).isEqualTo(simulation.path("comparison").path(section).size());
            }
            // 같은 날 같은 요청은 재사용
            run(owner, path, entry.getValue()).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.created").value(false))
                    .andExpect(jsonPath("$.data.simulation.simulationId").value(id));
        }
        String list = mvc.perform(get("/api/v1/simulations?size=100").with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(requests.size()))
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(list).at("/data/content").findValuesAsText("type"))
                .containsExactlyInAnyOrderElementsOf(List.of(SimulationType.values()).stream().map(Enum::name).toList());

        // 기준일이 바뀌면 10종 모두 신규 건이다
        given(clock.now()).willReturn(NOW.plusDays(1));
        given(goalClock.now()).willReturn(NOW.plusDays(1));
        given(goalClock.today()).willReturn(NOW.toLocalDate().plusDays(1));
        for (Map.Entry<String, String> entry : requests.entrySet()) {
            run(owner, entry.getKey(), entry.getValue()).andExpect(status().isCreated()).andExpect(jsonPath("$.data.created").value(true));
        }
        mvc.perform(get("/api/v1/simulations?size=100").with(user(owner.toString()))).andExpect(jsonPath("$.data.totalElements").value(requests.size() * 2));
    }

    @Test
    void authenticationAndOwnershipAreEnforcedOnEveryPath() throws Exception {
        for (Map.Entry<String, String> entry : requests.entrySet()) {
            mvc.perform(post("/api/v1/simulations" + entry.getKey()).contentType(MediaType.APPLICATION_JSON).content(entry.getValue()))
                    .andExpect(status().isUnauthorized());
        }
        String body = run(owner, "/installment", requests.get("/installment")).andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).at("/data/simulation/simulationId").asLong();
        mvc.perform(get("/api/v1/simulations")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/simulations/{id}", id)).andExpect(status().isUnauthorized());
        for (String path : QUERY_PATHS) {
            mvc.perform(get("/api/v1/simulations/{id}" + path, id)).andExpect(status().isUnauthorized());
            // 타인 건은 존재를 드러내지 않는다
            mvc.perform(get("/api/v1/simulations/{id}" + path, id).with(user(other.toString()))).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }
        mvc.perform(delete("/api/v1/simulations/{id}", id).with(user(other.toString()))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/simulations").with(user(other.toString()))).andExpect(jsonPath("$.data.totalElements").value(0));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM simulation WHERE deleted_at IS NOT NULL", Integer.class)).isZero();

        // 삭제 후 모든 조회가 404이고 목록에서 빠지며 같은 요청은 새 건이다
        mvc.perform(delete("/api/v1/simulations/{id}", id).with(user(owner.toString()))).andExpect(status().isNoContent());
        for (String path : QUERY_PATHS) {
            mvc.perform(get("/api/v1/simulations/{id}" + path, id).with(user(owner.toString()))).andExpect(status().isNotFound());
        }
        mvc.perform(delete("/api/v1/simulations/{id}", id).with(user(owner.toString()))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/simulations").with(user(owner.toString()))).andExpect(jsonPath("$.data.totalElements").value(0));
        run(owner, "/installment", requests.get("/installment")).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.simulation.simulationId").value(org.hamcrest.Matchers.not((int) id)));
    }

    @Test
    void emptyDataUserGetsEmptyGoalImpactsAndSectionsWithPageBoundaries() throws Exception {
        // 목표·소득·거래·계좌가 없는 사용자: 사용자 입력만으로 성립하는 유형은 실행되고 목표 영향은 빈 배열이다
        mvc.perform(get("/api/v1/simulations").with(user(other.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0)).andExpect(jsonPath("$.data.content").isEmpty());
        String body = run(other, "/cash-advance", "{\"amount\":1000000,\"rate\":12}").andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.simulation.goalImpacts").isEmpty())
                .andExpect(jsonPath("$.data.simulation.before.monthlyIncome").isEmpty())
                .andExpect(jsonPath("$.data.simulation.before.coverage").isArray())
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).at("/data/simulation/simulationId").asLong();
        mvc.perform(get("/api/v1/simulations/{id}/goal-impact", id).with(user(other.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        // 범위를 넘긴 페이지는 오류가 아니라 빈 페이지다
        for (String section : List.of("assets", "debts", "costs")) {
            mvc.perform(get("/api/v1/simulations/{id}/" + section + "?page=1&size=100", id).with(user(other.toString())))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.content").isEmpty());
        }
        // 페이지 경계: page ≥ 0, size 1~100
        for (String path : List.of("", "/" + id + "/assets", "/" + id + "/debts", "/" + id + "/costs")) {
            for (String query : List.of("page=-1", "size=0", "size=101")) {
                mvc.perform(get("/api/v1/simulations" + path + "?" + query).with(user(other.toString()))).andExpect(status().isBadRequest());
            }
            mvc.perform(get("/api/v1/simulations" + path + "?page=0&size=100").with(user(other.toString()))).andExpect(status().isOk());
        }
        // 식별자 경계: 0·음수·비숫자는 400, 없는 id는 404
        for (String bad : List.of("0", "-1", "abc")) {
            mvc.perform(get("/api/v1/simulations/" + bad).with(user(other.toString()))).andExpect(status().isBadRequest());
            mvc.perform(delete("/api/v1/simulations/" + bad).with(user(other.toString()))).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/simulations/{id}", id + 1_000_000).with(user(other.toString()))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/simulations?type=UNKNOWN").with(user(other.toString()))).andExpect(status().isBadRequest());
        // 자원 참조 유형은 자료가 없으면 404·400이며 저장하지 않는다
        run(other, "/loan/prepayment", requests.get("/loan/prepayment")).andExpect(status().isNotFound());
        run(other, "/revolving", requests.get("/revolving")).andExpect(status().isNotFound());
        run(other, "/spending", "{\"monthlyReduction\":1}").andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM simulation WHERE user_id = ?", Integer.class, other)).isEqualTo(1);
    }

    private JsonNode data(MockHttpServletRequestBuilder request) throws Exception {
        String body = mvc.perform(request.with(user(owner.toString())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data");
    }

    private ResultActions run(Long userId, String path, String body) throws Exception {
        return mvc.perform(post("/api/v1/simulations" + path).with(user(userId.toString()))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void expense(LocalDate date, long amount) {
        jdbc.update("""
                INSERT INTO `transaction` (transaction_id, user_id, account_id, card_id, category_id, transaction_date,
                    transaction_time, transaction_type, amount, balance_after, merchant_name, payment_method, spending_type,
                    summary, memo, hidden, source_type, created_at, updated_at)
                VALUES (?, ?, NULL, NULL, NULL, ?, NULL, 'EXPENSE', ?, NULL, NULL, 'CARD', 'VARIABLE', 'test', NULL, FALSE, 'SEED', ?, NULL)
                """, UUID.randomUUID().toString(), owner, date, amount, NOW);
    }

    private long insertProduct(long companyId, String type, String name, String savingType, String loanType) {
        jdbc.update("""
                INSERT INTO financial_product (company_id, source_type, external_product_code, product_type, product_name, join_way,
                    saving_type, loan_type, special_condition, join_member, min_rate, max_rate, max_limit, baseline_performance,
                    max_benefit_limit, description, benefits, synced_at)
                VALUES (?, 'SEED', NULL, ?, ?, NULL, ?, ?, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, ?)
                """, companyId, type, name, savingType, loanType, LocalDateTime.of(2026, 9, 1, 9, 0));
        return jdbc.queryForObject("SELECT MAX(product_id) FROM financial_product", Long.class);
    }

    private void option(long productId, int term, String rate, String repaymentType) {
        // 예적금은 rate, 대출은 min_rate/max_rate를 읽는다
        jdbc.update("""
                INSERT INTO financial_product_option (product_id, term, rate, preferred_rate, rate_type, min_rate, max_rate, repayment_type, limit_amount)
                VALUES (?, ?, ?, NULL, 'FIXED', ?, ?, ?, NULL)
                """, productId, term, repaymentType == null ? new java.math.BigDecimal(rate) : null,
                repaymentType == null ? null : new java.math.BigDecimal(rate), repaymentType == null ? null : new java.math.BigDecimal("15"),
                repaymentType);
    }

    private void insertLoan(Long userId, String accountNo, long productId, long balance, LocalDate next, LocalDate maturity) {
        jdbc.update("""
                INSERT INTO loan (user_id, withdrawal_account_id, withdrawal_account_type, financial_product_id, institution_code,
                    institution_name, account_no_hash, account_no_masked, account_name, loan_type, repayment_type, status, loan_period,
                    start_date, maturity_date, original_principal, balance, interest_rate, repayment_day, next_repayment_date, created_at, updated_at)
                VALUES (?, 1, 'DEMAND_DEPOSIT', ?, 'TESTBANK', '테스트은행', ?, '1234-***-**123', '신용대출', 'CREDIT', 'BULLET', 'ACTIVE', 12,
                    ?, ?, ?, ?, 12, 15, ?, ?, NULL)
                """, userId, productId, hasher.hash(accountNo), maturity.minusYears(1), maturity, balance, balance, next, NOW);
    }

    private Long insertAccount(Long userId, String accountNo, long balance) {
        jdbc.update("""
                INSERT INTO account (user_id, bank_code, bank_name, account_no_encrypted, account_no_hash, account_no_masked,
                    account_name, account_type, status, balance, last_transaction_date, currency, source_type, created_at, updated_at)
                VALUES (?, '004', 'KB국민은행', X'00', ?, '1111-**-**4444', '계좌', 'DEMAND_DEPOSIT', 'ACTIVE', ?, NULL, 'KRW', 'SEED', ?, ?)
                """, userId, hasher.hash(accountNo), balance, NOW, NOW);
        return jdbc.queryForObject("SELECT account_id FROM account WHERE user_id = ? AND account_no_hash = ?", Long.class,
                userId, hasher.hash(accountNo));
    }

    private Long insertCard(Long userId, Long accountId) {
        jdbc.update("""
                INSERT INTO card (user_id, withdrawal_account_id, withdrawal_account_type, card_no_hash, card_no_masked,
                                  issuer_code, issuer_name, card_name, status, credit_limit, created_at)
                VALUES (?, ?, 'DEMAND_DEPOSIT', ?, '1234-****-****-5678', 'KB_CARD', 'KB국민카드', '테스트카드', 'ACTIVE', 3000000, ?)
                """, userId, accountId, "5".repeat(64), Timestamp.valueOf(NOW));
        return jdbc.queryForObject("SELECT MAX(card_id) FROM card WHERE user_id = ?", Long.class, userId);
    }
}
