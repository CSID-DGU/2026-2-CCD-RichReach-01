package com.richreach.transaction.service;

import com.richreach.global.category.SpendingCategory;
import com.richreach.transaction.entity.MerchantCategoryRule;
import com.richreach.transaction.repository.MerchantCategoryRuleRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 가맹점명을 화면용 11개 업종으로 분류한다. 거래를 저장할 때 업종을 정하는 데 쓴다.
 *
 * <p>규칙은 DB({@code merchant_category_rules})에 있고, 작아서 처음 쓸 때 한 번 읽어 둔다. 규칙을 바꾼 뒤에는
 * {@link #refresh()}로 다시 읽는다. 어떤 규칙에도 걸리지 않거나 가맹점명이 없으면 {@code ETC}다.
 * 규칙의 적용 방식(우선순위, 정규화)은 {@link MerchantMatcher}에 있다.
 *
 * <p>규칙 행이 잘못되어 있으면(11개 업종이 아닌 값, 정규화하면 비는 문구) 그 행만 건너뛰고 서버 로그에 남긴다.
 * 규칙 하나가 잘못되었다고 모든 거래 저장이 실패하면 안 되어서다. 시드 규칙이 올바른지는 테스트가 확인한다.
 * 가맹점명은 소비 정보라서 로그에 남기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MerchantCategoryClassifier {

    private final MerchantCategoryRuleRepository rules;

    /** 읽어 둔 분류기를 읽고 비우는 일을 한 번에 하나씩만 하게 하는 잠금. */
    private final Object lock = new Object();

    /** 읽는 쪽은 잠그지 않고 쓰도록 volatile이고, 바꾸는 쪽은 {@link #lock} 안에서만 바꾼다. */
    private volatile MerchantMatcher matcher;

    /** 가맹점명의 업종. 어떤 규칙에도 걸리지 않거나 가맹점명이 없으면 {@link SpendingCategory#ETC}. */
    public SpendingCategory classify(String merchantName) {
        return matcher().match(merchantName).orElse(SpendingCategory.ETC);
    }

    /**
     * 규칙을 다시 읽는다. 마이그레이션으로 규칙을 바꾼 뒤나 테스트에서 쓴다. 읽는 중이면 끝날 때까지 기다린 뒤 비우므로,
     * 이전 규칙을 읽던 작업이 늦게 끝나 낡은 규칙을 다시 저장하는 일이 없다.
     */
    public void refresh() {
        synchronized (lock) {
            matcher = null;
        }
    }

    private MerchantMatcher matcher() {
        MerchantMatcher loaded = matcher;
        if (loaded != null) {
            return loaded;
        }
        synchronized (lock) {
            if (matcher == null) {
                matcher = load();
            }
            return matcher;
        }
    }

    private MerchantMatcher load() {
        List<MerchantMatcher.Rule> loaded = rules.findByActiveTrue().stream()
            .map(this::toRule).flatMap(Optional::stream).toList();
        return new MerchantMatcher(loaded);
    }

    private Optional<MerchantMatcher.Rule> toRule(MerchantCategoryRule rule) {
        Optional<SpendingCategory> category = rule.spendingCategory();
        String pattern = MerchantMatcher.normalize(rule.getPattern());
        if (category.isEmpty() || pattern.isEmpty()) {
            log.error("잘못된 가맹점 분류 규칙을 건너뜁니다: ruleId={}", rule.getId());
            return Optional.empty();
        }
        return Optional.of(new MerchantMatcher.Rule(pattern, rule.getMatchType(), category.get(), rule.getPriority()));
    }

}
