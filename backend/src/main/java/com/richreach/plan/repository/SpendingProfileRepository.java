package com.richreach.plan.repository;

import com.richreach.plan.entity.SpendingProfile;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpendingProfileRepository extends JpaRepository<SpendingProfile, Long> {

    /** 사용자의 대상 월 프로필 중 가장 최근 것 */
    Optional<SpendingProfile> findFirstByUserIdAndTargetMonthOrderByIdDesc(Long userId, String targetMonth);
}
