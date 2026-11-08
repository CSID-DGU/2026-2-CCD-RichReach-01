package com.richreach.transaction.repository;

import com.richreach.transaction.entity.CardTransaction;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CardTransactionRepository extends JpaRepository<CardTransaction, Long> {

    /** 보유카드 한 장의 거래 중 주어진 키에 해당하는 것. 재동기화 때 이미 저장된 거래를 찾는 데 쓴다. */
    List<CardTransaction> findByUserCardIdAndDedupeKeyIn(Long userCardId, Collection<String> dedupeKeys);

    List<CardTransaction> findByUserCardId(Long userCardId);

    List<CardTransaction> findByUserId(Long userId);

}
