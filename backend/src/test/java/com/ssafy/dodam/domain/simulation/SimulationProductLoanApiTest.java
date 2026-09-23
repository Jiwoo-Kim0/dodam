package com.ssafy.dodam.domain.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
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
import com.ssafy.dodam.domain.simulation.entity.Simulation;
import com.ssafy.dodam.domain.simulation.repository.SimulationRepository;
import com.ssafy.dodam.domain.simulation.repository.SimulationScenarioRepository;
import com.ssafy.dodam.domain.simulation.service.SimulationClock;
import com.ssafy.dodam.domain.user.entity.FinancialProfile;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.FinancialProfileRepository;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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
import org.springframework.test.web.servlet.ResultActions;

/**
 * 585 실행 API 4종과 공통 조회 연결. 기준일 2026-01-01로 고정해 {@code ProductLoanSimulationCalculatorTest}의 검산 값을 그대로 쓴다.
 * 상품·대출 fixture는 엔티티 스키마(H2)에 직접 넣으며 모든 번호·금리는 합성 값이다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:simulation585;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class SimulationProductLoanApiTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 1, 12, 0);
    private static final String LOAN_NO = "1234567890123";
    private static final String OTHER_LOAN_NO = "9876543210987";
    private static final String GOAL_ACCOUNT_NO = "1111222233334444";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired FinancialProfileRepository profiles;
    @Autowired GoalRepository goals;
    @Autowired SavingGoalRepository savingGoals;
    @Autowired GoalAccountRepository goalAccounts;
    @Autowired SimulationRepository simulations;
    @Autowired SimulationScenarioRepository scenarios;
    @Autowired SensitiveNumberHasher hasher;
    @MockitoBean SimulationClock clock;
    @MockitoBean GoalClock goalClock;
    private Long owner;
    private Long other;
    private Long goalId;
    private long depositProduct;
    private long savingsProduct;
    private long creditProduct;
    private long indexDepositProduct;
    private long mortgageProduct;
    private long ownerLoanId;

    @BeforeEach
    void setup() {
        given(clock.now()).willReturn(NOW);
        given(goalClock.now()).willReturn(NOW);
        given(goalClock.today()).willReturn(NOW.toLocalDate());
        jdbc.update("DELETE FROM simulation_goal_impact");
        jdbc.update("DELETE FROM simulation_scenario");
        jdbc.update("DELETE FROM simulation");
        jdbc.update("DELETE FROM loan");
        jdbc.update("DELETE FROM financial_product_option");
        jdbc.update("DELETE FROM financial_product");
        jdbc.update("DELETE FROM financial_company");
        owner = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "시뮬레이션")).getId();
        other = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "다른 사용자")).getId();
        FinancialProfile profile = FinancialProfile.empty(owner);
        profile.update(3_000_000L, null, null, null, null);
        profiles.save(profile);
        Long accountId = insertAccount(owner, GOAL_ACCOUNT_NO, 1_000_000L);
        // 저축속도(694·702): 연결 입출금 계좌의 2025-10~12 순현금흐름 333,333 × 3 / 3 = 333,333, 남은 2,000,000 → 6개월 + 1일(2026-07-02, 658 일 단위)
        for (int month = 10; month <= 12; month++) {
            income(accountId, LocalDate.of(2025, month, 25), 333_333L);
        }
        Goal goal = goals.save(Goal.create(owner, "비상금", GoalType.SAVING, NOW.minusMonths(3)));
        goalId = goal.getId();
        savingGoals.save(SavingGoal.create(goalId, 3_000_000L, NOW.toLocalDate().plusYears(1)));
        goalAccounts.save(GoalAccount.link(owner, goalId, accountId, "DEMAND_DEPOSIT"));

        jdbc.update("INSERT INTO financial_company (company_code, company_name, sector) VALUES ('TESTBANK', '테스트은행', 'BANK')");
        long company = jdbc.queryForObject("SELECT company_id FROM financial_company WHERE company_code = 'TESTBANK'", Long.class);
        depositProduct = insertProduct(company, "DEPOSIT", "정기예금", "FIXED_DEPOSIT", null, null);
        option(depositProduct, 12, "3.65", null, null, null, null);
        option(depositProduct, 24, null, null, null, null, null); // 금리 누락 옵션
        savingsProduct = insertProduct(company, "SAVINGS", "자유적금", "FLEXIBLE", null, null);
        option(savingsProduct, 2, "3.65", null, null, null, null);
        indexDepositProduct = insertProduct(company, "DEPOSIT", "지수연동예금", "INDEX_LINKED_DEPOSIT", null, null);
        option(indexDepositProduct, 12, "5", null, null, null, null);
        creditProduct = insertProduct(company, "LOAN", "신용대출", null, "PERSONAL_CREDIT", 50_000_000L);
        option(creditProduct, 12, null, "0", "10", "EQUAL_PRINCIPAL", 5_000_000L);
        option(creditProduct, 2, null, "6", "10", "BULLET", null);
        mortgageProduct = insertProduct(company, "LOAN", "주택담보대출", null, "MORTGAGE", null);
        option(mortgageProduct, 2, null, "3", "5", "BULLET", null);
        // 검산표 대출: 잔액 1,200,000, 12%, BULLET, 2/1·3/1(만기 3/1), 신용대출 상품 연결
        ownerLoanId = insertLoan(owner, LOAN_NO, creditProduct, "ACTIVE", 1_200_000L, "12", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1));
        insertLoan(other, OTHER_LOAN_NO, creditProduct, "ACTIVE", 1_200_000L, "12", LocalDate.of(2026, 2, 1), LocalDate.of(2026, 3, 1));
    }

    @Test
    void savingProductRunsStoresAndLinksToCommonQueries() throws Exception {
        String body = run(owner, "/saving-product", "{\"productId\":" + depositProduct + ",\"monthlyAmount\":1000000,\"term\":12}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.created").value(true))
                .andExpect(jsonPath("$.data.simulation.type").value("SAVING_PRODUCT"))
                .andExpect(jsonPath("$.data.simulation.request.productId").value(depositProduct))
                .andExpect(jsonPath("$.data.simulation.request.term").value(12))
                .andExpect(jsonPath("$.data.simulation.after.details.grossInterest").value(36_500))
                .andExpect(jsonPath("$.data.simulation.after.details.grossPayout").value(1_036_500))
                .andExpect(jsonPath("$.data.simulation.after.details.taxBasis").value("PRE_TAX"))
                .andExpect(jsonPath("$.data.simulation.after.details.product.option.rate").value(3.65))
                .andExpect(jsonPath("$.data.simulation.after.details.product.syncedAt").exists())
                .andExpect(jsonPath("$.data.simulation.after.assumptions").isArray())
                .andExpect(jsonPath("$.data.simulation.cashflow.basis").value("SCENARIO_NET_CASH_CHANGE"))
                .andExpect(jsonPath("$.data.simulation.cashflow.before").isEmpty())
                .andExpect(jsonPath("$.data.simulation.cashflow.currentMonth.yearMonth").value("2026-01"))
                .andExpect(jsonPath("$.data.simulation.cashflow.currentMonth.delta").value(-1_000_000))
                .andExpect(jsonPath("$.data.simulation.cashflow.truncated").value(false))
                .andExpect(jsonPath("$.data.simulation.cashflow.fullTermEndDate").value("2027-01-01"))
                .andExpect(jsonPath("$.data.simulation.cashflow.months.length()").value(12))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].goalId").value(goalId))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].beforeForecastDate").value("2026-07-02"))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].afterForecastDate").isEmpty())
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].differenceDays").isEmpty())
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].status").value("NOT_COMPUTABLE"))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].reasons[0]").value("FUNDING_SOURCE_UNKNOWN"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).at("/data/simulation/simulationId").asLong();
        JsonNode runImpacts = json.readTree(body).at("/data/simulation/goalImpacts");

        // 상세·goal-impact·섹션 조회가 같은 저장값을 돌려준다
        String detail = mvc.perform(get("/api/v1/simulations/{id}", id).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.after.details.grossInterest").value(36_500))
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(detail).at("/data/goalImpacts")).isEqualTo(runImpacts);
        String goalImpact = mvc.perform(get("/api/v1/simulations/{id}/goal-impact", id).with(user(owner.toString())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(goalImpact).at("/data")).isEqualTo(runImpacts);
        mvc.perform(get("/api/v1/simulations/{id}/assets", id).with(user(owner.toString())))
                .andExpect(jsonPath("$.data.totalElements").value(4))
                .andExpect(jsonPath("$.data.content[0].key").value("INITIAL_CASH_CHANGE"))
                .andExpect(jsonPath("$.data.content[0].after").value(-1_000_000));
        mvc.perform(get("/api/v1/simulations/{id}/costs", id).with(user(owner.toString()))).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/v1/simulations/{id}/cashflow", id).with(user(owner.toString())))
                .andExpect(jsonPath("$.data.months[11].delta").value(1_036_500));
        mvc.perform(get("/api/v1/simulations?type=SAVING_PRODUCT").with(user(owner.toString()))).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/v1/simulations/{id}", id).with(user(other.toString()))).andExpect(status().isNotFound());

        // 같은 날 같은 요청은 상품 금리가 바뀌어도 재사용
        jdbc.update("UPDATE financial_product_option SET rate = 9.99 WHERE product_id = ? AND term = 12", depositProduct);
        run(owner, "/saving-product", "{\"productId\":" + depositProduct + ",\"monthlyAmount\":1000000,\"term\":12}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.created").value(false))
                .andExpect(jsonPath("$.data.simulation.simulationId").value(id))
                .andExpect(jsonPath("$.data.simulation.after.details.grossInterest").value(36_500));
        // 적금: 2개월 검산(이자 870)
        run(owner, "/saving-product", "{\"productId\":" + savingsProduct + ",\"monthlyAmount\":100000,\"term\":2}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.simulation.after.details.grossInterest").value(870))
                .andExpect(jsonPath("$.data.simulation.after.assumptions", org.hamcrest.Matchers.hasItem("FIXED_MONTHLY_INSTALLMENT")))
                .andExpect(jsonPath("$.data.simulation.cashflow.months[1].delta").value(200_870));
        assertThat(simulations.count()).isEqualTo(2);
    }

    @Test
    void savingProductRejectsMissingUnsupportedAndInvalidWithoutStoring() throws Exception {
        run(owner, "/saving-product", "{\"productId\":999999,\"monthlyAmount\":1,\"term\":12}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        run(owner, "/saving-product", "{\"productId\":" + indexDepositProduct + ",\"monthlyAmount\":1,\"term\":12}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SIMULATION_PRODUCT_NOT_SUPPORTED"));
        run(owner, "/saving-product", "{\"productId\":" + creditProduct + ",\"monthlyAmount\":1,\"term\":12}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SIMULATION_PRODUCT_NOT_SUPPORTED"));
        for (int term : List.of(6, 24)) { // 없는 기간, 금리 누락 기간
            run(owner, "/saving-product", "{\"productId\":" + depositProduct + ",\"monthlyAmount\":1,\"term\":" + term + "}")
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SIMULATION_PRODUCT_OPTION_UNAVAILABLE"));
        }
        for (String payload : List.of("{}", "{\"productId\":1,\"monthlyAmount\":0,\"term\":12}", "{\"productId\":1,\"monthlyAmount\":1,\"term\":0}",
                "{\"productId\":1,\"monthlyAmount\":1,\"term\":121}", "{\"productId\":0,\"monthlyAmount\":1,\"term\":12}",
                "{\"productId\":1,\"monthlyAmount\":1,\"term\":\"abc\"}", "{\"productId\":1,\"monthlyAmount\":\"x\",\"term\":12}")) {
            run(owner, "/saving-product", payload).andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/v1/simulations/saving-product").contentType(MediaType.APPLICATION_JSON)
                .content("{\"productId\":1,\"monthlyAmount\":1,\"term\":12}")).andExpect(status().isUnauthorized());
        assertThat(simulations.count()).isZero();
    }

    @Test
    void loanNewAutoModeReusesFirstSelectionAndExplicitModeRevalidates() throws Exception {
        String body = run(owner, "/loan/new", "{\"productId\":" + creditProduct + ",\"principal\":1200000,\"term\":12}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.simulation.type").value("LOAN_NEW"))
                .andExpect(jsonPath("$.data.simulation.request.repaymentType").isEmpty())
                .andExpect(jsonPath("$.data.simulation.request.repaymentTypeMode").value("AUTO"))
                .andExpect(jsonPath("$.data.simulation.scenarios[0].parameters.repaymentTypeMode").value("AUTO"))
                .andExpect(jsonPath("$.data.simulation.after.details.resolvedRepaymentType").value("EQUAL_PRINCIPAL"))
                .andExpect(jsonPath("$.data.simulation.after.details.afterInterest").value(0))
                .andExpect(jsonPath("$.data.simulation.after.details.firstMonthlyRepayment").value(100_000))
                .andExpect(jsonPath("$.data.simulation.after.details.product.effectiveLimit").value(5_000_000))
                .andExpect(jsonPath("$.data.simulation.cashflow.currentMonth.delta").value(1_200_000))
                .andExpect(jsonPath("$.data.simulation.cashflow.months[11].cumulative").value(0))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].status").value("NOT_COMPUTABLE"))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].reasons[0]").value("GOAL_ALLOCATION_UNKNOWN"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).at("/data/simulation/simulationId").asLong();
        mvc.perform(get("/api/v1/simulations/{id}/debts", id).with(user(owner.toString())))
                .andExpect(jsonPath("$.data.totalElements").value(1)).andExpect(jsonPath("$.data.content[0].after").value(1_200_000));
        mvc.perform(get("/api/v1/simulations/{id}/costs", id).with(user(owner.toString())))
                .andExpect(jsonPath("$.data.content[0].key").value("INTEREST_TOTAL"));

        // 같은 날 옵션이 바뀌어도 AUTO·null 재요청은 첫 선택값을 재사용, 명시는 현재 조건으로 새 검증
        jdbc.update("UPDATE financial_product_option SET repayment_type = 'BULLET' WHERE product_id = ? AND term = 12", creditProduct);
        run(owner, "/loan/new", "{\"productId\":" + creditProduct + ",\"principal\":1200000,\"term\":12,\"repaymentType\":null}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.simulation.simulationId").value(id))
                .andExpect(jsonPath("$.data.simulation.after.details.resolvedRepaymentType").value("EQUAL_PRINCIPAL"));
        run(owner, "/loan/new", "{\"productId\":" + creditProduct + ",\"principal\":1200000,\"term\":12,\"repaymentType\":\"EQUAL_PRINCIPAL\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SIMULATION_PRODUCT_OPTION_UNAVAILABLE"));
        run(owner, "/loan/new", "{\"productId\":" + creditProduct + ",\"principal\":1200000,\"term\":12,\"repaymentType\":\"BULLET\"}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.simulation.request.repaymentTypeMode").value("EXPLICIT"))
                .andExpect(jsonPath("$.data.simulation.after.details.resolvedRepaymentType").value("BULLET"));

        run(owner, "/loan/new", "{\"productId\":" + creditProduct + ",\"principal\":6000000,\"term\":12,\"repaymentType\":\"BULLET\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SIMULATION_PRODUCT_LIMIT_EXCEEDED"));
        run(owner, "/loan/new", "{\"productId\":" + depositProduct + ",\"principal\":1,\"term\":12}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SIMULATION_PRODUCT_NOT_SUPPORTED"));
        run(owner, "/loan/new", "{\"productId\":999999,\"principal\":1,\"term\":12}").andExpect(status().isNotFound());
        for (String payload : List.of("{\"productId\":1,\"principal\":1,\"term\":12,\"repaymentType\":\"FOO\"}",
                "{\"productId\":1,\"principal\":1,\"term\":361}", "{\"productId\":1,\"principal\":0,\"term\":12}", "{\"productId\":1,\"term\":12}")) {
            run(owner, "/loan/new", payload).andExpect(status().isBadRequest());
        }
        assertThat(simulations.count()).isEqualTo(2);
    }

    @Test
    void prepaymentResolvesOwnLoanDefaultsDateAndNeverEchoesAccountNumber() throws Exception {
        String body = run(owner, "/loan/prepayment", "{\"accountNo\":\"" + LOAN_NO + "\",\"prepaymentAmount\":200000}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.simulation.type").value("LOAN_PREPAYMENT"))
                .andExpect(jsonPath("$.data.simulation.request.loanId").value(ownerLoanId))
                .andExpect(jsonPath("$.data.simulation.request.paymentDate").value("2026-01-01"))
                .andExpect(jsonPath("$.data.simulation.request.accountNo").doesNotExist())
                .andExpect(jsonPath("$.data.simulation.after.details.beforeInterest").value(24_000))
                .andExpect(jsonPath("$.data.simulation.after.details.afterInterest").value(20_000))
                .andExpect(jsonPath("$.data.simulation.after.details.interestSaving").value(4_000))
                .andExpect(jsonPath("$.data.simulation.after.details.loan.accountName").value("신용대출"))
                .andExpect(jsonPath("$.data.simulation.cashflow.currentMonth.delta").value(-200_000))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].reasons[0]").value("FUNDING_SOURCE_UNKNOWN"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(LOAN_NO).doesNotContain(hasher.hash(LOAN_NO));
        long id = json.readTree(body).at("/data/simulation/simulationId").asLong();
        Simulation saved = simulations.findById(id).orElseThrow();
        assertThat(saved.getRequestSnapshot().toString()).doesNotContain(LOAN_NO).doesNotContain(hasher.hash(LOAN_NO));
        assertThat(saved.getAfterSnapshot().toString()).doesNotContain(LOAN_NO).doesNotContain(hasher.hash(LOAN_NO));
        assertThat(scenarios.findAllBySimulationIdOrderBySequenceNoAsc(id).get(0).getParameters().toString()).doesNotContain(LOAN_NO);
        // 생략과 "오늘" 명시는 같은 지문, 미래 날짜는 별개 계산(2/1 약정상환 후 → 이자 22,000)
        run(owner, "/loan/prepayment", "{\"accountNo\":\"" + LOAN_NO + "\",\"prepaymentAmount\":200000,\"paymentDate\":\"2026-01-01\"}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.simulation.simulationId").value(id));
        run(owner, "/loan/prepayment", "{\"accountNo\":\"" + LOAN_NO + "\",\"prepaymentAmount\":200000,\"paymentDate\":\"2026-02-01\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.simulation.after.details.afterInterest").value(22_000))
                .andExpect(jsonPath("$.data.simulation.cashflow.currentMonth.delta").value(0))
                .andExpect(jsonPath("$.data.simulation.cashflow.months[0].delta").value(-200_000));
        mvc.perform(get("/api/v1/simulations/{id}/debts", id).with(user(owner.toString())))
                .andExpect(jsonPath("$.data.totalElements").value(2)).andExpect(jsonPath("$.data.content[1].key").value("PREPAYMENT_DEBT_CHANGE"));

        run(other, "/loan/prepayment", "{\"accountNo\":\"" + LOAN_NO + "\",\"prepaymentAmount\":1}").andExpect(status().isNotFound());
        run(owner, "/loan/prepayment", "{\"accountNo\":\"5555555555555\",\"prepaymentAmount\":1}").andExpect(status().isNotFound());
        run(owner, "/loan/prepayment", "{\"accountNo\":\"" + LOAN_NO + "\",\"prepaymentAmount\":1,\"paymentDate\":\"2025-12-31\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SIMULATION_INVALID_PAYMENT_DATE"));
        run(owner, "/loan/prepayment", "{\"accountNo\":\"" + LOAN_NO + "\",\"prepaymentAmount\":1200001}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SIMULATION_AMOUNT_EXCEEDS_BALANCE"));
        run(owner, "/loan/prepayment", "{\"accountNo\":\"" + LOAN_NO + "\",\"prepaymentAmount\":1,\"paymentDate\":\"2026-03-01\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SIMULATION_AMOUNT_EXCEEDS_BALANCE"));
        for (String payload : List.of("{\"accountNo\":\"123\",\"prepaymentAmount\":1}", "{\"accountNo\":\"12-34567890123\",\"prepaymentAmount\":1}",
                "{\"accountNo\":\"" + LOAN_NO + "\",\"prepaymentAmount\":0}", "{\"prepaymentAmount\":1}",
                "{\"accountNo\":\"" + LOAN_NO + "\",\"prepaymentAmount\":1,\"paymentDate\":\"not-a-date\"}")) {
            run(owner, "/loan/prepayment", payload).andExpect(status().isBadRequest());
        }
        // OVERDUE 대출은 계산 불가
        jdbc.update("UPDATE loan SET status = 'OVERDUE' WHERE loan_id = ?", ownerLoanId);
        run(owner, "/loan/prepayment", "{\"accountNo\":\"" + LOAN_NO + "\",\"prepaymentAmount\":1,\"paymentDate\":\"2026-01-02\"}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SIMULATION_LOAN_DATA_UNAVAILABLE"));
        assertThat(simulations.count()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT balance FROM loan WHERE loan_id = ?", Long.class, ownerLoanId)).isEqualTo(1_200_000L);
    }

    @Test
    void loanIdIdentifiesTheSameLoanAsAccountNumberAndExactlyOneIsRequired() throws Exception {
        // 계좌번호 실행과 loanId 실행은 같은 대출·같은 지문이라 같은 날 재사용(200)된다
        String byAccount = run(owner, "/loan/refinance", "{\"accountNo\":\"" + LOAN_NO + "\",\"targetProductId\":" + creditProduct + "}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(byAccount).at("/data/simulation/simulationId").asLong();
        run(owner, "/loan/refinance", "{\"loanId\":" + ownerLoanId + ",\"targetProductId\":" + creditProduct + "}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.created").value(false))
                .andExpect(jsonPath("$.data.simulation.simulationId").value(id))
                .andExpect(jsonPath("$.data.simulation.request.loanId").value(ownerLoanId))
                .andExpect(jsonPath("$.data.simulation.request.accountNo").doesNotExist());
        run(owner, "/loan/prepayment", "{\"loanId\":" + ownerLoanId + ",\"prepaymentAmount\":200000}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.simulation.request.loanId").value(ownerLoanId));

        // 둘 다 또는 둘 다 없음은 400, 타인·없는 loanId는 계좌번호 경로와 같은 404
        run(owner, "/loan/refinance", "{\"accountNo\":\"" + LOAN_NO + "\",\"loanId\":" + ownerLoanId + ",\"targetProductId\":" + creditProduct + "}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        run(owner, "/loan/refinance", "{\"targetProductId\":" + creditProduct + "}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        run(owner, "/loan/prepayment", "{\"prepaymentAmount\":1}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        run(other, "/loan/refinance", "{\"loanId\":" + ownerLoanId + ",\"targetProductId\":" + creditProduct + "}")
                .andExpect(status().isNotFound());
        run(owner, "/loan/prepayment", "{\"loanId\":999999,\"prepaymentAmount\":1}")
                .andExpect(status().isNotFound());
        for (String payload : List.of("{\"loanId\":0,\"targetProductId\":" + creditProduct + "}",
                "{\"loanId\":1.5,\"targetProductId\":" + creditProduct + "}")) {
            run(owner, "/loan/refinance", payload).andExpect(status().isBadRequest());
        }

        // 복합의 자식도 같은 본문이라 loanId를 받는다(복합은 자식 2개 이상)
        run(owner, "/combined", "{\"scenarios\":[{\"type\":\"LOAN_REFINANCE\",\"parameters\":{\"loanId\":" + ownerLoanId
                + ",\"targetProductId\":" + creditProduct + "}},{\"type\":\"SAVING\",\"parameters\":{\"goalId\":" + goalId + ",\"monthlySavingAmount\":100000,\"durationMonths\":3}}]}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.simulation.scenarios[0].parameters.loanId").value(ownerLoanId));
    }

    @Test
    void refinanceProjectsGoalsByRepaymentDateAndAllQueryPathsAgree() throws Exception {
        // 1,200,000 전액을 6% BULLET 2회로 대환: 이자 24,000 → 12,000, 2/1·3/1에 6,000씩 절감
        // 목표: 남은 2,000,000·속도 333,333 → 6개월 + 2원 → 7/2. 절감 반영: 1,660,667 → 1,321,334 → 3/1 뒤 3개월 + 321,335/333,333 × 30일(29일) = 6/30, -2일
        // (658 전에는 월 단위 올림이라 8/1 → 7/1, -31일이었다. 12,000원 절감이 한 달을 당기는 것이 과장이었다)
        String body = run(owner, "/loan/refinance", "{\"accountNo\":\"" + LOAN_NO + "\",\"targetProductId\":" + creditProduct + "}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.simulation.type").value("LOAN_REFINANCE"))
                .andExpect(jsonPath("$.data.simulation.request.refinanceAmount").isEmpty())
                .andExpect(jsonPath("$.data.simulation.request.refinanceAmountMode").value("FULL_BALANCE"))
                .andExpect(jsonPath("$.data.simulation.after.details.beforeInterest").value(24_000))
                .andExpect(jsonPath("$.data.simulation.after.details.afterInterest").value(12_000))
                .andExpect(jsonPath("$.data.simulation.after.details.remainingInstallments").value(2))
                .andExpect(jsonPath("$.data.simulation.after.details.comparisonGroup").value("CREDIT"))
                .andExpect(jsonPath("$.data.simulation.cashflow.months[0].delta").value(6_000))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].beforeForecastDate").value("2026-07-02"))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].afterForecastDate").value("2026-06-30"))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].differenceDays").value(-2))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].status").value("COMPUTED"))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].reasons", org.hamcrest.Matchers.hasItem("REPAYMENT_DELTA_ALLOCATED_TO_GOALS")))
                .andExpect(jsonPath("$.data.simulation.after.goals[0].expectedAchievementDate").value("2026-06-30"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).at("/data/simulation/simulationId").asLong();
        JsonNode runImpacts = json.readTree(body).at("/data/simulation/goalImpacts");
        String detail = mvc.perform(get("/api/v1/simulations/{id}", id).with(user(owner.toString()))).andReturn().getResponse().getContentAsString();
        String goalImpact = mvc.perform(get("/api/v1/simulations/{id}/goal-impact", id).with(user(owner.toString()))).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(detail).at("/data/goalImpacts")).isEqualTo(runImpacts);
        assertThat(json.readTree(goalImpact).at("/data")).isEqualTo(runImpacts);
        assertThat(jdbc.queryForObject("SELECT after_forecast_date FROM simulation_goal_impact WHERE simulation_id = ?", LocalDate.class, id))
                .isEqualTo(LocalDate.of(2026, 6, 30));
        // 재사용 POST도 같은 배열, 명시 금액은 별개 지문(부분 대환)
        String reuse = run(owner, "/loan/refinance", "{\"accountNo\":\"" + LOAN_NO + "\",\"targetProductId\":" + creditProduct + "}")
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(reuse).at("/data/simulation/goalImpacts")).isEqualTo(runImpacts);
        run(owner, "/loan/refinance", "{\"accountNo\":\"" + LOAN_NO + "\",\"targetProductId\":" + creditProduct + ",\"refinanceAmount\":600000}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.simulation.request.refinanceAmountMode").value("EXPLICIT"))
                .andExpect(jsonPath("$.data.simulation.after.details.afterInterest").value(18_000))
                .andExpect(jsonPath("$.data.simulation.after.details.keptBalance").value(600_000));

        run(owner, "/loan/refinance", "{\"accountNo\":\"" + LOAN_NO + "\",\"targetProductId\":" + mortgageProduct + "}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SIMULATION_PRODUCT_NOT_SUPPORTED"));
        run(owner, "/loan/refinance", "{\"accountNo\":\"" + LOAN_NO + "\",\"targetProductId\":" + depositProduct + "}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SIMULATION_PRODUCT_NOT_SUPPORTED"));
        run(owner, "/loan/refinance", "{\"accountNo\":\"" + LOAN_NO + "\",\"targetProductId\":" + creditProduct + ",\"refinanceAmount\":1200001}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SIMULATION_AMOUNT_EXCEEDS_BALANCE"));
        run(owner, "/loan/refinance", "{\"accountNo\":\"" + LOAN_NO + "\",\"targetProductId\":999999}").andExpect(status().isNotFound());
        run(other, "/loan/refinance", "{\"accountNo\":\"" + LOAN_NO + "\",\"targetProductId\":" + creditProduct + "}").andExpect(status().isNotFound());
        run(owner, "/loan/refinance", "{\"accountNo\":\"" + LOAN_NO + "\",\"targetProductId\":" + creditProduct + ",\"refinanceAmount\":0}")
                .andExpect(status().isBadRequest());
        // 잔여 회차와 term이 맞는 옵션이 없으면 실행 불가(기간 밴드 후보는 비교 가능해도 계약 조건 미지원)
        jdbc.update("UPDATE financial_product_option SET term = 12 WHERE product_id = ? AND term = 2", creditProduct);
        run(owner, "/loan/refinance", "{\"accountNo\":\"" + LOAN_NO + "\",\"targetProductId\":" + creditProduct + ",\"refinanceAmount\":1}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SIMULATION_PRODUCT_OPTION_UNAVAILABLE"));
        // 당일 대환 거부
        given(clock.now()).willReturn(LocalDateTime.of(2026, 2, 1, 9, 0));
        run(owner, "/loan/refinance", "{\"accountNo\":\"" + LOAN_NO + "\",\"targetProductId\":" + creditProduct + "}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SIMULATION_LOAN_DATA_UNAVAILABLE"));
        assertThat(simulations.count()).isEqualTo(2);
    }

    @Test
    void legacyTypesKeepTheirContractWithoutExtensionFields() throws Exception {
        run(owner, "/saving", "{\"monthlySavingAmount\":500000}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.simulation.after.details").doesNotExist())
                .andExpect(jsonPath("$.data.simulation.after.goalImpactDetails").doesNotExist())
                .andExpect(jsonPath("$.data.simulation.cashflow.basis").value("MONTHLY_AVAILABLE_FUNDS"))
                .andExpect(jsonPath("$.data.simulation.cashflow.currentMonth").doesNotExist())
                .andExpect(jsonPath("$.data.simulation.cashflow.truncated").doesNotExist())
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].status").doesNotExist())
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].reasons").doesNotExist())
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].afterForecastDate").value("2026-05-01"));
        long id = json.readTree(mvc.perform(get("/api/v1/simulations").with(user(owner.toString()))).andReturn().getResponse()
                .getContentAsString()).at("/data/content/0/simulationId").asLong();
        mvc.perform(get("/api/v1/simulations/{id}/goal-impact", id).with(user(owner.toString())))
                .andExpect(jsonPath("$.data[0].goalId").value(goalId)).andExpect(jsonPath("$.data[0].status").doesNotExist());
    }

    private ResultActions run(Long userId, String path, String body) throws Exception {
        return mvc.perform(post("/api/v1/simulations" + path).with(user(userId.toString()))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long insertProduct(long companyId, String type, String name, String savingType, String loanType, Long maxLimit) {
        jdbc.update("""
                INSERT INTO financial_product (company_id, source_type, external_product_code, product_type, product_name, join_way,
                    saving_type, loan_type, special_condition, join_member, min_rate, max_rate, max_limit, baseline_performance,
                    max_benefit_limit, description, benefits, synced_at)
                VALUES (?, 'SEED', NULL, ?, ?, NULL, ?, ?, NULL, NULL, NULL, NULL, ?, NULL, NULL, NULL, NULL, ?)
                """, companyId, type, name, savingType, loanType, maxLimit, LocalDateTime.of(2025, 12, 31, 9, 0));
        return jdbc.queryForObject("SELECT MAX(product_id) FROM financial_product", Long.class);
    }

    private void option(long productId, int term, String rate, String minRate, String maxRate, String repaymentType, Long limit) {
        jdbc.update("""
                INSERT INTO financial_product_option (product_id, term, rate, preferred_rate, rate_type, min_rate, max_rate, repayment_type, limit_amount)
                VALUES (?, ?, ?, NULL, 'FIXED', ?, ?, ?, ?)
                """, productId, term, rate == null ? null : new java.math.BigDecimal(rate), minRate == null ? null : new java.math.BigDecimal(minRate),
                maxRate == null ? null : new java.math.BigDecimal(maxRate), repaymentType, limit);
    }

    private long insertLoan(Long userId, String accountNo, long productId, String status, long balance, String rate, LocalDate next, LocalDate maturity) {
        jdbc.update("""
                INSERT INTO loan (user_id, withdrawal_account_id, withdrawal_account_type, financial_product_id, institution_code,
                    institution_name, account_no_hash, account_no_masked, account_name, loan_type, repayment_type, status, loan_period,
                    start_date, maturity_date, original_principal, balance, interest_rate, repayment_day, next_repayment_date, created_at, updated_at)
                VALUES (?, 1, 'DEMAND_DEPOSIT', ?, 'TESTBANK', '테스트은행', ?, '1234-***-**123', '신용대출', 'CREDIT', 'BULLET', ?, 12,
                    ?, ?, ?, ?, ?, 1, ?, ?, NULL)
                """, userId, productId, hasher.hash(accountNo), status, maturity.minusYears(1), maturity, balance, balance,
                new java.math.BigDecimal(rate), next, NOW);
        return jdbc.queryForObject("SELECT loan_id FROM loan WHERE user_id = ? AND account_no_hash = ?", Long.class, userId, hasher.hash(accountNo));
    }

    /**
     * 저축속도(694·702 GoalSavingPaceReader)용 수입. 입출금 연결 목표의 속도는 직전 3개 완료 월 중 목표에 연결된 입출금 계좌의
     * 순현금흐름이므로 수입 행에 그 계좌의 account_id를 넣는다. 계좌도 카드도 없는 지출 행은 속도에 들어가지 않는다.
     */
    private void income(Long accountId, LocalDate date, long amount) {
        jdbc.update("""
                INSERT INTO `transaction` (transaction_id, user_id, account_id, transaction_date, transaction_type, amount,
                    payment_method, spending_type, summary, hidden, source_type, created_at)
                VALUES (?, ?, ?, ?, 'INCOME', ?, 'ACCOUNT', 'NON_SPENDING', 'salary', FALSE, 'SEED', ?)
                """, UUID.randomUUID().toString(), owner, accountId, date, amount, NOW);
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
}
