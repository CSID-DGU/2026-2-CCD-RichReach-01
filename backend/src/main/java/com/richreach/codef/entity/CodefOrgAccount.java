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
import java.time.LocalDate;
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

    /** 거래(승인내역) 수집을 마친 연속된 기간. 아직 수집 기록이 없으면 null. */
    @Column(name = "tx_collected_from")
    private LocalDate txCollectedFrom;

    @Column(name = "tx_collected_to")
    private LocalDate txCollectedTo;

    /** 기록할 때의 보유카드 id 목록(쉼표로 구분). 카드 구성이 바뀌었는지 가리는 데 쓴다. */
    @Column(name = "tx_collected_card_ids", length = 255)
    private String txCollectedCardIds;

    /** 화면에 보여줄 가린 카드사 아이디(앞 2자만 남김). 연결할 때 저장하고, 기록이 없으면 null. 원문은 저장하지 않는다. */
    @Column(name = "login_id_masked", length = 30)
    private String loginIdMasked;

    /** 거래내역 자동 동기화를 쓸지 사용자가 정한 설정. 설정 값만 저장하고 실제 주기 실행은 별도다. */
    @Column(name = "auto_sync_enabled", nullable = false)
    private boolean autoSyncEnabled = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /**
     * 카드 연동 화면에서 연결한 카드사 계정. 업종은 카드(CD), 고객 구분은 개인(P), 로그인 방식은 아이디(1)로 고정이라
     * 호출하는 쪽이 이 값들을 알 필요가 없다.
     */
    public static CodefOrgAccount connected(CodefConnection connection, String organization, LocalDateTime now) {
        return new CodefOrgAccount(connection, null, organization, "CD", "P", "1", STATUS_ACTIVE, now);
    }

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

    /** 거래 수집을 마친 기간을 기록한다. 기록을 지우려면 모두 null로 넘긴다. */
    public void recordCollectedRange(LocalDate from, LocalDate to, String cardIds) {
        this.txCollectedFrom = from;
        this.txCollectedTo = to;
        this.txCollectedCardIds = cardIds;
    }

    /** 연결(재연결)할 때 가린 아이디를 남긴다. 값이 없으면 기존 기록을 그대로 둔다. */
    public void rememberLoginId(String maskedLoginId) {
        if (maskedLoginId != null) {
            this.loginIdMasked = maskedLoginId;
        }
    }

    public void changeAutoSync(boolean enabled) {
        this.autoSyncEnabled = enabled;
    }

    /**
     * 연결을 해제한다. 행은 지우지 않고(동기화 작업 기록이 참조한다) 해제 상태로 표시하며, 수집 기록과 화면용 아이디는
     * 지운다. 다시 연결하면 새 계정 행이 만들어진다.
     */
    public void disconnect() {
        this.status = STATUS_DELETED;
        this.lastErrorCode = null;
        this.loginIdMasked = null;
        recordCollectedRange(null, null, null);
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
