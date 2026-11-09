package com.richreach.plan.entity;

import com.richreach.global.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;

/**
 * 월간 플랜 생성 작업 한 건. 요청한 예산도 가지고 있어서, 실행 스레드가 요청 없이 작업만 보고 플랜을 만들 수 있다.
 * 예산 금액은 소비 정보라서 {@code toString}을 만들지 않고 로그에 남기지 않는다.
 *
 * <p>상태는 IN_PROGRESS에서 COMPLETED 또는 FAILED로만 바뀌고, 끝난 작업은 다시 바뀌지 않는다.
 * {@link DynamicUpdate}로 바뀐 컬럼만 UPDATE해서, 진행률을 갱신하는 쪽과 결과를 기록하는 쪽이 서로의 값을 덮어쓰지 않는다.
 */
@Entity
@DynamicUpdate
@Table(name = "plan_jobs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanJob extends BaseEntity {

    public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
    public static final String STATUS_COMPLETED = "COMPLETED";
    public static final String STATUS_FAILED = "FAILED";

    /** plan_jobs.failure_reason의 컬럼 길이(V23)와 같아야 한다. */
    public static final int FAILURE_REASON_MAX_LENGTH = 500;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 화면에 내려가는 작업 ID(UUID) */
    @Column(name = "job_id", nullable = false, updatable = false, length = 36, columnDefinition = "CHAR(36)")
    private String jobId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "plan_month", nullable = false, updatable = false, length = 7, columnDefinition = "CHAR(7)")
    private String planMonth;

    @Column(name = "budget_total", nullable = false, updatable = false)
    private long budgetTotal;

    /** 업종:금액을 쉼표로 이은 값 */
    @Column(name = "budget_encoded", nullable = false, updatable = false, length = 600)
    private String budgetEncoded;

    @Column(name = "request_hash", nullable = false, updatable = false, length = 64, columnDefinition = "CHAR(64)")
    private String requestHash;

    @Column(nullable = false, length = 20)
    private String status;

    @Column(nullable = false)
    private int progress;

    @Column(name = "plan_id")
    private Long planId;

    @Column(name = "failure_code", length = 40)
    private String failureCode;

    @Column(name = "failure_reason", length = FAILURE_REASON_MAX_LENGTH)
    private String failureReason;

    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    /** 막 시작한 작업. */
    public static PlanJob start(long userId, String jobId, String planMonth, long budgetTotal, String budgetEncoded,
                                String requestHash, LocalDateTime now) {
        PlanJob job = new PlanJob();
        job.userId = userId;
        job.jobId = jobId;
        job.planMonth = planMonth;
        job.budgetTotal = budgetTotal;
        job.budgetEncoded = budgetEncoded;
        job.requestHash = requestHash;
        job.status = STATUS_IN_PROGRESS;
        job.progress = 0;
        job.startedAt = now;
        return job;
    }

    public boolean isInProgress() {
        return STATUS_IN_PROGRESS.equals(status);
    }

    /** 진행 중인 채로 제한 시간이 지나 멈춘 것으로 보는 작업인지. */
    public boolean isTimedOut(Duration timeout, LocalDateTime now) {
        return isInProgress() && startedAt.plus(timeout).isBefore(now);
    }

    /** 진행률을 올린다. 진행 중일 때만, 그리고 값이 커질 때만 바뀌고 0~99로 제한한다. (100은 완료할 때만 된다.) */
    public void updateProgress(int value) {
        if (isInProgress() && value > progress) {
            this.progress = Math.min(value, 99);
        }
    }

    /** 플랜을 저장하고 끝났다. */
    public void complete(long planId, LocalDateTime now) {
        this.status = STATUS_COMPLETED;
        this.progress = 100;
        this.planId = planId;
        this.finishedAt = now;
    }

    /** 실패로 끝났다. 이유가 길면 컬럼 길이에 맞게 자른다. */
    public void fail(String code, String reason, LocalDateTime now) {
        this.status = STATUS_FAILED;
        this.failureCode = code;
        this.failureReason = reason == null || reason.length() <= FAILURE_REASON_MAX_LENGTH
            ? reason : reason.substring(0, FAILURE_REASON_MAX_LENGTH);
        this.finishedAt = now;
    }

}
