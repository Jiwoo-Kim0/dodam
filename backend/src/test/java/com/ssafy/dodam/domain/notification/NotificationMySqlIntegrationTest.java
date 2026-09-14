package com.ssafy.dodam.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ssafy.dodam.domain.notification.entity.Notification;
import com.ssafy.dodam.domain.notification.entity.NotificationSourceType;
import com.ssafy.dodam.domain.notification.entity.NotificationType;
import com.ssafy.dodam.domain.notification.repository.NotificationRepository;
import com.ssafy.dodam.domain.notification.service.NotificationCommandService;
import com.ssafy.dodam.domain.notification.service.NotificationPublisher;
import com.ssafy.dodam.domain.notification.service.NotificationPublisher.NotificationCommand;
import com.ssafy.dodam.domain.notification.service.NotificationQueryService;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 로컬 일회용 MySQL에서만 실행한다(일반 test는 건너뜀). H2가 검증하지 못하는 것: V32 DDL과 엔티티 매핑(`TEXT`, `CHAR(64)`, `TINYINT(1)`)의
 * `validate` 통과, `(user_id, dedupe_key)` 유일 제약이 DB 단계에서 살아 있는가, 벌크 UPDATE(전체 읽음)가 MySQL에서 건수를 맞게 돌려주는가.
 *
 * <p>실행 방법: V1~V32가 적용된 `dodam_516_test` 스키마의 MySQL 8.0을 띄우고 `PATTERN_MYSQL_TEST_URL`·`PATTERN_MYSQL_TEST_PASSWORD`를 설정한다
 * (Pattern MySQL 테스트와 같은 DB를 공유한다).
 */
@EnabledIfEnvironmentVariable(
        named = "PATTERN_MYSQL_TEST_URL",
        matches = "jdbc:mysql://127\\.0\\.0\\.1:[0-9]+/dodam_516_test(?:\\?.*)?"
)
@ActiveProfiles("test")
@SpringBootTest
class NotificationMySqlIntegrationTest {

    private static final long USER_ID = 519_001L;
    private static final long OTHER_ID = 519_002L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 12, 0);

    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationRepository notifications;
    @Autowired NotificationPublisher publisher;
    @Autowired NotificationQueryService query;
    @Autowired NotificationCommandService commands;

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

    @BeforeEach
    void setUp() {
        cleanUp();
        for (long id : new long[] {USER_ID, OTHER_ID}) {
            jdbc.update("INSERT INTO `user` (user_id, email, password_hash, name, role, created_at, updated_at) VALUES (?, ?, 'unused', ?, 'USER', ?, ?)",
                    id, "notification519-" + id + "@example.com", "알림 " + id, Timestamp.valueOf(NOW), Timestamp.valueOf(NOW));
        }
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    void 실제_스키마에서_저장_유일제약_벌크읽음을_검증한다() {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'notification'
                  AND ((column_name = 'message' AND data_type = 'text') OR (column_name = 'dedupe_key' AND column_type = 'char(64)'))
                """, Integer.class)).isEqualTo(2);

        String key = NotificationPublisher.dedupeKey("PAYMENT_DUE", "SUBSCRIPTION", "SUB-1", "2026-09-25");
        Notification first = publisher.publish(new NotificationCommand(USER_ID, NotificationType.PAYMENT_DUE, "결제 예정",
                "긴 내용 ".repeat(200), NotificationSourceType.SUBSCRIPTION, "SUB-1", key));
        assertThat(publisher.publish(new NotificationCommand(USER_ID, NotificationType.PAYMENT_DUE, "결제 예정", "내용",
                NotificationSourceType.SUBSCRIPTION, "SUB-1", key)).getId()).isEqualTo(first.getId());
        publisher.publish(NotificationCommand.of(USER_ID, NotificationType.SYSTEM, "공지", "내용"));
        publisher.publish(NotificationCommand.of(OTHER_ID, NotificationType.SYSTEM, "타인", "내용"));

        // 유일 제약이 DB 단계에서 살아 있다(퍼블리셔를 우회한 직접 INSERT).
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO notification (user_id, type, title, message, dedupe_key, is_read, created_at)
                VALUES (?, 'PAYMENT_DUE', '중복', '중복', ?, 0, ?)
                """, USER_ID, key, Timestamp.valueOf(NOW))).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(query.getUnreadCount(USER_ID).unreadCount()).isEqualTo(2);
        assertThat(query.getNotifications(USER_ID, "PAYMENT_DUE", null, null, null, 0, 20).totalElements()).isEqualTo(1);
        assertThat(commands.markAllRead(USER_ID).updatedCount()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM notification WHERE user_id = ? AND is_read = 1", Integer.class, USER_ID)).isEqualTo(2);
        assertThat(query.getUnreadCount(OTHER_ID).unreadCount()).isEqualTo(1);

        commands.delete(USER_ID, first.getId());
        assertThat(jdbc.queryForObject("SELECT deleted_at IS NOT NULL FROM notification WHERE notification_id = ?", Boolean.class, first.getId()))
                .isTrue();
        assertThat(notifications.findByIdAndUserIdAndDeletedAtIsNull(first.getId(), USER_ID)).isEmpty();
    }

    private void cleanUp() {
        jdbc.update("DELETE FROM notification WHERE user_id IN (?, ?)", USER_ID, OTHER_ID);
        jdbc.update("DELETE FROM `user` WHERE user_id IN (?, ?)", USER_ID, OTHER_ID);
    }
}
