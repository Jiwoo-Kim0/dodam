package com.ssafy.dodam.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ssafy.dodam.domain.notification.entity.Notification;
import com.ssafy.dodam.domain.notification.entity.NotificationSourceType;
import com.ssafy.dodam.domain.notification.entity.NotificationType;
import com.ssafy.dodam.domain.notification.repository.NotificationRepository;
import com.ssafy.dodam.domain.notification.service.NotificationClock;
import com.ssafy.dodam.domain.notification.service.NotificationPublisher;
import com.ssafy.dodam.domain.notification.service.NotificationPublisher.NotificationCommand;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
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
 * API 22 통합 테스트(H2). 알림은 명세에 생성 API가 없어 {@link NotificationPublisher}로 만든다.
 *
 * <p>기본 fixture(본인 5건, 타인 1건). 시각은 2026-09-14 10:00 기준 상대값이다.
 * <pre>
 * N1 PAYMENT_DUE   SUBSCRIPTION/SUB-1   created -5d    unread
 * N2 GOAL_PROGRESS GOAL/7               created -3d    read(-2d)
 * N3 CASHFLOW_RISK CASHFLOW_FORECAST/3  created -1d    unread
 * N4 MONTHLY_REPORT REPORT/2026-08      created -10h   unread
 * N5 SYSTEM        (원천 없음)           created  0     unread
 * O1 (타인) PAYMENT_DUE                 created -1d    unread
 * </pre>
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:notification519;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class NotificationApiTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 10, 0);

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired NotificationRepository notifications;
    @Autowired NotificationPublisher publisher;
    @MockitoBean NotificationClock clock;

    private Long owner;
    private Long other;
    private Long n1;
    private Long n2;
    private Long n3;
    private Long n4;
    private Long n5;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM notification");
        owner = newUser("본인");
        other = newUser("타인");
        n1 = publishAt(NOW.minusDays(5), owner, NotificationType.PAYMENT_DUE, "넷플릭스 결제 예정", "9월 25일 17,000원이 결제됩니다.",
                NotificationSourceType.SUBSCRIPTION, "SUB-1", "k-n1");
        n2 = publishAt(NOW.minusDays(3), owner, NotificationType.GOAL_PROGRESS, "목표 50% 달성", "여행 자금 목표의 절반을 모았어요.",
                NotificationSourceType.GOAL, "7", "k-n2");
        n3 = publishAt(NOW.minusDays(1), owner, NotificationType.CASHFLOW_RISK, "잔액 부족 위험", "9월 20일 카드 결제일에 잔액이 부족할 수 있어요.",
                NotificationSourceType.CASHFLOW_FORECAST, "3", "k-n3");
        n4 = publishAt(NOW.minusHours(10), owner, NotificationType.MONTHLY_REPORT, "8월 리포트 도착", "8월 금융 리포트가 준비됐어요.",
                NotificationSourceType.REPORT, "2026-08", "k-n4");
        n5 = publishAt(NOW, owner, NotificationType.SYSTEM, "환영합니다", "도담에 오신 것을 환영합니다.", null, null, null);
        publishAt(NOW.minusDays(1), other, NotificationType.PAYMENT_DUE, "타인 알림", "타인", NotificationSourceType.SUBSCRIPTION, "SUB-9", "k-o1");

        given(clock.now()).willReturn(NOW.minusDays(2));
        Notification read = notifications.findById(n2).orElseThrow();
        read.markRead(NOW.minusDays(2));
        notifications.saveAndFlush(read);
        given(clock.now()).willReturn(NOW);
    }

    // ---------- S15P21D105-519 ----------

    @Test
    void 목록은_본인_미삭제_알림을_최신순으로_주고_필터와_페이지를_적용한다() throws Exception {
        mvc.perform(get("/api/v1/notifications").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(5))
                .andExpect(jsonPath("$.data.content[0].notificationId").value(n5))
                .andExpect(jsonPath("$.data.content[1].notificationId").value(n4))
                .andExpect(jsonPath("$.data.content[4].notificationId").value(n1))
                .andExpect(jsonPath("$.data.content[0].isRead").value(false))
                .andExpect(jsonPath("$.data.content[0].sourceType").isEmpty())
                .andExpect(jsonPath("$.data.content[4].sourceType").value("SUBSCRIPTION"))
                .andExpect(jsonPath("$.data.content[4].sourceId").value("SUB-1"));

        mvc.perform(get("/api/v1/notifications?isRead=false").with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(4));
        mvc.perform(get("/api/v1/notifications?isRead=true").with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].notificationId").value(n2))
                .andExpect(jsonPath("$.data.content[0].readAt").isNotEmpty());
        mvc.perform(get("/api/v1/notifications?type=payment_due").with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].type").value("PAYMENT_DUE"));
        // 날짜 범위는 created_at의 날짜 기준으로 양끝을 포함한다: 9/11 ~ 9/13 → N2(9/11), N3(9/13)
        mvc.perform(get("/api/v1/notifications?fromDate=2026-09-11&toDate=2026-09-13").with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[0].notificationId").value(n3))
                .andExpect(jsonPath("$.data.content[1].notificationId").value(n2));
        mvc.perform(get("/api/v1/notifications?fromDate=2026-09-14").with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(2));
        mvc.perform(get("/api/v1/notifications?page=1&size=2").with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.content.length()").value(2))
                .andExpect(jsonPath("$.data.content[0].notificationId").value(n3))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andExpect(jsonPath("$.data.last").value(false));
    }

    @Test
    void 목록_파라미터_검증() throws Exception {
        for (String query : List.of("page=-1", "size=0", "size=101", "isRead=maybe", "fromDate=2026/09/01", "type=bad-type!")) {
            mvc.perform(get("/api/v1/notifications?" + query).with(user(owner.toString()))).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/v1/notifications?type=UNKNOWN_TYPE").with(user(owner.toString())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        mvc.perform(get("/api/v1/notifications?fromDate=2026-09-13&toDate=2026-09-11").with(user(owner.toString())))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void 상세와_미확인_수() throws Exception {
        mvc.perform(get("/api/v1/notifications/{id}", n1).with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.notificationId").value(n1))
                .andExpect(jsonPath("$.data.title").value("넷플릭스 결제 예정"))
                .andExpect(jsonPath("$.data.sourceType").value("SUBSCRIPTION"))
                .andExpect(jsonPath("$.data.isRead").value(false))
                .andExpect(jsonPath("$.data.readAt").isEmpty());
        mvc.perform(get("/api/v1/notifications/unread-count").with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.unreadCount").value(4));
        mvc.perform(get("/api/v1/notifications/unread-count").with(user(other.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    // ---------- S15P21D105-520 ----------

    @Test
    void 읽음_처리는_멱등이고_전체_읽음은_건수를_돌려준다() throws Exception {
        mvc.perform(patch("/api/v1/notifications/{id}/read", n1).with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isRead").value(true))
                .andExpect(jsonPath("$.data.readAt").value("2026-09-14T10:00:00"));
        given(clock.now()).willReturn(NOW.plusHours(1));
        mvc.perform(patch("/api/v1/notifications/{id}/read", n1).with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.readAt").value("2026-09-14T10:00:00"));
        mvc.perform(get("/api/v1/notifications/unread-count").with(user(owner.toString())))
                .andExpect(jsonPath("$.data.unreadCount").value(3));

        mvc.perform(patch("/api/v1/notifications/read-all").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updatedCount").value(3))
                .andExpect(jsonPath("$.data.unreadCount").value(0));
        mvc.perform(patch("/api/v1/notifications/read-all").with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.updatedCount").value(0));
        mvc.perform(get("/api/v1/notifications/{id}", n5).with(user(owner.toString())))
                .andExpect(jsonPath("$.data.isRead").value(true))
                .andExpect(jsonPath("$.data.readAt").value("2026-09-14T11:00:00"));
        // 타인 알림은 영향 없음
        mvc.perform(get("/api/v1/notifications/unread-count").with(user(other.toString())))
                .andExpect(jsonPath("$.data.unreadCount").value(1));
    }

    @Test
    void 삭제는_소프트_삭제이고_이후_모든_조회에서_빠진다() throws Exception {
        mvc.perform(delete("/api/v1/notifications/{id}", n3).with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(get("/api/v1/notifications/{id}", n3).with(user(owner.toString())))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mvc.perform(patch("/api/v1/notifications/{id}/read", n3).with(user(owner.toString()))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/notifications/{id}", n3).with(user(owner.toString()))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/notifications").with(user(owner.toString())))
                .andExpect(jsonPath("$.data.totalElements").value(4));
        mvc.perform(get("/api/v1/notifications/unread-count").with(user(owner.toString())))
                .andExpect(jsonPath("$.data.unreadCount").value(3));
        mvc.perform(patch("/api/v1/notifications/read-all").with(user(owner.toString())))
                .andExpect(jsonPath("$.data.updatedCount").value(3));

        Notification deleted = notifications.findById(n3).orElseThrow();
        assertThat(deleted.isDeleted()).isTrue();
        assertThat(deleted.getDeletedAt()).isEqualTo(NOW);
        assertThat(deleted.isRead()).isFalse();
    }

    @Test
    void 인증과_소유권을_강제한다() throws Exception {
        mvc.perform(get("/api/v1/notifications")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/notifications/unread-count")).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/notifications/read-all")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/notifications/{id}", n1)).andExpect(status().isUnauthorized());

        for (var request : List.of(get("/api/v1/notifications/{id}", n1), patch("/api/v1/notifications/{id}/read", n1),
                delete("/api/v1/notifications/{id}", n1))) {
            mvc.perform(request.with(user(other.toString()))).andExpect(status().isNotFound());
        }
        mvc.perform(get("/api/v1/notifications/{id}", 999_999).with(user(owner.toString()))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/notifications/{id}", 0).with(user(owner.toString()))).andExpect(status().isBadRequest());
        assertThat(notifications.findById(n1).orElseThrow().isRead()).isFalse();
    }

    @Test
    void 퍼블리셔는_dedupeKey로_중복을_막고_삭제된_알림을_되살리지_않는다() {
        String key = NotificationPublisher.dedupeKey("PAYMENT_DUE", "SUBSCRIPTION", "SUB-1", "2026-09-25");
        assertThat(key).hasSize(64).matches("[0-9a-f]+");
        assertThat(NotificationPublisher.dedupeKey("PAYMENT_DUE", "SUBSCRIPTION", "SUB-1", "2026-09-25")).isEqualTo(key);

        NotificationCommand command = new NotificationCommand(owner, NotificationType.PAYMENT_DUE, "결제 예정", "내용",
                NotificationSourceType.SUBSCRIPTION, "SUB-1", key);
        Notification first = publisher.publish(command);
        Notification again = publisher.publish(command);
        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(notifications.count()).isEqualTo(7);

        // 같은 키라도 다른 사용자는 별개
        assertThat(publisher.publish(new NotificationCommand(other, NotificationType.PAYMENT_DUE, "결제 예정", "내용",
                NotificationSourceType.SUBSCRIPTION, "SUB-1", key)).getId()).isNotEqualTo(first.getId());

        // 삭제된 뒤에도 되살리지 않는다
        Notification stored = notifications.findById(first.getId()).orElseThrow();
        stored.delete(NOW);
        notifications.saveAndFlush(stored);
        assertThat(publisher.publish(command).getId()).isEqualTo(first.getId());
        assertThat(notifications.findByIdAndUserIdAndDeletedAtIsNull(first.getId(), owner)).isEmpty();

        // 키가 없으면 매번 새로 만든다
        Notification a = publisher.publish(NotificationCommand.of(owner, NotificationType.SYSTEM, "공지", "내용"));
        Notification b = publisher.publish(NotificationCommand.of(owner, NotificationType.SYSTEM, "공지", "내용"));
        assertThat(a.getId()).isNotEqualTo(b.getId());

        assertThatThrownBy(() -> publisher.publish(new NotificationCommand(owner, NotificationType.SYSTEM, "", "내용", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> publisher.publish(new NotificationCommand(owner, NotificationType.SYSTEM, "제목", "내용",
                NotificationSourceType.GOAL, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---------- fixture ----------

    private Long publishAt(LocalDateTime at, Long userId, NotificationType type, String title, String message,
            NotificationSourceType sourceType, String sourceId, String dedupeKey) {
        given(clock.now()).willReturn(at);
        return publisher.publish(new NotificationCommand(userId, type, title, message, sourceType, sourceId, dedupeKey)).getId();
    }

    private Long newUser(String name) {
        return users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", name)).getId();
    }
}
