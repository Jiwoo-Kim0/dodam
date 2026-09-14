package com.ssafy.dodam.domain.notification.service;

import com.ssafy.dodam.domain.notification.entity.Notification;
import com.ssafy.dodam.domain.notification.entity.NotificationSourceType;
import com.ssafy.dodam.domain.notification.entity.NotificationType;
import com.ssafy.dodam.domain.notification.push.NotificationPushDispatcher;
import com.ssafy.dodam.domain.notification.repository.NotificationRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 다른 도메인이 알림을 만들 때 쓰는 진입점(D10 이벤트 연동의 계약). 명세 22에는 생성 API가 없다.
 *
 * <p><b>중복 방지</b>: `dedupeKey`가 있으면 같은 사용자의 같은 키 알림이 이미 있을 때 새로 만들지 않고 기존 행을 돌려준다
 * (삭제된 행이어도 다시 만들지 않는다. 사용자가 지운 알림이 되살아나면 안 된다). 동시 요청은 `(user_id, dedupe_key)` 유일 제약이
 * 막고, 제약 위반이면 기존 행을 다시 읽어 돌려준다.
 *
 * <p>사용자 설정 `notification_enabled`는 여기서 보지 않는다. 앱 안의 알림함에는 항상 남기고, 푸시 발송(FCM, D10)에서 설정을 확인한다.
 */
@Service
public class NotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationPublisher.class);

    private final NotificationRepository notifications;
    private final NotificationClock clock;
    private final ObjectProvider<NotificationPushDispatcher> pushDispatcher;

    public NotificationPublisher(NotificationRepository notifications, NotificationClock clock,
            ObjectProvider<NotificationPushDispatcher> pushDispatcher) {
        this.notifications = notifications;
        this.clock = clock;
        this.pushDispatcher = pushDispatcher;
    }

    /**
     * @return 새로 만든 알림 또는 dedupeKey가 같은 기존 알림
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Notification publish(NotificationCommand command) {
        // 같은 빈 안의 호출은 프록시를 거치지 않으므로 여기서도 트랜잭션을 연다(커밋 후 푸시 동기화가 등록되게).
        return publishDetailed(command).notification();
    }

    /**
     * 알림을 저장하고, 새로 만들어졌으면 트랜잭션 커밋 뒤 푸시 발송을 시도한다(S15P21D105-580). 푸시 실패는 로그만 남기고 알림 저장에 영향을 주지 않는다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public PublishOutcome publishDetailed(NotificationCommand command) {
        if (command.dedupeKey() != null) {
            Optional<Notification> existing = notifications.findByUserIdAndDedupeKey(command.userId(), command.dedupeKey());
            if (existing.isPresent()) {
                return new PublishOutcome(existing.get(), false);
            }
        }
        Notification notification = Notification.create(command.userId(), command.type(), command.title(), command.message(),
                command.sourceType(), command.sourceId(), command.dedupeKey(), clock.now());
        Notification saved;
        try {
            saved = notifications.saveAndFlush(notification);
        } catch (DataIntegrityViolationException raced) {
            if (command.dedupeKey() == null) {
                throw raced;
            }
            return new PublishOutcome(
                    notifications.findByUserIdAndDedupeKey(command.userId(), command.dedupeKey()).orElseThrow(() -> raced), false);
        }
        schedulePush(saved.getId());
        return new PublishOutcome(saved, true);
    }

    private void schedulePush(Long notificationId) {
        NotificationPushDispatcher dispatcher = pushDispatcher.getIfAvailable();
        if (dispatcher == null || !TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    dispatcher.dispatch(notificationId);
                } catch (RuntimeException exception) {
                    log.warn("Push dispatch failed for notificationId={}", notificationId, exception);
                }
            }
        });
    }

    /** @param created true면 이번 호출로 새로 만들어졌다. false면 dedupeKey가 같은 기존 알림이다 */
    public record PublishOutcome(Notification notification, boolean created) {
    }

    /**
     * 중복 방지 키. 유형·원천·기간 같은 식별 조각을 이어 SHA-256 hex(64자)로 만든다. 같은 조각이면 항상 같은 키다.
     * 예) dedupeKey("PAYMENT_DUE", "SUBSCRIPTION", "SUB-U0001-01", "2026-09-25")
     */
    public static String dedupeKey(String... parts) {
        if (parts == null || parts.length == 0) {
            throw new IllegalArgumentException("dedupe 조각이 비어 있습니다.");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(String.join("|", parts).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    /**
     * 알림 생성 명령.
     *
     * @param sourceType 원천 도메인. sourceId와 함께 지정하거나 둘 다 null
     * @param sourceId   원천 PK 문자열(100자 이하)
     * @param dedupeKey  중복 방지 키(64자 이하). null이면 매번 새 알림
     */
    public record NotificationCommand(
            Long userId,
            NotificationType type,
            String title,
            String message,
            NotificationSourceType sourceType,
            String sourceId,
            String dedupeKey
    ) {
        public static NotificationCommand of(Long userId, NotificationType type, String title, String message) {
            return new NotificationCommand(userId, type, title, message, null, null, null);
        }
    }
}
