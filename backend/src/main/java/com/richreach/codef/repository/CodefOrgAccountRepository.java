package com.richreach.codef.repository;

import com.richreach.codef.dto.AutoSyncCandidate;
import com.richreach.codef.entity.CodefOrgAccount;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CodefOrgAccountRepository extends JpaRepository<CodefOrgAccount, Long> {

    List<CodefOrgAccount> findByConnectionId(Long connectionId);

    /** 사용자의 해제하지 않은 카드사 계정 전부. */
    @Query("""
        select a from CodefOrgAccount a
        where a.connection.user.id = :userId and a.status <> 'DELETED'
        order by a.id
        """)
    List<CodefOrgAccount> findActiveByUserId(@Param("userId") Long userId);

    /** 사용자의 카드사 계정 중 {@code ids}에 해당하는 것. 다른 사용자의 계정이나 해제한 계정은 나오지 않는다. */
    @Query("""
        select a from CodefOrgAccount a
        where a.connection.user.id = :userId and a.id in :ids and a.status <> 'DELETED'
        order by a.id
        """)
    List<CodefOrgAccount> findActiveByUserIdAndIdIn(@Param("userId") Long userId, @Param("ids") Collection<Long> ids);

    /**
     * 자동 동기화 대상 계정. 연결이 정상(ACTIVE)이고 사용자가 자동 동기화를 켰으며, {@code since} 이후에 카드 목록 수집을
     * 시작한 적이 없는 계정이다. 성공과 실패를 가리지 않고 최근에 시도한 계정은 뺀다. 사용자가 방금 직접 동기화했거나 오늘 이미
     * 실패한 계정을 다시 부르지 않기 위해서다. 한 번도 수집한 적이 없는 계정은 포함한다. 사용자, 계정 순서로 정렬한다.
     */
    @Query("""
        select new com.richreach.codef.dto.AutoSyncCandidate(a.id, a.connection.user.id)
        from CodefOrgAccount a
        where a.status = 'ACTIVE' and a.autoSyncEnabled = true
          and not exists (
            select 1 from CodefSyncJob j
            where j.orgAccount = a and j.apiType = 'CARD_LIST' and j.startedAt > :since)
        order by a.connection.user.id, a.id
        """)
    List<AutoSyncCandidate> findAutoSyncCandidates(@Param("since") LocalDateTime since);

    /** 연동 안에서 해당 기관(카드사)에 등록된 계정. 해제(DELETED)한 계정은 없는 것으로 본다. */
    Optional<CodefOrgAccount> findByConnectionIdAndOrganizationAndStatusNot(Long connectionId, String organization,
                                                                           String status);

}
