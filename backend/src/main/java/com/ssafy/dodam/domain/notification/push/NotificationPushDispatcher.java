package com.ssafy.dodam.domain.notification.push;

import com.ssafy.dodam.domain.notification.entity.Notification;
import com.ssafy.dodam.domain.notification.entity.NotificationType;
import com.ssafy.dodam.domain.notification.entity.UserDevice;
import com.ssafy.dodam.domain.notification.push.NotificationPushSender.PushResult;
import com.ssafy.dodam.domain.notification.repository.NotificationRepository;
import com.ssafy.dodam.domain.notification.repository.UserDeviceRepository;
import com.ssafy.dodam.domain.notification.service.NotificationClock;
import com.ssafy.dodam.domain.user.entity.UserSetting;
import com.ssafy.dodam.domain.user.repository.UserSettingRepository;
import com.ssafy.dodam.global.crypto.FieldCryptor;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 새 알림 1건을 사용자의 활성 기기에 푸시한다. 알림함 저장과 분리되어 있어 발송 실패가 알림 생성을 되돌리지 않는다.
 *
 * <p>사용자 설정을 여기서 확인한다: `notification_enabled=false`면 보내지 않고, 데일리 브리핑·월간 리포트는 각각 `daily_briefing_enabled`·
 * `monthly_report_enabled`도 켜져 있어야 한다. FCM이 토큰 무효를 돌려준 기기는 `enabled=false`로 바꿔 다음 발송 대상에서 뺀다.
 */
@Service
public class NotificationPushDispatcher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPushDispatcher.class);

    private final NotificationRepository notifications;
    private final UserDeviceRepository devices;
    private final UserSettingRepository settings;
    private final NotificationPushSender sender;
    private final FieldCryptor cryptor;
    private final NotificationClock clock;

    public NotificationPushDispatcher(NotificationRepository notifications, UserDeviceRepository devices,
            UserSettingRepository settings, NotificationPushSender sender, FieldCryptor cryptor, NotificationClock clock) {
        this.notifications = notifications;
        this.devices = devices;
        this.settings = settings;
        this.sender = sender;
        this.cryptor = cryptor;
        this.clock = clock;
    }

    /**
     * @return 실제 발송(SENT)된 기기 수
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int dispatch(Long notificationId) {
        Notification notification = notifications.findById(notificationId).orElse(null);
        if (notification == null || notification.isDeleted()) {
            return 0;
        }
        UserSetting setting = settings.findById(notification.getUserId()).orElse(null);
        if (!allowedBySetting(setting, notification.getType())) {
            log.debug("Push suppressed by user setting: notificationId={} type={}", notificationId, notification.getType());
            return 0;
        }
        List<UserDevice> targets = devices.findAllByUserIdAndEnabledTrue(notification.getUserId());
        int sent = 0;
        for (UserDevice device : targets) {
            String token;
            try {
                token = cryptor.decrypt(device.getFcmTokenEncrypted());
            } catch (RuntimeException exception) {
                log.warn("FCM token decrypt failed deviceId={} — disabling", device.getId(), exception);
                device.disable(clock.now());
                continue;
            }
            PushResult result = sender.send(token, notification);
            switch (result.status()) {
                case SENT -> sent++;
                case INVALID_TOKEN -> {
                    log.info("FCM token invalid deviceId={} ({}) — disabling", device.getId(), result.detail());
                    device.disable(clock.now());
                }
                case FAILED -> log.warn("Push failed deviceId={} notificationId={} ({})", device.getId(), notificationId, result.detail());
                case SKIPPED -> { }
            }
        }
        return sent;
    }

    static boolean allowedBySetting(UserSetting setting, String type) {
        if (setting == null) {
            return true;
        }
        if (!setting.isNotificationEnabled()) {
            return false;
        }
        if (NotificationType.DAILY_BRIEFING.name().equals(type)) {
            return setting.isDailyBriefingEnabled();
        }
        if (NotificationType.MONTHLY_REPORT.name().equals(type)) {
            return setting.isMonthlyReportEnabled();
        }
        return true;
    }
}
