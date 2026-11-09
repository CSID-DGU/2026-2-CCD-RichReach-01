package com.richreach.transaction.service;

import com.richreach.global.category.SpendingCategory;
import com.richreach.transaction.entity.CardTransaction;
import com.richreach.transaction.entity.TransactionStatus;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * 거래 목록의 조회 조건. 조건이 있는 것만 더한다. 항상 한 사용자의 거래만 본다.
 *
 * <p>업종 조건은 사용자가 고친 값이 있으면 그 값, 없으면 자동 분류 값(화면에 보이는 업종)으로 비교한다.
 * {@code ETC}는 분류 전(NULL)이거나 6개 업종이 아닌 값도 포함한다. 화면에서 ETC로 보이는 거래와 같게 하려는 것이다.
 */
final class TransactionSpecifications {

    /** ETC를 뺀 5개 업종의 코드. ETC 조건에서 "다른 업종이 아닌 것"을 가를 때 쓴다. */
    private static final List<String> NON_ETC_CODES = Arrays.stream(SpendingCategory.values())
        .filter(category -> category != SpendingCategory.ETC).map(Enum::name).toList();

    private TransactionSpecifications() {
    }

    /**
     * @param from            이 일시 이상. 없으면 제한 없음
     * @param to              이 일시 미만. 없으면 제한 없음
     * @param cardId          이 보유카드의 거래만. 없으면 모든 카드
     * @param cardIds         이 보유카드들의 거래만. 없으면(null) 제한 없음. 비어 있으면 어떤 거래도 맞지 않는다.
     * @param category        이 업종으로 보이는 거래만. 없으면 모든 업종
     * @param includeCanceled false이면 전액 취소된 거래를 뺀다.
     */
    static Specification<CardTransaction> filter(long userId, LocalDateTime from, LocalDateTime to, Long cardId,
                                                 Collection<Long> cardIds, SpendingCategory category,
                                                 boolean includeCanceled) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("userId"), userId));
            if (from != null) {
                predicates.add(builder.greaterThanOrEqualTo(root.<LocalDateTime>get("approvedAt"), from));
            }
            if (to != null) {
                predicates.add(builder.lessThan(root.<LocalDateTime>get("approvedAt"), to));
            }
            if (cardId != null) {
                predicates.add(builder.equal(root.get("userCardId"), cardId));
            }
            if (cardIds != null) {
                predicates.add(cardIds.isEmpty() ? builder.disjunction() : root.get("userCardId").in(cardIds));
            }
            if (category != null) {
                Expression<String> shown = builder.coalesce(root.<String>get("categoryOverride"),
                    root.<String>get("category"));
                if (category == SpendingCategory.ETC) {
                    predicates.add(builder.or(builder.isNull(shown), builder.not(shown.in(NON_ETC_CODES))));
                } else {
                    predicates.add(builder.equal(shown, category.name()));
                }
            }
            if (!includeCanceled) {
                predicates.add(builder.notEqual(root.get("status"), TransactionStatus.CANCELED));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

}
