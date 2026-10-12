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
