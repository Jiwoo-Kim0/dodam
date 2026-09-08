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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * ERD `finance_connection`. 사용자 1명당 SSAFY 금융망 계정(userKey) 연결 상태 1행.
 * 이메일·userKey는 AES-GCM 암호문 + HMAC 해시 + 마스킹으로 나눠 저장한다 (FieldCryptor).
 */
@Entity
@Table(name = "finance_connection")
public class FinanceConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "connection_id")
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "member_email_encrypted", length = 512)
    private byte[] memberEmailEncrypted;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "member_email_hash", length = 64, unique = true)
    private String memberEmailHash;

    @Column(name = "member_email_masked", length = 255)
    private String memberEmailMasked;

    @Column(name = "external_user_key_encrypted", length = 512)
    private byte[] externalUserKeyEncrypted;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "external_user_key_hash", length = 64, unique = true)
    private String externalUserKeyHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private FinanceConnectionStatus status;

    @Column(name = "connected_at")
    private LocalDateTime connectedAt;

    @Column(name = "disconnected_at")
    private LocalDateTime disconnectedAt;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    protected FinanceConnection() {
    }

    private FinanceConnection(
            Long userId,
            byte[] memberEmailEncrypted,
            String memberEmailHash,
            String memberEmailMasked,
            FinanceConnectionStatus status
    ) {
        this.userId = userId;
        this.memberEmailEncrypted = memberEmailEncrypted;
        this.memberEmailHash = memberEmailHash;
        this.memberEmailMasked = memberEmailMasked;
        this.status = status;
    }

    public static FinanceConnection connected(
            Long userId,
            byte[] memberEmailEncrypted,
            String memberEmailHash,
            String memberEmailMasked,
            byte[] externalUserKeyEncrypted,
            String externalUserKeyHash,
            LocalDateTime connectedAt
    ) {
        FinanceConnection connection = new FinanceConnection(
                userId, memberEmailEncrypted, memberEmailHash, memberEmailMasked,
                FinanceConnectionStatus.CONNECTED
        );
        connection.externalUserKeyEncrypted = externalUserKeyEncrypted;
        connection.externalUserKeyHash = externalUserKeyHash;
        connection.connectedAt = connectedAt;
        return connection;
    }

    /**
     * 금융망 호출이 실패해 userKey를 못 받은 상태. 회원은 만들어지고 연결만 나중에 다시 시도한다.
     */
    public static FinanceConnection failed(
            Long userId,
            byte[] memberEmailEncrypted,
            String memberEmailHash,
            String memberEmailMasked
    ) {
        return new FinanceConnection(
                userId, memberEmailEncrypted, memberEmailHash, memberEmailMasked,
                FinanceConnectionStatus.FAILED
        );
    }

    public void disconnect(LocalDateTime at) {
        this.status = FinanceConnectionStatus.DISCONNECTED;
        this.disconnectedAt = at;
    }

    public boolean isConnected() {
        return status == FinanceConnectionStatus.CONNECTED;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public byte[] getMemberEmailEncrypted() {
        return memberEmailEncrypted;
    }

    public String getMemberEmailHash() {
        return memberEmailHash;
    }

    public String getMemberEmailMasked() {
        return memberEmailMasked;
    }

    public byte[] getExternalUserKeyEncrypted() {
        return externalUserKeyEncrypted;
    }

    public String getExternalUserKeyHash() {
        return externalUserKeyHash;
    }

    public FinanceConnectionStatus getStatus() {
        return status;
    }

    public LocalDateTime getConnectedAt() {
        return connectedAt;
    }

    public LocalDateTime getDisconnectedAt() {
        return disconnectedAt;
    }

    public LocalDateTime getLastSyncedAt() {
        return lastSyncedAt;
    }
}
