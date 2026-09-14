package com.ssafy.dodam.domain.notification.event;

import com.ssafy.dodam.domain.notification.entity.NotificationSourceType;
import com.ssafy.dodam.domain.notification.entity.NotificationType;
import com.ssafy.dodam.domain.notification.event.JdbcNotificationEventReader.CashflowRiskRow;
import com.ssafy.dodam.domain.notification.event.JdbcNotificationEventReader.DueCardStatement;
import com.ssafy.dodam.domain.notification.event.JdbcNotificationEventReader.DueLoan;
import com.ssafy.dodam.domain.notification.event.JdbcNotificationEventReader.DueMinusAccount;
import com.ssafy.dodam.domain.notification.event.JdbcNotificationEventReader.DueSubscription;
import com.ssafy.dodam.domain.notification.event.JdbcNotificationEventReader.RecommendationRow;
import com.ssafy.dodam.domain.notification.event.JdbcNotificationEventReader.SavingGoalRow;
import com.ssafy.dodam.domain.notification.event.JdbcNotificationEventReader.SettingRow;
import com.ssafy.dodam.domain.notification.service.NotificationClock;
import com.ssafy.dodam.domain.notification.service.NotificationProperties;
import com.ssafy.dodam.domain.notification.service.NotificationPublisher;
import com.ssafy.dodam.domain.notification.service.NotificationPublisher.NotificationCommand;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 알림 이벤트 규칙(S15P21D105-581). 다른 도메인의 저장 상태를 읽어 알림으로 바꾼다. 생성은 전부 {@link NotificationPublisher}를 거치고
 * dedupe 키로 멱등이라 하루에 몇 번 돌아도 같은 알림이 두 번 생기지 않는다.
 *
 * <table>
 *   <tr><th>규칙</th><th>대상</th><th>dedupe 키</th></tr>
 *   <tr><td>PAYMENT_DUE</td><td>ACTIVE 정기결제 다음 결제일, SCHEDULED 카드 청구 출금일이 오늘~D+{dueDaysAhead}</td><td>유형·원천·ID·날짜</td></tr>
 *   <tr><td>REPAYMENT_DUE / OVERDUE_RISK</td><td>대출 다음 상환일(OVERDUE 상태면 OVERDUE_RISK), 마이너스통장 이자 납부일</td><td>유형·원천·ID·날짜</td></tr>
 *   <tr><td>CASHFLOW_RISK</td><td>최신 완료 예측의 HIGH·CRITICAL 위험(위험일 오늘~D+{riskDaysAhead})</td><td>예측 ID·위험 ID</td></tr>
 *   <tr><td>GOAL_PROGRESS / GOAL_ACHIEVED</td><td>모으기 목표 진행률 25·50·75% 도달, 100% 또는 ACHIEVED</td><td>목표 ID·구간</td></tr>
 *   <tr><td>RECOMMENDATION</td><td>최근 {recommendationDaysBack}일 안 생성된 PENDING 개선안</td><td>개선안 ID</td></tr>
 *   <tr><td>MONTHLY_REPORT</td><td>월간 리포트 설정 사용자, 매월 1~3일(직접 실행은 항상)</td><td>지난달 yyyy-MM</td></tr>
 *   <tr><td>DAILY_BRIEFING</td><td>브리핑 설정 사용자, 설정 시각(기본 08:00, 사용자 시간대)의 정시</td><td>날짜</td></tr>
 * </table>
 *
 * 사용자 설정 `notification_enabled`가 꺼진 사용자는 리포트·브리핑 대상에서 빠진다(다른 규칙의 알림함 저장은 유지, 푸시만 차단).
 */
