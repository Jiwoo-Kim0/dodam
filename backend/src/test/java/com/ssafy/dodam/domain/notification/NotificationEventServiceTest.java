package com.ssafy.dodam.domain.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ssafy.dodam.domain.notification.entity.Notification;
import com.ssafy.dodam.domain.notification.event.JdbcNotificationEventReader;
import com.ssafy.dodam.domain.notification.event.JdbcNotificationEventReader.CashflowRiskRow;
import com.ssafy.dodam.domain.notification.event.JdbcNotificationEventReader.DueCardStatement;
import com.ssafy.dodam.domain.notification.event.JdbcNotificationEventReader.DueLoan;
import com.ssafy.dodam.domain.notification.event.JdbcNotificationEventReader.DueMinusAccount;
import com.ssafy.dodam.domain.notification.event.JdbcNotificationEventReader.DueSubscription;
import com.ssafy.dodam.domain.notification.event.JdbcNotificationEventReader.RecommendationRow;
import com.ssafy.dodam.domain.notification.event.JdbcNotificationEventReader.SavingGoalRow;
import com.ssafy.dodam.domain.notification.event.JdbcNotificationEventReader.SettingRow;
import com.ssafy.dodam.domain.notification.event.NotificationEventService;
import com.ssafy.dodam.domain.notification.event.NotificationEventService.EventRunResult;
import com.ssafy.dodam.domain.notification.repository.NotificationRepository;
import com.ssafy.dodam.domain.notification.service.NotificationClock;
import com.ssafy.dodam.domain.user.entity.User;
import com.ssafy.dodam.domain.user.repository.UserRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
 * 이벤트 규칙(581)의 판정·문구·dedupe·설정 조건. 다른 도메인 테이블 조회는 {@link JdbcNotificationEventReader}를 mock으로 대체하고,
 * 실제 SQL은 {@code NotificationEventReaderMySqlTest}(MySQL 게이트)가 seed로 확인한다.
 */
