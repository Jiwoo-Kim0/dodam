package com.ssafy.dodam.domain.notification.push;

import com.ssafy.dodam.domain.notification.entity.Notification;

/**
 * 기기 토큰 하나에 푸시를 보낸다. 구현은 FCM({@link FcmNotificationPushSender})과 미발송 로그({@link LoggingNotificationPushSender}).
 * 발송 실패는 예외가 아니라 {@link PushResult}로 돌려줘 호출자가 토큰 무효만 처리하고 나머지는 로그로 남기게 한다.
 */
public interface NotificationPushSender {

    PushResult send(String fcmToken, Notification notification);

    /**
     * @param status  SENT / INVALID_TOKEN(기기 해제 대상) / FAILED(일시 오류) / SKIPPED(발송 비활성)
     * @param detail  메시지 ID 또는 오류 요약. 토큰 원문은 넣지 않는다
     */
    record PushResult(Status status, String detail) {
        public enum Status { SENT, INVALID_TOKEN, FAILED, SKIPPED }

        public static PushResult sent(String messageId) {
            return new PushResult(Status.SENT, messageId);
        }

        public static PushResult invalidToken(String detail) {
            return new PushResult(Status.INVALID_TOKEN, detail);
        }

        public static PushResult failed(String detail) {
            return new PushResult(Status.FAILED, detail);
        }

        public static PushResult skipped(String detail) {
            return new PushResult(Status.SKIPPED, detail);
        }
    }
}
