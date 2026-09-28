package com.ssafy.dodam.domain.subscription;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.auth.repository.AuthSessionRepository;
import com.ssafy.dodam.domain.finance.repository.FinanceConnectionRepository;
import com.ssafy.dodam.domain.user.repository.FinancialProfileRepository;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import com.ssafy.dodam.domain.user.repository.UserSettingRepository;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyFinanceClient;
import com.ssafy.dodam.infrastructure.external.ssafy.SsafyMember;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Subscription API 테스트 공통 준비. 실제 회원가입·로그인 토큰으로 호출하고, 정기결제·카드·거래 행은 JdbcTemplate로 넣는다
 * (명세 7에 등록 API가 없다). 날짜는 모두 오늘 기준 상대값이라 어느 날 돌려도 같은 결과가 난다.
 *
 * <p>기본 데이터(`seedDefaultSubscriptions`): 본인 6건 + 타인 1건.
 * <pre>
 * SUB-T-ICLOUD   iCloud+     CLOUD    1,100 MONTHLY ACTIVE   next = 오늘+2   (카드 없음)
 * SUB-T-NETFLIX  넷플릭스     VIDEO   17,000 MONTHLY ACTIVE   next = 오늘+20  카드 A
 * SUB-T-GYM      헬스장 연간   FITNESS 300,000 YEARLY ACTIVE   next = 오늘+100 카드 A, payment_day 없음
 * SUB-T-SPOTIFY  스포티파이   MUSIC   10,900 MONTHLY PAUSED   next 없음, payment_day 3
 * SUB-T-WATCHA   왓챠        VIDEO    7,900 MONTHLY EXPIRED  end_date = 오늘-30
 * SUB-T-MILLIE   밀리의서재   BOOK     9,900 MONTHLY CANCELED canceled_at 있음
 * SUB-O-NETFLIX  (타인) 넷플릭스 ACTIVE
 * </pre>
 */
abstract class SubscriptionApiTestSupport {

    static final String EMAIL = "subscription@example.com";
    static final String OTHER_EMAIL = "other-subscription@example.com";
    static final String PASSWORD = "password123";

    static final String ICLOUD = "SUB-T-ICLOUD";
    static final String NETFLIX = "SUB-T-NETFLIX";
    static final String GYM = "SUB-T-GYM";
    static final String SPOTIFY = "SUB-T-SPOTIFY";
    static final String WATCHA = "SUB-T-WATCHA";
    static final String MILLIE = "SUB-T-MILLIE";
    static final String OTHER_NETFLIX = "SUB-O-NETFLIX";

