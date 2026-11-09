package com.richreach.codef.repository;

import com.richreach.codef.entity.CodefOrgPolicy;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodefOrgPolicyRepository extends JpaRepository<CodefOrgPolicy, Long> {

    Optional<CodefOrgPolicy> findByOrganizationAndApiType(String organization, String apiType);

}
