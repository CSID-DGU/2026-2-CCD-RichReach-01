package com.richreach.plan.repository;

import com.richreach.plan.entity.PlanJob;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 플랜 생성 작업의 조회와 상태 갱신.
 *
 * <p>상태를 바꾸는 갱신은 모두 {@code status = 'IN_PROGRESS'} 조건이 붙은 한 번의 UPDATE다. 완료, 실패, 시간 초과가 같은 행을
 * 두고 경쟁해도 DB가 순서대로 처리해서 <b>먼저 이긴 하나만 성공</b>하고, 진 쪽은 0행을 돌려받는다. 그래서 시간 초과로 실패가 기록된
 * 작업이 나중에 완료되는 일이 없다. 반환값(바뀐 행 수)이 1이면 이 호출이 이긴 것이다.
 */
public interface PlanJobRepository extends JpaRepository<PlanJob, Long> {

    /** 사용자의 작업. 없거나 다른 사용자의 작업이면 비어 있다. */
    Optional<PlanJob> findByJobIdAndUserId(String jobId, Long userId);

    /** 사용자의 가장 최근 작업 중 주어진 상태인 것. */
    Optional<PlanJob> findFirstByUserIdAndStatusOrderByIdDesc(Long userId, String status);

    /** 진행률을 올린다. 진행 중이고 값이 커질 때만 바뀌며 완료 전에는 99를 넘지 않는다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update PlanJob j set j.progress = :progress
        where j.id = :id and j.status = 'IN_PROGRESS' and j.progress < :progress
        """)
    int updateProgress(@Param("id") Long id, @Param("progress") int progress);

    /**
     * 시작한 지 오래되지 않은 진행 중 작업만 완료로 바꾼다. {@code cutoff} 이전에 시작한 작업(시간 초과)이나 이미 끝난 작업은
     * 바뀌지 않고 0을 돌려준다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update PlanJob j set j.status = 'COMPLETED', j.progress = 100, j.planId = :planId, j.finishedAt = :now
        where j.id = :id and j.status = 'IN_PROGRESS' and j.startedAt >= :cutoff
        """)
    int completeIfInTime(@Param("id") Long id, @Param("planId") Long planId, @Param("now") LocalDateTime now,
                         @Param("cutoff") LocalDateTime cutoff);

    /** 진행 중인 작업을 실패로 바꾼다. 이미 끝난 작업은 바뀌지 않고 0을 돌려준다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update PlanJob j set j.status = 'FAILED', j.failureCode = :code, j.failureReason = :reason, j.finishedAt = :now
        where j.id = :id and j.status = 'IN_PROGRESS'
        """)
    int failIfInProgress(@Param("id") Long id, @Param("code") String code, @Param("reason") String reason,
                         @Param("now") LocalDateTime now);

    /** {@code cutoff} 이전에 시작한 채 진행 중인 작업을 시간 초과로 실패시킨다. 시간 초과가 아니거나 이미 끝났으면 0이다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        update PlanJob j set j.status = 'FAILED', j.failureCode = :code, j.failureReason = :reason, j.finishedAt = :now
        where j.id = :id and j.status = 'IN_PROGRESS' and j.startedAt < :cutoff
        """)
    int failIfTimedOut(@Param("id") Long id, @Param("code") String code, @Param("reason") String reason,
                       @Param("now") LocalDateTime now, @Param("cutoff") LocalDateTime cutoff);

}
