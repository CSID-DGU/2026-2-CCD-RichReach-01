package com.richreach.codef.repository;

import com.richreach.codef.entity.CodefSyncJob;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CodefSyncJobRepository extends JpaRepository<CodefSyncJob, Long> {

    List<CodefSyncJob> findByOrgAccountIdOrderByStartedAtDesc(Long orgAccountId);

    /** 카드사 계정의 가장 최근 작업. */
    Optional<CodefSyncJob> findFirstByOrgAccountIdAndApiTypeOrderByIdDesc(Long orgAccountId, String apiType);

    /** 사용자의 동기화 묶음. 다른 사용자의 묶음이면 비어 있다. */
    @Query("""
        select j from CodefSyncJob j join fetch j.orgAccount a
        where j.batchId = :batchId and a.connection.user.id = :userId
        order by j.id
        """)
    List<CodefSyncJob> findByBatchIdAndUserId(@Param("batchId") String batchId, @Param("userId") Long userId);

    /** 작업을 실행할 때 필요한 카드사 계정, 연동(connectedId), 사용자를 한 번에 읽는다. */
    @Query("""
        select j from CodefSyncJob j join fetch j.orgAccount a join fetch a.connection c join fetch c.user
        where j.id = :id
        """)
    Optional<CodefSyncJob> findWithAccountById(@Param("id") Long id);

    /** 사용자의 카드사 계정별 가장 최근 카드 목록 수집 작업. */
    @Query("""
        select j from CodefSyncJob j join fetch j.orgAccount a
        where a.connection.user.id = :userId and j.apiType = 'CARD_LIST'
          and j.id = (select max(j2.id) from CodefSyncJob j2 where j2.orgAccount = a and j2.apiType = 'CARD_LIST')
        """)
    List<CodefSyncJob> findLatestCardListJobsByUserId(@Param("userId") Long userId);

    /** 아직 진행 중이면서 {@code since} 이후에 시작한 작업이 있는지. 오래 멈춘 작업은 진행 중으로 보지 않는다. */
    @Query("""
        select count(j) > 0 from CodefSyncJob j
        where j.orgAccount.connection.user.id = :userId and j.status = 'IN_PROGRESS' and j.startedAt > :since
        """)
    boolean existsRunningSince(@Param("userId") Long userId, @Param("since") LocalDateTime since);

}
