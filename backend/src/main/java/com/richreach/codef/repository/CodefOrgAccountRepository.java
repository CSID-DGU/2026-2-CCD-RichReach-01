package com.richreach.codef.repository;

import com.richreach.codef.entity.CodefOrgAccount;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodefOrgAccountRepository extends JpaRepository<CodefOrgAccount, Long> {

    List<CodefOrgAccount> findByConnectionId(Long connectionId);

    /** 연동 안에서 해당 기관(카드사)에 등록된 계정. 해제(DELETED)한 계정은 없는 것으로 본다. */
    Optional<CodefOrgAccount> findByConnectionIdAndOrganizationAndStatusNot(Long connectionId, String organization,
                                                                           String status);

}
