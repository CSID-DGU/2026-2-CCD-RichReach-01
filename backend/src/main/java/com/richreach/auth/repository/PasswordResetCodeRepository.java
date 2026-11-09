package com.richreach.auth.repository;

import com.richreach.auth.entity.PasswordResetCode;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

    Optional<PasswordResetCode> findByUserId(Long userId);

    /** 같은 인증번호로 동시에 확인해도 틀린 횟수가 어긋나지 않도록 행을 잠그고 읽는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PasswordResetCode c where c.userId = :userId")
    Optional<PasswordResetCode> findByUserIdForUpdate(@Param("userId") Long userId);

}
