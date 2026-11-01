package com.richreach.user.repository;

import com.richreach.user.entity.UserTermAgreement;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserTermAgreementRepository extends JpaRepository<UserTermAgreement, Long> {

    List<UserTermAgreement> findByUserId(Long userId);

    /** 사용자가 특정 약관 버전에 이미 동의한 종류들. (같은 버전에 다시 동의해도 행이 중복되지 않게 할 때 쓴다.) */
    List<UserTermAgreement> findByUserIdAndTermVersionAndTermTypeIn(Long userId, String termVersion,
                                                                     Collection<String> termTypes);

}