@Service
public class NotificationEventService {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventService.class);
    private static final DateTimeFormatter KOREAN_DATE = DateTimeFormatter.ofPattern("M월 d일", Locale.KOREAN);
    private static final LocalTime DEFAULT_BRIEFING_TIME = LocalTime.of(8, 0);
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Seoul");
    private static final int[] GOAL_MILESTONES = {25, 50, 75};

    private final JdbcNotificationEventReader reader;
    private final NotificationPublisher publisher;
    private final NotificationProperties properties;
    private final NotificationClock clock;

    public NotificationEventService(JdbcNotificationEventReader reader, NotificationPublisher publisher,
            NotificationProperties properties, NotificationClock clock) {
        this.reader = reader;
        this.publisher = publisher;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * 일일 규칙 전부. `userId`가 null이면 전체 사용자(스케줄), 아니면 그 사용자만(직접 실행).
     * 직접 실행은 월간 리포트·브리핑도 날짜·시각 조건 없이 만든다(시연·확인용).
     */
    public EventRunResult runAll(Long userId, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        boolean manual = userId != null;
        Map<String, Integer> created = new LinkedHashMap<>();
        created.put("paymentDue", paymentDue(userId, today));
        created.put("repaymentDue", repaymentDue(userId, today));
        created.put("cashflowRisk", cashflowRisk(userId, today));
        created.put("goal", goalProgress(userId));
        created.put("recommendation", recommendation(userId, now));
        created.put("monthlyReport", monthlyReport(userId, today, manual));
        created.put("dailyBriefing", dailyBriefing(userId, now, manual));
        int total = created.values().stream().mapToInt(Integer::intValue).sum();
        log.info("Notification events run: scope={} today={} created={} detail={}", manual ? "user " + userId : "all users", today, total, created);
        return new EventRunResult(today, created, total);
    }

    // ---------- 규칙 ----------

    int paymentDue(Long userId, LocalDate today) {
        LocalDate to = today.plusDays(properties.events().dueDaysAhead());
        int created = 0;
        for (DueSubscription due : reader.subscriptionsDue(userId, today, to)) {
            created += publish(new NotificationCommand(due.userId(), NotificationType.PAYMENT_DUE,
                    due.name() + " 결제 예정",
                    relative(due.paymentDate(), today) + "(" + KOREAN_DATE.format(due.paymentDate()) + ") " + won(due.amount()) + "이 결제될 예정입니다. 출금 계좌 잔액을 확인해 주세요.",
                    NotificationSourceType.SUBSCRIPTION, due.subscriptionId(),
                    key("PAYMENT_DUE", "SUBSCRIPTION", due.subscriptionId(), due.paymentDate())));
        }
        for (DueCardStatement due : reader.cardStatementsDue(userId, today, to)) {
            created += publish(new NotificationCommand(due.userId(), NotificationType.PAYMENT_DUE,
                    due.cardName() + " 카드 결제일 안내",
                    relative(due.paymentDate(), today) + "(" + KOREAN_DATE.format(due.paymentDate()) + ") " + due.billingMonth() + " 이용대금 " + won(due.totalBalance()) + "이 출금될 예정입니다.",
                    NotificationSourceType.CARD, String.valueOf(due.cardId()),
                    key("PAYMENT_DUE", "CARD", due.cardId(), due.paymentDate())));
        }
        return created;
    }

    int repaymentDue(Long userId, LocalDate today) {
        LocalDate to = today.plusDays(properties.events().dueDaysAhead());
        int created = 0;
        for (DueLoan due : reader.loansDue(userId, today, to)) {
            boolean overdue = "OVERDUE".equals(due.status());
            NotificationType type = overdue ? NotificationType.OVERDUE_RISK : NotificationType.REPAYMENT_DUE;
            String title = overdue ? due.accountName() + " 연체 확인 필요" : due.accountName() + " 상환일 안내";
            String message = overdue
                    ? "연체 상태인 대출입니다. " + KOREAN_DATE.format(due.repaymentDate()) + " 상환 예정이며 남은 원금은 " + won(due.balance()) + "입니다."
                    : relative(due.repaymentDate(), today) + "(" + KOREAN_DATE.format(due.repaymentDate()) + ")이 상환일입니다. 남은 원금은 " + won(due.balance()) + "입니다.";
            created += publish(new NotificationCommand(due.userId(), type, title, message, NotificationSourceType.LOAN,
                    String.valueOf(due.loanId()), key(type.name(), "LOAN", due.loanId(), due.repaymentDate())));
        }
        for (DueMinusAccount due : reader.minusInterestDue(userId, today, to)) {
            created += publish(new NotificationCommand(due.userId(), NotificationType.REPAYMENT_DUE,
                    due.accountName() + " 이자 납부 예정",
                    relative(due.interestDate(), today) + "(" + KOREAN_DATE.format(due.interestDate()) + ") 이자 " + won(due.accruedInterest()) + "이 출금될 예정입니다. 사용 중인 한도는 " + won(due.usedAmount()) + "입니다.",
                    NotificationSourceType.MINUS_ACCOUNT, String.valueOf(due.accountId()),
                    key("REPAYMENT_DUE", "MINUS_ACCOUNT", due.accountId(), due.interestDate())));
        }
        return created;
    }

    int cashflowRisk(Long userId, LocalDate today) {
        LocalDate to = today.plusDays(properties.events().riskDaysAhead());
        int created = 0;
        for (CashflowRiskRow risk : reader.cashflowRisks(userId, today, to)) {
            String when = risk.riskDate() == null ? "가까운 시일 내" : KOREAN_DATE.format(risk.riskDate());
            String message = risk.message() != null && !risk.message().isBlank() ? risk.message()
                    : when + " 예정된 출금을 반영하면 잔액이 부족해질 수 있어요.";
            if (risk.expectedBalance() != null) {
                message += " 예상 잔액 " + won(risk.expectedBalance()) + ".";
            }
            created += publish(new NotificationCommand(risk.userId(), NotificationType.CASHFLOW_RISK,
                    when + " 잔액 부족 위험" + ("CRITICAL".equals(risk.severity()) ? " (긴급)" : ""), message,
                    NotificationSourceType.CASHFLOW_FORECAST, String.valueOf(risk.forecastId()),
                    key("CASHFLOW_RISK", "CASHFLOW_FORECAST", risk.forecastId(), risk.riskId())));
        }
        return created;
    }

    int goalProgress(Long userId) {
        int created = 0;
        for (SavingGoalRow goal : reader.savingGoals(userId)) {
            if (goal.targetAmount() <= 0) {
                continue;
            }
            long progress = Math.min(100, goal.currentAmount() * 100 / goal.targetAmount());
            boolean achieved = "ACHIEVED".equals(goal.status()) || progress >= 100;
            for (int milestone : GOAL_MILESTONES) {
                if (progress >= milestone && !achieved) {
                    created += publish(new NotificationCommand(goal.userId(), NotificationType.GOAL_PROGRESS,
                            "'" + goal.name() + "' 목표 " + milestone + "% 달성",
                            "지금까지 " + won(goal.currentAmount()) + "을 모았어요. 목표 " + won(goal.targetAmount()) + "까지 "
                                    + won(Math.max(goal.targetAmount() - goal.currentAmount(), 0)) + " 남았습니다.",
                            NotificationSourceType.GOAL, String.valueOf(goal.goalId()),
                            key("GOAL_PROGRESS", "GOAL", goal.goalId(), milestone)));
                }
            }
            if (achieved) {
                created += publish(new NotificationCommand(goal.userId(), NotificationType.GOAL_ACHIEVED,
                        "'" + goal.name() + "' 목표를 달성했어요!",
                        "목표 금액 " + won(goal.targetAmount()) + "을 모두 모았습니다. 다음 목표를 세워 볼까요?",
                        NotificationSourceType.GOAL, String.valueOf(goal.goalId()),
                        key("GOAL_ACHIEVED", "GOAL", goal.goalId())));
            }
        }
        return created;
    }

    int recommendation(Long userId, LocalDateTime now) {
        int created = 0;
        for (RecommendationRow row : reader.recentRecommendations(userId, now.minusDays(properties.events().recommendationDaysBack()))) {
            String saving = row.monthlyCostSaving() != null && row.monthlyCostSaving() > 0
                    ? " 실행하면 월 " + won(row.monthlyCostSaving()) + "을 아낄 수 있어요." : "";
            created += publish(new NotificationCommand(row.userId(), NotificationType.RECOMMENDATION,
                    "새 개선안: " + row.title(),
                    row.domain() + " 영역의 개선안이 도착했어요." + saving,
                    NotificationSourceType.RECOMMENDATION, String.valueOf(row.recommendationId()),
                    key("RECOMMENDATION", "RECOMMENDATION", row.recommendationId())));
        }
        return created;
    }

    int monthlyReport(Long userId, LocalDate today, boolean manual) {
        if (!manual && today.getDayOfMonth() > 3) {
            return 0;
        }
        YearMonth lastMonth = YearMonth.from(today).minusMonths(1);
        int created = 0;
        for (SettingRow user : reader.monthlyReportUsers(userId)) {
            created += publish(new NotificationCommand(user.userId(), NotificationType.MONTHLY_REPORT,
                    lastMonth.getMonthValue() + "월 금융 리포트가 도착했어요",
                    user.name() + "님의 " + lastMonth.getMonthValue() + "월 소비·저축 흐름과 개선된 점을 정리했어요. 리포트에서 확인해 주세요.",
                    NotificationSourceType.REPORT, lastMonth.toString(),
                    key("MONTHLY_REPORT", "REPORT", lastMonth)));
        }
        return created;
    }

    /**
     * 데일리 브리핑. 스케줄(매 정시)에서는 사용자 시간대의 현재 시가 설정 시각의 시와 같은 사용자만, 직접 실행은 시각 조건 없이 만든다.
     */
    public int dailyBriefing(Long userId, LocalDateTime now, boolean manual) {
        int created = 0;
        for (SettingRow user : reader.dailyBriefingUsers(userId)) {
            ZoneId zone = zoneOf(user.timezone());
            ZonedDateTime local = now.atZone(DEFAULT_ZONE).withZoneSameInstant(zone);
            LocalTime briefingTime = user.dailyBriefingTime() == null ? DEFAULT_BRIEFING_TIME : user.dailyBriefingTime();
            if (!manual && local.getHour() != briefingTime.getHour()) {
                continue;
            }
            LocalDate today = local.toLocalDate();
            created += publish(new NotificationCommand(user.userId(), NotificationType.DAILY_BRIEFING,
                    "오늘의 금융 브리핑 (" + KOREAN_DATE.format(today) + ")", briefingMessage(user.userId(), today),
                    null, null, key("DAILY_BRIEFING", user.userId(), today)));
        }
        return created;
    }

    String briefingMessage(Long userId, LocalDate today) {
        List<String> lines = new ArrayList<>();
        long budget = reader.monthlyBudget(userId);
        if (budget > 0) {
            long spent = reader.spending(userId, today.withDayOfMonth(1), today);
            long remainingDays = ChronoUnit.DAYS.between(today, today.withDayOfMonth(today.lengthOfMonth())) + 1;
            long available = Math.max(budget - spent, 0) / Math.max(remainingDays, 1);
            lines.add("오늘 사용 가능 금액은 약 " + won(available) + "이에요 (이달 예산 " + won(budget) + " 중 " + won(spent) + " 사용).");
        }
        List<String> dueToday = new ArrayList<>();
        reader.subscriptionsDue(userId, today, today).forEach(d -> dueToday.add(d.name() + " " + won(d.amount()) + " 결제"));
        reader.cardStatementsDue(userId, today, today).forEach(d -> dueToday.add(d.cardName() + " 카드 " + won(d.totalBalance()) + " 출금"));
        reader.loansDue(userId, today, today).forEach(d -> dueToday.add(d.accountName() + " 상환"));
        reader.minusInterestDue(userId, today, today).forEach(d -> dueToday.add(d.accountName() + " 이자 " + won(d.accruedInterest())));
        lines.add(dueToday.isEmpty() ? "오늘 예정된 고정 지출은 없어요." : "오늘 예정: " + String.join(", ", dueToday) + ".");
        return String.join(" ", lines);
    }

    // ---------- helpers ----------

    private int publish(NotificationCommand command) {
        try {
            return publisher.publishDetailed(command).created() ? 1 : 0;
        } catch (RuntimeException exception) {
            log.warn("Notification event publish failed: type={} user={} source={}/{}", command.type(), command.userId(),
                    command.sourceType(), command.sourceId(), exception);
            return 0;
        }
    }

    static String key(Object... parts) {
        String[] strings = new String[parts.length];
        for (int i = 0; i < parts.length; i++) {
            strings[i] = String.valueOf(parts[i]);
        }
        return NotificationPublisher.dedupeKey(strings);
    }

    static String won(long amount) {
        return String.format(Locale.KOREAN, "%,d원", amount);
    }

    static String relative(LocalDate date, LocalDate today) {
        long days = ChronoUnit.DAYS.between(today, date);
        if (days <= 0) {
            return "오늘";
        }
        if (days == 1) {
            return "내일";
        }
        return days + "일 후";
    }

    static ZoneId zoneOf(String timezone) {
        if (timezone == null || timezone.isBlank()) {
            return DEFAULT_ZONE;
        }
        try {
            return ZoneId.of(timezone);
        } catch (DateTimeException exception) {
            return DEFAULT_ZONE;
        }
    }

    /**
     * @param date    기준일(서비스 시간대)
     * @param created 규칙별 새로 만든 알림 수
     * @param total   합계
     */
    public record EventRunResult(LocalDate date, Map<String, Integer> created, int total) {
        public EventRunResult {
            created = Map.copyOf(created);
        }
    }
}
