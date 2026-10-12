package com.richreach.codef.repository;

import com.richreach.codef.entity.CodefOrgAccount;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodefOrgAccountRepository extends JpaRepository<CodefOrgAccount, Long> {

    List<CodefOrgAccount> findByConnectionId(Long connectionId);

}
