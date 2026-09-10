package com.ssafy.dodam.domain.finance.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/**
 * ERD `finance_sync`. 사용자가 요청한 금융 데이터 동기화 1회 실행 이력.
 * 도메인(scope)별 세부 결과는 컬럼이 없어 응답에만 싣고, 실패한 도메인의 메시지만 `error_message`에 모아 둔다.
 */
@Entity
@Table(name = "finance_sync")
public class FinanceSync {

    private static final int ERROR_MESSAGE_MAX_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sync_id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 30)
    private FinanceSyncScope scope;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private FinanceSyncStatus status;

    @Column(name = "progress")
    private Integer progress;

    @Column(name = "error_message", length = ERROR_MESSAGE_MAX_LENGTH)
    private String errorMessage;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected FinanceSync() {
    }

    private FinanceSync(Long userId, FinanceSyncScope scope, LocalDateTime startedAt) {
        this.userId = userId;
        this.scope = scope;
        this.status = FinanceSyncStatus.RUNNING;
        this.progress = 0;
        this.startedAt = startedAt;
        this.updatedAt = startedAt;
    }

    public static FinanceSync start(Long userId, FinanceSyncScope scope, LocalDateTime startedAt) {
        return new FinanceSync(userId, scope, startedAt);
    }

    public void complete(int progress, LocalDateTime at) {
        this.status = FinanceSyncStatus.COMPLETED;
        this.progress = clampProgress(progress);
        this.errorMessage = null;
        this.completedAt = at;
        this.updatedAt = at;
    }

    public void fail(int progress, String errorMessage, LocalDateTime at) {
        this.status = FinanceSyncStatus.FAILED;
        this.progress = clampProgress(progress);
        this.errorMessage = truncate(errorMessage);
        this.completedAt = at;
        this.updatedAt = at;
    }

    public boolean isInProgress() {
        return status.isInProgress();
    }

    /**
     * 프로세스가 도중에 죽어 RUNNING으로 남은 행인지. 기준 시각보다 먼저 시작했으면 만료로 본다.
     */
    public boolean isStaleSince(LocalDateTime threshold) {
        return isInProgress() && startedAt.isBefore(threshold);
    }

    private static int clampProgress(int progress) {
        return Math.max(0, Math.min(100, progress));
    }

    private static String truncate(String message) {
        if (message == null || message.length() <= ERROR_MESSAGE_MAX_LENGTH) {
            return message;
        }
        return message.substring(0, ERROR_MESSAGE_MAX_LENGTH);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public FinanceSyncScope getScope() {
        return scope;
    }

    public FinanceSyncStatus getStatus() {
        return status;
    }

    public Integer getProgress() {
        return progress;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
