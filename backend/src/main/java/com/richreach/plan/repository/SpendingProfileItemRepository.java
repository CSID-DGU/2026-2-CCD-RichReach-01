package com.richreach.plan.repository;

import com.richreach.plan.entity.SpendingProfileItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpendingProfileItemRepository extends JpaRepository<SpendingProfileItem, Long> {

    List<SpendingProfileItem> findByProfileId(Long profileId);
}
