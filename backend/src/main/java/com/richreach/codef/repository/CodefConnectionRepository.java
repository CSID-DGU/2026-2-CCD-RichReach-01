package com.richreach.codef.repository;

import com.richreach.codef.entity.CodefConnection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CodefConnectionRepository extends JpaRepository<CodefConnection, Long> {

    Optional<CodefConnection> findByUserId(Long userId);

    /** 회원 탈퇴 때 사용자의 연동을 지운다. 계정과 작업 기록을 먼저 지운 뒤에 불러야 한다. 지운 행 수를 돌려준다. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from CodefConnection c where c.user.id = :userId")
    int deleteByUserId(@Param("userId") Long userId);

}
