package com.ssafy.dodam.domain.pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ssafy.dodam.domain.finance.entity.FinanceConnection;
import com.ssafy.dodam.domain.finance.repository.FinanceConnectionRepository;
import com.ssafy.dodam.domain.pattern.dto.response.PatternFeatureSummary.SourceFingerprint;
import com.ssafy.dodam.domain.pattern.entity.AnalysisMode;
import com.ssafy.dodam.domain.pattern.entity.PatternAnalysis;
import com.ssafy.dodam.domain.pattern.entity.PatternAnalysisStatus;
import com.ssafy.dodam.domain.pattern.repository.PatternAnalysisRepository;
import com.ssafy.dodam.domain.pattern.service.ClusterModelProvider;
import com.ssafy.dodam.domain.pattern.service.PatternAnalysisExecutionStore;
import com.ssafy.dodam.domain.pattern.service.PatternClock;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.entity.UserRole;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import com.ssafy.dodam.global.security.AuthPrincipal;
import com.ssafy.dodam.global.exception.BusinessException;
import com.ssafy.dodam.global.exception.ErrorCode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API 3 통합 테스트(H2). 기준일은 2026-09-14로 고정해 분석 기간이 2026-03 ~ 2026-08이 된다.
 *
 * <p>모집단 fixture는 성향 4종(저축·소비·변동·균형) × 12명 = 48명이다. 본인은 저축형에 대출상환 30만 원과 입출금 계좌 600만 원을 더한
 * 사용자라 "저축 중심형" 군집에 배정되고 유동성은 HIGH가 나와야 한다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:pattern516;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class PatternApiTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 10, 0);
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 1, 9, 0);
    private static final List<YearMonth> WINDOW = List.of(
            YearMonth.of(2026, 3), YearMonth.of(2026, 4), YearMonth.of(2026, 5),
            YearMonth.of(2026, 6), YearMonth.of(2026, 7), YearMonth.of(2026, 8));
    private static final int PER_ARCHETYPE = 12;
    private static final SourceFingerprint FINGERPRINT = new SourceFingerprint(0, 0, null, null);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired FinanceConnectionRepository connections;
    @Autowired PatternAnalysisRepository analyses;
    @Autowired PatternAnalysisExecutionStore execution;
    @Autowired ClusterModelProvider modelProvider;
    @MockitoBean PatternClock clock;

    private final Map<String, Long> categories = new HashMap<>();
    private Long owner;
    private int sequence;

    @BeforeEach
    void setUp() {
        given(clock.now()).willReturn(NOW);
        given(clock.today()).willReturn(NOW.toLocalDate());
        jdbc.update("DELETE FROM pattern_analysis");
        jdbc.update("DELETE FROM `transaction`");
        jdbc.update("DELETE FROM account");
        modelProvider.invalidate();
        ensureCategories();
        owner = newUser("본인");
        seedMonths(owner, Archetype.OWNER, 0);
        insertAccount(owner, 6_000_000);
    }

    @Test
    void 하이브리드_분석은_군집을_배정하고_같은_날_결과를_재사용한다() throws Exception {
        seedPopulation();

        String body = mvc.perform(post("/api/v1/patterns/analyze").with(user(owner.toString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.cached").value(false))
                .andExpect(jsonPath("$.data.analysis.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.analysis.requestedMode").value("HYBRID"))
                .andExpect(jsonPath("$.data.analysis.analysisMode").value("HYBRID"))
                .andExpect(jsonPath("$.data.analysis.fallbackReason").isEmpty())
                .andExpect(jsonPath("$.data.analysis.lookbackMonths").value(6))
                .andExpect(jsonPath("$.data.analysis.metrics.dataPointCount").value(36))
                .andExpect(jsonPath("$.data.analysis.metrics.spendingRegularity").value(1.0))
                .andExpect(jsonPath("$.data.analysis.metrics.savingConsistency").value(1.0))
                .andExpect(jsonPath("$.data.analysis.metrics.fixedExpenseRatio").value(0.5556))
                .andExpect(jsonPath("$.data.analysis.metrics.discretionarySpendingRatio").value(0.2222))
                .andExpect(jsonPath("$.data.analysis.metrics.debtBurden").value(0.1))
                .andExpect(jsonPath("$.data.analysis.metrics.cashflowStability").value(1.0))
                .andExpect(jsonPath("$.data.analysis.liquidityLevel").value("HIGH"))
                .andExpect(jsonPath("$.data.analysis.featureSummary.windowStart").value("2026-03"))
                .andExpect(jsonPath("$.data.analysis.featureSummary.windowEnd").value("2026-08"))
                .andExpect(jsonPath("$.data.analysis.featureSummary.incomeBasis").value("TRANSACTIONS"))
                .andExpect(jsonPath("$.data.analysis.featureSummary.monthlyIncome").value(3_000_000))
                .andExpect(jsonPath("$.data.analysis.featureSummary.populationSize").value(PER_ARCHETYPE * 4 + 1))
                .andExpect(jsonPath("$.data.analysis.clusterProfile.clusterLabel").value("저축 중심형"))
                .andExpect(jsonPath("$.data.analysis.clusterProfile.modelVersion").value(ClusterModelProvider.MODEL_VERSION))
                .andExpect(jsonPath("$.data.analysis.clusterProfile.featureWindowMonths").value(6))
                .andExpect(jsonPath("$.data.analysis.clusterProfile.features.length()").value(5))
                .andReturn().getResponse().getContentAsString();
        JsonNode analysis = json.readTree(body).at("/data/analysis");
        long id = analysis.get("analysisId").asLong();
        assertThat(analysis.at("/clusterProfile/sampleSize").asInt()).isGreaterThanOrEqualTo(PER_ARCHETYPE);
        assertThat(analysis.at("/clusterProfile/confidence").asDouble()).isGreaterThanOrEqualTo(0.2);
        assertThat(analysis.at("/clusterProfile/features/1/feature").asText()).isEqualTo("savingRate");
        assertThat(analysis.at("/clusterProfile/features/1/userValue").asDouble()).isEqualTo(0.3333);

        mvc.perform(post("/api/v1/patterns/analyze").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cached").value(true))
                .andExpect(jsonPath("$.data.analysis.analysisId").value(id));
        mvc.perform(post("/api/v1/patterns/analyze").with(user(owner.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"forceRefresh\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.cached").value(false))
                .andExpect(jsonPath("$.data.analysis.analysisId").value(id + 1));
        mvc.perform(get("/api/v1/patterns/me").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisId").value(id + 1))
                .andExpect(jsonPath("$.data.clusterProfile.clusterLabel").value("저축 중심형"))
                .andExpect(jsonPath("$.data.featureSummary.months.length()").value(6));

        PatternAnalysis stored = analyses.findById(id).orElseThrow();
        assertThat(stored.getStatus()).isEqualTo(PatternAnalysisStatus.COMPLETED);
        assertThat(stored.hasCluster()).isTrue();
        assertThat(stored.getClusterStats().get("clusterLabel").asText()).isEqualTo("저축 중심형");
    }

    @Test
    void 규칙만_요청하면_군집을_붙이지_않고_모드가_다르면_재사용하지_않는다() throws Exception {
        seedPopulation();

        mvc.perform(post("/api/v1/patterns/analyze").with(user(owner.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"analysisMode\":\"rule_only\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.analysis.requestedMode").value("RULE_ONLY"))
                .andExpect(jsonPath("$.data.analysis.analysisMode").value("RULE_ONLY"))
                .andExpect(jsonPath("$.data.analysis.fallbackReason").value("RULE_ONLY_REQUESTED"))
                .andExpect(jsonPath("$.data.analysis.clusterProfile").isEmpty())
                .andExpect(jsonPath("$.data.analysis.metrics.debtBurden").value(0.1));
        mvc.perform(post("/api/v1/patterns/analyze").with(user(owner.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"analysisMode\":\"HYBRID\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.cached").value(false))
                .andExpect(jsonPath("$.data.analysis.clusterProfile.clusterLabel").value("저축 중심형"));
        mvc.perform(post("/api/v1/patterns/analyze").with(user(owner.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"analysisMode\":\"FUZZY\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 모집단이_부족하면_규칙_기반으로_자동_전환한다() throws Exception {
        mvc.perform(post("/api/v1/patterns/analyze").with(user(owner.toString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.analysis.requestedMode").value("HYBRID"))
                .andExpect(jsonPath("$.data.analysis.analysisMode").value("RULE_ONLY"))
                .andExpect(jsonPath("$.data.analysis.fallbackReason").value("INSUFFICIENT_POPULATION"))
                .andExpect(jsonPath("$.data.analysis.featureSummary.populationSize").value(1))
                .andExpect(jsonPath("$.data.analysis.clusterProfile").isEmpty())
                .andExpect(jsonPath("$.data.analysis.metrics.fixedExpenseRatio").value(0.5556))
                .andExpect(jsonPath("$.data.analysis.liquidityLevel").value("HIGH"));
        mvc.perform(get("/api/v1/patterns/me").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clusterProfile").isEmpty());
    }

    @Test
    void 데이터가_짧은_사용자는_군집_없이_규칙_결과만_받는다() throws Exception {
        seedPopulation();
        Long newcomer = newUser("신규");
        seedMonths(newcomer, Archetype.BALANCED, 0, WINDOW.subList(4, 6));

        mvc.perform(post("/api/v1/patterns/analyze").with(user(newcomer.toString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.analysis.analysisMode").value("RULE_ONLY"))
                .andExpect(jsonPath("$.data.analysis.fallbackReason").value("USER_DATA_TOO_SHORT"))
                .andExpect(jsonPath("$.data.analysis.featureSummary.monthsWithData").value(2))
                .andExpect(jsonPath("$.data.analysis.clusterProfile").isEmpty())
                .andExpect(jsonPath("$.data.analysis.liquidityLevel").isEmpty());
    }

    @Test
    void 데이터가_없으면_연결_상태에_따라_409를_돌려주고_조회는_결과_없음이다() throws Exception {
        Long disconnected = newUser("미연결");
        mvc.perform(post("/api/v1/patterns/analyze").with(user(disconnected.toString())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("FINANCE_NOT_CONNECTED"));

        Long connected = newUser("연결됨");
        connections.save(FinanceConnection.connected(connected, new byte[] {1}, "h".repeat(64), "c***@example.com",
                new byte[] {2}, "k".repeat(64), NOW));
        mvc.perform(post("/api/v1/patterns/analyze").with(user(connected.toString())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PATTERN_INSUFFICIENT_DATA"));

        mvc.perform(get("/api/v1/patterns/me").with(user(connected.toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PATTERN_ANALYSIS_NOT_FOUND"));
        mvc.perform(get("/api/v1/patterns/me/spending").with(user(connected.toString())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PATTERN_INSUFFICIENT_DATA"));
        mvc.perform(get("/api/v1/patterns/me/cashflow").with(user(connected.toString())))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/v1/patterns/me/priority-profile").with(user(connected.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("DEFAULT"))
                .andExpect(jsonPath("$.data.profileType").value("BALANCED"))
                .andExpect(jsonPath("$.data.clusterDeviationAlpha").value(0.0));
        assertThat(analyses.count()).isZero();
    }

    @Test
    void 소비_자금운용_우선순위_조회는_현재_거래로_계산하고_군집은_최근_분석을_따른다() throws Exception {
        seedPopulation();

        mvc.perform(get("/api/v1/patterns/me/spending?months=6").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startMonth").value("2026-03"))
                .andExpect(jsonPath("$.data.endMonth").value("2026-08"))
                .andExpect(jsonPath("$.data.monthCount").value(6))
                .andExpect(jsonPath("$.data.monthsWithData").value(6))
                .andExpect(jsonPath("$.data.totalSpending").value(1_800_000 * 6))
                .andExpect(jsonPath("$.data.monthlyAverage").value(1_800_000))
                .andExpect(jsonPath("$.data.spendingToIncomeRatio").value(0.6))
                .andExpect(jsonPath("$.data.changeRatePercent").value(0.0))
                .andExpect(jsonPath("$.data.months.length()").value(6))
                .andExpect(jsonPath("$.data.categories[0].code").value("HOUSING"))
                .andExpect(jsonPath("$.data.categories[0].sharePercent").value(38.9))
                .andExpect(jsonPath("$.data.clusterProfile").isEmpty());
        mvc.perform(get("/api/v1/patterns/me/spending?months=2").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startMonth").value("2026-07"))
                .andExpect(jsonPath("$.data.monthCount").value(2))
                .andExpect(jsonPath("$.data.totalSpending").value(1_800_000 * 2));
        mvc.perform(get("/api/v1/patterns/me/spending?months=13").with(user(owner.toString())))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/patterns/me/spending?months=abc").with(user(owner.toString())))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/v1/patterns/me/cashflow").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.incomeBasis").value("TRANSACTIONS"))
                .andExpect(jsonPath("$.data.monthlyIncome").value(3_000_000))
                .andExpect(jsonPath("$.data.averages.spending").value(1_800_000))
                .andExpect(jsonPath("$.data.averages.savingTransfer").value(1_000_000))
                .andExpect(jsonPath("$.data.averages.loanRepayment").value(300_000))
                .andExpect(jsonPath("$.data.averages.net").value(1_200_000))
                .andExpect(jsonPath("$.data.savingRate").value(0.3333))
                .andExpect(jsonPath("$.data.debtBurden").value(0.1))
                .andExpect(jsonPath("$.data.cashflowStability").value(1.0))
                .andExpect(jsonPath("$.data.liquidity.level").value("HIGH"))
                .andExpect(jsonPath("$.data.liquidity.liquidBalance").value(6_000_000))
                .andExpect(jsonPath("$.data.months[0].net").value(1_200_000));

        mvc.perform(get("/api/v1/patterns/me/priority-profile").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("LIVE_CALCULATION"))
                .andExpect(jsonPath("$.data.clusterDeviationAlpha").value(0.0))
                .andExpect(jsonPath("$.data.focusAreas.length()").value(5));

        mvc.perform(post("/api/v1/patterns/analyze").with(user(owner.toString()))).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/patterns/me/spending").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.clusterProfile.clusterLabel").value("저축 중심형"));
        String profile = mvc.perform(get("/api/v1/patterns/me/priority-profile").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.source").value("PATTERN_ANALYSIS"))
                .andExpect(jsonPath("$.data.analysisId").isNumber())
                .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(profile).at("/data/clusterDeviationAlpha").asDouble()).isGreaterThan(0.0);
    }

    @Test
    void 인증과_소유권을_강제한다() throws Exception {
        seedPopulation();
        mvc.perform(post("/api/v1/patterns/analyze").with(user(owner.toString()))).andExpect(status().isCreated());
        Long other = newUser("타인");

        for (String path : List.of("/me", "/me/spending", "/me/cashflow", "/me/priority-profile", "/me/peer-comparison")) {
            mvc.perform(get("/api/v1/patterns" + path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/patterns/analyze")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/patterns/me").with(user(other.toString())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PATTERN_ANALYSIS_NOT_FOUND"));
    }

    /**
     * 700: 같은 날 재사용은 분석 기간 안 원천 거래가 그대로일 때만이다. 일반 사용자 경로(수동 거래 API·분류·숨김)를 그대로 타서
     * 변경 뒤 오래된 분석이 재사용되지 않는지, 기간 밖·타인 변경은 재사용을 깨지 않는지 본다.
     */
    @Test
    void 원천_거래가_바뀌면_같은_날에도_재사용하지_않고_기간_밖_변경은_재사용한다() throws Exception {
        seedPopulation();
        long id = analyzeExpecting(false);
        analyzeExpecting(true, id);

        // 추가(수동 거래 등록, 기간 안)
        String added = json.readTree(mvc.perform(post("/api/v1/transactions").with(principal(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionDate\":\"2026-07-20\",\"transactionType\":\"EXPENSE\",\"amount\":15000,"
                                + "\"categoryId\":" + categories.get("FOOD") + ",\"paymentMethod\":\"CASH\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString()).at("/data/transactionId").asText();
        long afterAdd = analyzeExpecting(false);
        assertThat(afterAdd).isGreaterThan(id);
        analyzeExpecting(true, afterAdd);

        // 카테고리 변경
        mvc.perform(patch("/api/v1/transactions/{id}/category", added).with(principal(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"categoryId\":" + categories.get("GROCERY") + "}"))
                .andExpect(status().isOk());
        long afterCategory = analyzeExpecting(false);
        assertThat(afterCategory).isGreaterThan(afterAdd);
        analyzeExpecting(true, afterCategory);

        // 숨김
        mvc.perform(patch("/api/v1/transactions/{id}/visibility", added).with(principal(owner))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"hidden\":true}"))
                .andExpect(status().isOk());
        long afterHide = analyzeExpecting(false);
        assertThat(afterHide).isGreaterThan(afterCategory);
        analyzeExpecting(true, afterHide);

        // 삭제(하드 삭제 — updated_at이 남지 않으므로 건수·금액 합으로 잡아야 한다)
        mvc.perform(delete("/api/v1/transactions/{id}", added).with(principal(owner)))
                .andExpect(status().isOk());
        long afterDelete = analyzeExpecting(false);
        assertThat(afterDelete).isGreaterThan(afterHide);
        analyzeExpecting(true, afterDelete);

        // 기간 밖(이번 달·기간 이전 달) 추가와 타인 거래 추가는 현재 분석을 무효화하지 않는다.
        for (String date : List.of("2026-09-10", "2026-02-28")) {
            mvc.perform(post("/api/v1/transactions").with(principal(owner))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"transactionDate\":\"" + date + "\",\"transactionType\":\"EXPENSE\",\"amount\":99000,"
                                    + "\"categoryId\":" + categories.get("FOOD") + "}"))
                    .andExpect(status().isCreated());
        }
        Long other = newUser("타인");
        seedMonths(other, Archetype.SAVER, 0);
        mvc.perform(post("/api/v1/transactions").with(principal(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"transactionDate\":\"2026-07-20\",\"transactionType\":\"EXPENSE\",\"amount\":15000,"
                                + "\"categoryId\":" + categories.get("FOOD") + "}"))
                .andExpect(status().isCreated());
        analyzeExpecting(true, afterDelete);
        assertThat(analyses.findFirstByUserIdAndStatusOrderByAnalyzedAtDescIdDesc(other, PatternAnalysisStatus.COMPLETED)).isEmpty();
    }

    /** 700 이전 완료 행은 지문이 없으므로 같은 날이어도 재사용하지 않는다. 오래된 결과 노출보다 한 번의 재계산이 낫다. */
    @Test
    void 지문이_없는_이전_분석은_같은_날에도_재사용하지_않는다() throws Exception {
        seedPopulation();
        long id = analyzeExpecting(false);
        JsonNode summary = json.readTree(jdbc.queryForObject(
                "SELECT feature_summary_json FROM pattern_analysis WHERE analysis_id = ?", String.class, id));
        assertThat(summary.at("/sourceFingerprint/transactionCount").asLong()).isEqualTo(36);
        ((ObjectNode) summary).remove("sourceFingerprint");
        jdbc.update("UPDATE pattern_analysis SET feature_summary_json = ? FORMAT JSON WHERE analysis_id = ?", summary.toString(), id);

        long recomputed = analyzeExpecting(false);
        assertThat(recomputed).isGreaterThan(id);
        analyzeExpecting(true, recomputed);
    }

    private long analyzeExpecting(boolean cached) throws Exception {
        return json.readTree(mvc.perform(post("/api/v1/patterns/analyze").with(user(owner.toString())))
                .andExpect(status().is(cached ? 200 : 201))
                .andExpect(jsonPath("$.data.cached").value(cached))
                .andReturn().getResponse().getContentAsString()).at("/data/analysis/analysisId").asLong();
    }

    private void analyzeExpecting(boolean cached, long expectedId) throws Exception {
        assertThat(analyzeExpecting(cached)).isEqualTo(expectedId);
    }

    /** 거래 API는 @AuthenticationPrincipal AuthPrincipal을 쓰므로 실제 주체 객체로 인증한다(SimulationApiTest와 같다). */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor principal(Long userId) {
        return authentication(new UsernamePasswordAuthenticationToken(new AuthPrincipal(userId, 1L, UserRole.USER), null,
                List.of(new SimpleGrantedAuthority(UserRole.USER.authority()))));
    }

    @Test
    void 진행_중_접수는_중복을_막고_폐기하면_바로_다시_받는다() {
        var admitted = execution.admit(owner, AnalysisMode.HYBRID, false, 6, FINGERPRINT);
        assertThat(admitted.cached()).isFalse();
        assertThatThrownBy(() -> execution.admit(owner, AnalysisMode.HYBRID, true, 6, FINGERPRINT))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PATTERN_ANALYSIS_IN_PROGRESS);

        execution.abandon(owner, admitted.analysis().getId());
        assertThat(analyses.findById(admitted.analysis().getId())).isEmpty();
        var next = execution.admit(owner, AnalysisMode.HYBRID, false, 6, FINGERPRINT);
        assertThat(next.cached()).isFalse();

        // 임대가 지난 접수는 새 접수를 막지 못하고, 늦게 도착한 완료는 만료로 거부된다.
        given(clock.now()).willReturn(NOW.plusMinutes(6));
        var replacement = execution.admit(owner, AnalysisMode.HYBRID, false, 6, FINGERPRINT);
        assertThat(replacement.analysis().getId()).isGreaterThan(next.analysis().getId());
        assertThatThrownBy(() -> execution.complete(owner, next.analysis().getId(),
                new PatternAnalysisExecutionStore.Completion(null, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.PATTERN_ANALYSIS_EXPIRED);
    }

    // ---------- fixture ----------

    private enum Archetype { OWNER, SAVER, SPENDER, VOLATILE, BALANCED }

    /** 632: 군집 안 위치(백분위·구간·문장)와 동년배 비교. 모집단 48명 + 본인은 모두 30대이고 성별은 교대로, 본인은 여성이다. */
    @Test
    void 군집_내_위치와_동년배_비교를_저장하고_축별로_조회한다() throws Exception {
        seedPopulation();
        // 인구통계가 없는 상태에서 분석하면 군집 위치는 있지만 동년배 비교는 DEMOGRAPHICS_MISSING이다
        String first = mvc.perform(post("/api/v1/patterns/analyze").with(user(owner.toString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.analysis.clusterProfile.clusterLabel").value("저축 중심형"))
                .andExpect(jsonPath("$.data.analysis.clusterProfile.positionSummary.feature").value("savingRate"))
                .andExpect(jsonPath("$.data.analysis.clusterProfile.positionSummary.rankBand").isNotEmpty())
                .andExpect(jsonPath("$.data.analysis.clusterProfile.features[1].percentileInCluster").isNumber())
                .andExpect(jsonPath("$.data.analysis.clusterProfile.features[1].rankBand").isNotEmpty())
                .andExpect(jsonPath("$.data.analysis.clusterProfile.insights").isArray())
                .andReturn().getResponse().getContentAsString();
        long firstId = json.readTree(first).at("/data/analysis/analysisId").asLong();
        mvc.perform(get("/api/v1/patterns/me/peer-comparison").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisId").value(firstId))
                .andExpect(jsonPath("$.data.modelVersion").value(ClusterModelProvider.MODEL_VERSION))
                .andExpect(jsonPath("$.data.unavailableReason").value("DEMOGRAPHICS_MISSING"))
                .andExpect(jsonPath("$.data.peerGroup").isEmpty())
                .andExpect(jsonPath("$.data.features").isEmpty())
                // (635) 비교 불가여도 기준 시점·기간은 분석 행에서 채운다
                .andExpect(jsonPath("$.data.analyzedAt").value("2026-09-14T10:00:00"))
                .andExpect(jsonPath("$.data.analysisPeriod.startMonth").value("2026-03"))
                .andExpect(jsonPath("$.data.analysisPeriod.endMonth").value("2026-08"))
                .andExpect(jsonPath("$.data.analysisPeriod.months").value(6));
        assertThat(analyses.findById(firstId).orElseThrow().getPeerStats().get("AGE_BAND_GENDER").get("unavailableReason").asText())
                .isEqualTo("DEMOGRAPHICS_MISSING");

        // 모집단·본인에게 생년월일(30대)·성별을 준 뒤 다시 분석하면 동년배 비교가 채워진다. 30대 여성 = 24 + 본인 = 25 < 30 → 30대(49)로 확장
        jdbc.update("UPDATE `user` SET birth_date = '1990-05-01', gender = CASE WHEN MOD(user_id, 2) = 0 THEN 'FEMALE' ELSE 'MALE' END");
        jdbc.update("UPDATE `user` SET gender = 'FEMALE' WHERE user_id = ?", owner);
        modelProvider.invalidate();
        String second = mvc.perform(post("/api/v1/patterns/analyze").with(user(owner.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"forceRefresh\":true}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long secondId = json.readTree(second).at("/data/analysis/analysisId").asLong();

        String body = mvc.perform(get("/api/v1/patterns/me/peer-comparison").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.analysisId").value(secondId))
                .andExpect(jsonPath("$.data.unavailableReason").doesNotExist())
                .andExpect(jsonPath("$.data.peerGroup.dimension").value("AGE_BAND"))
                .andExpect(jsonPath("$.data.peerGroup.fallbackFrom").value("AGE_BAND_GENDER"))
                .andExpect(jsonPath("$.data.peerGroup.ageBand").value("30S"))
                .andExpect(jsonPath("$.data.peerGroup.gender").isEmpty())
                .andExpect(jsonPath("$.data.peerGroup.sampleSize").value(PER_ARCHETYPE * 4 + 1))
                .andExpect(jsonPath("$.data.features.length()").value(5))
                .andExpect(jsonPath("$.data.features[1].feature").value("savingRate"))
                .andExpect(jsonPath("$.data.features[1].percentileInPeer").isNumber())
                // (635) 값 방향: 저축률만 높을수록 좋다
                .andExpect(jsonPath("$.data.features[0].direction").value("LOWER_IS_BETTER"))
                .andExpect(jsonPath("$.data.features[1].direction").value("HIGHER_IS_BETTER"))
                .andExpect(jsonPath("$.data.features[4].direction").value("LOWER_IS_BETTER"))
                .andExpect(jsonPath("$.data.analyzedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.analysisPeriod.endMonth").value("2026-08"))
                .andExpect(jsonPath("$.data.clusterShare.length()").value(4))
                .andReturn().getResponse().getContentAsString();
        JsonNode peer = json.readTree(body).at("/data");
        // 저축률 0.3333은 저축형 12명(같은 값)만 같거나 높다 → 상위 25% 안
        assertThat(peer.at("/features/1/percentileInPeer").asDouble()).isGreaterThanOrEqualTo(70.0);
        assertThat(peer.at("/clusterShare")).anySatisfy(share -> {
            assertThat(share.get("mine").asBoolean()).isTrue();
            assertThat(share.get("clusterLabel").asText()).isEqualTo("저축 중심형");
        });
        assertThat(peer.at("/insights")).extracting(node -> node.get("code").asText()).contains("PEER_CLUSTER_SHARE");

        // 군집 × 나이대: 30대 저축 중심형 13명 ≥ 10
        mvc.perform(get("/api/v1/patterns/me/peer-comparison?dimension=CLUSTER_AGE_BAND").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.peerGroup.dimension").value("CLUSTER_AGE_BAND"))
                .andExpect(jsonPath("$.data.peerGroup.fallbackFrom").isEmpty())
                .andExpect(jsonPath("$.data.peerGroup.sampleSize").value(PER_ARCHETYPE + 1));
        // 성별만: 여성 25 < 30 → 전체 모집단으로 넓히고 사유를 남긴다
        mvc.perform(get("/api/v1/patterns/me/peer-comparison?dimension=gender").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.peerGroup.dimension").value("ALL"))
                .andExpect(jsonPath("$.data.peerGroup.sampleSize").value(PER_ARCHETYPE * 4 + 1))
                .andExpect(jsonPath("$.data.unavailableReason").value("PEER_GROUP_TOO_SMALL"));
        mvc.perform(get("/api/v1/patterns/me/peer-comparison?dimension=ALL").with(user(owner.toString())))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/patterns/me/peer-comparison?dimension=nope").with(user(owner.toString())))
                .andExpect(status().isBadRequest());

        // 규칙 기반 분석이 최신이면 군집이 없어 CLUSTER_NOT_ASSIGNED
        mvc.perform(post("/api/v1/patterns/analyze").with(user(owner.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"analysisMode\":\"RULE_ONLY\",\"forceRefresh\":true}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/patterns/me/peer-comparison").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unavailableReason").value("CLUSTER_NOT_ASSIGNED"));
    }

    private void seedPopulation() {
        for (int i = 0; i < PER_ARCHETYPE; i++) {
            seedMonths(newUser("저축" + i), Archetype.SAVER, i);
            seedMonths(newUser("소비" + i), Archetype.SPENDER, i);
            seedMonths(newUser("변동" + i), Archetype.VOLATILE, i);
            seedMonths(newUser("균형" + i), Archetype.BALANCED, i);
        }
    }

    private void seedMonths(Long userId, Archetype type, int variant) {
        seedMonths(userId, type, variant, WINDOW);
    }

    private void seedMonths(Long userId, Archetype type, int variant, List<YearMonth> months) {
        long jitter = variant * 5_000L;
        for (int index = 0; index < months.size(); index++) {
            YearMonth ym = months.get(index);
            income(userId, ym, 3_000_000);
            switch (type) {
                case OWNER -> {
                    fixed(userId, ym, "HOUSING", 700_000);
                    fixed(userId, ym, "LOAN_REPAYMENT", 300_000);
                    variable(userId, ym, "FOOD", 400_000);
                    variable(userId, ym, "GROCERY", 400_000);
                    saving(userId, ym, 1_000_000);
                }
                case SAVER -> {
                    fixed(userId, ym, "HOUSING", 700_000);
                    variable(userId, ym, "FOOD", 400_000 + jitter);
                    variable(userId, ym, "GROCERY", 400_000);
                    saving(userId, ym, 1_000_000);
                }
                case SPENDER -> {
                    fixed(userId, ym, "HOUSING", 700_000);
                    variable(userId, ym, "FOOD", 1_000_000 + jitter);
                    variable(userId, ym, "SHOPPING", 1_000_000);
                }
                case VOLATILE -> {
                    fixed(userId, ym, "HOUSING", 700_000);
                    variable(userId, ym, "FOOD", (index % 2 == 0 ? 500_000 : 1_900_000) + jitter);
                    saving(userId, ym, 200_000);
                }
                case BALANCED -> {
                    fixed(userId, ym, "HOUSING", 900_000);
                    variable(userId, ym, "FOOD", 500_000 + jitter);
                    variable(userId, ym, "GROCERY", 600_000);
                    saving(userId, ym, 500_000);
                }
            }
        }
    }

    private void income(Long userId, YearMonth ym, long amount) {
        insertTransaction(userId, ym.atDay(25), "INCOME", "INCOME", "NON_SPENDING", amount);
    }

    private void fixed(Long userId, YearMonth ym, String category, long amount) {
        insertTransaction(userId, ym.atDay(5), "EXPENSE", category, "FIXED", amount);
    }

    private void variable(Long userId, YearMonth ym, String category, long amount) {
        insertTransaction(userId, ym.atDay(12), "EXPENSE", category, "VARIABLE", amount);
    }

    private void saving(Long userId, YearMonth ym, long amount) {
        insertTransaction(userId, ym.atDay(26), "EXPENSE", "SAVING", "NON_SPENDING", amount);
    }

    private void insertTransaction(Long userId, LocalDate date, String type, String category, String spendingType, long amount) {
        jdbc.update("""
                INSERT INTO `transaction` (transaction_id, user_id, account_id, card_id, category_id, transaction_date, transaction_time,
                                           transaction_type, amount, merchant_name, payment_method, spending_type, hidden, source_type, created_at)
                VALUES (?, ?, 1, NULL, ?, ?, '10:00:00', ?, ?, ?, 'ACCOUNT', ?, FALSE, 'SEED', ?)
                """, "PT-" + userId + "-" + (++sequence), userId, categories.get(category), date, type, amount, category,
                spendingType, Timestamp.valueOf(CREATED_AT));
    }

    private void insertAccount(Long userId, long balance) {
        jdbc.update("""
                INSERT INTO account (user_id, bank_code, bank_name, account_no_encrypted, account_no_hash, account_no_masked, account_name,
                                     account_type, status, balance, currency, source_type, created_at, updated_at)
                VALUES (?, '004', 'KB국민은행', X'00', ?, '***-**-1234', '주거래', 'DEMAND_DEPOSIT', 'ACTIVE', ?, 'KRW', 'SEED', ?, ?)
                """, userId, String.format("%064d", userId), balance, Timestamp.valueOf(CREATED_AT), Timestamp.valueOf(CREATED_AT));
    }

    private void ensureCategories() {
        Map<String, String[]> definitions = Map.of(
                "INCOME", new String[] {"수입", "NON_SPENDING"},
                "SAVING", new String[] {"저축·투자", "NON_SPENDING"},
                "HOUSING", new String[] {"주거·관리비", "FIXED"},
                "LOAN_REPAYMENT", new String[] {"대출상환", "FIXED"},
                "FOOD", new String[] {"식비", "VARIABLE"},
                "GROCERY", new String[] {"마트·편의점", "VARIABLE"},
                "SHOPPING", new String[] {"쇼핑", "VARIABLE"});
        definitions.forEach((code, def) -> {
            List<Long> existing = jdbc.queryForList("SELECT category_id FROM transaction_category WHERE code = ?", Long.class, code);
            if (existing.isEmpty()) {
                jdbc.update("INSERT INTO transaction_category (code, name, default_spending_type, active) VALUES (?, ?, ?, TRUE)",
                        code, def[0], def[1]);
                existing = jdbc.queryForList("SELECT category_id FROM transaction_category WHERE code = ?", Long.class, code);
            }
            categories.put(code, existing.get(0));
        });
    }

    private Long newUser(String name) {
        return users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", name)).getId();
    }
}
