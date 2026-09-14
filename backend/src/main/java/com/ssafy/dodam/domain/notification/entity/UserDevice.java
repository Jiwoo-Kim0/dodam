package com.ssafy.dodam.domain.notification.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 푸시 알림 기기(ERD `user_device`, V33). FCM 토큰은 암호문·해시로만 저장하고 원문은 응답에도 내보내지 않는다.
 *
 * <p>토큰 해시는 전체 UNIQUE다. 다른 사용자가 같은 기기로 로그인해 토큰을 등록하면 {@link #transferTo}로 소유자를 바꾼다(기기는 하나의
 * 로그인 사용자에게만 푸시를 받아야 한다). FCM이 토큰 무효를 돌려주면 {@link #disable}로 발송 대상에서 뺀다.
 */
@Getter
@Entity
@Table(name = "user_device")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "device_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "fcm_token_encrypted", nullable = false, columnDefinition = "VARBINARY(1024)")
    private byte[] fcmTokenEncrypted;

    @Column(name = "fcm_token_hash", nullable = false, length = 64, columnDefinition = "CHAR(64)")
    private String fcmTokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "platform", nullable = false, length = 20)
    private DevicePlatform platform;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    private UserDevice(Long userId, byte[] fcmTokenEncrypted, String fcmTokenHash, DevicePlatform platform, LocalDateTime now) {
        this.userId = userId;
        this.fcmTokenEncrypted = fcmTokenEncrypted;
        this.fcmTokenHash = fcmTokenHash;
        this.platform = platform;
        this.enabled = true;
        this.lastUsedAt = now;
        this.createdAt = now;
    }

    public static UserDevice register(Long userId, byte[] fcmTokenEncrypted, String fcmTokenHash, DevicePlatform platform,
            LocalDateTime now) {
        if (userId == null || userId <= 0) {
            throw new IllegalArgumentException("사용자를 지정해야 합니다.");
        }
        if (fcmTokenEncrypted == null || fcmTokenEncrypted.length == 0 || fcmTokenHash == null || fcmTokenHash.length() != 64) {
            throw new IllegalArgumentException("토큰 암호문·해시가 올바르지 않습니다.");
        }
        if (platform == null) {
            throw new IllegalArgumentException("platform을 지정해야 합니다.");
        }
        return new UserDevice(userId, fcmTokenEncrypted, fcmTokenHash, platform, now);
    }

    /** 같은 토큰을 다시 등록: 사용 시각을 갱신하고 해제됐던 기기면 다시 켠다. 플랫폼이 바뀌었으면 따라간다. */
    public void touch(DevicePlatform platform, byte[] fcmTokenEncrypted, LocalDateTime now) {
        this.platform = platform;
        this.fcmTokenEncrypted = fcmTokenEncrypted;
        this.enabled = true;
        this.lastUsedAt = now;
        this.updatedAt = now;
    }

    /** 다른 사용자가 같은 기기로 로그인해 토큰을 등록했다. 기기의 소유자를 바꾼다. */
    public void transferTo(Long userId, DevicePlatform platform, byte[] fcmTokenEncrypted, LocalDateTime now) {
        this.userId = userId;
        touch(platform, fcmTokenEncrypted, now);
    }

    public void disable(LocalDateTime now) {
        if (enabled) {
            this.enabled = false;
            this.updatedAt = now;
        }
    }
}
