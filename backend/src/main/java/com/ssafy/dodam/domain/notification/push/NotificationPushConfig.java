package com.ssafy.dodam.domain.notification.push;

import com.ssafy.dodam.domain.notification.service.NotificationProperties;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 푸시 발송 구현 선택. `app.notification.push.enabled=true`이고 서비스 계정 키를 읽을 수 있을 때만 FCM을 쓴다.
 * 키 문제로 서버 기동이 막히면 알림 API 전체가 죽으므로, 초기화 실패는 경고만 남기고 미발송 구현으로 넘어간다.
 */
@Configuration
public class NotificationPushConfig {

    private static final Logger log = LoggerFactory.getLogger(NotificationPushConfig.class);

    @Bean
    public NotificationPushSender notificationPushSender(NotificationProperties properties) {
        NotificationProperties.Push push = properties.push();
        if (!push.enabled()) {
            log.info("Notification push disabled (app.notification.push.enabled=false)");
            return new LoggingNotificationPushSender();
        }
        if (push.serviceAccountPath().isBlank()) {
            log.warn("Notification push enabled but app.notification.push.service-account-path is empty — falling back to logging sender");
            return new LoggingNotificationPushSender();
        }
        try {
            return new FcmNotificationPushSender(push.serviceAccountPath());
        } catch (IOException | RuntimeException exception) {
            log.warn("FCM initialization failed — falling back to logging sender", exception);
            return new LoggingNotificationPushSender();
        }
    }
}
