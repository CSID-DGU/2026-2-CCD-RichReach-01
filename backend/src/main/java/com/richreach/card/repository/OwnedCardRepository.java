package com.richreach.card.repository;

import com.richreach.card.entity.OwnedCard;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OwnedCardRepository extends JpaRepository<OwnedCard, Long> {

    /** 카드사 계정 하나에서 수집한 모든 카드. 사라진(active=false) 카드도 포함한다. */
    List<OwnedCard> findByOrgAccountId(Long orgAccountId);

    List<OwnedCard> findByUserId(Long userId);

    /** 사용자의 활성 보유카드. 카드 상품을 한 번에 읽어 목록을 만들 때 카드마다 쿼리가 나가지 않게 한다. */
    @Query("""
        select c from OwnedCard c left join fetch c.cardProduct
        where c.userId = :userId and c.active = true
        order by c.id
        """)
    List<OwnedCard> findActiveByUserId(@Param("userId") Long userId);

}
