package com.ssafy.dodam.domain.loan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.dodam.domain.loan.service.LoanAnalysisClock;
import com.ssafy.dodam.global.security.SensitiveNumberHasher;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 시장금리 비교와 고금리 판정 검증.
 *
 * <p>fixture는 일반 신용대출 비교군의 상품 대표값을 4.0 / 6.0 / 8.0으로 두어 기준금리 중앙값 6.0을 손으로
 * 검산할 수 있게 만들었다. 상품 11은 옵션이 2개(5.0, 7.0)이고 그 중앙값이 6.0이므로 옵션 수가 많은 상품이
 * 기준을 더 끌어당기지 않는지 함께 확인한다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties =
        "spring.datasource.url=jdbc:h2:mem:loan_rate_comparison;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@Sql(scripts = "/sql/loan-rate-comparison-test-data.sql")
class LoanRateComparisonApiIntegrationTest {
    private static final String HIGH_INTEREST = "/api/v1/loans/high-interest";
    private static final String HIGH_RATE_LOAN = "1101110001111";
    private static final String NORMAL_RATE_LOAN = "1102220002222";
    private static final String MORTGAGE_LOAN = "1103330003333";
    private static final String AUTO_LOAN = "1104440004444";
    private static final String NO_PRODUCT_LOAN = "1105550005555";
    private static final String UNSUPPORTED_LOAN = "1106660006666";
    private static final String COMPLETED_LOAN = "1107770007777";
    private static final String NO_PERIOD_LOAN = "1108880008888";
    private static final String UNKNOWN_SECTOR_LOAN = "1109990009999";
    private static final String OTHERS_LOAN = "1101010101010";

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private SensitiveNumberHasher hasher;
    @MockitoBean private LoanAnalysisClock clock;

    @BeforeEach
    void setUp() {
        when(clock.today()).thenReturn(LocalDate.of(2026, 9, 9));
        hash(1, HIGH_RATE_LOAN);
        hash(2, NORMAL_RATE_LOAN);
        hash(3, MORTGAGE_LOAN);
        hash(4, AUTO_LOAN);
        hash(5, NO_PRODUCT_LOAN);
        hash(6, UNSUPPORTED_LOAN);
        hash(7, COMPLETED_LOAN);
        hash(8, NO_PERIOD_LOAN);
        hash(9, UNKNOWN_SECTOR_LOAN);
        hash(11, OTHERS_LOAN);
    }

    private void hash(long loanId, String accountNo) {
        jdbc.update("UPDATE loan SET account_no_hash = ? WHERE loan_id = ?", hasher.hash(accountNo), loanId);
    }

    private String path(String accountNo) {
        return "/api/v1/loans/" + accountNo + "/rate-comparison";
    }

