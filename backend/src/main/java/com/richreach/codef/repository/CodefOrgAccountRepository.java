package com.richreach.codef.repository;

import com.richreach.codef.entity.CodefOrgAccount;
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

    /** 연동 안에서 해당 기관(카드사)에 등록된 계정. 해제(DELETED)한 계정은 없는 것으로 본다. */
    Optional<CodefOrgAccount> findByConnectionIdAndOrganizationAndStatusNot(Long connectionId, String organization,
                                                                           String status);

}
