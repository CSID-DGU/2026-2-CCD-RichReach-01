package com.richreach.codef.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 연동 내에서 기관(카드사)별로 등록한 계정. */
@Entity
@Table(name = "codef_org_accounts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CodefOrgAccount {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_NEEDS_CARD_AUTH = "NEEDS_CARD_AUTH";
    public static final String STATUS_LOCKED = "LOCKED";
    public static final String STATUS_DELETED = "DELETED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id", nullable = false)
    private CodefConnection connection;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "policy_id")
    private CodefOrgPolicy policy;

    @Column(nullable = false, length = 10)
    private String organization;

    @Column(name = "business_type", nullable = false, length = 10)
    private String businessType;

    @Column(name = "client_type", nullable = false, length = 5)
    private String clientType;

    /** 0 인증서, 1 아이디 */
    @Column(name = "login_type", nullable = false, length = 5)
    private String loginType;

    @Column(name = "registration_status", length = 30)
    private String registrationStatus;

    /** ACTIVE, NEEDS_CARD_AUTH, LOCKED, ERROR, DELETED */
    @Column(nullable = false, length = 30)
    private String status;

    @Column(name = "last_error_code", length = 20)
    private String lastErrorCode;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 카드사 인증에 성공한 상태로 되돌린다. (처음 연결하거나, 실패했던 카드사를 다시 연결할 때) */
    public void markConnected() {
        this.status = STATUS_ACTIVE;
        this.lastErrorCode = null;
    }

    /** 카드 목록을 가져와 동기화를 마쳤다. */
    public void markSynced(LocalDateTime syncedAt) {
        this.lastSyncedAt = syncedAt;
        this.lastErrorCode = null;
    }

    /** 동기화에 실패한 오류 코드를 남긴다. 연결 상태(status)는 바꾸지 않는다. */
    public void recordSyncError(String errorCode) {
        this.lastErrorCode = errorCode;
    }

    /** 카드사가 계정을 잠근 상태인지. 이때 다시 로그인을 시도하면 잠김이 길어질 수 있다. */
    public boolean isLocked() {
        return STATUS_LOCKED.equals(status);
    }

    /** 사용자에게 연결된 것으로 보여줄 상태인지. 해제(DELETED)나 실패한 계정은 연결로 보지 않는다. */
    public boolean isConnected() {
        return STATUS_ACTIVE.equals(status) || STATUS_NEEDS_CARD_AUTH.equals(status);
    }

    public CodefOrgAccount(CodefConnection connection, CodefOrgPolicy policy, String organization,
        String businessType, String clientType, String loginType, String status, LocalDateTime createdAt) {
        this.connection = connection;
        this.policy = policy;
        this.organization = organization;
        this.businessType = businessType;
        this.clientType = clientType;
        this.loginType = loginType;
        this.status = status;
        this.createdAt = createdAt;
    }

}
