package com.richreach.transaction.repository;

import com.richreach.transaction.entity.CardTransaction;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CardTransactionRepository extends JpaRepository<CardTransaction, Long>,
    JpaSpecificationExecutor<CardTransaction> {

    /** 사용자의 거래 한 건. 다른 사용자의 거래이거나 없으면 비어 있다. */
    Optional<CardTransaction> findByIdAndUserId(Long id, Long userId);

    /** 보유카드 한 장의 거래 중 주어진 키에 해당하는 것. 재동기화 때 이미 저장된 거래를 찾는 데 쓴다. */
    List<CardTransaction> findByUserCardIdAndDedupeKeyIn(Long userCardId, Collection<String> dedupeKeys);

    List<CardTransaction> findByUserCardId(Long userCardId);

    List<CardTransaction> findByUserId(Long userId);

    /** id가 afterId보다 큰 거래를 id 순서로 한 묶음씩. 모든 거래를 나누어 훑을 때(재분류) 쓴다. */
    List<CardTransaction> findByIdGreaterThanOrderById(Long afterId, Pageable pageable);

    /**
     * 사용자의 거래를 업종별로 모은 합계. 승인일시가 {@code from} 이상 {@code to} 미만인 거래만 모은다.
     * 기간은 승인일 기준이고, 금액은 그 거래의 취소·환불을 뺀 값이다. (취소가 일어난 날이 아니라 원거래의 승인일로 센다.
     * 취소 발생일은 저장하지 않는다.) 거래 수는 전액 취소된 거래만 빼고 센다. 승인 금액이 0원인 거래도 센다.
     */
    @Query("""
        select new com.richreach.transaction.repository.CategoryTotal(
            coalesce(t.categoryOverride, t.category),
            sum(t.amount - t.canceledAmount),
            sum(t.canceledAmount),
            sum(case when t.status = com.richreach.transaction.entity.TransactionStatus.CANCELED then 0L else 1L end))
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