@ActiveProfiles("test")
@AutoConfigureMockMvc
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:notification581;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
class NotificationEventServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 14, 8, 0);
    private static final LocalDate TODAY = NOW.toLocalDate();

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired NotificationRepository notifications;
    @Autowired NotificationEventService events;
    @MockitoBean JdbcNotificationEventReader reader;
    @MockitoBean NotificationClock clock;

    private Long owner;

    @BeforeEach
    void setUp() {
        given(clock.now()).willReturn(NOW);
        jdbc.update("DELETE FROM notification");
        owner = users.save(User.createLocalUser(UUID.randomUUID() + "@example.com", "unused", "본인")).getId();
        given(reader.subscriptionsDue(any(), any(), any())).willReturn(List.of());
        given(reader.cardStatementsDue(any(), any(), any())).willReturn(List.of());
        given(reader.loansDue(any(), any(), any())).willReturn(List.of());
        given(reader.minusInterestDue(any(), any(), any())).willReturn(List.of());
        given(reader.cashflowRisks(any(), any(), any())).willReturn(List.of());
        given(reader.savingGoals(any())).willReturn(List.of());
        given(reader.recentRecommendations(any(), any())).willReturn(List.of());
        given(reader.monthlyReportUsers(any())).willReturn(List.of());
        given(reader.dailyBriefingUsers(any())).willReturn(List.of());
        given(reader.monthlyBudget(anyLong())).willReturn(0L);
        given(reader.spending(anyLong(), any(), any())).willReturn(0L);
    }

    @Test
    void 결제_상환_예정과_위험_추천은_원천을_가리키는_알림이_되고_다시_돌려도_늘지_않는다() {
        given(reader.subscriptionsDue(eq(owner), eq(TODAY), eq(TODAY.plusDays(3))))
                .willReturn(List.of(new DueSubscription(owner, "SUB-1", "넷플릭스", 17_000, TODAY.plusDays(2))));
        given(reader.cardStatementsDue(eq(owner), eq(TODAY), eq(TODAY.plusDays(3))))
                .willReturn(List.of(new DueCardStatement(owner, 11L, "도담 체크", "2026-08", 452_300, TODAY)));
        given(reader.loansDue(eq(owner), eq(TODAY), eq(TODAY.plusDays(3)))).willReturn(List.of(
                new DueLoan(owner, 5L, "신용대출", 12_000_000, "ACTIVE", TODAY.plusDays(1)),
                new DueLoan(owner, 6L, "마이너스 전환대출", 3_000_000, "OVERDUE", TODAY.plusDays(3))));
        given(reader.minusInterestDue(eq(owner), eq(TODAY), eq(TODAY.plusDays(3))))
                .willReturn(List.of(new DueMinusAccount(owner, 9L, "마이너스통장", 25_400, 1_500_000, TODAY.plusDays(3))));
        given(reader.cashflowRisks(eq(owner), eq(TODAY), eq(TODAY.plusDays(7)))).willReturn(List.of(
                new CashflowRiskRow(owner, 100L, 1L, "NEGATIVE_BALANCE", "CRITICAL", null, TODAY.plusDays(5), -120_000L),
                new CashflowRiskRow(owner, 100L, 2L, "LOW_BALANCE", "HIGH", "잔액이 최소 기준 아래로 내려갑니다.", TODAY.plusDays(6), 50_000L)));
        given(reader.recentRecommendations(eq(owner), eq(NOW.minusDays(7))))
                .willReturn(List.of(new RecommendationRow(owner, 77L, "구독 2개 정리", "SPENDING", "SUBSCRIPTION_CANCEL", 21_800L)));

        EventRunResult first = events.runAll(owner, NOW);

        assertThat(first.created()).containsEntry("paymentDue", 2).containsEntry("repaymentDue", 3).containsEntry("cashflowRisk", 2)
                .containsEntry("recommendation", 1).containsEntry("goal", 0);
        assertThat(first.total()).isEqualTo(8);
        List<Notification> all = notifications.findAll();
        assertThat(all).hasSize(8);
        assertThat(all).filteredOn(n -> n.getType().equals("PAYMENT_DUE")).extracting(Notification::getSourceType, Notification::getSourceId)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("SUBSCRIPTION", "SUB-1"), org.assertj.core.groups.Tuple.tuple("CARD", "11"));
        Notification netflix = all.stream().filter(n -> "SUB-1".equals(n.getSourceId())).findFirst().orElseThrow();
        assertThat(netflix.getTitle()).isEqualTo("넷플릭스 결제 예정");
        assertThat(netflix.getMessage()).startsWith("2일 후(9월 16일) 17,000원이 결제될 예정입니다.");
        assertThat(all).filteredOn(n -> "6".equals(n.getSourceId())).extracting(Notification::getType).containsExactly("OVERDUE_RISK");
        assertThat(all).filteredOn(n -> "9".equals(n.getSourceId())).extracting(Notification::getSourceType).containsExactly("MINUS_ACCOUNT");
        assertThat(all).filteredOn(n -> n.getType().equals("CASHFLOW_RISK")).extracting(Notification::getTitle)
                .containsExactlyInAnyOrder("9월 19일 잔액 부족 위험 (긴급)", "9월 20일 잔액 부족 위험");
        assertThat(all).filteredOn(n -> n.getType().equals("RECOMMENDATION")).extracting(Notification::getMessage)
                .allMatch(m -> m.contains("월 21,800원"));

        EventRunResult second = events.runAll(owner, NOW.plusHours(2));
        assertThat(second.total()).isZero();
        assertThat(notifications.count()).isEqualTo(8);
    }

    @Test
    void 목표는_구간별로_한_번씩_알리고_달성하면_달성_알림만_남긴다() {
        SavingGoalRow half = new SavingGoalRow(owner, 1L, "여행 자금", "ACTIVE", 2_600_000, 5_000_000);
        given(reader.savingGoals(owner)).willReturn(List.of(half));
        assertThat(events.runAll(owner, NOW).created()).containsEntry("goal", 2); // 25%, 50%

        given(reader.savingGoals(owner)).willReturn(List.of(new SavingGoalRow(owner, 1L, "여행 자금", "ACTIVE", 3_800_000, 5_000_000)));
        assertThat(events.runAll(owner, NOW).created()).containsEntry("goal", 1); // 75%만 추가

        given(reader.savingGoals(owner)).willReturn(List.of(new SavingGoalRow(owner, 1L, "여행 자금", "ACHIEVED", 5_000_000, 5_000_000)));
        assertThat(events.runAll(owner, NOW).created()).containsEntry("goal", 1); // 달성
        assertThat(events.runAll(owner, NOW).created()).containsEntry("goal", 0);

        assertThat(notifications.findAll()).extracting(Notification::getType)
                .containsExactlyInAnyOrder("GOAL_PROGRESS", "GOAL_PROGRESS", "GOAL_PROGRESS", "GOAL_ACHIEVED");
        assertThat(notifications.findAll()).filteredOn(n -> n.getType().equals("GOAL_ACHIEVED")).extracting(Notification::getTitle)
                .containsExactly("'여행 자금' 목표를 달성했어요!");
    }

    @Test
    void 월간_리포트는_스케줄에서는_월초에만_직접_실행은_항상_만들고_브리핑은_설정_시각에만_만든다() {
        SettingRow setting = new SettingRow(owner, "도담", LocalTime.of(9, 0), "Asia/Seoul");
        given(reader.monthlyReportUsers(any())).willReturn(List.of(setting));
        given(reader.dailyBriefingUsers(any())).willReturn(List.of(setting));
        given(reader.monthlyBudget(owner)).willReturn(900_000L);
        given(reader.spending(eq(owner), eq(TODAY.withDayOfMonth(1)), eq(TODAY))).willReturn(390_000L);
        given(reader.subscriptionsDue(eq(owner), eq(TODAY), eq(TODAY)))
                .willReturn(List.of(new DueSubscription(owner, "SUB-1", "넷플릭스", 17_000, TODAY)));

        // 스케줄(전체 사용자) 실행: 14일이라 리포트 없음, 08시는 설정 09시와 달라 브리핑 없음
        EventRunResult scheduled = events.runAll(null, NOW);
        assertThat(scheduled.created()).containsEntry("monthlyReport", 0).containsEntry("dailyBriefing", 0);
        // 09시 정시 브리핑 스케줄
        assertThat(events.dailyBriefing(null, NOW.withHour(9), false)).isEqualTo(1);
        Notification briefing = notifications.findAll().get(0);
        assertThat(briefing.getType()).isEqualTo("DAILY_BRIEFING");
        // 9월 14일 기준 남은 날 17일: (900,000 − 390,000) / 17 = 30,000
        assertThat(briefing.getMessage()).contains("오늘 사용 가능 금액은 약 30,000원").contains("오늘 예정: 넷플릭스 17,000원 결제.");
        assertThat(events.dailyBriefing(null, NOW.withHour(9).plusMinutes(30), false)).isZero();

        // 직접 실행: 리포트(지난달 8월)와 브리핑을 시각 조건 없이 만든다(브리핑은 이미 있어 0)
        EventRunResult manual = events.runAll(owner, NOW);
        assertThat(manual.created()).containsEntry("monthlyReport", 1).containsEntry("dailyBriefing", 0);
        assertThat(notifications.findAll()).filteredOn(n -> n.getType().equals("MONTHLY_REPORT"))
                .extracting(Notification::getTitle, Notification::getSourceType, Notification::getSourceId)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("8월 금융 리포트가 도착했어요", "REPORT", "2026-08"));

        // 월초 스케줄은 리포트를 만든다(이미 있으면 0)
        assertThat(events.runAll(null, LocalDateTime.of(2026, 10, 1, 8, 0)).created()).containsEntry("monthlyReport", 1);
    }

    @Test
    void 이벤트_실행_API는_본인만_대상으로_결과를_돌려준다() throws Exception {
        given(reader.subscriptionsDue(eq(owner), eq(TODAY), eq(TODAY.plusDays(3))))
                .willReturn(List.of(new DueSubscription(owner, "SUB-1", "넷플릭스", 17_000, TODAY.plusDays(2))));
        mvc.perform(post("/api/v1/notifications/events/run").with(user(owner.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.date").value("2026-09-14"))
                .andExpect(jsonPath("$.data.created.paymentDue").value(1))
                .andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(post("/api/v1/notifications/events/run").with(user(owner.toString())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(post("/api/v1/notifications/events/run")).andExpect(status().isUnauthorized());
    }
}
