package com.ssafy.dodam.domain.forecast;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.dodam.domain.improvement.service.ImprovementClock;
import com.ssafy.dodam.domain.report.service.ReportClock;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /api/v1/spending-forecasts} 조회 계층(S15P21D105-706).
 *
 * <p>계산 자체는 {@code SpendingForecastCalculatorTest}·{@code SpendingForecastQueryServiceTest}가 단위로 덮는다.
 * 여기서 보는 것은 컨트롤러를 지나야 드러나는 것들이다 — 인증 없는 호출, 남의 거래가 섞이지 않는가, 파라미터 경계,
 * 그리고 이력이 모자랄 때도 200으로 사유를 싣는가.
 *
 * <p><b>datasource URL과 {@code @MockitoBean} 구성을 {@code ReportApiTest}와 똑같이 맞춘 것은 의도다.</b> 이 둘이
 * 컨텍스트 캐시 키를 이루므로 하나라도 다르면 Spring 컨텍스트가 하나 더 뜬다. 캐시 한도(32)를 넘기면 밀려난 컨텍스트가
 * 공유 H2에 {@code create-drop}을 돌려 무관한 테스트 수십 개가 {@code Table not found}로 깨진 적이 있다(587).
 * 두 클록 목은 이 테스트에서 쓰지 않지만 키를 맞추기 위해 남겨 둔다. 지우면 컨텍스트가 갈라진다.
 *
 * <p>그래서 {@code SpendingForecastClock}은 목으로 고정하지 않고, 이력을 <b>실제 기준 달에서 거꾸로</b> 심는다.
 * 기준 달이 언제든 같은 관계가 성립하므로 달이 바뀌어도 테스트가 깨지지 않는다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:report590;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class SpendingForecastApiTest {

    /** {@code SpendingForecastClock}과 같은 지역. 기준 달을 서버와 같게 잡아야 경계가 어긋나지 않는다 */
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");
    private static final String ENDPOINT = "/api/v1/spending-forecasts";

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

    /** 쓰지 않는다. 위 Javadoc대로 {@code ReportApiTest}와 컨텍스트를 공유하기 위한 선언이다 */
    @MockitoBean ReportClock reportClock;
    @MockitoBean ImprovementClock improvementClock;

    private YearMonth base;
    private Long owner;
    private Long other;

    @BeforeEach
    void setUp() {
        base = YearMonth.now(SERVICE_ZONE);
        owner = newUser("예측 사용자");
        other = newUser("다른 사용자");
    }

    @Test
    @DisplayName("완료된 24개월로 예측하고 화면에는 최근 12개월만 싣는다")
    void forecastsFromCompletedMonthsAndReturnsTwelve() throws Exception {
        for (int back = 1; back <= 24; back++) {
            variableExpense(owner, base.minusMonths(back), 1_000_000);
        }
        // 이번 달은 아직 끝나지 않았다. 넣어도 추정 입력에도 이력에도 들어가면 안 된다.
        variableExpense(owner, base, 9_999_999);

        mvc.perform(get(ENDPOINT).with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.baseMonth").value(base.toString()))
                .andExpect(jsonPath("$.data.horizonMonths").value(3))
                .andExpect(jsonPath("$.data.fallbackReason").value(nullValue()))
                .andExpect(jsonPath("$.data.history.length()").value(12))
                .andExpect(jsonPath("$.data.history[0].month").value(base.minusMonths(12).toString()))
                .andExpect(jsonPath("$.data.history[11].month").value(base.minusMonths(1).toString()))
                .andExpect(jsonPath("$.data.history[11].amount").value(1_000_000))
                .andExpect(jsonPath("$.data.forecasts.length()").value(3))
                .andExpect(jsonPath("$.data.forecasts[0].month").value(base.plusMonths(1).toString()))
                .andExpect(jsonPath("$.data.forecasts[2].month").value(base.plusMonths(3).toString()))
                // 24개월이 전부 같으면 계절 계수 1·추세 0·잔차 0이라 예측이 그 값 그대로 나오고 구간도 붙는다
                .andExpect(jsonPath("$.data.forecasts[0].predicted").value(1_000_000))
                .andExpect(jsonPath("$.data.forecasts[0].lowerBound").value(1_000_000))
                .andExpect(jsonPath("$.data.forecasts[0].upperBound").value(1_000_000));
    }

    @Test
    @DisplayName("horizonMonths는 1~12만 받고 벗어나면 400이다")
    void validatesHorizonBoundary() throws Exception {
        for (int back = 1; back <= 24; back++) {
            variableExpense(owner, base.minusMonths(back), 1_000_000);
        }

        mvc.perform(get(ENDPOINT).param("horizonMonths", "12").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.forecasts.length()").value(12))
                .andExpect(jsonPath("$.data.forecasts[11].month").value(base.plusMonths(12).toString()));
        mvc.perform(get(ENDPOINT).param("horizonMonths", "1").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.forecasts.length()").value(1));
        mvc.perform(get(ENDPOINT).param("horizonMonths", "0").with(user(owner.toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get(ENDPOINT).param("horizonMonths", "13").with(user(owner.toString())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    @DisplayName("인증 없이 부르면 401이다")
    void rejectsAnonymous() throws Exception {
        mvc.perform(get(ENDPOINT)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("남의 거래는 읽지 않는다")
    void isolatesOtherUsersTransactions() throws Exception {
        for (int back = 1; back <= 24; back++) {
            variableExpense(owner, base.minusMonths(back), 1_000_000);
        }

        // 거래가 owner에게만 있으므로 other는 자기 이력이 비어 있는 사용자와 구분되지 않아야 한다.
        mvc.perform(get(ENDPOINT).with(user(other.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.history").isEmpty())
                .andExpect(jsonPath("$.data.forecasts").isEmpty())
                .andExpect(jsonPath("$.data.fallbackReason").value("FEATURES_UNAVAILABLE"));
    }

    @Test
    @DisplayName("이력이 3개월보다 짧으면 예측 없이 200과 사유를 준다")
    void returnsReasonInsteadOfFailingWhenHistoryIsShort() throws Exception {
        variableExpense(owner, base.minusMonths(1), 1_000_000);
        variableExpense(owner, base.minusMonths(2), 1_000_000);

        // 예측 카드 하나 때문에 화면이 실패하면 안 되므로 404·400이 아니라 200 + 사유다.
        mvc.perform(get(ENDPOINT).with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.history.length()").value(2))
                .andExpect(jsonPath("$.data.forecasts").isEmpty())
                .andExpect(jsonPath("$.data.fallbackReason").value("USER_DATA_TOO_SHORT"));
    }

    @Test
    @DisplayName("spending_type이 NULL이면 카테고리 기본값으로 해석한다")
    void resolvesNullSpendingTypeByCategoryDefault() throws Exception {
        YearMonth month = base.minusMonths(1);
        long variableCategory = category("FORECAST_TEST_VARIABLE", "예측 테스트 변동", "VARIABLE");
        long fixedCategory = category("FORECAST_TEST_FIXED", "예측 테스트 고정", "FIXED");

        // 들어와야 하는 둘: 카테고리 기본값이 VARIABLE인 NULL, 카테고리가 아예 없는 NULL
        expense(owner, month, 11_000, null, variableCategory);
        expense(owner, month, 44_000, null, null);
        // 빠져야 하는 둘: 카테고리 기본값이 FIXED인 NULL, NON_SPENDING 명시
        expense(owner, month, 22_000, null, fixedCategory);
        expense(owner, month, 33_000, "NON_SPENDING", variableCategory);

        mvc.perform(get(ENDPOINT).with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.history.length()").value(1))
                .andExpect(jsonPath("$.data.history[0].month").value(month.toString()))
                .andExpect(jsonPath("$.data.history[0].amount").value(55_000));
    }

    @Test
    @DisplayName("거래가 없던 달은 0이 아니라 목록에서 빠진다")
    void skipsMonthsWithoutTransactions() throws Exception {
        variableExpense(owner, base.minusMonths(1), 1_000_000);
        variableExpense(owner, base.minusMonths(2), 1_000_000);
        // 3개월 전은 비워 둔다
        variableExpense(owner, base.minusMonths(4), 1_000_000);

        mvc.perform(get(ENDPOINT).with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.history.length()").value(3))
                .andExpect(jsonPath("$.data.history[0].month").value(base.minusMonths(4).toString()))
                .andExpect(jsonPath("$.data.history[1].month").value(base.minusMonths(2).toString()));
    }

    // ---------- helpers ----------

    private Long newUser(String name) {
        return users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", name)).getId();
    }

    private void variableExpense(Long userId, YearMonth month, long amount) {
        expense(userId, month, amount, "VARIABLE", null);
    }

    /** 달의 10일에 하나씩 넣는다. 예측은 월 합계만 보므로 일자는 달 안이기만 하면 된다 */
    private void expense(Long userId, YearMonth month, long amount, String spendingType, Long categoryId) {
        jdbc.update("""
                INSERT INTO `transaction` (transaction_id, user_id, account_id, card_id, category_id, transaction_date,
                    transaction_time, transaction_type, amount, balance_after, merchant_name, payment_method, spending_type,
                    summary, memo, hidden, source_type, created_at, updated_at)
                VALUES (?, ?, NULL, NULL, ?, ?, NULL, 'EXPENSE', ?, NULL, NULL, 'CARD', ?, 'test', NULL, FALSE, 'SEED', ?, NULL)
                """, UUID.randomUUID().toString(), userId, categoryId, month.atDay(10), amount, spendingType,
                LocalDateTime.now());
    }

    private long category(String code, String name, String defaultSpendingType) {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM transaction_category WHERE code = ?", Integer.class, code) == 0) {
            jdbc.update("INSERT INTO transaction_category (code, name, default_spending_type, active) VALUES (?, ?, ?, TRUE)",
                    code, name, defaultSpendingType);
        }
        return jdbc.queryForObject("SELECT category_id FROM transaction_category WHERE code = ?", Long.class, code);
    }
}
