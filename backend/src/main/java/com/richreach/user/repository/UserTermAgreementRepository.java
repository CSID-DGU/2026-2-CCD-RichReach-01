package com.richreach.user.repository;

import com.richreach.user.entity.UserTermAgreement;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTermAgreementRepository extends JpaRepository<UserTermAgreement, Long> {

    List<UserTermAgreement> findByUserId(Long userId);

}
