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

/** 기관 계정별 동기화(데이터 수집) 작업 이력. */
@Entity
@Table(name = "codef_sync_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CodefSyncJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_account_id", nullable = false)
    private CodefOrgAccount orgAccount;

    /** CARD_LIST, APPROVAL, RESULT_CHECK, BILLING */
    @Column(name = "api_type", nullable = false, length = 30)
    private String apiType;

    @Column(name = "req_start_date", length = 8)
    private String reqStartDate;

    @Column(name = "req_end_date", length = 8)
    private String reqEndDate;

    @Column(name = "comm_start_date", length = 8)
    private String commStartDate;

    @Column(name = "comm_end_date", length = 8)
    private String commEndDate;

    @Column(name = "result_code", length = 20)
    private String resultCode;

    @Column(name = "transaction_id", length = 100)
    private String transactionId;

    @Column(name = "fetched_count")
    private Integer fetchedCount;

    @Column(name = "inserted_count")
    private Integer insertedCount;

    @Column(name = "updated_count")
    private Integer updatedCount;

    /** SUCCESS, FAIL, NO_DATA */
    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private boolean retryable;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    public CodefSyncJob(CodefOrgAccount orgAccount, String apiType, String status, boolean retryable,
        LocalDateTime startedAt) {
        this.orgAccount = orgAccount;
        this.apiType = apiType;
        this.status = status;
        this.retryable = retryable;
        this.startedAt = startedAt;
    }

}
