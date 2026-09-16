package com.ssafy.dodam.domain.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
