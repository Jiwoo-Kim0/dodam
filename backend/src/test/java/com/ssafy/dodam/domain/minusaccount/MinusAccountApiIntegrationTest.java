package com.ssafy.dodam.domain.minusaccount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.context.jdbc.Sql.ExecutionPhase.BEFORE_TEST_CLASS;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest
@Sql(scripts = "/sql/minus-account-test-data.sql", executionPhase = BEFORE_TEST_CLASS)
class MinusAccountApiIntegrationTest {

    private static final String IN_USE_NO = "1010000000001";
    private static final String UNUSED_NO = "1020000000002";
    private static final String OTHER_USER_NO = "1030000000003";
    private static final String CLOSED_NO = "1040000000004";
    private static final String WITHDRAWAL_NO = "1050000000005";
    private static final String MULTI_WITHDRAWAL_MINUS_NO = "1070000000007";
    private static final String NO_WITHDRAWAL_MINUS_NO = "1100000000010";
    private static final String UNKNOWN_NO = "9999999999999";

    private static final String BASE = "/api/v1/loans/minus-accounts";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SensitiveNumberHasher sensitiveNumberHasher;

    private LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        updateHash(101L, IN_USE_NO);
        updateHash(102L, UNUSED_NO);
        updateHash(103L, OTHER_USER_NO);
        updateHash(104L, CLOSED_NO);
        updateHash(105L, WITHDRAWAL_NO);
        updateHash(107L, MULTI_WITHDRAWAL_MINUS_NO);
        updateHash(110L, NO_WITHDRAWAL_MINUS_NO);

        // 기준일 의존을 피한다. 101은 16일 뒤가 이자일, 102는 어제(과거), 104는 이자일이 없다.
        updateNextInterestDate(101L, today.plusDays(16));
        updateNextInterestDate(102L, today.minusDays(1));
        updateNextInterestDate(104L, null);

        // 상태를 원래대로 되돌려 테스트 간 순서 의존을 없앤다.
        resetMinusAccount(101L, 3500000, 6500000, 21000);
        resetMinusAccount(102L, 0, 5000000, 0);
        resetMinusAccount(107L, 1000000, 4000000, 5000);
        resetMinusAccount(110L, 2000000, 3000000, 5000);
        resetBalance(101L, -3500000);
        resetBalance(102L, 0);
        resetBalance(105L, 5000000);

        jdbcTemplate.update("DELETE FROM `transaction`");
        insertTransaction("T-1", 1L, 101L, today.minusDays(1), 500000, -3500000, "TRANSFER", "카드대금");
        insertTransaction("T-2", 1L, 101L, today.minusMonths(2), 300000, -3000000, "TRANSFER", "생활비");
        insertTransaction("T-3", 1L, 101L, today.minusMonths(11), 200000, -2700000, "TRANSFER", "기간 밖");
        insertTransactionHidden("T-4", 1L, 101L, today.minusDays(2), 100000, -3600000);
        insertTransaction("T-5", 2L, 103L, today.minusDays(1), 700000, -19400000, "TRANSFER", "타인 거래");

