package com.ssafy.dodam.domain.notification.entity;

import com.ssafy.dodam.global.exception.InvalidRequestException;
import java.util.Locale;

/** ERD `user_device.platform` CHECK 제약과 동일한 값. */
public enum DevicePlatform {
    ANDROID,
    IOS,
    WEB;

    public static DevicePlatform parse(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidRequestException("platform은 ANDROID, IOS, WEB 중 하나여야 합니다.");
        }
        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidRequestException("platform은 ANDROID, IOS, WEB 중 하나여야 합니다.");
        }
    }
}
