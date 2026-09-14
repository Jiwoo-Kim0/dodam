package com.ssafy.dodam.domain.notification.push;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.ssafy.dodam.domain.notification.entity.Notification;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Firebase Cloud Messaging 발송. 서비스 계정 키(`FCM_SERVICE_ACCOUNT_PATH`, compose가 `/run/secrets/fcm-service-account.json`으로 마운트)로
 * {@link FirebaseApp}을 한 번 초기화한다.
 *
 * <p>페이로드는 `notification`(제목·내용)과 `data`(notificationId, type, sourceType, sourceId)다. 앱은 data의 (sourceType, sourceId)로 상세 화면에 이동한다.
 * FCM이 UNREGISTERED·INVALID_ARGUMENT(토큰 형식 오류)를 돌려주면 {@link PushResult.Status#INVALID_TOKEN}으로 알려 기기를 해제하게 한다.
 */
public class FcmNotificationPushSender implements NotificationPushSender {

    private static final Logger log = LoggerFactory.getLogger(FcmNotificationPushSender.class);
    static final String APP_NAME = "dodam-fcm";

    private final FirebaseMessaging messaging;

    public FcmNotificationPushSender(String serviceAccountPath) throws IOException {
        Path path = Path.of(serviceAccountPath);
        if (!Files.isReadable(path)) {
            throw new IOException("FCM 서비스 계정 키를 읽을 수 없습니다: " + serviceAccountPath);
        }
        FirebaseApp app;
        synchronized (FcmNotificationPushSender.class) {
            app = FirebaseApp.getApps().stream().filter(a -> APP_NAME.equals(a.getName())).findFirst().orElse(null);
            if (app == null) {
                try (InputStream in = new FileInputStream(path.toFile())) {
                    app = FirebaseApp.initializeApp(FirebaseOptions.builder()
                            .setCredentials(GoogleCredentials.fromStream(in))
                            .build(), APP_NAME);
                }
            }
        }
        this.messaging = FirebaseMessaging.getInstance(app);
        log.info("FCM push sender initialized (service account: {})", path.getFileName());
    }

    FcmNotificationPushSender(FirebaseMessaging messaging) {
        this.messaging = messaging;
    }

    @Override
    public PushResult send(String fcmToken, Notification notification) {
        Map<String, String> data = new HashMap<>();
        data.put("notificationId", String.valueOf(notification.getId()));
        data.put("type", notification.getType());
        if (notification.getSourceType() != null) {
            data.put("sourceType", notification.getSourceType());
            data.put("sourceId", notification.getSourceId());
        }
        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(notification.getTitle())
                        .setBody(notification.getMessage())
                        .build())
                .putAllData(data)
                .build();
        try {
            return PushResult.sent(messaging.send(message));
        } catch (FirebaseMessagingException exception) {
            MessagingErrorCode code = exception.getMessagingErrorCode();
            if (code == MessagingErrorCode.UNREGISTERED || code == MessagingErrorCode.INVALID_ARGUMENT) {
                return PushResult.invalidToken(String.valueOf(code));
            }
            log.warn("FCM send failed for notificationId={} code={}", notification.getId(), code, exception);
            return PushResult.failed(String.valueOf(code));
        }
    }
}
