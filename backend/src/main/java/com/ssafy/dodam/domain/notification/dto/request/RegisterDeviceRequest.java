package com.ssafy.dodam.domain.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * POST /api/v1/notifications/devices Body.
 *
 * @param fcmToken Expo/FCM 기기 토큰 원문. 서버는 암호문·해시로만 저장한다
 * @param platform ANDROID | IOS | WEB
 */
public record RegisterDeviceRequest(
        @NotBlank @Size(max = 4096) String fcmToken,
        @NotBlank @Size(max = 20) String platform
) {
}