        jdbcTemplate.update("DELETE FROM notification");
        insertAlert(1L, 101L, "MINUS_LIMIT", "한도 임박", false, null);
        insertAlert(1L, 101L, "MINUS_INTEREST", "이자 납부일", true, "2026-09-02 09:00:00");
        insertDeletedAlert(1L, 101L, "MINUS_OVERDUE", "삭제된 알림");
        insertAlert(2L, 103L, "MINUS_LIMIT", "타인 알림", false, null);
    }

    // ---------- 목록 ----------

    @Test
    @WithMockUser(username = "1")
    void 본인_마이너스통장만_사용액_순으로_조회한다() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.data[0].accountNoMasked").value("104-***-****04"))
                .andExpect(jsonPath("$.data[0].usedAmount").value(12000000))
                .andExpect(jsonPath("$.data[0].status").value("CLOSED"))
                .andExpect(jsonPath("$.data[0].overdueDays").value(17))
                .andExpect(jsonPath("$.data[0].overdue").value(true))
                .andExpect(jsonPath("$.data[1].accountNoMasked").value("101-***-****01"))
                .andExpect(jsonPath("$.data[1].usedAmount").value(3500000))
                .andExpect(jsonPath("$.data[1].availableAmount").value(6500000))
                // 백분율이 아니라 비율이다. 3,500,000 / 10,000,000 = 0.3500
                .andExpect(jsonPath("$.data[1].usageRate").value(0.3500))
                .andExpect(jsonPath("$.data[1].overdue").value(false))
                .andExpect(jsonPath("$.data[2].accountNoMasked").value("102-***-****02"))
                .andExpect(jsonPath("$.data[2].usageRate").value(0.0000));
    }

    @Test
    @WithMockUser(username = "1")
    void 목록은_계좌_상태로_거른다() throws Exception {
        mockMvc.perform(get(BASE).param("status", "closed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].accountNoMasked").value("104-***-****04"));

        mockMvc.perform(get(BASE).param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    @WithMockUser(username = "1")
    void 지원하지_않는_상태는_거부한다() throws Exception {
        mockMvc.perform(get(BASE).param("status", "UNKNOWN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser(username = "5")
    void 보유하지_않으면_빈_목록을_반환한다() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void 인증이_없으면_401이다() throws Exception {
        mockMvc.perform(get(BASE))
                .andExpect(status().isUnauthorized());
    }

    // ---------- 상세 ----------

    @Test
    @WithMockUser(username = "1")
    void 상세는_계좌_일반정보를_함께_반환한다() throws Exception {
        mockMvc.perform(get(BASE + "/" + IN_USE_NO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountNoMasked").value("101-***-****01"))
                .andExpect(jsonPath("$.data.limitAmount").value(10000000))
                .andExpect(jsonPath("$.data.usedAmount").value(3500000))
                // ERD 기준: MINUS 계좌의 잔액은 -used_amount다.
                .andExpect(jsonPath("$.data.balance").value(-3500000))
                .andExpect(jsonPath("$.data.currency").value("KRW"))
                .andExpect(jsonPath("$.data.interestRate").value(7.2000));
    }

    @Test
    @WithMockUser(username = "1")
    void 타인_계좌와_없는_계좌는_모두_404다() throws Exception {
        mockMvc.perform(get(BASE + "/" + OTHER_USER_NO))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(BASE + "/" + UNKNOWN_NO))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "1")
    void 마이너스통장이_아닌_계좌는_404다() throws Exception {
        // 입출금계좌를 마이너스통장 경로로 조회해도 존재 여부가 드러나지 않아야 한다.
        mockMvc.perform(get(BASE + "/" + WITHDRAWAL_NO))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "1")
    void 계좌번호_형식이_틀리면_400이다() throws Exception {
        mockMvc.perform(get(BASE + "/12345"))
                .andExpect(status().isBadRequest());
    }

    // ---------- 이자 ----------

    @Test
    @WithMockUser(username = "1")
    void 이자는_저장값과_계산값을_함께_반환한다() throws Exception {
        // 3,500,000 x 7.2% / 365 = 690.4... -> 절사 690원, 16일치 11,040원
        mockMvc.perform(get(BASE + "/" + IN_USE_NO + "/interest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accruedInterest").value(21000))
                .andExpect(jsonPath("$.data.daysToNextInterestDate").value(16))
                .andExpect(jsonPath("$.data.estimatedDailyInterest").value(690))
                .andExpect(jsonPath("$.data.estimatedInterestUntilNextDate").value(11040))
                .andExpect(jsonPath("$.data.overdue").value(false));
    }

    @Test
    @WithMockUser(username = "1")
    void 사용액이_0원이면_예상이자도_0원이다() throws Exception {
        mockMvc.perform(get(BASE + "/" + UNUSED_NO + "/interest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accruedInterest").value(0))
                .andExpect(jsonPath("$.data.estimatedDailyInterest").value(0))
                // 이자일이 지났으므로 남은 일수와 기간 예상이자는 null이다.
                .andExpect(jsonPath("$.data.daysToNextInterestDate").value(nullValue()))
                .andExpect(jsonPath("$.data.estimatedInterestUntilNextDate").value(nullValue()));
    }

    @Test
    @WithMockUser(username = "1")
    void 이자일이_없으면_기간_예상이자는_null이다() throws Exception {
        mockMvc.perform(get(BASE + "/" + CLOSED_NO + "/interest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextInterestDate").value(nullValue()))
                .andExpect(jsonPath("$.data.daysToNextInterestDate").value(nullValue()))
                .andExpect(jsonPath("$.data.estimatedInterestUntilNextDate").value(nullValue()))
                // 연체 가산금리 계약이 없어 연체 상태에서도 같은 금리로 계산한다.
                .andExpect(jsonPath("$.data.overdue").value(true));
    }

    // ---------- 거래내역 ----------

    @Test
    @WithMockUser(username = "1")
    void 거래내역은_기본_3개월을_최신순으로_반환한다() throws Exception {
        // T-3(11개월 전)은 기본 기간 밖, T-4는 숨김 처리라 제외된다.
        mockMvc.perform(get(BASE + "/" + IN_USE_NO + "/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].transactionId").value("T-1"))
                .andExpect(jsonPath("$.data.content[0].balanceAfter").value(-3500000))
                .andExpect(jsonPath("$.data.content[0].categoryCode").value("TRANSFER"))
                .andExpect(jsonPath("$.data.content[1].transactionId").value("T-2"));
    }

    @Test
    @WithMockUser(username = "1")
    void 거래내역은_조회기간_정책을_따른다() throws Exception {
        mockMvc.perform(get(BASE + "/" + IN_USE_NO + "/transactions")
                        .param("startDate", today.minusMonths(12).minusDays(5).toString())
                        .param("endDate", today.toString()))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(BASE + "/" + IN_USE_NO + "/transactions")
                        .param("startDate", today.toString())
                        .param("endDate", today.minusDays(1).toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "1")
    void 거래내역_페이지_크기_경계를_검증한다() throws Exception {
        mockMvc.perform(get(BASE + "/" + IN_USE_NO + "/transactions").param("size", "101"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + "/" + IN_USE_NO + "/transactions").param("page", "-1"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(BASE + "/" + IN_USE_NO + "/transactions").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.last").value(false));
    }

    @Test
    @WithMockUser(username = "1")
    void 거래가_없으면_빈_페이지를_반환한다() throws Exception {
        mockMvc.perform(get(BASE + "/" + UNUSED_NO + "/transactions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // ---------- 알림 ----------

    @Test
    @WithMockUser(username = "1")
    void 알림은_삭제된_건을_빼고_최신순으로_반환한다() throws Exception {
        mockMvc.perform(get(BASE + "/" + IN_USE_NO + "/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(2)))
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    @WithMockUser(username = "1")
    void 알림은_읽지_않은_것만_거를_수_있다() throws Exception {
        mockMvc.perform(get(BASE + "/" + IN_USE_NO + "/alerts").param("unreadOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(1)))
                .andExpect(jsonPath("$.data.content[0].title").value("한도 임박"))
                .andExpect(jsonPath("$.data.content[0].isRead").value(false));
    }

    @Test
    @WithMockUser(username = "1")
    void 알림이_없으면_빈_페이지를_반환한다() throws Exception {
        mockMvc.perform(get(BASE + "/" + UNUSED_NO + "/alerts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content", hasSize(0)));
    }

    // ---------- 원금 상환 ----------

    @Test
    @WithMockUser(username = "1")
    void 원금을_상환하면_사용액과_양쪽_계좌_잔액이_함께_바뀐다() throws Exception {
        mockMvc.perform(post(BASE + "/" + IN_USE_NO + "/repayments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":500000,\"repayAccountNo\":\"" + WITHDRAWAL_NO + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.repaidAmount").value(500000))
                .andExpect(jsonPath("$.data.usedAmount").value(3000000))
                .andExpect(jsonPath("$.data.availableAmount").value(7000000))
                // 원금 상환은 미수이자를 건드리지 않는다.
                .andExpect(jsonPath("$.data.accruedInterest").value(21000))
                .andExpect(jsonPath("$.data.repayAccountBalance").value(4500000));

        assertThat(usedAmount(101L)).isEqualTo(3000000L);
        assertThat(availableAmount(101L)).isEqualTo(7000000L);
        // ERD 계산 기준: balance = -used_amount
        assertThat(balance(101L)).isEqualTo(-3000000L);
        assertThat(balance(105L)).isEqualTo(4500000L);
    }

    @Test
    @WithMockUser(username = "1")
    void 출금계좌를_생략하면_유일한_입출금계좌를_쓴다() throws Exception {
        mockMvc.perform(post(BASE + "/" + IN_USE_NO + "/repayments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.repayAccountNoMasked").value("105-***-****05"));
    }

    @Test
    @WithMockUser(username = "1")
    void 전액_상환하면_사용액이_0이_된다() throws Exception {
        mockMvc.perform(post(BASE + "/" + IN_USE_NO + "/repayments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":3500000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usedAmount").value(0))
                .andExpect(jsonPath("$.data.availableAmount").value(10000000));

        assertThat(balance(101L)).isEqualTo(0L);
    }

    @Test
    @WithMockUser(username = "1")
    void 사용액을_초과하는_상환은_거부한다() throws Exception {
        mockMvc.perform(post(BASE + "/" + IN_USE_NO + "/repayments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":3500001}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPAYMENT_AMOUNT_EXCEEDED"));

        assertThat(usedAmount(101L)).isEqualTo(3500000L);
    }

    @Test
    @WithMockUser(username = "1")
    void 사용액이_0원인_계좌는_상환할_수_없다() throws Exception {
        mockMvc.perform(post(BASE + "/" + UNUSED_NO + "/repayments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPAYMENT_AMOUNT_EXCEEDED"));
    }

    @Test
    @WithMockUser(username = "1")
    void 상환금액이_0이거나_음수면_400이다() throws Exception {
        mockMvc.perform(post(BASE + "/" + IN_USE_NO + "/repayments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":0}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(BASE + "/" + IN_USE_NO + "/repayments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":-1}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(BASE + "/" + IN_USE_NO + "/repayments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "1")
    void 출금계좌_잔액이_부족하면_거부한다() throws Exception {
        mockMvc.perform(post(BASE + "/" + IN_USE_NO + "/repayments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":3500000,\"repayAccountNo\":\"" + WITHDRAWAL_NO + "\"}"))
                .andExpect(status().isOk());

        // 출금계좌 잔액 5,000,000 - 3,500,000 = 1,500,000 이므로 다음 요청은 부족하다.
        resetMinusAccount(101L, 3500000, 6500000, 21000);
        resetBalance(101L, -3500000);
        mockMvc.perform(post(BASE + "/" + IN_USE_NO + "/repayments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":2000000,\"repayAccountNo\":\"" + WITHDRAWAL_NO + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_ACCOUNT_BALANCE"));
    }

    @Test
    @WithMockUser(username = "1")
    void 해지된_계좌는_상환할_수_없다() throws Exception {
        mockMvc.perform(post(BASE + "/" + CLOSED_NO + "/repayments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MINUS_ACCOUNT_NOT_ACTIVE"));
    }

    @Test
    @WithMockUser(username = "3")
    void 입출금계좌가_여러개면_출금계좌_지정을_요구한다() throws Exception {
        mockMvc.perform(post(BASE + "/" + MULTI_WITHDRAWAL_MINUS_NO + "/repayments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_ACCOUNT_REQUIRED"));
    }

    @Test
    @WithMockUser(username = "4")
    void 입출금계좌가_없어도_출금계좌_지정을_요구한다() throws Exception {
        mockMvc.perform(post(BASE + "/" + NO_WITHDRAWAL_MINUS_NO + "/repayments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WITHDRAWAL_ACCOUNT_REQUIRED"));
    }

    @Test
    @WithMockUser(username = "1")
    void 마이너스통장을_출금계좌로_지정할_수_없다() throws Exception {
        mockMvc.perform(post(BASE + "/" + IN_USE_NO + "/repayments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000,\"repayAccountNo\":\"" + UNUSED_NO + "\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "1")
    void 타인_계좌는_상환할_수_없다() throws Exception {
        mockMvc.perform(post(BASE + "/" + OTHER_USER_NO + "/repayments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":1000}"))
                .andExpect(status().isNotFound());
    }

    // ---------- 이자 납부 ----------

    @Test
    @WithMockUser(username = "1")
    void 이자를_납부하면_미수이자만_줄어든다() throws Exception {
        mockMvc.perform(post(BASE + "/" + IN_USE_NO + "/interest-payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":10000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paidInterest").value(10000))
                .andExpect(jsonPath("$.data.accruedInterest").value(11000))
                // 원금은 그대로다.
                .andExpect(jsonPath("$.data.usedAmount").value(3500000))
                .andExpect(jsonPath("$.data.paymentAccountBalance").value(4990000));

        assertThat(usedAmount(101L)).isEqualTo(3500000L);
        assertThat(balance(101L)).isEqualTo(-3500000L);
    }

    @Test
    @WithMockUser(username = "1")
    void 금액을_생략하면_미수이자_전액을_납부한다() throws Exception {
        mockMvc.perform(post(BASE + "/" + IN_USE_NO + "/interest-payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.paidInterest").value(21000))
                .andExpect(jsonPath("$.data.accruedInterest").value(0));
    }

    @Test
    @WithMockUser(username = "1")
    void 미수이자를_초과하는_납부는_거부한다() throws Exception {
        mockMvc.perform(post(BASE + "/" + IN_USE_NO + "/interest-payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\":21001}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REPAYMENT_AMOUNT_EXCEEDED"));
    }

    @Test
    @WithMockUser(username = "1")
    void 미수이자가_없으면_납부할_수_없다() throws Exception {
        mockMvc.perform(post(BASE + "/" + UNUSED_NO + "/interest-payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("NO_ACCRUED_INTEREST"));
    }

    // ---------- fixture helpers ----------

    private void updateHash(long accountId, String accountNo) {
        jdbcTemplate.update(
                "UPDATE account SET account_no_hash = ? WHERE account_id = ?",
                sensitiveNumberHasher.hash(accountNo),
                accountId
        );
    }

    private void updateNextInterestDate(long accountId, LocalDate date) {
        jdbcTemplate.update(
                "UPDATE minus_account SET next_interest_date = ? WHERE account_id = ?",
                date,
                accountId
        );
    }

    private void resetMinusAccount(long accountId, long used, long available, long accrued) {
        jdbcTemplate.update(
                """
                UPDATE minus_account
                   SET used_amount = ?, available_amount = ?, accrued_interest = ?
                 WHERE account_id = ?
                """,
                used, available, accrued, accountId
        );
    }

    private void resetBalance(long accountId, long balance) {
        jdbcTemplate.update(
                "UPDATE account SET balance = ? WHERE account_id = ?",
                balance,
                accountId
        );
    }

    private void insertTransaction(
            String id,
            long userId,
            long accountId,
            LocalDate date,
            long amount,
            long balanceAfter,
            String type,
            String summary
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO `transaction` (
                    transaction_id, user_id, account_id, category_id, transaction_date,
                    transaction_time, transaction_type, amount, balance_after, summary,
                    hidden, source_type
                ) VALUES (?, ?, ?, 901, ?, '10:00:00', ?, ?, ?, ?, FALSE, 'SEED')
                """,
                id, userId, accountId, date, type, amount, balanceAfter, summary
        );
    }

    private void insertTransactionHidden(
            String id,
            long userId,
            long accountId,
            LocalDate date,
            long amount,
            long balanceAfter
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO `transaction` (
                    transaction_id, user_id, account_id, category_id, transaction_date,
                    transaction_time, transaction_type, amount, balance_after, summary,
                    hidden, source_type
                ) VALUES (?, ?, ?, 901, ?, '10:00:00', 'TRANSFER', ?, ?, '숨김', TRUE, 'SEED')
                """,
                id, userId, accountId, date, amount, balanceAfter
        );
    }

    private void insertAlert(
            long userId,
            long accountId,
            String type,
            String title,
            boolean read,
            String readAt
    ) {
        jdbcTemplate.update(
                """
                INSERT INTO notification (
                    user_id, type, title, message, source_type, source_id,
                    is_read, created_at, read_at, deleted_at
                ) VALUES (?, ?, ?, '테스트 알림', 'MINUS_ACCOUNT', ?, ?, CURRENT_TIMESTAMP, ?, NULL)
                """,
                userId, type, title, String.valueOf(accountId), read, readAt
        );
    }

    private void insertDeletedAlert(long userId, long accountId, String type, String title) {
        jdbcTemplate.update(
                """
                INSERT INTO notification (
                    user_id, type, title, message, source_type, source_id,
                    is_read, created_at, read_at, deleted_at
                ) VALUES (?, ?, ?, '삭제됨', 'MINUS_ACCOUNT', ?, FALSE,
                          CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP)
                """,
                userId, type, title, String.valueOf(accountId)
        );
    }

    private Long usedAmount(long accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT used_amount FROM minus_account WHERE account_id = ?", Long.class, accountId);
    }

    private Long availableAmount(long accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT available_amount FROM minus_account WHERE account_id = ?",
                Long.class,
                accountId
        );
    }

    private Long balance(long accountId) {
        return jdbcTemplate.queryForObject(
                "SELECT balance FROM account WHERE account_id = ?", Long.class, accountId);
    }
}
