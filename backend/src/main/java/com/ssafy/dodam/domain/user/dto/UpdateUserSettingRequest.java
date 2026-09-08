package com.ssafy.dodam.domain.user.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * API 명세: notificationEnabled?, dailyBriefingEnabled?, monthlyReportEnabled?, dailyBriefingTime?(HH:mm), timezone?
 * 모두 선택이며 null인 항목은 변경하지 않는다.
 */
public record UpdateUserSettingRequest(
        Boolean notificationEnabled,
        Boolean dailyBriefingEnabled,
        Boolean monthlyReportEnabled,

        @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "dailyBriefingTime은 HH:mm 형식이어야 합니다.")
        String dailyBriefingTime,

        @Size(max = 50, message = "timezone은 50자 이하여야 합니다.")
        String timezone
) {

    public boolean isEmpty() {
        return notificationEnabled == null
                && dailyBriefingEnabled == null
                && monthlyReportEnabled == null
                && dailyBriefingTime == null
                && timezone == null;
    }
}
