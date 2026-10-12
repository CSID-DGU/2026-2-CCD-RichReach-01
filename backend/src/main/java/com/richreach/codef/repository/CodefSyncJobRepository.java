package com.richreach.codef.repository;

import com.richreach.codef.entity.CodefSyncJob;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodefSyncJobRepository extends JpaRepository<CodefSyncJob, Long> {

    List<CodefSyncJob> findByOrgAccountIdOrderByStartedAtDesc(Long orgAccountId);

}
