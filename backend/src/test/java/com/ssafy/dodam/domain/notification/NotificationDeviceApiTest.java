package com.ssafy.dodam.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.notification.entity.Notification;
import com.ssafy.dodam.domain.notification.entity.NotificationType;
import com.ssafy.dodam.domain.notification.entity.UserDevice;
import com.ssafy.dodam.domain.notification.push.NotificationPushSender;
import com.ssafy.dodam.domain.notification.push.NotificationPushSender.PushResult;
import com.ssafy.dodam.domain.notification.repository.UserDeviceRepository;
import com.ssafy.dodam.domain.notification.service.NotificationClock;
import com.ssafy.dodam.domain.notification.service.NotificationPublisher;
import com.ssafy.dodam.domain.notification.service.NotificationPublisher.NotificationCommand;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 기기 토큰 등록·해제(580)와 새 알림의 푸시 디스패치. FCM 대신 보낸 내용을 기록하는 발송기를 끼운다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:notification580;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class NotificationDeviceApiTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 10, 0);
    private static final String TOKEN = "ExponentPushToken[abcDEF123456]";

    @TestConfiguration
    static class RecordingSenderConfig {
        @Bean
        @Primary
        RecordingSender recordingSender() {
            return new RecordingSender();
        }
    }

    static class RecordingSender implements NotificationPushSender {
        final List<String> tokens = new ArrayList<>();
        final List<Long> notificationIds = new ArrayList<>();
        PushResult next = PushResult.sent("msg-1");

        @Override
        public PushResult send(String fcmToken, Notification notification) {
            tokens.add(fcmToken);
            notificationIds.add(notification.getId());
            return next;
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired UserDeviceRepository devices;
    @Autowired NotificationPublisher publisher;
    @Autowired RecordingSender sender;
    @MockitoBean NotificationClock clock;

    private Long owner;
    private Long other;

    @BeforeEach
    void setUp() {
        given(clock.now()).willReturn(NOW);
        jdbc.update("DELETE FROM notification");
        jdbc.update("DELETE FROM user_device");
        jdbc.update("DELETE FROM user_setting");
        sender.tokens.clear();
        sender.notificationIds.clear();
        sender.next = PushResult.sent("msg-1");
        owner = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "본인")).getId();
        other = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "타인")).getId();
    }

    @Test
    void 기기_등록은_토큰을_암호화해_저장하고_같은_토큰_재등록은_같은_기기다() throws Exception {
        String body = mvc.perform(post("/api/v1/notifications/devices").with(user(owner.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("fcmToken", TOKEN, "platform", "android"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.platform").value("ANDROID"))
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.fcmToken").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        long deviceId = json.readTree(body).at("/data/deviceId").asLong();

        UserDevice stored = devices.findById(deviceId).orElseThrow();
        assertThat(new String(stored.getFcmTokenEncrypted())).doesNotContain("ExponentPushToken");
        assertThat(stored.getFcmTokenHash()).hasSize(64);

        given(clock.now()).willReturn(NOW.plusHours(1));
        mvc.perform(post("/api/v1/notifications/devices").with(user(owner.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("fcmToken", TOKEN, "platform", "IOS"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.deviceId").value(deviceId))
                .andExpect(jsonPath("$.data.platform").value("IOS"))
                .andExpect(jsonPath("$.data.lastUsedAt").value("2026-09-14T11:00:00"));
        mvc.perform(get("/api/v1/notifications/devices").with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));

        // 다른 사용자가 같은 기기로 로그인해 등록하면 소유자가 바뀐다.
        mvc.perform(post("/api/v1/notifications/devices").with(user(other.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("fcmToken", TOKEN, "platform", "ANDROID"))))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.deviceId").value(deviceId));
        mvc.perform(get("/api/v1/notifications/devices").with(user(owner.toString())))
                .andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/v1/notifications/devices").with(user(other.toString())))
                .andExpect(jsonPath("$.data.length()").value(1));

        mvc.perform(delete("/api/v1/notifications/devices/{id}", deviceId).with(user(owner.toString()))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/notifications/devices/{id}", deviceId).with(user(other.toString()))).andExpect(status().isOk());
        assertThat(devices.findById(deviceId)).isEmpty();

        mvc.perform(post("/api/v1/notifications/devices").with(user(owner.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("fcmToken", " ", "platform", "ANDROID"))))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/notifications/devices").with(user(owner.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("fcmToken", TOKEN, "platform", "TV"))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(post("/api/v1/notifications/devices").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("fcmToken", TOKEN, "platform", "ANDROID")))).andExpect(status().isUnauthorized());
    }

    @Test
    void 새_알림은_활성_기기에_푸시되고_토큰_무효면_기기를_해제한다() throws Exception {
        register(owner, TOKEN, "ANDROID");
        register(owner, "second-device-token", "IOS");
        register(other, "other-token", "ANDROID");

        Notification created = publisher.publish(NotificationCommand.of(owner, NotificationType.SYSTEM, "푸시", "내용"));
        assertThat(sender.tokens).containsExactlyInAnyOrder(TOKEN, "second-device-token");
        assertThat(sender.notificationIds).containsOnly(created.getId());

        // dedupe로 기존 알림을 돌려준 경우에는 다시 보내지 않는다.
        sender.tokens.clear();
        String key = NotificationPublisher.dedupeKey("SYSTEM", "dup");
        publisher.publish(new NotificationCommand(owner, NotificationType.SYSTEM, "중복", "내용", null, null, key));
        publisher.publish(new NotificationCommand(owner, NotificationType.SYSTEM, "중복", "내용", null, null, key));
        assertThat(sender.tokens).hasSize(2);

        // 토큰 무효 응답 → 두 기기 모두 해제, 이후 발송 대상 없음
        sender.tokens.clear();
        sender.next = PushResult.invalidToken("UNREGISTERED");
        publisher.publish(NotificationCommand.of(owner, NotificationType.SYSTEM, "무효", "내용"));
        assertThat(sender.tokens).hasSize(2);
        assertThat(devices.findAllByUserIdAndEnabledTrue(owner)).isEmpty();
        assertThat(devices.findAllByUserIdOrderByIdAsc(owner)).hasSize(2).allMatch(d -> !d.isEnabled());
        sender.tokens.clear();
        publisher.publish(NotificationCommand.of(owner, NotificationType.SYSTEM, "이후", "내용"));
        assertThat(sender.tokens).isEmpty();

        // 재등록하면 다시 켜진다.
        register(owner, TOKEN, "ANDROID");
        assertThat(devices.findAllByUserIdAndEnabledTrue(owner)).hasSize(1);
    }

    @Test
    void 사용자_설정이_꺼져_있으면_알림함에는_남고_푸시는_보내지_않는다() throws Exception {
        register(owner, TOKEN, "ANDROID");
        jdbc.update("""
                INSERT INTO user_setting (user_id, notification_enabled, daily_briefing_enabled, monthly_report_enabled, daily_briefing_time, timezone, updated_at)
                VALUES (?, TRUE, FALSE, TRUE, '08:00:00', 'Asia/Seoul', ?)
                """, owner, Timestamp.valueOf(NOW));

        publisher.publish(NotificationCommand.of(owner, NotificationType.DAILY_BRIEFING, "브리핑", "내용"));
        assertThat(sender.tokens).isEmpty();
        publisher.publish(NotificationCommand.of(owner, NotificationType.MONTHLY_REPORT, "리포트", "내용"));
        assertThat(sender.tokens).hasSize(1);

        jdbc.update("UPDATE user_setting SET notification_enabled = FALSE WHERE user_id = ?", owner);
        sender.tokens.clear();
        Notification stored = publisher.publish(NotificationCommand.of(owner, NotificationType.PAYMENT_DUE, "결제", "내용"));
        assertThat(sender.tokens).isEmpty();
        mvc.perform(get("/api/v1/notifications/{id}", stored.getId()).with(user(owner.toString()))).andExpect(status().isOk());
    }

    private void register(Long userId, String token, String platform) throws Exception {
        mvc.perform(post("/api/v1/notifications/devices").with(user(userId.toString()))
                        .contentType(MediaType.APPLICATION_JSON).content(json(Map.of("fcmToken", token, "platform", platform))))
                .andExpect(status().isCreated());
    }

    private String json(Object value) throws Exception {
        return json.writeValueAsString(value);
    }
}
