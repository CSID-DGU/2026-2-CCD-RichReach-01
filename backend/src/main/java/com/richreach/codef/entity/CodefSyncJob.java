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
import java.time.Duration;
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

    public static final String API_CARD_LIST = "CARD_LIST";

    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_SUCCESS = "SUCCESS";
    /** 성공했지만 가져온 데이터가 없는 경우 */
    public static final String STATUS_NO_DATA = "NO_DATA";
    public static final String STATUS_FAIL = "FAIL";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "org_account_id", nullable = false)
    private CodefOrgAccount orgAccount;

    /** 한 번의 동기화 요청을 묶는 ID. 화면에는 jobId로 내려간다. 묶음이 생기기 전의 이력은 null이다. */
    @Column(name = "batch_id", length = 36)
    private String batchId;

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

    /** IN_PROGRESS, SUCCESS, NO_DATA, FAIL */
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

    /** 한 번의 동기화 요청에 속한, 막 시작한 카드 목록 수집 작업. */
    public static CodefSyncJob startCardList(CodefOrgAccount orgAccount, String batchId, LocalDateTime startedAt) {
        CodefSyncJob job = new CodefSyncJob(orgAccount, API_CARD_LIST, STATUS_IN_PROGRESS, true, startedAt);
        job.batchId = batchId;
        return job;
    }

    /** 카드 목록을 가져왔다. 한 장도 없으면 NO_DATA로 남긴다. */
    public void markFetched(int cardCount, String resultCode, LocalDateTime finishedAt) {
        this.status = cardCount > 0 ? STATUS_SUCCESS : STATUS_NO_DATA;
        this.fetchedCount = cardCount;
        this.resultCode = resultCode;
        this.finishedAt = finishedAt;
    }

    /** 실패로 끝났다. {@code retryable}이 false면 다시 시도하면 안 되는 오류다(계정 잠김 위험 등). */
    public void markFailed(String resultCode, boolean retryable, LocalDateTime finishedAt) {
        this.status = STATUS_FAIL;
        this.resultCode = resultCode;
        this.retryable = retryable;
        this.finishedAt = finishedAt;
    }

    public boolean isInProgress() {
        return STATUS_IN_PROGRESS.equals(status);
    }

    public boolean isFailed() {
        return STATUS_FAIL.equals(status);
    }

    /**
     * 진행 중인 채로 제한 시간이 지났는지. 시작한 지 {@code timeout}이 정확히 지난 순간도 지난 것으로 본다.
     * 새 동기화를 시작할 때의 판정(진행 중인 작업이 있는지)과 상태 조회, 결과 기록이 모두 같은 기준을 쓰도록 한 곳에 둔다.
     */
    public boolean isTimedOut(Duration timeout, LocalDateTime now) {
        return isInProgress() && !startedAt.plus(timeout).isAfter(now);
    }

}
