package com.richreach.codef.repository;

import com.richreach.codef.entity.CodefConnection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CodefConnectionRepository extends JpaRepository<CodefConnection, Long> {

    Optional<CodefConnection> findByUserId(Long userId);

}
