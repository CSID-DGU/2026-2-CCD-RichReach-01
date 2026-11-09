package com.richreach.user.repository;

import com.richreach.user.entity.User;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    /** 이름과 (숫자만 남긴) 전화번호가 모두 같은 계정. 전화번호는 유일해서 최대 한 명이다. */
    Optional<User> findByNameAndPhone(String name, String phone);

    /** 같은 계정으로 동시에 재설정해도 한 번만 바뀌도록 행을 잠그고 읽는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    /** 같은 계정으로 동시에 로그인을 시도해도 실패 횟수가 어긋나지 않도록 행을 잠그고 읽는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.email = :email")
    Optional<User> findByEmailForUpdate(@Param("email") String email);

    boolean existsByEmail(String email);

    boolean existsByPhone(String phone);

}
