package com.ssafy.dodam.domain.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
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
import com.ssafy.dodam.domain.simulation.repository.SimulationRepository;
import com.ssafy.dodam.domain.simulation.repository.SimulationScenarioRepository;
import com.ssafy.dodam.domain.simulation.service.SimulationClock;
import com.ssafy.dodam.domain.user.entity.FinancialProfile;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.FinancialProfileRepository;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import java.sql.Timestamp;
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
 * 586 실행 API 4종(installment·revolving·cash-advance·combined)과 공통 조회 연결. 기준일 2026-09-15로 고정해
 * {@code CardSimulationCalculatorTest}의 검산 값을 그대로 쓴다. 카드·대출·계좌 번호는 모두 합성 값이다.
 * 거래가 없어 기준선 평균 지출은 0이다 — 소비 감소 자식은 그래서 항상 거부되며, 그 경로를 "자식 오류 → 0행 저장" 검증에 쓴다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:simulation586;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class SimulationCardCombinedApiTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 15, 12, 0);
    private static final String LOAN_NO = "1234567890123";
    private static final String OTHER_LOAN_NO = "9876543210987";
    private static final String GOAL_ACCOUNT_NO = "1111222233334444";
    private static final String OTHER_ACCOUNT_NO = "5555666677778888";
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
    private Long ownerCard;
    private Long otherCard;
    private long creditProduct;

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
        // 3개월 전 생성, 잔액 1,000,000 → 속도 333,333, 남은 2,000,000 → 7개월(2027-04-15)
        Long accountId = insertAccount(owner, GOAL_ACCOUNT_NO, 1_000_000L);
        Long otherAccountId = insertAccount(other, OTHER_ACCOUNT_NO, 0L);
        Goal goal = goals.save(Goal.create(owner, "비상금", GoalType.SAVING, NOW.minusMonths(3)));
        goalId = goal.getId();
        savingGoals.save(SavingGoal.create(goalId, 3_000_000L, NOW.toLocalDate().plusYears(1)));
        goalAccounts.save(GoalAccount.link(owner, goalId, accountId, "DEMAND_DEPOSIT"));
        ownerCard = insertCard(owner, accountId, "a1");
        otherCard = insertCard(other, otherAccountId, "b2");

        jdbc.update("INSERT INTO financial_company (company_code, company_name, sector) VALUES ('TESTBANK', '테스트은행', 'BANK')");
        long company = jdbc.queryForObject("SELECT company_id FROM financial_company WHERE company_code = 'TESTBANK'", Long.class);
        creditProduct = insertProduct(company, "LOAN", "신용대출", "PERSONAL_CREDIT");
        option(creditProduct, 2, "6", "BULLET");
        // 잔액 1,200,000, 12%, BULLET, 10/15·11/15(만기 11/15)
        insertLoan(owner, LOAN_NO, creditProduct, 1_200_000L, LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 15));
        insertLoan(other, OTHER_LOAN_NO, creditProduct, 1_200_000L, LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 15));
    }

    @Test
    void installmentRunsStoresReusesAndDistinguishesFeeModes() throws Exception {
        String body = run(owner, "/installment", "{\"purchaseAmount\":1200000,\"months\":2,\"feeRate\":12}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.created").value(true))
                .andExpect(jsonPath("$.data.simulation.type").value("INSTALLMENT"))
                .andExpect(jsonPath("$.data.simulation.request.feeRate").value(12.0))
                .andExpect(jsonPath("$.data.simulation.request.feeRateMode").value("EXPLICIT"))
                .andExpect(jsonPath("$.data.simulation.request.requestHash").doesNotExist())
                .andExpect(jsonPath("$.data.simulation.scenarios.length()").value(1))
                .andExpect(jsonPath("$.data.simulation.scenarios[0].parameters.feeRateMode").value("EXPLICIT"))
                .andExpect(jsonPath("$.data.simulation.after.details.totalFee").value(17_951))
                .andExpect(jsonPath("$.data.simulation.after.details.inputSource").value("USER_INPUT"))
                .andExpect(jsonPath("$.data.simulation.after.details.repaymentSchedule.length()").value(2))
                .andExpect(jsonPath("$.data.simulation.after.details.repaymentSchedule[0].fee").value(11_836))
                .andExpect(jsonPath("$.data.simulation.after.details.repaymentSchedule[0].payment").value(611_836))
                .andExpect(jsonPath("$.data.simulation.after.details.repaymentSchedule[0].closingPrincipal").value(600_000))
                .andExpect(jsonPath("$.data.simulation.cashflow.basis").value("SCENARIO_NET_CASH_CHANGE"))
                .andExpect(jsonPath("$.data.simulation.cashflow.currentMonth.delta").value(1_200_000))
                .andExpect(jsonPath("$.data.simulation.cashflow.months[1].cumulative").value(-17_951))
                .andExpect(jsonPath("$.data.simulation.cashflow.fullTermEndDate").value("2026-11-15"))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].goalId").value(goalId))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].beforeForecastDate").value("2027-03-16"))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].afterForecastDate").isEmpty())
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].status").value("NOT_COMPUTABLE"))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].reasons[0]").value("GOAL_ALLOCATION_UNKNOWN"))
                .andExpect(jsonPath("$.data.simulation.after.assumptions", hasItem("CARD_TERMS_USER_INPUT")))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).at("/data/simulation/simulationId").asLong();
        JsonNode runImpacts = json.readTree(body).at("/data/simulation/goalImpacts");
        // 상세·goal-impact·섹션 조회가 같은 저장값을 돌려준다
        String detail = mvc.perform(get("/api/v1/simulations/{id}", id).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.after.details.totalFee").value(17_951))
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(detail).at("/data/goalImpacts")).isEqualTo(runImpacts);
        String goalImpact = mvc.perform(get("/api/v1/simulations/{id}/goal-impact", id).with(user(owner.toString())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(goalImpact).at("/data")).isEqualTo(runImpacts);
        mvc.perform(get("/api/v1/simulations/{id}/costs", id).with(user(owner.toString())))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].key").value("INSTALLMENT_FEE_TOTAL"))
                .andExpect(jsonPath("$.data.content[0].after").value(17_951));
        mvc.perform(get("/api/v1/simulations/{id}/debts", id).with(user(owner.toString())))
                .andExpect(jsonPath("$.data.content[0].after").value(1_200_000));
        mvc.perform(get("/api/v1/simulations?type=INSTALLMENT").with(user(owner.toString()))).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/v1/simulations/{id}", id).with(user(other.toString()))).andExpect(status().isNotFound());

        // 같은 날 같은 요청(12 = 12.0000)은 재사용, 생략 수수료율은 별개 결과(ZERO_FEE_ASSUMED), 명시적 0은 또 별개
        run(owner, "/installment", "{\"purchaseAmount\":1200000,\"months\":2,\"feeRate\":12.0000}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.created").value(false))
                .andExpect(jsonPath("$.data.simulation.simulationId").value(id));
        run(owner, "/installment", "{\"purchaseAmount\":1200000,\"months\":2}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.simulation.request.feeRateMode").value("ASSUMED_ZERO"))
                .andExpect(jsonPath("$.data.simulation.after.details.totalFee").value(0))
                .andExpect(jsonPath("$.data.simulation.after.assumptions", hasItem("ZERO_FEE_ASSUMED")));
        run(owner, "/installment", "{\"purchaseAmount\":1200000,\"months\":2,\"feeRate\":0}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.simulation.request.feeRateMode").value("EXPLICIT"))
                .andExpect(jsonPath("$.data.simulation.after.assumptions").isArray())
                .andExpect(jsonPath("$.data.simulation.after.assumptions", org.hamcrest.Matchers.not(hasItem("ZERO_FEE_ASSUMED"))));
        run(owner, "/installment", "{\"purchaseAmount\":1200000,\"months\":2,\"feeRate\":null}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.created").value(false));
        assertThat(simulations.count()).isEqualTo(3);

        // 소수 금액은 절사하지 않고 거부, 범위 밖 값은 검증 오류
        run(owner, "/installment", "{\"purchaseAmount\":1200000.5,\"months\":2}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        run(owner, "/installment", "{\"purchaseAmount\":1200000,\"months\":2.5}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        for (String payload : List.of("{}", "{\"purchaseAmount\":0,\"months\":2}", "{\"purchaseAmount\":1,\"months\":0}",
                "{\"purchaseAmount\":1,\"months\":121}", "{\"purchaseAmount\":1,\"months\":1,\"feeRate\":100.0001}",
                "{\"purchaseAmount\":1,\"months\":1,\"feeRate\":-1}", "{\"purchaseAmount\":1,\"months\":1,\"feeRate\":1.00001}")) {
            run(owner, "/installment", payload).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        mvc.perform(post("/api/v1/simulations/installment").contentType(MediaType.APPLICATION_JSON)
                .content("{\"purchaseAmount\":1,\"months\":1}")).andExpect(status().isUnauthorized());
        assertThat(simulations.count()).isEqualTo(3);
    }

    @Test
    void revolvingChecksCardOwnershipWithoutExposingOtherCards() throws Exception {
        String body = run(owner, "/revolving", "{\"cardId\":" + ownerCard + ",\"balance\":1000000,\"paymentRatio\":50,\"rate\":12}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.simulation.type").value("REVOLVING"))
                .andExpect(jsonPath("$.data.simulation.request.cardId").value(ownerCard))
                .andExpect(jsonPath("$.data.simulation.request.paymentRatio").value(50.0))
                .andExpect(jsonPath("$.data.simulation.after.details.initialPayment").value(500_000))
                .andExpect(jsonPath("$.data.simulation.after.details.carriedBalance").value(500_000))
                .andExpect(jsonPath("$.data.simulation.after.details.paidOff").value(true))
                .andExpect(jsonPath("$.data.simulation.after.details.repaymentSchedule[0].interest").value(4_932))
                .andExpect(jsonPath("$.data.simulation.after.details.repaymentSchedule[0].principalPayment").value(250_000))
                .andExpect(jsonPath("$.data.simulation.after.details.calculationPolicy").value("PRINCIPAL_RATIO_WITH_INTEREST_PAID_SEPARATELY"))
                .andExpect(jsonPath("$.data.simulation.cashflow.currentMonth.delta").value(500_000))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].status").value("NOT_COMPUTABLE"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("card_no").doesNotContain("cardNo");
        long id = json.readTree(body).at("/data/simulation/simulationId").asLong();
        mvc.perform(get("/api/v1/simulations/{id}/assets", id).with(user(owner.toString())))
                .andExpect(jsonPath("$.data.content[0].key").value("INITIAL_CASH_CHANGE"))
                .andExpect(jsonPath("$.data.content[0].after").value(500_000));
        // 타인 카드·없는 카드는 같은 404, 카드 미지정은 허용
        run(owner, "/revolving", "{\"cardId\":" + otherCard + ",\"balance\":1000000,\"paymentRatio\":50,\"rate\":12}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        run(owner, "/revolving", "{\"cardId\":999999,\"balance\":1000000,\"paymentRatio\":50,\"rate\":12}")
                .andExpect(status().isNotFound());
        run(owner, "/revolving", "{\"balance\":1000000,\"paymentRatio\":100,\"rate\":12}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.simulation.request.cardId").isEmpty())
                .andExpect(jsonPath("$.data.simulation.after.details.terminationReason").value("NO_CARRY_OVER"))
                .andExpect(jsonPath("$.data.simulation.cashflow.months.length()").value(0));
        // 120회차 미완납: 종료일 없음, 잔액·평가 종료일 표시
        run(owner, "/revolving", "{\"balance\":1000000,\"paymentRatio\":0.0001,\"rate\":12}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.simulation.after.details.paidOff").value(false))
                .andExpect(jsonPath("$.data.simulation.after.details.remainingBalance").value(999_879))
                .andExpect(jsonPath("$.data.simulation.after.details.evaluatedThrough").value("2036-09-15"))
                .andExpect(jsonPath("$.data.simulation.cashflow.fullTermEndDate").doesNotExist())
                .andExpect(jsonPath("$.data.simulation.after.assumptions", hasItem("REVOLVING_HORIZON_LIMIT")));
        for (String payload : List.of("{\"balance\":1000000,\"paymentRatio\":0,\"rate\":12}", "{\"balance\":1000000,\"paymentRatio\":101,\"rate\":12}",
                "{\"balance\":0,\"paymentRatio\":50,\"rate\":12}", "{\"balance\":1,\"paymentRatio\":50}", "{\"cardId\":0,\"balance\":1,\"paymentRatio\":50,\"rate\":1}")) {
            run(owner, "/revolving", payload).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        run(owner, "/revolving", "{\"balance\":1000000.9,\"paymentRatio\":50,\"rate\":12}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(simulations.count()).isEqualTo(3);
    }

    @Test
    void cashAdvanceDefaultsRepaymentMonthsAndReusesExplicitOne() throws Exception {
        String body = run(owner, "/cash-advance", "{\"amount\":1000000,\"rate\":12}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.simulation.type").value("CASH_ADVANCE"))
                .andExpect(jsonPath("$.data.simulation.request.repaymentMonths").value(1))
                .andExpect(jsonPath("$.data.simulation.after.details.interest").value(9_863))
                .andExpect(jsonPath("$.data.simulation.after.details.days").value(30))
                .andExpect(jsonPath("$.data.simulation.after.details.repaymentDate").value("2026-10-15"))
                .andExpect(jsonPath("$.data.simulation.after.details.repaymentSchedule[0].payment").value(1_009_863))
                .andExpect(jsonPath("$.data.simulation.cashflow.months[0].delta").value(-1_009_863))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].reasons[0]").value("GOAL_ALLOCATION_UNKNOWN"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).at("/data/simulation/simulationId").asLong();
        run(owner, "/cash-advance", "{\"amount\":1000000,\"rate\":12.00,\"repaymentMonths\":1}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.simulation.simulationId").value(id));
        run(owner, "/cash-advance", "{\"amount\":1000000,\"rate\":12,\"repaymentMonths\":null}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.simulation.simulationId").value(id));
        run(owner, "/cash-advance", "{\"amount\":1000000,\"rate\":12,\"repaymentMonths\":120}")
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.simulation.after.details.days").value(3_653));
        mvc.perform(get("/api/v1/simulations/{id}/costs", id).with(user(owner.toString())))
                .andExpect(jsonPath("$.data.content[0].after").value(9_863));
        for (String payload : List.of("{\"amount\":0,\"rate\":12}", "{\"amount\":1}", "{\"amount\":1,\"rate\":100.5}",
                "{\"amount\":1,\"rate\":1,\"repaymentMonths\":0}", "{\"amount\":1,\"rate\":1,\"repaymentMonths\":121}")) {
            run(owner, "/cash-advance", payload).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        }
        assertThat(simulations.count()).isEqualTo(2);
    }

    @Test
    void combinedSavingGoalIdChangesOnlySelectedGoal() throws Exception {
        jdbc.update("""
                INSERT INTO `transaction` (transaction_id, user_id, transaction_date, transaction_type, amount,
                    payment_method, spending_type, summary, hidden, source_type, created_at)
                VALUES (?, ?, ?, 'EXPENSE', 30000, 'CARD', 'VARIABLE', 'test', FALSE, 'SEED', ?)
                """, UUID.randomUUID().toString(), owner, NOW.toLocalDate().minusMonths(1), NOW);
        Goal second = goals.save(Goal.create(owner, "여행", GoalType.SAVING, NOW.minusMonths(1)));
        savingGoals.save(SavingGoal.create(second.getId(), 1_200_000L, NOW.toLocalDate().plusYears(1)));
        String request = "{\"scenarios\":["
                + "{\"type\":\"SAVING\",\"parameters\":{\"goalId\":" + second.getId() + ",\"monthlySavingAmount\":100000,\"durationMonths\":3}},"
                + "{\"type\":\"SPENDING\",\"parameters\":{\"monthlyReduction\":1000,\"durationMonths\":3}}]}";
        run(owner, "/combined", request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.simulation.after.goals[0].monthlySaving").value(333_333))
                .andExpect(jsonPath("$.data.simulation.after.goals[1].monthlySaving").value(100_000));
        run(owner, "/combined", request.replace("\"goalId\":" + second.getId(), "\"goalId\":" + second.getId() + ".5"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void combinedStoresChildScenarioRowsAndNeverEchoesAccountNumbers() throws Exception {
        // 저축 변경(계좌번호 지정) + 할부 + 대환(계좌번호 지정): 자식 3건, 저장 파라미터에는 내부 id만
        String request = "{\"scenarios\":["
                + "{\"type\":\"SAVING\",\"parameters\":{\"accountNo\":\"" + GOAL_ACCOUNT_NO + "\",\"monthlySavingAmount\":500000,\"durationMonths\":2}},"
                + "{\"type\":\"installment\",\"parameters\":{\"purchaseAmount\":1200000,\"months\":2,\"feeRate\":12}},"
                + "{\"type\":\"LOAN_REFINANCE\",\"parameters\":{\"accountNo\":\"" + LOAN_NO + "\",\"targetProductId\":" + creditProduct + "}}"
                + "]}";
        String body = run(owner, "/combined", request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.created").value(true))
                .andExpect(jsonPath("$.data.simulation.type").value("COMBINED"))
                .andExpect(jsonPath("$.data.simulation.request.scenarios.length()").value(3))
                .andExpect(jsonPath("$.data.simulation.request.scenarios[0].type").value("SAVING"))
                .andExpect(jsonPath("$.data.simulation.request.scenarios[0].parameters.accountNo").doesNotExist())
                .andExpect(jsonPath("$.data.simulation.request.scenarios[1].parameters.feeRateMode").value("EXPLICIT"))
                .andExpect(jsonPath("$.data.simulation.request.scenarios[2].parameters.refinanceAmountMode").value("FULL_BALANCE"))
                .andExpect(jsonPath("$.data.simulation.scenarios.length()").value(3))
                .andExpect(jsonPath("$.data.simulation.scenarios[0].sequenceNo").value(1))
                .andExpect(jsonPath("$.data.simulation.scenarios[0].scenarioType").value("SAVING"))
                .andExpect(jsonPath("$.data.simulation.scenarios[1].sequenceNo").value(2))
                .andExpect(jsonPath("$.data.simulation.scenarios[1].scenarioType").value("INSTALLMENT"))
                .andExpect(jsonPath("$.data.simulation.scenarios[2].scenarioType").value("LOAN_REFINANCE"))
                .andExpect(jsonPath("$.data.simulation.scenarios[2].parameters.loanId").exists())
                .andExpect(jsonPath("$.data.simulation.after.details.scenarioCount").value(3))
                .andExpect(jsonPath("$.data.simulation.after.details.children.length()").value(3))
                .andExpect(jsonPath("$.data.simulation.after.details.children[1].type").value("INSTALLMENT"))
                .andExpect(jsonPath("$.data.simulation.after.details.children[2].details.beforeInterest").value(24_000))
                .andExpect(jsonPath("$.data.simulation.after.details.costBasis").value("PAYMENT_DATE"))
                .andExpect(jsonPath("$.data.simulation.after.details.costComplete").value(true))
                .andExpect(jsonPath("$.data.simulation.after.details.evaluationThroughDate").value("2026-11-15"))
                .andExpect(jsonPath("$.data.simulation.after.details.financeCost.before").value(24_000))
                .andExpect(jsonPath("$.data.simulation.after.details.financeCost.after").value(12_000 + 17_951))
                .andExpect(jsonPath("$.data.simulation.after.details.goalEffectPolicy").value("NOT_COMPUTABLE"))
                .andExpect(jsonPath("$.data.simulation.cashflow.basis").value("SCENARIO_NET_CASH_CHANGE"))
                .andExpect(jsonPath("$.data.simulation.cashflow.currentMonth.delta").value(1_200_000))
                .andExpect(jsonPath("$.data.simulation.cashflow.months.length()").value(2))
                // 저축 −166,667(500,000 − 333,333) + 할부 −611,836 + 대환 +6,000
                .andExpect(jsonPath("$.data.simulation.cashflow.months[0].delta").value(-772_503))
                .andExpect(jsonPath("$.data.simulation.cashflow.months[1].cumulative").value(-339_285))
                .andExpect(jsonPath("$.data.simulation.cashflow.fullTermEndDate").value("2026-11-15"))
                .andExpect(jsonPath("$.data.simulation.after.goals[0].expectedAchievementDate").isEmpty())
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].status").value("NOT_COMPUTABLE"))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].reasons[0]").value("COMBINED_GOAL_EFFECT_NOT_COMPUTABLE"))
                .andExpect(jsonPath("$.data.simulation.goalImpacts[0].reasons[1]").value("GOAL_ALLOCATION_UNKNOWN"))
                .andExpect(jsonPath("$.data.simulation.after.assumptions", hasItem("CHILD_SCENARIOS_INDEPENDENT")))
                .andExpect(jsonPath("$.data.simulation.after.assumptions", hasItem("LOAN_LOCAL_SNAPSHOT")))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(GOAL_ACCOUNT_NO).doesNotContain(LOAN_NO).doesNotContain(hasher.hash(LOAN_NO));
        long id = json.readTree(body).at("/data/simulation/simulationId").asLong();
        JsonNode runImpacts = json.readTree(body).at("/data/simulation/goalImpacts");
        assertThat(scenarios.findAllBySimulationIdOrderBySequenceNoAsc(id)).hasSize(3)
                .allSatisfy(s -> assertThat(s.getParameters().toString()).doesNotContain(GOAL_ACCOUNT_NO).doesNotContain(LOAN_NO));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM simulation_scenario", Long.class)).isEqualTo(3L);

        // 공통 조회 3경로 일치, 섹션 조회는 집계 항목만
        String detail = mvc.perform(get("/api/v1/simulations/{id}", id).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.scenarios.length()").value(3))
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(detail).at("/data/goalImpacts")).isEqualTo(runImpacts);
        String goalImpact = mvc.perform(get("/api/v1/simulations/{id}/goal-impact", id).with(user(owner.toString())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(goalImpact).at("/data")).isEqualTo(runImpacts);
        mvc.perform(get("/api/v1/simulations/{id}/costs", id).with(user(owner.toString())))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].key").value("FINANCE_COST_EVALUATED"))
                .andExpect(jsonPath("$.data.content[0].before").value(24_000))
                .andExpect(jsonPath("$.data.content[0].delta").value(12_000 + 17_951 - 24_000));
        mvc.perform(get("/api/v1/simulations/{id}/debts", id).with(user(owner.toString())))
                .andExpect(jsonPath("$.data.content[0].after").value(1_200_000));
        mvc.perform(get("/api/v1/simulations?type=COMBINED").with(user(owner.toString()))).andExpect(jsonPath("$.data.totalElements").value(1));
        mvc.perform(get("/api/v1/simulations?type=INSTALLMENT").with(user(owner.toString()))).andExpect(jsonPath("$.data.totalElements").value(0));
        mvc.perform(get("/api/v1/simulations/{id}", id).with(user(other.toString()))).andExpect(status().isNotFound());

        // 같은 날 같은 요청은 재사용, 순서를 바꾸면 별개 요청
        run(owner, "/combined", request).andExpect(status().isOk()).andExpect(jsonPath("$.data.created").value(false))
                .andExpect(jsonPath("$.data.simulation.simulationId").value(id));
        String swapped = "{\"scenarios\":["
                + "{\"type\":\"INSTALLMENT\",\"parameters\":{\"purchaseAmount\":1200000,\"months\":2,\"feeRate\":12}},"
                + "{\"type\":\"SAVING\",\"parameters\":{\"accountNo\":\"" + GOAL_ACCOUNT_NO + "\",\"monthlySavingAmount\":500000,\"durationMonths\":2}},"
                + "{\"type\":\"LOAN_REFINANCE\",\"parameters\":{\"accountNo\":\"" + LOAN_NO + "\",\"targetProductId\":" + creditProduct + "}}"
                + "]}";
        run(owner, "/combined", swapped).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.simulation.scenarios[0].scenarioType").value("INSTALLMENT"))
                .andExpect(jsonPath("$.data.simulation.after.details.financeCost.after").value(12_000 + 17_951));
        assertThat(simulations.count()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM simulation_scenario", Long.class)).isEqualTo(6L);
    }

    @Test
    void combinedRejectsInvalidStructureConflictsAndChildErrorsWithoutStoring() throws Exception {
        String installment = "{\"type\":\"INSTALLMENT\",\"parameters\":{\"purchaseAmount\":1200000,\"months\":2}}";
        String cash = "{\"type\":\"CASH_ADVANCE\",\"parameters\":{\"amount\":1000000,\"rate\":12}}";
        // 개수
        run(owner, "/combined", "{\"scenarios\":[" + installment + "]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        run(owner, "/combined", "{\"scenarios\":[]}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        run(owner, "/combined", "{}").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        StringBuilder eleven = new StringBuilder("{\"scenarios\":[");
        for (int i = 1; i <= 11; i++) {
            eleven.append(i > 1 ? "," : "").append("{\"type\":\"CASH_ADVANCE\",\"parameters\":{\"amount\":").append(i).append(",\"rate\":1}}");
        }
        run(owner, "/combined", eleven.append("]}").toString())
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        // 구조: 알 수 없는 유형, 중첩, 참조 필드, 알 수 없는 파라미터, 자식 검증 오류(경로 제공, 원문 미포함)
        run(owner, "/combined", "{\"scenarios\":[" + installment + ",{\"type\":\"FOO\",\"parameters\":{}}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message", containsString("scenarios[1].type")));
        run(owner, "/combined", "{\"scenarios\":[" + installment + ",{\"type\":\"COMBINED\",\"parameters\":{\"scenarios\":[]}}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message", containsString("scenarios[1].type")));
        run(owner, "/combined", "{\"scenarios\":[" + installment + ",{\"type\":\"CASH_ADVANCE\",\"simulationId\":1,\"parameters\":{\"amount\":1,\"rate\":1}}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("scenarios[1].simulationId")));
        run(owner, "/combined", "{\"scenarios\":[" + installment + ",{\"type\":\"CASH_ADVANCE\",\"parameters\":{\"amount\":1,\"rate\":1,\"dependsOn\":1}}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message", containsString("scenarios[1].parameters.dependsOn")));
        run(owner, "/combined", "{\"scenarios\":[" + cash + ",{\"type\":\"INSTALLMENT\",\"parameters\":{\"purchaseAmount\":987654321,\"months\":0}}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message", containsString("scenarios[1].parameters.months")))
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.not(containsString("987654321"))));
        run(owner, "/combined", "{\"scenarios\":[" + cash + ",{\"type\":\"INSTALLMENT\",\"parameters\":{\"purchaseAmount\":10.5,\"months\":1}}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.message", containsString("scenarios[1].parameters.purchaseAmount")));
        run(owner, "/combined", "{\"scenarios\":[" + cash + ",\"INSTALLMENT\"]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        // 중복·충돌
        run(owner, "/combined", "{\"scenarios\":[" + installment + ",{\"type\":\"INSTALLMENT\",\"parameters\":{\"purchaseAmount\":1200000,\"months\":2,\"feeRate\":0}}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SIMULATION_COMBINED_DUPLICATE_SCENARIO"));
        run(owner, "/combined", "{\"scenarios\":[{\"type\":\"SAVING\",\"parameters\":{\"monthlySavingAmount\":1}},{\"type\":\"SAVING\",\"parameters\":{\"monthlySavingAmount\":2}}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SIMULATION_COMBINED_SCENARIO_CONFLICT"));
        run(owner, "/combined", "{\"scenarios\":[{\"type\":\"LOAN_PREPAYMENT\",\"parameters\":{\"accountNo\":\"" + LOAN_NO + "\",\"prepaymentAmount\":1}},"
                + "{\"type\":\"LOAN_REFINANCE\",\"parameters\":{\"accountNo\":\"" + LOAN_NO + "\",\"targetProductId\":" + creditProduct + "}}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SIMULATION_COMBINED_SCENARIO_CONFLICT"));
        run(owner, "/combined", "{\"scenarios\":[{\"type\":\"REVOLVING\",\"parameters\":{\"balance\":1,\"paymentRatio\":50,\"rate\":1}},"
                + "{\"type\":\"REVOLVING\",\"parameters\":{\"cardId\":" + ownerCard + ",\"balance\":2,\"paymentRatio\":50,\"rate\":1}}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SIMULATION_COMBINED_SCENARIO_CONFLICT"));
        // 자식 소유권: 타인 카드·타인 대출은 404, 재사용 경로보다 먼저 검사된다
        run(owner, "/combined", "{\"scenarios\":[" + installment + ",{\"type\":\"REVOLVING\",\"parameters\":{\"cardId\":" + otherCard + ",\"balance\":1,\"paymentRatio\":50,\"rate\":1}}]}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        run(owner, "/combined", "{\"scenarios\":[" + installment + ",{\"type\":\"LOAN_PREPAYMENT\",\"parameters\":{\"accountNo\":\"" + OTHER_LOAN_NO + "\",\"prepaymentAmount\":1}}]}")
                .andExpect(status().isNotFound());
        // 619: SAVING 자식의 goalId도 단독 실행과 같은 검증 — 타인 목표 404, accountNo와 동시 지정 400
        run(other, "/combined", "{\"scenarios\":[" + installment + ",{\"type\":\"SAVING\",\"parameters\":{\"goalId\":" + goalId + ",\"monthlySavingAmount\":1}}]}")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        run(owner, "/combined", "{\"scenarios\":[" + installment + ",{\"type\":\"SAVING\",\"parameters\":{\"goalId\":" + goalId + ",\"accountNo\":\"" + GOAL_ACCOUNT_NO + "\",\"monthlySavingAmount\":1}}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        // 자식 계산 오류(거래가 없어 평균 지출 0 → 감소액 초과)는 전체 실패이며 부모·자식 어느 행도 남지 않는다
        run(owner, "/combined", "{\"scenarios\":[" + installment + ",{\"type\":\"SPENDING\",\"parameters\":{\"monthlyReduction\":1}}]}")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SIMULATION_REDUCTION_EXCEEDS_SPENDING"));
        mvc.perform(post("/api/v1/simulations/combined").contentType(MediaType.APPLICATION_JSON)
                .content("{\"scenarios\":[" + installment + "," + cash + "]}")).andExpect(status().isUnauthorized());
        assertThat(simulations.count()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM simulation_scenario", Long.class)).isZero();
    }

    private ResultActions run(Long userId, String path, String body) throws Exception {
        return mvc.perform(post("/api/v1/simulations" + path).with(user(userId.toString()))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private long insertProduct(long companyId, String type, String name, String loanType) {
        jdbc.update("""
                INSERT INTO financial_product (company_id, source_type, external_product_code, product_type, product_name, join_way,
                    saving_type, loan_type, special_condition, join_member, min_rate, max_rate, max_limit, baseline_performance,
                    max_benefit_limit, description, benefits, synced_at)
                VALUES (?, 'SEED', NULL, ?, ?, NULL, NULL, ?, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, ?)
                """, companyId, type, name, loanType, LocalDateTime.of(2026, 9, 1, 9, 0));
        return jdbc.queryForObject("SELECT MAX(product_id) FROM financial_product", Long.class);
    }

    private void option(long productId, int term, String minRate, String repaymentType) {
        jdbc.update("""
                INSERT INTO financial_product_option (product_id, term, rate, preferred_rate, rate_type, min_rate, max_rate, repayment_type, limit_amount)
                VALUES (?, ?, NULL, NULL, 'FIXED', ?, 15, ?, NULL)
                """, productId, term, new java.math.BigDecimal(minRate), repaymentType);
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

    private Long insertCard(Long userId, Long accountId, String hashSeed) {
        String hash = String.format("%-64s", hashSeed).replace(' ', '0');
        jdbc.update("""
                INSERT INTO card (user_id, withdrawal_account_id, withdrawal_account_type, card_no_hash, card_no_masked,
                                  issuer_code, issuer_name, card_name, status, credit_limit, created_at)
                VALUES (?, ?, 'DEMAND_DEPOSIT', ?, '1234-****-****-5678', 'KB_CARD', 'KB국민카드', '테스트카드', 'ACTIVE', 3000000, ?)
                """, userId, accountId, hash, Timestamp.valueOf(NOW));
        return jdbc.queryForObject("SELECT MAX(card_id) FROM card WHERE user_id = ?", Long.class, userId);
    }
}
