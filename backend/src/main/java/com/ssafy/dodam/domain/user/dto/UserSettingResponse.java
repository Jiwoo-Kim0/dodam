package com.ssafy.dodam.domain.user.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.ssafy.dodam.domain.user.entity.UserSetting;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record UserSettingResponse(
        boolean notificationEnabled,
        boolean dailyBriefingEnabled,
        boolean monthlyReportEnabled,
        @JsonFormat(pattern = "HH:mm") LocalTime dailyBriefingTime,
        String timezone,
        LocalDateTime updatedAt
) {

    public static UserSettingResponse from(UserSetting setting) {
        return new UserSettingResponse(
                setting.isNotificationEnabled(),
                setting.isDailyBriefingEnabled(),
                setting.isMonthlyReportEnabled(),
                setting.getDailyBriefingTime(),
                setting.getTimezone(),
                setting.getUpdatedAt()
        );
    }
}
