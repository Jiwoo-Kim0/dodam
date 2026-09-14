package com.ssafy.dodam.domain.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.goal.entity.Goal;
import com.ssafy.dodam.domain.goal.entity.GoalType;
import com.ssafy.dodam.domain.goal.repository.GoalRepository;
import com.ssafy.dodam.domain.simulation.dto.SimulationBaseline;
import com.ssafy.dodam.domain.simulation.dto.SimulationInput;
import com.ssafy.dodam.domain.simulation.dto.SimulationResult;
import com.ssafy.dodam.domain.simulation.entity.Simulation;
import com.ssafy.dodam.domain.simulation.entity.SimulationType;
import com.ssafy.dodam.domain.simulation.repository.SimulationRepository;
import com.ssafy.dodam.domain.simulation.service.SimulationCalculationVersion;
import com.ssafy.dodam.domain.simulation.service.SimulationClock;
import com.ssafy.dodam.domain.simulation.service.SimulationRequestHash;
import com.ssafy.dodam.domain.simulation.service.SimulationRunner;
import com.ssafy.dodam.domain.simulation.service.SimulationStore;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
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
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 공통 조회·삭제 API와 저장·재사용 계약(550). 실행 경로(계산기)는 551부터라 결과를 Store에 직접 넣어 검증한다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:simulation550;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class SimulationQueryApiTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 13, 12, 0);
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired UserRepository users;
    @Autowired GoalRepository goals;
    @Autowired SimulationRepository simulations;
    @Autowired SimulationStore store;
    @Autowired SimulationRunner runner;
    @MockitoBean SimulationClock clock;
    private Long owner;
    private Long other;
    private Long goalId;

    /** 551 이전에는 확정 입력 구현체가 없어 테스트용 최소 구현을 쓴다. */
    private record StubInput(SimulationType type, String fingerprint) implements SimulationInput {
        @Override
        public int durationMonths() {
            return 3;
        }

        @Override
        public ObjectNode parameters(ObjectMapper json) {
            return json.createObjectNode().put("stub", fingerprint);
        }
    }

    @BeforeEach
    void setup() {
        given(clock.now()).willReturn(NOW);
        jdbc.update("DELETE FROM simulation_goal_impact");
        jdbc.update("DELETE FROM simulation_scenario");
        jdbc.update("DELETE FROM simulation");
        owner = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "시뮬레이션")).getId();
        other = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "다른 사용자")).getId();
        goalId = goals.save(Goal.create(owner, "비상금", GoalType.SAVING, NOW.minusMonths(3))).getId();
    }

    private Simulation save(Long userId, SimulationType type, String fingerprint, LocalDateTime now) {
        LocalDate base = now.toLocalDate();
        SimulationBaseline baseline = new SimulationBaseline(base, null, 0L, Map.of(), List.of(), List.of(),
                List.of(SimulationBaseline.INCOME_UNKNOWN));
        SimulationResult.Snapshot before = new SimulationResult.Snapshot(base, null, 600_000L, 100_000L, null,
                List.of(new SimulationResult.GoalForecast(goalId, "비상금", 1_000_000L, 100_000L, base.plusMonths(10))));
        SimulationResult.Snapshot after = new SimulationResult.Snapshot(base, null, 500_000L, 100_000L, null,
                List.of(new SimulationResult.GoalForecast(goalId, "비상금", 1_000_000L, 200_000L, base.plusMonths(5))));
        SimulationResult result = new SimulationResult("제목", "요약", before, after,
                new SimulationResult.Comparison(List.of(SimulationResult.Item.amount("MONTHLY_SPENDING", "월 지출", 600_000L, 500_000L)),
                        List.of(SimulationResult.Item.amount("A1", "자산1", 0L, 1L), SimulationResult.Item.amount("A2", "자산2", 0L, 2L)),
                        List.of(), List.of()),
                new SimulationResult.Cashflow(SimulationResult.Cashflow.MONTHLY_AVAILABLE_FUNDS, null, null,
                        List.of(new SimulationResult.Month("2026-10", 100_000L, 100_000L))),
                List.of(new SimulationResult.GoalImpact(goalId, base.plusMonths(10), base.plusMonths(5))), List.of("STUB"));
        StubInput input = new StubInput(type, fingerprint);
        String hash = SimulationRequestHash.of(userId, type, fingerprint, base, SimulationCalculationVersion.CURRENT);
        return tx.execute(status -> store.save(userId, input, hash, baseline, result, now));
    }

    @Test
    void storeSavesAtomicallyAndReusesSameDaySameFingerprint() {
        Simulation saved = save(owner, SimulationType.SPENDING, "a", NOW);
        assertThat(saved.getRequestSnapshot().path("requestHash").asText()).hasSize(64);
        assertThat(saved.getRequestSnapshot().path("calculationVersion").asText()).isEqualTo(SimulationCalculationVersion.CURRENT);
        assertThat(saved.getBeforeSnapshot().path("coverage").get(0).asText()).isEqualTo("INCOME_UNKNOWN");
        assertThat(saved.getAfterSnapshot().path("assumptions").get(0).asText()).isEqualTo("STUB");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM simulation_scenario WHERE simulation_id = ?", Integer.class, saved.getSimulationId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT difference_days FROM simulation_goal_impact WHERE simulation_id = ?", Integer.class, saved.getSimulationId()))
                .isEqualTo((int) java.time.temporal.ChronoUnit.DAYS.between(NOW.toLocalDate().plusMonths(10), NOW.toLocalDate().plusMonths(5)));

        String hash = SimulationRequestHash.of(owner, SimulationType.SPENDING, "a", NOW.toLocalDate(), SimulationCalculationVersion.CURRENT);
        assertThat(reusable(owner, SimulationType.SPENDING, hash, NOW.toLocalDate())).map(Simulation::getSimulationId)
                .contains(saved.getSimulationId());
        assertThat(reusable(owner, SimulationType.SPENDING, hash, NOW.toLocalDate().plusDays(1))).isEmpty();
        assertThat(reusable(owner, SimulationType.SAVING, hash, NOW.toLocalDate())).isEmpty();
        assertThat(reusable(other, SimulationType.SPENDING, hash, NOW.toLocalDate())).isEmpty();
        tx.executeWithoutResult(s -> simulations.findById(saved.getSimulationId()).orElseThrow().delete(NOW));
        assertThat(reusable(owner, SimulationType.SPENDING, hash, NOW.toLocalDate())).isEmpty();
        // 잠금 트랜잭션 밖에서는 저장할 수 없다
        assertThatThrownBy(() -> store.findReusable(owner, SimulationType.SPENDING, hash, NOW.toLocalDate()))
                .isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
    }

    private java.util.Optional<Simulation> reusable(Long userId, SimulationType type, String hash, LocalDate day) {
        return tx.execute(s -> store.findReusable(userId, type, hash, day));
    }

    @Test
    void runnerRejectsTypesWithoutCalculator() {
        assertThatThrownBy(() -> runner.run(owner, new StubInput(SimulationType.LOAN_NEW, "x")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.SIMULATION_TYPE_NOT_SUPPORTED);
        assertThat(simulations.count()).isZero();
    }

    @Test
    void queriesAreOwnedFilteredPagedAndSoftDeleted() throws Exception {
        long spendingId = save(owner, SimulationType.SPENDING, "a", NOW).getSimulationId();
        long savingId = save(owner, SimulationType.SAVING, "b", NOW.plusMinutes(1)).getSimulationId();
        save(other, SimulationType.SPENDING, "c", NOW);

        mvc.perform(get("/api/v1/simulations").with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].simulationId").value(savingId))
                .andExpect(jsonPath("$.data.content[0].status").value("COMPLETED"));
        mvc.perform(get("/api/v1/simulations?type=SPENDING").with(user(owner.toString())))
                .andExpect(jsonPath("$.data.totalElements").value(1)).andExpect(jsonPath("$.data.content[0].simulationId").value(spendingId));
        mvc.perform(get("/api/v1/simulations?type=LOAN_NEW").with(user(owner.toString()))).andExpect(jsonPath("$.data.totalElements").value(0));
        for (String query : List.of("type=UNKNOWN", "page=-1", "size=0", "size=101")) {
            mvc.perform(get("/api/v1/simulations?" + query).with(user(owner.toString()))).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/simulations")).andExpect(status().isUnauthorized());

        mvc.perform(get("/api/v1/simulations/{id}", spendingId).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.request.requestHash").doesNotExist())
                .andExpect(jsonPath("$.data.request.stub").value("a"))
                .andExpect(jsonPath("$.data.scenarios[0].scenarioType").value("SPENDING"))
                .andExpect(jsonPath("$.data.before.monthlySpending").value(600_000))
                .andExpect(jsonPath("$.data.goalImpacts[0].goalId").value(goalId))
                .andExpect(jsonPath("$.data.goalImpacts[0].differenceDays").isNumber());
        for (String suffix : List.of("", "/comparison", "/cashflow", "/goal-impact", "/assets", "/debts", "/costs")) {
            mvc.perform(get("/api/v1/simulations/" + spendingId + suffix).with(user(owner.toString()))).andExpect(status().isOk());
            mvc.perform(get("/api/v1/simulations/" + spendingId + suffix).with(user(other.toString()))).andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/simulations/" + spendingId + suffix)).andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/v1/simulations/{id}/comparison", spendingId).with(user(owner.toString())))
                .andExpect(jsonPath("$.data.items[0].key").value("MONTHLY_SPENDING")).andExpect(jsonPath("$.data.items[0].delta").value(-100_000));
        mvc.perform(get("/api/v1/simulations/{id}/cashflow", spendingId).with(user(owner.toString())))
                .andExpect(jsonPath("$.data.months[0].yearMonth").value("2026-10"));
        mvc.perform(get("/api/v1/simulations/{id}/assets?page=1&size=1", spendingId).with(user(owner.toString())))
                .andExpect(jsonPath("$.data.totalElements").value(2)).andExpect(jsonPath("$.data.content[0].key").value("A2"));
        mvc.perform(get("/api/v1/simulations/{id}/assets?page=5&size=1", spendingId).with(user(owner.toString())))
                .andExpect(jsonPath("$.data.content.length()").value(0));
        mvc.perform(get("/api/v1/simulations/{id}/debts", spendingId).with(user(owner.toString())))
                .andExpect(jsonPath("$.data.totalElements").value(0));

        mvc.perform(delete("/api/v1/simulations/{id}", spendingId).with(user(other.toString()))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/simulations/{id}", spendingId).with(user(owner.toString()))).andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/simulations/{id}", spendingId).with(user(owner.toString()))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/simulations/{id}", spendingId).with(user(owner.toString()))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/simulations").with(user(owner.toString()))).andExpect(jsonPath("$.data.totalElements").value(1));
        assertThat(simulations.findById(spendingId).orElseThrow().isDeleted()).isTrue();
    }
}
