package com.richreach.card.repository;

import com.richreach.card.entity.OwnedCard;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OwnedCardRepository extends JpaRepository<OwnedCard, Long> {

    /** 카드사 계정 하나에서 수집한 모든 카드. 사라진(active=false) 카드도 포함한다. */
    List<OwnedCard> findByOrgAccountId(Long orgAccountId);

    List<OwnedCard> findByUserId(Long userId);

}
