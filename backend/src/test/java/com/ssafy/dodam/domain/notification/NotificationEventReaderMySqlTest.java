package com.ssafy.dodam.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.ssafy.dodam.domain.notification.event.JdbcNotificationEventReader;
import com.ssafy.dodam.domain.notification.event.NotificationEventService;
import com.ssafy.dodam.domain.notification.event.NotificationEventService.EventRunResult;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 이벤트 리더의 실제 SQL을 seed가 적재된 MySQL(V1~V33, seed 1~9번)에서 검증한다. 일반 test는 건너뛴다.
 * seed 사용자 U0004(짝수: 대출·적금·구독 보유, 알림·브리핑·리포트 설정 모두 on)를 대상으로 규칙을 직접 실행하고 만든 알림을 정리한다.
 *
 * <p>실행: `PATTERN_MYSQL_TEST_URL=jdbc:mysql://127.0.0.1:<port>/dodam_516_test`, `PATTERN_MYSQL_TEST_PASSWORD`.
 */
@EnabledIfEnvironmentVariable(
        named = "PATTERN_MYSQL_TEST_URL",
        matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/dodam_516_test(?:\\?.*)?"
)
@ActiveProfiles("test")
@SpringBootTest
class NotificationEventReaderMySqlTest {

    /** 짝수 사용자(대출·적금·구독 보유)이면서 알림·브리핑·리포트 설정이 모두 켜진 seed 사용자(U0002는 리포트 off). */
    private static final String U0002 = "seed.kmeans.u0004@dodam.invalid";

    @Autowired JdbcTemplate jdbc;
    @Autowired JdbcNotificationEventReader reader;
    @Autowired NotificationEventService events;

    private Long userId;

    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("PATTERN_MYSQL_TEST_URL"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.datasource.username", () -> "root");
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("PATTERN_MYSQL_TEST_PASSWORD", ""));
        registry.add("spring.flyway.enabled", () -> "false");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("app.pattern.model-location", () -> "");
    }

    @AfterEach
    void tearDown() {
        if (userId != null) {
            jdbc.update("DELETE FROM notification WHERE user_id = ? AND (dedupe_key IS NULL OR dedupe_key NOT LIKE 'SEED-%')", userId);
        }
    }

    @Test
    void seed_데이터에서_모든_리더_SQL이_실행되고_규칙이_알림을_만든다() {
        userId = jdbc.queryForObject("SELECT user_id FROM `user` WHERE email = ?", Long.class, U0002);
        LocalDate today = LocalDate.of(2026, 9, 14);

        // 리더 SQL 전부 실행 가능(결과 유무는 seed 날짜에 따라 다르므로 예외 없이 돌아오는지만 본다)
        assertThat(reader.subscriptionsDue(userId, today, today.plusDays(365))).isNotNull();
        assertThat(reader.cardStatementsDue(userId, today.minusYears(1), today.plusDays(365))).isNotNull();
        assertThat(reader.loansDue(userId, today.minusYears(1), today.plusDays(365))).isNotEmpty();
        assertThat(reader.minusInterestDue(null, today.minusYears(1), today.plusDays(365))).isNotNull();
        assertThat(reader.cashflowRisks(null, today, today.plusDays(30))).isNotNull();
        assertThat(reader.savingGoals(userId)).isNotEmpty();
        assertThat(reader.recentRecommendations(null, today.minusYears(1).atStartOfDay())).isNotNull();
        assertThat(reader.monthlyReportUsers(userId)).hasSize(1);
        assertThat(reader.dailyBriefingUsers(userId)).hasSize(1);
        assertThat(reader.monthlyBudget(userId)).isGreaterThan(0);
        assertThat(reader.spending(userId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31))).isGreaterThan(0);

        int before = jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id = ?", Integer.class, userId);
        EventRunResult result = events.runAll(userId, LocalDateTime.of(2026, 9, 14, 8, 0));
        int after = jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id = ?", Integer.class, userId);

        // 직접 실행은 월간 리포트(8월)·브리핑을 항상 만들고, 목표는 seed 진행률에 따른다. dedupe라 두 번째 실행은 0.
        assertThat(result.created().get("monthlyReport")).isEqualTo(1);
        assertThat(result.created().get("dailyBriefing")).isEqualTo(1);
        assertThat(after - before).isEqualTo(result.total());
        assertThat(events.runAll(userId, LocalDateTime.of(2026, 9, 14, 9, 0)).total()).isZero();
    }
}
