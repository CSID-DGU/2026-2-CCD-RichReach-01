package com.richreach.transaction.service;

import com.richreach.transaction.entity.CardTransaction;
import com.richreach.transaction.repository.CardTransactionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 가맹점 분류 규칙이 바뀐 뒤 이미 저장된 거래의 자동 분류를 다시 계산한다.
 *
 * <p>규칙은 마이그레이션으로 바꾸므로, 바꾼 뒤 이 서비스를 한 번 실행하면 기존 거래가 새 규칙을 따른다. 거래는 재동기화에서도
 * 새 규칙으로 다시 분류되지만, 다시 수집되지 않는 오래된 거래는 이 서비스로만 바뀐다.
 * <ul>
 *   <li>자동 분류 값(category)만 바꾸고, 사용자가 고친 카테고리와 수집 시각은 건드리지 않는다.</li>
 *   <li>거래가 많아도 메모리가 불어나지 않게 일정한 수씩 나누어, 묶음마다 별도 트랜잭션으로 처리한다.</li>
 *   <li>한계: 저장할 때 입력에 업종이 함께 와서 그대로 쓴 거래도 가맹점명으로 다시 분류된다. 카드사가 업종을 주기 시작하면
 *       그 값을 구분해 두어야 한다.</li>
 * </ul>
 * 가맹점명과 금액은 소비 정보라서 로그에 남기지 않고 건수만 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionReclassifyService {

    private static final int BATCH_SIZE = 500;

    private final CardTransactionRepository transactions;
    private final MerchantCategoryClassifier classifier;
    private final TransactionTemplate transaction;

    /** 한 묶음을 처리한 결과. */
    private record Batch(long lastId, int size, int changed) {

    }

    /**
     * 모든 거래의 자동 분류를 규칙에 맞게 다시 계산한다. 규칙은 먼저 새로 읽는다.
     *
     * @return 분류가 바뀐 거래 수
     */
    public int reclassifyAll() {
        classifier.refresh();
        long lastId = 0;
        int changed = 0;
        int total = 0;
        while (true) {
            long afterId = lastId;
            Batch batch = transaction.execute(status -> reclassifyBatch(afterId));
            if (batch.size() == 0) {
                break;
            }
            lastId = batch.lastId();
            changed += batch.changed();
            total += batch.size();
        }
        log.info("거래 재분류를 마쳤습니다: 대상={}건, 변경={}건", total, changed);
        return changed;
    }

    private Batch reclassifyBatch(long afterId) {
        List<CardTransaction> batch = transactions.findByIdGreaterThanOrderById(afterId, PageRequest.ofSize(BATCH_SIZE));
        int changed = 0;
        for (CardTransaction item : batch) {
            if (item.changeCategory(classifier.classify(item.getMerchantName()).name())) {
                changed++;
            }
        }
        long lastId = batch.isEmpty() ? afterId : batch.get(batch.size() - 1).getId();
        return new Batch(lastId, batch.size(), changed);
    }

}
