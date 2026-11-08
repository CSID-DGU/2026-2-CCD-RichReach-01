package com.richreach.transaction.repository;

import com.richreach.transaction.entity.CardTransaction;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardTransactionRepository extends JpaRepository<CardTransaction, Long> {

    /** 보유카드 한 장의 거래 중 주어진 키에 해당하는 것. 재동기화 때 이미 저장된 거래를 찾는 데 쓴다. */
    List<CardTransaction> findByUserCardIdAndDedupeKeyIn(Long userCardId, Collection<String> dedupeKeys);

    List<CardTransaction> findByUserCardId(Long userCardId);

    List<CardTransaction> findByUserId(Long userId);

    /**
     * 사용자의 거래를 업종별로 모은 합계. 승인일시가 {@code from} 이상 {@code to} 미만인 거래만 모은다.
     * 금액은 취소·환불을 뺀 값이고, 거래 수는 취소·환불을 빼고도 금액이 남은 거래만 센다.
     */
    @Query("""
        select new com.richreach.transaction.repository.CategoryTotal(
            coalesce(t.categoryOverride, t.category),
            sum(t.amount - t.canceledAmount),
            sum(t.canceledAmount),
            sum(case when t.amount - t.canceledAmount > 0 then 1L else 0L end))
        from CardTransaction t
        where t.userId = :userId and t.approvedAt >= :from and t.approvedAt < :to
        group by coalesce(t.categoryOverride, t.category)
        """)
    List<CategoryTotal> sumByCategory(@Param("userId") Long userId, @Param("from") LocalDateTime from,
                                      @Param("to") LocalDateTime to);

    /** 사용자의 거래 중 가장 오래된 승인일시. 거래가 없으면 null. */
    @Query("select min(t.approvedAt) from CardTransaction t where t.userId = :userId")
    LocalDateTime findEarliestApprovedAt(@Param("userId") Long userId);

}