    static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 1, 10, 9, 0);

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected UserSettingRepository userSettingRepository;

    @Autowired
    protected FinancialProfileRepository financialProfileRepository;

    @Autowired
    protected FinanceConnectionRepository financeConnectionRepository;

    @Autowired
    protected AuthSessionRepository authSessionRepository;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @MockitoBean
    protected SsafyFinanceClient ssafyFinanceClient;

    protected String bearer;
    protected String otherBearer;
    protected Long userId;
    protected Long otherUserId;
    protected Long cardId;
    protected Long otherCardId;
    protected LocalDate today;

    @BeforeEach
    void setUpUsers() throws Exception {
        today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        jdbcTemplate.update("DELETE FROM subscription_payment_history");
        jdbcTemplate.update("DELETE FROM subscription");
        jdbcTemplate.update("DELETE FROM `transaction`");
        jdbcTemplate.update("DELETE FROM card");
        authSessionRepository.deleteAll();
        financeConnectionRepository.deleteAll();
        userSettingRepository.deleteAll();
        financialProfileRepository.deleteAll();
        userRepository.deleteAll();

        given(ssafyFinanceClient.createMember(anyString()))
                .willAnswer(invocation -> new SsafyMember(
                        invocation.getArgument(0), "회원", "00100", "user-key-" + invocation.getArgument(0)));

        signup(EMAIL, "도담");
        signup(OTHER_EMAIL, "타인");
        bearer = login(EMAIL);
        otherBearer = login(OTHER_EMAIL);
        userId = userRepository.findByEmailAndDeletedAtIsNull(EMAIL).orElseThrow().getId();
        otherUserId = userRepository.findByEmailAndDeletedAtIsNull(OTHER_EMAIL).orElseThrow().getId();

        cardId = insertCard(userId, "도담 체크", "5***-****-****-1234", "a1");
        otherCardId = insertCard(otherUserId, "타인 카드", "4***-****-****-9999", "b1");
    }

    // ---------- fixture ----------

    protected void seedDefaultSubscriptions() {
        LocalDate icloudNext = today.plusDays(2);
        LocalDate netflixNext = today.plusDays(20);
        insertSubscription(ICLOUD, userId, null, "iCloud+", 1_100, "MONTHLY", icloudNext.getDayOfMonth(),
                icloudNext, null, "ACTIVE", "CLOUD", CREATED_AT, null);
        insertSubscription(NETFLIX, userId, cardId, "넷플릭스", 17_000, "MONTHLY", netflixNext.getDayOfMonth(),
                netflixNext, null, "ACTIVE", "VIDEO", CREATED_AT, null);
        insertSubscription(GYM, userId, cardId, "헬스장 연간", 300_000, "YEARLY", null,
                today.plusDays(100), null, "ACTIVE", "FITNESS", today.minusDays(265).atTime(9, 0), null);
        insertSubscription(SPOTIFY, userId, cardId, "스포티파이", 10_900, "MONTHLY", 3,
                null, null, "PAUSED", "MUSIC", CREATED_AT, null);
        insertSubscription(WATCHA, userId, null, "왓챠", 7_900, "MONTHLY", 10,
                null, today.minusDays(30), "EXPIRED", "VIDEO", CREATED_AT, null);
        insertSubscription(MILLIE, userId, null, "밀리의서재", 9_900, "MONTHLY", 25,
                null, null, "CANCELED", "BOOK", CREATED_AT, today.minusDays(15).atTime(12, 0));
        insertSubscription(OTHER_NETFLIX, otherUserId, otherCardId, "넷플릭스", 17_000, "MONTHLY", 1,
                today.plusDays(5), null, "ACTIVE", "VIDEO", CREATED_AT, null);
    }

    /** 넷플릭스 결제 이력 7건: 성공 6(오늘-10, -40, -69, -100, -130, -400), 실패 1(오늘-70). */
    protected void seedNetflixHistory() {
        insertHistory(NETFLIX, today.minusDays(10), 17_000, "SUCCESS", 0, "정상 결제");
        insertHistory(NETFLIX, today.minusDays(40), 17_000, "SUCCESS", 0, "정상 결제");
        insertHistory(NETFLIX, today.minusDays(69), 17_000, "SUCCESS", 1, "재시도 성공");
        insertHistory(NETFLIX, today.minusDays(70), 17_000, "FAILED", 1, "한도 초과");
        insertHistory(NETFLIX, today.minusDays(100), 17_000, "SUCCESS", 0, "정상 결제");
        insertHistory(NETFLIX, today.minusDays(130), 17_000, null, 0, null);
        insertHistory(NETFLIX, today.minusDays(400), 17_000, "SUCCESS", 0, "정상 결제");
    }

    protected Long insertCard(Long ownerId, String cardName, String masked, String hashSeed) {
        String hash = String.format("%-64s", hashSeed).replace(' ', '0');
        jdbcTemplate.update("""
                INSERT INTO card (user_id, withdrawal_account_id, withdrawal_account_type, card_no_encrypted, card_no_hash,
                                  card_no_masked, issuer_code, issuer_name, card_name, status, credit_limit, created_at)
                VALUES (?, 1, 'DEMAND_DEPOSIT', X'00', ?, ?, 'KB_CARD', 'KB국민카드', ?, 'ACTIVE', 3000000, ?)
                """, ownerId, hash, masked, cardName, Timestamp.valueOf(CREATED_AT));
        return jdbcTemplate.queryForObject("SELECT MAX(card_id) FROM card WHERE user_id = ?", Long.class, ownerId);
    }

    protected void insertSubscription(
            String id, Long ownerId, Long card, String name, long amount, String cycle, Integer paymentDay,
            LocalDate nextPaymentDate, LocalDate endDate, String status, String category,
            LocalDateTime createdAt, LocalDateTime canceledAt
    ) {
        jdbcTemplate.update("""
                INSERT INTO subscription (subscription_id, user_id, card_id, name, payment_amount, billing_cycle, payment_day,
                                          next_payment_date, end_date, status, category, created_at, updated_at, canceled_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?)
                """, id, ownerId, card, name, amount, cycle, paymentDay, nextPaymentDate, endDate, status, category,
                Timestamp.valueOf(createdAt), canceledAt == null ? null : Timestamp.valueOf(canceledAt));
    }

    protected void insertHistory(String subscriptionId, LocalDate date, long amount, String code, int retry, String message) {
        jdbcTemplate.update("""
                INSERT INTO subscription_payment_history (subscription_id, payment_date, payment_amount, result_code, retry_count, result_message)
                VALUES (?, ?, ?, ?, ?, ?)
                """, subscriptionId, date, amount, code, retry, message);
    }

    protected void insertTransaction(
            String id, Long ownerId, Long card, Long categoryId, LocalDate date, String type, long amount,
            String merchant, String paymentMethod, boolean hidden
    ) {
        jdbcTemplate.update("""
                INSERT INTO `transaction` (transaction_id, user_id, account_id, card_id, category_id, transaction_date, transaction_time,
                                           transaction_type, amount, merchant_name, payment_method, spending_type, hidden, source_type, created_at)
                VALUES (?, ?, ?, ?, ?, ?, '10:00:00', ?, ?, ?, ?, 'FIXED', ?, 'SEED', ?)
                """, id, ownerId, card == null ? 1L : null, card, categoryId, date, type, amount, merchant, paymentMethod, hidden,
                Timestamp.valueOf(CREATED_AT));
    }

    /** 서비스와 같은 규칙: 오늘 이후(오늘 포함) 가장 가까운 결제일. */
    protected LocalDate nextMonthlyOnOrAfterToday(int paymentDay) {
        LocalDate candidate = today.withDayOfMonth(Math.min(paymentDay, today.lengthOfMonth()));
        if (candidate.isBefore(today)) {
            LocalDate nextMonth = today.plusMonths(1);
            candidate = nextMonth.withDayOfMonth(Math.min(paymentDay, nextMonth.lengthOfMonth()));
        }
        return candidate;
    }

    // ---------- auth helpers ----------

    protected void signup(String email, String name) throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD, "name", name))))
                .andExpect(status().isCreated());
    }

    protected String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return "Bearer " + objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    protected String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }
}
