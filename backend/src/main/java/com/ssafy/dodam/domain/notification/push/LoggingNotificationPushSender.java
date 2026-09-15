package com.ssafy.dodam.domain.notification.push;

import com.ssafy.dodam.domain.notification.entity.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 푸시 비활성(`app.notification.push.enabled=false`) 환경의 구현. 무엇을 보냈을지 로그만 남긴다(토큰 원문은 남기지 않는다).
 * 로컬·H2 테스트·FCM 키가 없는 환경에서 쓰인다.
 */
public class LoggingNotificationPushSender implements NotificationPushSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingNotificationPushSender.class);

    @Override
    public PushResult send(String fcmToken, Notification notification) {
        log.info("Push disabled — would send notificationId={} type={} title='{}' to device token(len={})",
                notification.getId(), notification.getType(), notification.getTitle(), fcmToken == null ? 0 : fcmToken.length());
        return PushResult.skipped("push disabled");
    }
}