    @Test
    @WithMockUser(username = "1")
    void 공시_최저금리_중앙값으로_기준금리와_편차를_계산한다() throws Exception {
        String content = mvc.perform(get(path(HIGH_RATE_LOAN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.asOfDate").value("2026-09-09"))
                .andExpect(jsonPath("$.data.status").value("COMPLETE"))
                .andExpect(jsonPath("$.data.comparisonGroup").value("CREDIT"))
                .andExpect(jsonPath("$.data.termBand").value("<=24"))
                .andExpect(jsonPath("$.data.industry").value("BANK"))
                .andExpect(jsonPath("$.data.referenceMethod").value("DISCLOSED_MIN_RATE_MEDIAN"))
                // 상품 10·11·12의 대표값 4.0 / 6.0 / 8.0의 중앙값
                .andExpect(jsonPath("$.data.referenceRate").value(6.0000))
                .andExpect(jsonPath("$.data.referenceConfidence").value("NORMAL"))
                .andExpect(jsonPath("$.data.rateDifference").value(2.0000))
                .andExpect(jsonPath("$.data.comparableProductCount").value(3))
                // 자기 상품(13), 자격 한정 상품(14), 중금리(15·16)는 모집단에 없다.
                .andExpect(jsonPath("$.data.candidates[*].productId", contains(10, 11, 12)))
                .andExpect(jsonPath("$.data.candidates[*].productId", not(hasItem(13))))
                .andExpect(jsonPath("$.data.candidates[0].representativeRate").value(4.0000))
                .andExpect(jsonPath("$.data.candidates[1].representativeRate").value(6.0000))
                .andExpect(jsonPath("$.data.candidates[0].sourceType").value("MANUAL"))
                .andExpect(jsonPath("$.data.oldestSyncedAt").value("2026-09-01T09:00:00"))
                .andExpect(jsonPath("$.data.latestSyncedAt").value("2026-09-03T09:00:00"))
                .andExpect(jsonPath("$.data.assumptions", hasItem("REFERENCE_IS_NOT_APPROVAL_RATE")))
                .andExpect(jsonPath("$.data.assumptions", hasItem("DISCLOSED_SNAPSHOT_NOT_REALTIME")))
                .andReturn().getResponse().getContentAsString();
        assertThat(content).doesNotContain(HIGH_RATE_LOAN, hasher.hash(HIGH_RATE_LOAN), "accountNoHash");
        assertThat(content).contains("110-***-****11");
    }

    @Test
    @WithMockUser(username = "1")
    void topN은_후보만_줄이고_기준금리를_바꾸지_않는다() throws Exception {
        mvc.perform(get(path(HIGH_RATE_LOAN)).param("topN", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.referenceRate").value(6.0000))
                .andExpect(jsonPath("$.data.comparableProductCount").value(3))
                .andExpect(jsonPath("$.data.candidates", hasSize(1)))
                .andExpect(jsonPath("$.data.candidates[0].productId").value(10));
    }

    @Test
    @WithMockUser(username = "1")
    void 기간_밴드가_다른_옵션은_모집단에서_제외한다() throws Exception {
        // 상품 10에는 240개월 옵션(대표값 0.2)과 term NULL 옵션(0.1)이 있다. 12개월 대출에 섞이면
        // 기준금리가 무너지므로 대표값이 4.0으로 유지되는지 확인한다.
        mvc.perform(get(path(HIGH_RATE_LOAN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.candidates[0].representativeRate").value(4.0000))
                .andExpect(jsonPath("$.data.candidates[0].term").value(12));
    }

    @Test
    @WithMockUser(username = "1")
    void 담보대출은_상품_2개로_LOW_신뢰도와_모호한_상환방식을_알린다() throws Exception {
        mvc.perform(get(path(MORTGAGE_LOAN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETE"))
                .andExpect(jsonPath("$.data.comparisonGroup").value("MORTGAGE"))
                .andExpect(jsonPath("$.data.termBand").value(">120"))
                .andExpect(jsonPath("$.data.referenceRate").value(3.5000))
                .andExpect(jsonPath("$.data.referenceConfidence").value("LOW"))
                .andExpect(jsonPath("$.data.rateDifference").value(-0.5000))
                .andExpect(jsonPath("$.data.comparableProductCount").value(2))
                .andExpect(jsonPath("$.data.unmatchedConditions", hasItem("REPAYMENT_TYPE_AMBIGUOUS")));
    }

    @Test
    @WithMockUser(username = "1")
    void 자동차대출은_표본_미달이라_후보만_남기고_기준금리는_null이다() throws Exception {
        mvc.perform(get(path(AUTO_LOAN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PARTIAL"))
                .andExpect(jsonPath("$.data.reasons", contains("INSUFFICIENT_COMPARABLE_PRODUCTS")))
                .andExpect(jsonPath("$.data.referenceRate", nullValue()))
                .andExpect(jsonPath("$.data.rateDifference", nullValue()))
                .andExpect(jsonPath("$.data.comparableProductCount").value(1))
                .andExpect(jsonPath("$.data.candidates", hasSize(1)));
    }

    @ParameterizedTest
    @CsvSource({
            NO_PRODUCT_LOAN + ",COMPARISON_GROUP_UNKNOWN",
            UNSUPPORTED_LOAN + ",UNSUPPORTED_LOAN_TYPE",
            COMPLETED_LOAN + ",NOT_OUTSTANDING",
            NO_PERIOD_LOAN + ",MISSING_LOAN_PERIOD",
            UNKNOWN_SECTOR_LOAN + ",INDUSTRY_UNKNOWN"
    })
    @WithMockUser(username = "1")
    void 비교_불가는_200과_사유로_구분한다(String accountNo, String reason) throws Exception {
        mvc.perform(get(path(accountNo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.reasons", contains(reason)))
                .andExpect(jsonPath("$.data.referenceRate", nullValue()))
                .andExpect(jsonPath("$.data.candidates", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "1")
    void 다른_업권을_명시하면_참고_비교임을_표시한다() throws Exception {
        // 보유 업권이 BANK인 대출에 존재하지 않는 업권을 요청하면 모집단이 비고 참고 비교로 표시된다.
        mvc.perform(get(path(HIGH_RATE_LOAN)).param("industry", "SAVINGS_BANK"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.industry").value("SAVINGS_BANK"))
                .andExpect(jsonPath("$.data.unmatchedConditions", hasItem("OTHER_INDUSTRY_REFERENCE")))
                .andExpect(jsonPath("$.data.comparableProductCount").value(0));
    }

    @Test
    @WithMockUser(username = "1")
    void term을_명시하면_해당_밴드로_비교한다() throws Exception {
        // 240개월을 명시하면 12개월 대출도 >120 밴드로 비교되고 기간 불일치를 표시한다.
        mvc.perform(get(path(HIGH_RATE_LOAN)).param("term", "240"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.termBand").value(">120"))
                .andExpect(jsonPath("$.data.unmatchedConditions", hasItem("TERM_BAND_MATCH")));
    }

    @Test
    @WithMockUser(username = "1")
    void 고금리_판정은_임계값_이상만_담고_나머지는_사유로_분리한다() throws Exception {
        mvc.perform(get(HIGH_INTEREST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.thresholdMode").value("MARKET_SPREAD"))
                .andExpect(jsonPath("$.data.rateSpreadThreshold").value(1.0000))
                .andExpect(jsonPath("$.data.referenceMethod").value("DISCLOSED_MIN_RATE_MEDIAN"))
                // 대출 1(편차 +2.0)과 10(중금리군 기준 7.25 대비 9.0으로 +1.75)만 대상이다.
                .andExpect(jsonPath("$.data.loans[*].loanId", contains(1, 10)))
                .andExpect(jsonPath("$.data.loans[0].rateDifference").value(2.0000))
                .andExpect(jsonPath("$.data.loans[0].comparisonGroup").value("CREDIT"))
                .andExpect(jsonPath("$.data.loans[1].rateDifference").value(1.7500))
                .andExpect(jsonPath("$.data.loans[1].comparisonGroup").value("CREDIT_MID"))
                .andExpect(jsonPath("$.data.loans[1].requiresAttention").value(true))
                .andExpect(jsonPath("$.data.highInterestCount").value(2))
                // 대출 2·3은 판정했지만 임계값 미달, 4·5·6·8·9는 판정 불가다.
                .andExpect(jsonPath("$.data.evaluatedCount").value(4))
                .andExpect(jsonPath("$.data.unavailableCount").value(5))
                .andExpect(jsonPath("$.data.complete").value(false))
                .andExpect(jsonPath("$.data.unavailableReasons", contains("INCOMPLETE_LOAN_DATA")))
                .andExpect(jsonPath("$.data.unavailableLoans[*].loanId", contains(4, 5, 6, 8, 9)))
                .andExpect(jsonPath("$.data.assumptions", hasItem("EXCLUDES_OVERDUE_INTEREST_AND_ARREARS")));
    }

    @Test
    @WithMockUser(username = "1")
    void 중금리_대출을_일반_신용대출_기준으로_판정하지_않는다() throws Exception {
        // 대출 10을 일반 신용대출군(기준 6.0)으로 재면 편차가 +3.0이 된다. 중금리군 기준 +1.75여야 한다.
        mvc.perform(get(HIGH_INTEREST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loans[1].referenceRate").value(7.2500));
    }

    /**
     * 판정식이 `>=`인지 확인한다. 사용자 3의 세 대출은 기준금리 6.0에 대해 편차가 각각 정확히 1.0000,
     * 0.9999, 1.0001%p다. `>`로 잘못 구현하면 임계값과 같은 대출 12가 목록에서 빠진다.
     */
    @Test
    @WithMockUser(username = "3")
    void 임계값과_같은_편차는_포함하고_직전은_제외한다() throws Exception {
        mvc.perform(get(HIGH_INTEREST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.evaluatedCount").value(3))
                .andExpect(jsonPath("$.data.highInterestCount").value(2))
                .andExpect(jsonPath("$.data.unavailableCount").value(0))
                // 편차 내림차순이므로 직후(+1.0001) → 동일(+1.0000) 순서다.
                .andExpect(jsonPath("$.data.loans[*].loanId", contains(14, 12)))
                .andExpect(jsonPath("$.data.loans[0].rateDifference").value(1.0001))
                .andExpect(jsonPath("$.data.loans[1].rateDifference").value(1.0000))
                .andExpect(jsonPath("$.data.loans[*].loanId", not(hasItem(13))));
    }

    /**
     * 기간 밴드 경계를 확인한다. 밴드는 `≤24 / 25~60 / 61~120 / >120`이므로 각 경계의 앞뒤 값이 서로 다른
     * 밴드로 갈라져야 한다. 비교 조건에서 `<=`를 `<`로 잘못 쓰면 24·60·120이 다음 밴드로 밀린다.
     */
    @ParameterizedTest
    @CsvSource({"1,<=24", "24,<=24", "25,25-60", "60,25-60", "61,61-120", "120,61-120",
            "121,>120", "600,>120"})
    @WithMockUser(username = "1")
    void 기간_밴드_경계를_구분한다(int term, String expectedBand) throws Exception {
        mvc.perform(get(path(HIGH_RATE_LOAN)).param("term", String.valueOf(term)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.termBand").value(expectedBand));
    }

    @Test
    @WithMockUser(username = "1")
    void loanType_필터는_해당_유형만_평가한다() throws Exception {
        mvc.perform(get(HIGH_INTEREST).param("loanType", "MORTGAGE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loans", hasSize(0)))
                .andExpect(jsonPath("$.data.evaluatedCount").value(1))
                .andExpect(jsonPath("$.data.unavailableCount").value(0))
                .andExpect(jsonPath("$.data.complete").value(true));
    }

    @Test
    @WithMockUser(username = "2")
    void 타인의_대출은_평가하지_않는다() throws Exception {
        mvc.perform(get(HIGH_INTEREST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loans[*].loanId", contains(11)))
                .andExpect(jsonPath("$.data.evaluatedCount").value(1));
    }

    @Test
    @WithMockUser(username = "999")
    void 대출이_없으면_빈_결과와_complete_true다() throws Exception {
        mvc.perform(get(HIGH_INTEREST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.loans", hasSize(0)))
                .andExpect(jsonPath("$.data.evaluatedCount").value(0))
                .andExpect(jsonPath("$.data.unavailableCount").value(0))
                .andExpect(jsonPath("$.data.complete").value(true));
    }

    @ParameterizedTest
    @ValueSource(strings = {OTHERS_LOAN, "9999999999999"})
    @WithMockUser(username = "1")
    void 타인과_미존재_대출을_구분하지_않는다(String accountNo) throws Exception {
        mvc.perform(get(path(accountNo)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"123456789", "123456789012345678901", "abc1234567890"})
    @WithMockUser(username = "1")
    void 계좌번호_형식을_검증한다(String accountNo) throws Exception {
        mvc.perform(get(path(accountNo))).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @CsvSource({"topN,0", "topN,21", "topN,'' ", "term,0", "term,601", "term,abc", "industry,''",
            "industry,bank"})
    @WithMockUser(username = "1")
    void 입력_경계값을_거부한다(String name, String value) throws Exception {
        mvc.perform(get(path(HIGH_RATE_LOAN)).param(name, value.trim()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "1")
    void 지원하지_않는_thresholdMode는_400이다() throws Exception {
        mvc.perform(get(HIGH_INTEREST).param("thresholdMode", "PEER_AVERAGE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get(HIGH_INTEREST).param("thresholdMode", "MARKET_SPREAD"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "1")
    void 잘못된_loanType은_400이다() throws Exception {
        mvc.perform(get(HIGH_INTEREST).param("loanType", "UNKNOWN_TYPE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"high-interest", "1101110001111/rate-comparison"})
    void 인증이_필수다(String suffix) throws Exception {
        mvc.perform(get("/api/v1/loans/" + suffix)).andExpect(status().isUnauthorized());
    }
}
