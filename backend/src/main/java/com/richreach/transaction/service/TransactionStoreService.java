package com.richreach.transaction.service;

import com.richreach.global.util.Texts;
import com.richreach.global.util.Times;
import com.richreach.transaction.dto.CollectedTransaction;
import com.richreach.transaction.entity.CardTransaction;
import com.richreach.transaction.repository.CardTransactionRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수집한 거래를 저장한다. 보유카드 한 장의 거래 목록을 받아서, 처음 보는 거래는 새로 저장하고 이미 있는 거래는
 * 바뀐 것만 갱신한다. (재동기화에서 같은 거래를 두 번 만들지 않는다.)
 *
 * <ul>
 *   <li>같은 거래는 {@link TransactionKeys}의 키로 구분한다.</li>
 *   <li>취소·환불은 원거래의 취소 금액과 상태를 갱신한다. 새 거래를 만들지 않는다.</li>
 *   <li>업종 카테고리는 입력에 있으면 그대로 쓰고, 없으면 가맹점명으로 자동 분류한다.
 *       ({@link MerchantCategoryClassifier}. 어떤 규칙에도 안 걸리면 ETC) 재동기화는 자동 분류 값을 갱신하지만
 *       사용자가 고친 카테고리는 건드리지 않는다.</li>
 *   <li>승인일시가 없거나 금액이 음수인 항목은 저장하지 않고 건수만 센다.</li>
 *   <li>컬럼 길이를 넘는 값(가맹점명 등)은 잘라서 저장한다. 거래를 버리지 않는다.</li>
 * </ul>
 * 가맹점명과 금액은 소비 정보라서 로그에 남기지 않고 건수만 돌려준다. 같은 보유카드를 동시에 저장하는 일은 동기화가 사용자
 * 단위로 순서대로 처리하므로 막지 않는다. (겹치면 유일 키 위반으로 한쪽이 실패한다.)
 */
@Service
@RequiredArgsConstructor
public class TransactionStoreService {

    private final CardTransactionRepository transactions;
    private final MerchantCategoryClassifier classifier;
    private final Clock clock;

    /**
     * 저장 결과 건수. 동기화 작업 기록(inserted_count, updated_count)에 남길 수 있다.
     *
     * @param created   새로 저장한 거래
     * @param updated   이미 있던 거래 중 바뀐 것
     * @param unchanged 이미 있던 거래 중 바뀌지 않은 것
     * @param skipped   저장하지 못한 항목(승인일시가 없거나 금액이 음수)
     */
    public record SaveResult(int created, int updated, int unchanged, int skipped) {

    }

    /** 다듬은 입력. 키는 순번까지 포함해 만든다. */
    private record Prepared(String key, String approvalNo, LocalDateTime approvedAt, String merchantName, long amount,
                            long canceledAmount, int installmentMonths, boolean overseas, String category) {

    }

    /** 보유카드 한 장의 수집한 거래를 저장한다. 사용자와 보유카드는 호출하는 쪽(동기화)이 맞게 넘긴다. */
    @Transactional
    public SaveResult saveCollected(long userId, long userCardId, List<CollectedTransaction> collected) {
        List<Prepared> prepared = new ArrayList<>();
        int skipped = 0;
        Map<String, Integer> occurrences = new HashMap<>();
        for (CollectedTransaction item : collected) {
            if (item == null || item.approvedAt() == null || item.amount() < 0) {
                skipped++;
                continue;
            }
            String approvalNo = fit(item.approvalNo(), CardTransaction.APPROVAL_NO_MAX_LENGTH);
            String merchantName = fit(item.merchantName(), CardTransaction.MERCHANT_NAME_MAX_LENGTH);
            // 입력에 업종이 있으면 그대로 쓰고, 없으면 가맹점명으로 분류한다. (어디에도 안 맞으면 ETC)
            String category = fit(item.category(), CardTransaction.CATEGORY_MAX_LENGTH);
            if (category.isEmpty()) {
                category = classifier.classify(merchantName).name();
            }

            String base = TransactionKeys.base(approvalNo, item.approvedAt(), item.amount(), merchantName);
            int occurrence = occurrences.merge(base, 1, Integer::sum) - 1;
            prepared.add(new Prepared(TransactionKeys.key(base, occurrence), approvalNo, item.approvedAt(),
                merchantName, item.amount(), item.canceledAmount(), item.installmentMonths(), item.overseas(),
                category));
        }
        if (prepared.isEmpty()) {
            return new SaveResult(0, 0, 0, skipped);
        }

        Map<String, CardTransaction> existing = transactions.findByUserCardIdAndDedupeKeyIn(userCardId,
                prepared.stream().map(Prepared::key).toList()).stream()
            .collect(Collectors.toMap(CardTransaction::getDedupeKey, Function.identity()));

        LocalDateTime now = Times.nowSeconds(clock);
        List<CardTransaction> toCreate = new ArrayList<>();
        int updated = 0;
        int unchanged = 0;
        for (Prepared item : prepared) {
            CardTransaction found = existing.get(item.key());
            if (found == null) {
                toCreate.add(CardTransaction.collected(userId, userCardId, item.key(), item.approvalNo(),
                    item.approvedAt(), item.merchantName(), item.amount(), item.canceledAmount(),
                    item.installmentMonths(), item.overseas(), item.category(), now));
            } else if (found.refresh(item.merchantName(), item.amount(), item.canceledAmount(),
                item.installmentMonths(), item.overseas(), item.category(), now)) {
                updated++;
            } else {
                unchanged++;
            }
        }
        transactions.saveAll(toCreate);
        return new SaveResult(toCreate.size(), updated, unchanged, skipped);
    }

    /** 앞뒤 공백을 없애고 컬럼 길이에 맞게 자른다. 없으면 빈 문자열이다. */
    private static String fit(String text, int maxLength) {
        return text == null ? "" : Texts.fit(text.strip(), maxLength);
    }

}
