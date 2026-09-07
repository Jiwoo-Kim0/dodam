package com.ssafy.dodam.domain.user.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * ERD `user_setting`. PK가 user_id이며 회원가입 시 기본값 행을 함께 만든다.
 */
@Entity
@Table(name = "user_setting")
public class UserSetting {

    public static final String DEFAULT_TIMEZONE = "Asia/Seoul";

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "notification_enabled", nullable = false)
    private boolean notificationEnabled;

    @Column(name = "daily_briefing_enabled", nullable = false)
    private boolean dailyBriefingEnabled;

    @Column(name = "monthly_report_enabled", nullable = false)
    private boolean monthlyReportEnabled;

    @Column(name = "daily_briefing_time")
    private LocalTime dailyBriefingTime;

    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected UserSetting() {
    }

    private UserSetting(Long userId) {
        this.userId = userId;
        this.notificationEnabled = true;
        this.dailyBriefingEnabled = true;
        this.monthlyReportEnabled = true;
        this.timezone = DEFAULT_TIMEZONE;
    }

    public static UserSetting defaults(Long userId) {
        return new UserSetting(userId);
    }

    /**
     * null인 항목은 건드리지 않는다 (PATCH 시맨틱).
     */
    public void update(
            Boolean notificationEnabled,
            Boolean dailyBriefingEnabled,
            Boolean monthlyReportEnabled,
            LocalTime dailyBriefingTime,
            String timezone
    ) {
        if (notificationEnabled != null) {
            this.notificationEnabled = notificationEnabled;
        }
        if (dailyBriefingEnabled != null) {
            this.dailyBriefingEnabled = dailyBriefingEnabled;
        }
        if (monthlyReportEnabled != null) {
            this.monthlyReportEnabled = monthlyReportEnabled;
        }
        if (dailyBriefingTime != null) {
            this.dailyBriefingTime = dailyBriefingTime;
        }
        if (timezone != null) {
            this.timezone = timezone;
        }
    }

    @PrePersist
    @PreUpdate
    void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getUserId() {
        return userId;
    }

    public boolean isNotificationEnabled() {
        return notificationEnabled;
    }

    public boolean isDailyBriefingEnabled() {
        return dailyBriefingEnabled;
    }

    public boolean isMonthlyReportEnabled() {
        return monthlyReportEnabled;
    }

    public LocalTime getDailyBriefingTime() {
        return dailyBriefingTime;
    }

    public String getTimezone() {
        return timezone;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
