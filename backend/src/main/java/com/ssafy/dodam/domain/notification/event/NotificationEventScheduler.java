package com.ssafy.dodam.domain.notification.event;

import com.ssafy.dodam.domain.notification.service.NotificationClock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 알림 이벤트 스케줄(S15P21D105-581). `app.notification.events.enabled=true`일 때만 등록된다(테스트는 false).
 *
 * <ul>
 *   <li>매일 08:00(Asia/Seoul): 결제·상환 예정, 현금흐름 위험, 목표 진행, 추천, 월간 리포트(1~3일)</li>
 *   <li>매 정시: 데일리 브리핑(사용자 설정 시각의 시와 일치하는 사용자)</li>
 * </ul>
 * 서버가 여러 대면 같은 시각에 중복 실행되지만 dedupe 키가 같은 알림을 두 번 만들지 않는다. 실패는 로그만 남기고 다음 스케줄에 재시도된다.
 */
@Component
@EnableScheduling
@ConditionalOnProperty(prefix = "app.notification.events", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NotificationEventScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventScheduler.class);

    private final NotificationEventService events;
    private final NotificationClock clock;

    public NotificationEventScheduler(NotificationEventService events, NotificationClock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Scheduled(cron = "${app.notification.events.daily-cron:0 0 8 * * *}", zone = "Asia/Seoul")
    public void runDaily() {
        try {
            events.runAll(null, clock.now());
        } catch (RuntimeException exception) {
            log.error("Daily notification events failed", exception);
        }
    }

    @Scheduled(cron = "${app.notification.events.briefing-cron:0 0 * * * *}", zone = "Asia/Seoul")
    public void runBriefings() {
        try {
            int created = events.dailyBriefing(null, clock.now(), false);
            if (created > 0) {
                log.info("Daily briefings created: {}", created);
            }
        } catch (RuntimeException exception) {
            log.error("Daily briefing events failed", exception);
        }
    }
}
