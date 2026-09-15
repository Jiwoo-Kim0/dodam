package com.ssafy.dodam.domain.notification.event;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 알림 이벤트 규칙(S15P21D105-581)이 다른 도메인 테이블에서 후보를 읽는 읽기 전용 JDBC.
 *
 * <p>다른 도메인의 엔티티·서비스를 끌어오지 않고 마이그레이션 스키마를 직접 읽는다(cashflow·recommendation은 BE-B 소관이라 결합을 피한다).
 * 모든 조회는 `userId`가 null이면 전체 사용자(삭제 제외), 아니면 그 사용자만 본다.
 */
@Repository
public class JdbcNotificationEventReader {

    private static final String USER_FILTER = " AND (:userId IS NULL OR %s = :userId) ";

    private final NamedParameterJdbcTemplate jdbc;

    public JdbcNotificationEventReader(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** ACTIVE 정기결제 중 다음 결제일이 [from, to] 안인 것. */
    public List<DueSubscription> subscriptionsDue(Long userId, LocalDate from, LocalDate to) {
        String sql = """
                SELECT s.user_id, s.subscription_id, s.name, s.payment_amount, s.next_payment_date
                FROM subscription s JOIN `user` u ON u.user_id = s.user_id AND u.deleted_at IS NULL
                WHERE s.status = 'ACTIVE' AND s.next_payment_date BETWEEN :from AND :to
                """ + USER_FILTER.formatted("s.user_id") + " ORDER BY s.user_id, s.next_payment_date, s.subscription_id";
        return jdbc.query(sql, window(userId, from, to), (rs, i) -> new DueSubscription(rs.getLong("user_id"),
                rs.getString("subscription_id"), rs.getString("name"), rs.getLong("payment_amount"),
                rs.getObject("next_payment_date", LocalDate.class)));
    }

    /** 결제 예정(SCHEDULED) 카드 청구서 중 출금일이 [from, to] 안인 것. */
    public List<DueCardStatement> cardStatementsDue(Long userId, LocalDate from, LocalDate to) {
        String sql = """
                SELECT c.user_id, c.card_id, c.card_name, cs.billing_month, cs.total_balance, cs.payment_date
                FROM card_billing_statement cs
                JOIN card c ON c.card_id = cs.card_id
                JOIN `user` u ON u.user_id = c.user_id AND u.deleted_at IS NULL
                WHERE cs.status = 'SCHEDULED' AND cs.payment_date BETWEEN :from AND :to
                """ + USER_FILTER.formatted("c.user_id") + " ORDER BY c.user_id, cs.payment_date, c.card_id";
        return jdbc.query(sql, window(userId, from, to), (rs, i) -> new DueCardStatement(rs.getLong("user_id"), rs.getLong("card_id"),
                rs.getString("card_name"), rs.getString("billing_month"), rs.getLong("total_balance"),
                rs.getObject("payment_date", LocalDate.class)));
    }

    /** ACTIVE·OVERDUE 대출 중 다음 상환일이 [from, to] 안인 것. */
    public List<DueLoan> loansDue(Long userId, LocalDate from, LocalDate to) {
        String sql = """
                SELECT l.user_id, l.loan_id, l.account_name, l.balance, l.status, l.next_repayment_date
                FROM loan l JOIN `user` u ON u.user_id = l.user_id AND u.deleted_at IS NULL
                WHERE l.status IN ('ACTIVE', 'OVERDUE') AND l.next_repayment_date BETWEEN :from AND :to
                """ + USER_FILTER.formatted("l.user_id") + " ORDER BY l.user_id, l.next_repayment_date, l.loan_id";
        return jdbc.query(sql, window(userId, from, to), (rs, i) -> new DueLoan(rs.getLong("user_id"), rs.getLong("loan_id"),
                rs.getString("account_name"), rs.getLong("balance"), rs.getString("status"),
                rs.getObject("next_repayment_date", LocalDate.class)));
    }

    /** 마이너스통장 중 다음 이자 납부일이 [from, to] 안인 것(계좌 ACTIVE). */
    public List<DueMinusAccount> minusInterestDue(Long userId, LocalDate from, LocalDate to) {
        String sql = """
                SELECT m.user_id, m.account_id, a.account_name, m.accrued_interest, m.used_amount, m.next_interest_date
                FROM minus_account m
                JOIN account a ON a.account_id = m.account_id AND a.status = 'ACTIVE'
                JOIN `user` u ON u.user_id = m.user_id AND u.deleted_at IS NULL
                WHERE m.next_interest_date BETWEEN :from AND :to
                """ + USER_FILTER.formatted("m.user_id") + " ORDER BY m.user_id, m.next_interest_date, m.account_id";
        return jdbc.query(sql, window(userId, from, to), (rs, i) -> new DueMinusAccount(rs.getLong("user_id"), rs.getLong("account_id"),
                rs.getString("account_name"), rs.getLong("accrued_interest"), rs.getLong("used_amount"),
                rs.getObject("next_interest_date", LocalDate.class)));
    }

    /** 사용자별 최신 COMPLETED 현금흐름 예측의 HIGH·CRITICAL 위험 중 위험일이 [from, to] 안(또는 미정)인 것. */
    public List<CashflowRiskRow> cashflowRisks(Long userId, LocalDate from, LocalDate to) {
        String sql = """
                SELECT f.user_id, f.forecast_id, r.risk_id, r.type, r.severity, r.message, r.risk_date, r.expected_balance
                FROM (SELECT user_id, MAX(forecast_id) AS forecast_id FROM cashflow_forecast WHERE status = 'COMPLETED' GROUP BY user_id) latest
                JOIN cashflow_forecast f ON f.forecast_id = latest.forecast_id
                JOIN cashflow_risk r ON r.forecast_id = f.forecast_id
                JOIN `user` u ON u.user_id = f.user_id AND u.deleted_at IS NULL
                WHERE r.severity IN ('HIGH', 'CRITICAL')
                  AND (r.risk_date IS NULL OR r.risk_date BETWEEN :from AND :to)
                """ + USER_FILTER.formatted("f.user_id") + " ORDER BY f.user_id, r.risk_date, r.risk_id";
        return jdbc.query(sql, window(userId, from, to), (rs, i) -> new CashflowRiskRow(rs.getLong("user_id"), rs.getLong("forecast_id"),
                rs.getLong("risk_id"), rs.getString("type"), rs.getString("severity"), rs.getString("message"),
                rs.getObject("risk_date", LocalDate.class), rs.getObject("expected_balance", Long.class)));
    }

    /** 삭제되지 않은 모으기 목표(ACTIVE·ACHIEVED)와 목표 금액. */
    public List<SavingGoalRow> savingGoals(Long userId) {
        String sql = """
                SELECT g.user_id, g.goal_id, g.name, g.status, g.current_amount, sg.target_amount
                FROM goal g
                JOIN saving_goal sg ON sg.goal_id = g.goal_id
                JOIN `user` u ON u.user_id = g.user_id AND u.deleted_at IS NULL
                WHERE g.type = 'SAVING' AND g.deleted_at IS NULL AND g.status IN ('ACTIVE', 'ACHIEVED')
                """ + USER_FILTER.formatted("g.user_id") + " ORDER BY g.user_id, g.goal_id";
        return jdbc.query(sql, new MapSqlParameterSource("userId", userId), (rs, i) -> new SavingGoalRow(rs.getLong("user_id"),
                rs.getLong("goal_id"), rs.getString("name"), rs.getString("status"), rs.getLong("current_amount"), rs.getLong("target_amount")));
    }

    /** 최근 `since` 이후 생성된 PENDING 개선안. */
    public List<RecommendationRow> recentRecommendations(Long userId, LocalDateTime since) {
        String sql = """
                SELECT r.user_id, r.recommendation_id, r.title, r.domain, r.type, r.monthly_cost_saving
                FROM recommendation r JOIN `user` u ON u.user_id = r.user_id AND u.deleted_at IS NULL
                WHERE r.status = 'PENDING' AND r.created_at >= :since
                """ + USER_FILTER.formatted("r.user_id") + " ORDER BY r.user_id, r.recommendation_id";
        return jdbc.query(sql, new MapSqlParameterSource("userId", userId).addValue("since", since),
                (rs, i) -> new RecommendationRow(rs.getLong("user_id"), rs.getLong("recommendation_id"), rs.getString("title"),
                        rs.getString("domain"), rs.getString("type"), rs.getObject("monthly_cost_saving", Long.class)));
    }

    /** 월간 리포트를 켜 둔 사용자. */
    public List<SettingRow> monthlyReportUsers(Long userId) {
        return settingUsers(userId, "us.monthly_report_enabled = 1");
    }

    /** 데일리 브리핑을 켜 둔 사용자(시간·시간대 포함). 시각 일치 판정은 서비스가 한다. */
    public List<SettingRow> dailyBriefingUsers(Long userId) {
        return settingUsers(userId, "us.daily_briefing_enabled = 1");
    }

    private List<SettingRow> settingUsers(Long userId, String condition) {
        String sql = """
                SELECT u.user_id, u.name, us.daily_briefing_time, us.timezone
                FROM user_setting us JOIN `user` u ON u.user_id = us.user_id AND u.deleted_at IS NULL
                WHERE us.notification_enabled = 1
                """ + " AND " + condition + USER_FILTER.formatted("u.user_id") + " ORDER BY u.user_id";
        return jdbc.query(sql, new MapSqlParameterSource("userId", userId), (rs, i) -> new SettingRow(rs.getLong("user_id"),
                rs.getString("name"), rs.getObject("daily_briefing_time", LocalTime.class), rs.getString("timezone")));
    }

    /** 활성 줄이기 목표의 월 예산 합(없으면 0). */
    public long monthlyBudget(Long userId) {
        Long value = jdbc.queryForObject("""
                SELECT COALESCE(SUM(sg.monthly_budget), 0)
                FROM goal g JOIN spending_goal sg ON sg.goal_id = g.goal_id
                WHERE g.user_id = :userId AND g.type = 'SPENDING' AND g.status = 'ACTIVE' AND g.deleted_at IS NULL
                """, new MapSqlParameterSource("userId", userId), Long.class);
        return Objects.requireNonNullElse(value, 0L);
    }

    /** [from, to] 소비 합(API 6 정의: EXPENSE, NON_SPENDING 제외, 숨김 제외). */
    public long spending(Long userId, LocalDate from, LocalDate to) {
        Long value = jdbc.queryForObject("""
                SELECT COALESCE(SUM(entry.amount), 0) FROM `transaction` entry
                WHERE entry.user_id = :userId AND entry.hidden = FALSE AND entry.transaction_type = 'EXPENSE'
                  AND (entry.spending_type IS NULL OR entry.spending_type <> 'NON_SPENDING')
                  AND entry.transaction_date BETWEEN :from AND :to
                """, window(userId, from, to), Long.class);
        return Objects.requireNonNullElse(value, 0L);
    }

    private static MapSqlParameterSource window(Long userId, LocalDate from, LocalDate to) {
        return new MapSqlParameterSource("userId", userId).addValue("from", from).addValue("to", to);
    }

    public record DueSubscription(Long userId, String subscriptionId, String name, long amount, LocalDate paymentDate) { }

    public record DueCardStatement(Long userId, Long cardId, String cardName, String billingMonth, long totalBalance, LocalDate paymentDate) { }

    public record DueLoan(Long userId, Long loanId, String accountName, long balance, String status, LocalDate repaymentDate) { }

    public record DueMinusAccount(Long userId, Long accountId, String accountName, long accruedInterest, long usedAmount, LocalDate interestDate) { }

    public record CashflowRiskRow(Long userId, Long forecastId, Long riskId, String type, String severity, String message,
            LocalDate riskDate, Long expectedBalance) { }

    public record SavingGoalRow(Long userId, Long goalId, String name, String status, long currentAmount, long targetAmount) { }

    public record RecommendationRow(Long userId, Long recommendationId, String title, String domain, String type, Long monthlyCostSaving) { }

    public record SettingRow(Long userId, String name, LocalTime dailyBriefingTime, String timezone) { }
}
