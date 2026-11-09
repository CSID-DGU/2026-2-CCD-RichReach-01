package com.richreach.transaction.service;

import com.richreach.global.category.SpendingCategory;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.transaction.dto.TransactionResponse;
import com.richreach.transaction.entity.CardTransaction;
import com.richreach.transaction.repository.CardTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자가 거래의 업종을 직접 고친다. 고친 값은 자동 분류 값과 따로 저장되고, 재동기화나 가맹점 분류 규칙이 바뀌어도
 * 덮어쓰지 않는다. 지우면 자동 분류 값으로 돌아간다.
 *
 * <p>내 거래가 아니거나 없는 거래는 {@code TRANS-001}이다. 취소·환불된 거래도 고칠 수 있다. 업종은 6개 중 하나여야 한다.
 */
@Service
@RequiredArgsConstructor
public class TransactionCategoryService {

    private final CardTransactionRepository transactions;

    @Transactional
    public TransactionResponse change(long userId, long transactionId, SpendingCategory category) {
        CardTransaction transaction = find(userId, transactionId);
        transaction.overrideCategory(category.name());
        return TransactionResponse.from(transaction);
    }

    /** 고친 업종을 지우고 자동 분류 값으로 되돌린다. 고친 적이 없어도 오류 없이 현재 상태를 돌려준다. */
    @Transactional
    public TransactionResponse reset(long userId, long transactionId) {
        CardTransaction transaction = find(userId, transactionId);
        transaction.overrideCategory(null);
        return TransactionResponse.from(transaction);
    }

    private CardTransaction find(long userId, long transactionId) {
        return transactions.findByIdAndUserId(transactionId, userId)
            .orElseThrow(() -> new BusinessException(ErrorCode.TRANSACTION_NOT_FOUND));
    }

}
