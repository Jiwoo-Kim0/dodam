package com.ssafy.dodam.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 알림 1건(ERD `notification`, V32).
 *
 * <p>읽음은 `isRead`·`readAt`, 삭제는 `deletedAt` 소프트 삭제다. 삭제된 알림은 모든 조회에서 제외되고 404다.
 * `type`은 {@link NotificationType} 이름을 문자열로 저장한다(다른 도메인이 SQL로 넣은 미지 유형도 읽을 수 있게).
 * `(userId, dedupeKey)`는 유일 제약이라 같은 이벤트의 알림이 두 번 만들어지지 않는다.
 */
@Getter
@Entity
@Table(name = "notification")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    public static final int TITLE_MAX_LENGTH = 255;
    public static final int SOURCE_ID_MAX_LENGTH = 100;
    public static final int DEDUPE_KEY_LENGTH = 64;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "type", nullable = false, length = 50)
    private String type;

    @Column(name = "title", nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "source_type", length = 50)
    private String sourceType;

    @Column(name = "source_id", length = SOURCE_ID_MAX_LENGTH)
    private String sourceId;

    @Column(name = "dedupe_key", length = DEDUPE_KEY_LENGTH, columnDefinition = "CHAR(64)")
    private String dedupeKey;

    @Column(name = "is_read", nullable = false)
    private boolean read;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    private Notification(Long userId, String type, String title, String message, String sourceType, String sourceId,
            String dedupeKey, LocalDateTime now) {
        this.userId = userId;
        this.type = type;
        this.title = title;
        this.message = message;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.dedupeKey = dedupeKey;
        this.read = false;
        this.createdAt = now;
    }

    public static Notification create(Long userId, NotificationType type, String title, String message,
            NotificationSourceType sourceType, String sourceId, String dedupeKey, LocalDateTime now) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자를 지정해야 합니다.");
        }
        if (type == null) {
            throw new IllegalArgumentException("알림 유형을 지정해야 합니다.");
        }
        if (title == null || title.isBlank() || title.length() > TITLE_MAX_LENGTH) {
            throw new IllegalArgumentException("제목은 1~" + TITLE_MAX_LENGTH + "자여야 합니다.");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("내용이 비어 있습니다.");
        }
        if (sourceId != null && sourceId.length() > SOURCE_ID_MAX_LENGTH) {
            throw new IllegalArgumentException("sourceId는 " + SOURCE_ID_MAX_LENGTH + "자 이하여야 합니다.");
        }
        if (dedupeKey != null && (dedupeKey.isBlank() || dedupeKey.length() > DEDUPE_KEY_LENGTH)) {
            throw new IllegalArgumentException("dedupeKey는 1~" + DEDUPE_KEY_LENGTH + "자여야 합니다.");
        }
        if ((sourceType == null) != (sourceId == null)) {
            throw new IllegalArgumentException("sourceType과 sourceId는 함께 지정해야 합니다.");
        }
        return new Notification(userId, type.name(), title.strip(), message, sourceType == null ? null : sourceType.name(),
                sourceId, dedupeKey, now);
    }

    /** 읽음 처리. 이미 읽었으면 아무것도 바꾸지 않고 false를 돌려준다(명세 "이미 읽은 알림은 멱등 처리"). */
    public boolean markRead(LocalDateTime now) {
        if (read) {
            return false;
        }
        this.read = true;
        this.readAt = now;
        return true;
    }

    public void delete(LocalDateTime now) {
        if (deletedAt == null) {
            this.deletedAt = now;
        }
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
