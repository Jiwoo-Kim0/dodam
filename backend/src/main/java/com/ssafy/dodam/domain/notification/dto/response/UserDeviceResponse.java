package com.ssafy.dodam.domain.notification.dto.response;

import com.ssafy.dodam.domain.notification.entity.UserDevice;
import java.time.LocalDateTime;

/** 등록된 푸시 기기. 토큰은 응답에 포함하지 않는다. */
public record UserDeviceResponse(
        Long deviceId,
        String platform,
        boolean enabled,
        LocalDateTime lastUsedAt,
        LocalDateTime createdAt
) {

    public static UserDeviceResponse from(UserDevice device) {
        return new UserDeviceResponse(device.getId(), device.getPlatform().name(), device.isEnabled(), device.getLastUsedAt(),
                device.getCreatedAt());
    }
}
