package com.richreach.card.repository;

import com.richreach.card.entity.OwnedCard;
import jakarta.persistence.LockModeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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

    /**
     * 사용자의 활성 보유카드를 쓰기 잠금으로 읽는다. 같은 사용자의 선택 변경 요청이 동시에 와도 한 번에 하나씩 처리되게 한다.
     * 카드 상품은 여러 사용자가 함께 쓰는 행이라 잠기지 않도록 가져오지 않는다. id 순서로 잠가서 교착이 생기지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from OwnedCard c where c.userId = :userId and c.active = true order by c.id")
    List<OwnedCard> findActiveByUserIdForUpdate(@Param("userId") Long userId);

}
