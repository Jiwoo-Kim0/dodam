package com.ssafy.dodam.domain.diagnosis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.diagnosis.service.DiagnosisClock;
import com.ssafy.dodam.domain.pattern.repository.PatternAnalysisRepository;
import com.ssafy.dodam.domain.pattern.service.ClusterModelProvider;
import com.ssafy.dodam.domain.pattern.service.PatternClock;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 군집 이탈 진단 규칙(S15P21D105-641)의 통합 경로: 진단이 패턴 분석을 보장하고, HYBRID 배정 결과를 규칙 문제에 병합하거나 단독 문제로
 * 만든다. 모집단은 {@code PatternApiTest}와 같은 성향 4종 × 12명이며, 본인은 소비형 군집에 속하되 식비(재량)가 8월에 늘어 재량 소비
 * 비중과 소득 대비 소비율이 군집 기준 범위를 벗어나게 구성했다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:diagnosis641;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class ClusterDeviationDiagnosisApiTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 10, 0);
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 1, 9, 0);
    private static final List<YearMonth> WINDOW = List.of(
            YearMonth.of(2026, 3), YearMonth.of(2026, 4), YearMonth.of(2026, 5),
            YearMonth.of(2026, 6), YearMonth.of(2026, 7), YearMonth.of(2026, 8));
    private static final int PER_ARCHETYPE = 12;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired PatternAnalysisRepository analyses;
    @Autowired ClusterModelProvider modelProvider;
    @MockitoBean DiagnosisClock diagnosisClock;
    @MockitoBean PatternClock patternClock;

    private final Map<String, Long> categories = new HashMap<>();
    private Long owner;
    private int sequence;

    @BeforeEach
    void setUp() {
        given(diagnosisClock.now()).willReturn(NOW);
        given(patternClock.now()).willReturn(NOW);
        given(patternClock.today()).willReturn(NOW.toLocalDate());
        jdbc.update("DELETE FROM diagnosis_issue");
        jdbc.update("DELETE FROM diagnosis");
        jdbc.update("DELETE FROM pattern_analysis");
        jdbc.update("DELETE FROM `transaction`");
        jdbc.update("DELETE FROM account");
        modelProvider.invalidate();
        ensureCategories();
        owner = newUser("본인");
        insertAccount(owner, 6_000_000);
    }

    @Test
    void withoutAnalyzableDataTheClusterRuleReportsPatternNotAnalyzed() throws Exception {
        // 거래가 없으면 패턴 분석은 409로 거절되고 진단은 그대로 완료된다 — 규칙은 사유만 남긴다
        mvc.perform(post("/api/v1/diagnoses").with(user(owner.toString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.diagnosis.ruleVersion").value("v7-cluster-hybrid"))
                .andExpect(jsonPath("$.data.diagnosis.coverage[?(@.ruleKey == 'CLUSTER')].status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.diagnosis.coverage[?(@.ruleKey == 'CLUSTER')].reason").value("PATTERN_NOT_ANALYZED"))
                .andExpect(jsonPath("$.data.diagnosis.assumptions").value(org.hamcrest.Matchers.hasItem("CLUSTER_DEVIATION_USES_STORED_PATTERN_ANALYSIS")));
        assertThat(analyses.count()).isZero();
    }

    @Test
    void withoutPopulationTheAnalysisFallsBackToRulesAndTheReasonIsExposed() throws Exception {
        seedMonths(owner, Archetype.OWNER, 0);
        mvc.perform(post("/api/v1/diagnoses").with(user(owner.toString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.diagnosis.coverage[?(@.ruleKey == 'CLUSTER')].status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.diagnosis.coverage[?(@.ruleKey == 'CLUSTER')].reason").value("INSUFFICIENT_POPULATION"));
        // 진단이 패턴 분석을 실행해 두었다(RULE_ONLY 전환본)
        assertThat(analyses.count()).isEqualTo(1);
    }

    @Test
    void hybridAssignmentMergesIntoRuleIssuesAndCreatesStandaloneOnes() throws Exception {
        seedPopulation();
        seedMonths(owner, Archetype.OWNER, 0);

        String body = mvc.perform(post("/api/v1/diagnoses").with(user(owner.toString())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.diagnosis.coverage[?(@.ruleKey == 'CLUSTER')].reason").doesNotExist())
                .andExpect(jsonPath("$.data.diagnosis.coverage[?(@.ruleKey == 'CLUSTER')].status").value("EVALUATED"))
                .andExpect(jsonPath("$.data.diagnosis.coverage[?(@.ruleKey == 'CATEGORY_SPENDING_INCREASE')].status").value("EVALUATED"))
                .andReturn().getResponse().getContentAsString();
        long id = json.readTree(body).at("/data/diagnosis/diagnosisId").asLong();
        // 진단이 패턴 분석을 먼저 보장했다: 오늘 완료본 1건, 군집 배정됨
        assertThat(analyses.count()).isEqualTo(1);
        assertThat(analyses.findAll().get(0).hasCluster()).isTrue();

        String issuesBody = mvc.perform(get("/api/v1/diagnoses/{id}/issues?size=50", id).with(user(owner.toString())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<JsonNode> issues = new ArrayList<>();
        json.readTree(issuesBody).at("/data/content").forEach(issues::add);

        // 8월 식비 급증(규칙) 문제에 재량 소비 이탈 근거가 병합됐다 — 문제 수는 늘지 않고 detectionSource만 바뀐다
        JsonNode food = issues.stream().filter(i -> "CATEGORY_SPENDING_INCREASE".equals(i.get("type").asText())
                && "FOOD".equals(i.at("/currentValue/categoryCode").asText())).findFirst().orElseThrow();
        assertThat(food.get("detectionSource").asText()).isEqualTo("RULE_AND_CLUSTER");
        assertThat(food.get("modelVersion").asText()).isNotBlank();
        assertThat(food.at("/clusterContext/feature").asText()).isEqualTo("discretionaryShare");
        assertThat(food.at("/clusterContext/direction").asText()).isEqualTo("ABOVE");
        assertThat(food.at("/clusterContext/sampleSize").asInt()).isGreaterThanOrEqualTo(10);
        assertThat(food.at("/clusterContext/window").asText()).isEqualTo("2026-03~2026-08");
        assertThat(food.at("/clusterContext/deviation").asDouble()).isGreaterThanOrEqualTo(0.25);
        assertThat(issues.stream().filter(i -> "CLUSTER_DISCRETIONARY_HIGH".equals(i.get("type").asText()))).isEmpty();

        // 병합 대상이 없는 특징(소득 대비 소비율)은 단독 CLUSTER 문제로 만들어진다. 제목은 단정하지 않는다
        JsonNode ratio = issues.stream().filter(i -> "CLUSTER_SPENDING_RATIO_HIGH".equals(i.get("type").asText())).findFirst().orElseThrow();
        assertThat(ratio.get("detectionSource").asText()).isEqualTo("CLUSTER");
        assertThat(ratio.get("domain").asText()).isEqualTo("SPENDING");
        assertThat(ratio.get("title").asText()).isEqualTo("비슷한 소비 유형과 견줘 점검할 항목이 있어요");
        assertThat(ratio.get("description").asText()).contains("소득 대비 소비율이").contains("기준 범위");
        assertThat(ratio.at("/evidence/ruleKey").asText()).isEqualTo("CLUSTER_spendingToIncomeRatio");
        // 저축률은 군집보다 낮아도(소비형 군집은 저축 0) 여기서는 문제가 아니다: BELOW인데 군집 범위 안이거나 방향 필터에 걸린다
        assertThat(issues.stream().filter(i -> "CLUSTER_SAVING_RATE_LOW".equals(i.get("type").asText()))).isEmpty();
        // 모든 문제는 CLUSTER 근거의 모델 버전을 갖고, RULE 단독 문제에는 clusterContext가 없다
        for (JsonNode issue : issues) {
            if (!"RULE".equals(issue.get("detectionSource").asText())) {
                assertThat(issue.get("modelVersion").asText()).isNotBlank();
                assertThat(issue.at("/clusterContext/modelVersion").asText()).isEqualTo(issue.get("modelVersion").asText());
            } else {
                assertThat(issue.get("clusterContext").isNull() || issue.get("clusterContext").isMissingNode()).isTrue();
            }
        }

        // 같은 날 재요청은 캐시. 다시 진단해도 패턴 분석은 재사용(2건이 되지 않는다)
        mvc.perform(post("/api/v1/diagnoses").with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.cached").value(true));
        mvc.perform(post("/api/v1/diagnoses").with(user(owner.toString())).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"forceRefresh\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.diagnosis.coverage[?(@.ruleKey == 'CLUSTER')].status").value("EVALUATED"));
        assertThat(analyses.count()).isEqualTo(1);
    }

    // ---------- 모집단 (PatternApiTest와 같은 형태) ----------

    private enum Archetype { OWNER, SAVER, SPENDER, VOLATILE, BALANCED }

    private void seedPopulation() {
        for (int i = 0; i < PER_ARCHETYPE; i++) {
            seedMonths(newUser("저축" + i), Archetype.SAVER, i);
            seedMonths(newUser("소비" + i), Archetype.SPENDER, i);
            seedMonths(newUser("변동" + i), Archetype.VOLATILE, i);
            seedMonths(newUser("균형" + i), Archetype.BALANCED, i);
        }
    }

    private void seedMonths(Long userId, Archetype type, int variant) {
        long jitter = variant * 5_000L;
        for (int index = 0; index < WINDOW.size(); index++) {
            YearMonth ym = WINDOW.get(index);
            income(userId, ym, 3_000_000);
            switch (type) {
                case OWNER -> {
                    // 소비형 군집에 들어가되 식비만 조금 더 쓴다: 8월 급증(627 규칙)으로 재량 소비 비중이 군집 p95를 살짝 넘고,
                    // 소득 대비 소비율은 군집 범위를 크게 벗어난다. 편차를 더 키우면 K-Means가 본인을 단독 군집으로 떼어 낸다
                    fixed(userId, ym, "HOUSING", 700_000);
                    variable(userId, ym, "FOOD", ym.getMonthValue() == 8 ? 1_600_000 : 1_100_000);
                    variable(userId, ym, "SHOPPING", 1_000_000);
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
                    variable(userId, ym, "FOOD", index % 2 == 0 ? 1_500_000 + jitter : 300_000);
                    saving(userId, ym, 200_000);
                }
                case BALANCED -> {
                    fixed(userId, ym, "HOUSING", 700_000);
                    variable(userId, ym, "FOOD", 500_000 + jitter);
                    variable(userId, ym, "GROCERY", 400_000);
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
                """, "CD-" + userId + "-" + (++sequence), userId, categories.get(category), date, type, amount, category,
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
