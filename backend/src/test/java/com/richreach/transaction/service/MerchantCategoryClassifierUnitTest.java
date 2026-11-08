package com.richreach.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.global.category.SpendingCategory;
import com.richreach.transaction.entity.MatchType;
import com.richreach.transaction.entity.MerchantCategoryRule;
import com.richreach.transaction.repository.MerchantCategoryRuleRepository;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 분류기의 규칙 읽기와 다시 읽기를 DB 없이 가짜 리포지토리로 확인한다. */
class MerchantCategoryClassifierUnitTest {

    /** 규칙 한 줄. Mockito 목을 만들 때 다른 스터빙 안에서 부르지 않도록, 리포지토리 스터빙 전에 변수로 만들어 둔다. */
    private static MerchantCategoryRule rule(long id, String pattern, MatchType type, String uiCategory) {
        MerchantCategoryRule rule = mock(MerchantCategoryRule.class);
        when(rule.getId()).thenReturn(id);
        when(rule.getPattern()).thenReturn(pattern);
        when(rule.getMatchType()).thenReturn(type);
        when(rule.getPriority()).thenReturn(100);
        when(rule.spendingCategory()).thenReturn(SpendingCategory.fromCode(uiCategory));
        return rule;
    }

    @Test
    @DisplayName("규칙이 하나도 안 걸리면 ETC이고, 가맹점명이 없어도 ETC다")
    void defaultsToEtc() {
        MerchantCategoryRule starbucks = rule(1, "스타벅스", MatchType.CONTAINS, "CAFE");
        MerchantCategoryRuleRepository repository = mock(MerchantCategoryRuleRepository.class);
        when(repository.findByActiveTrue()).thenReturn(List.of(starbucks));
        MerchantCategoryClassifier classifier = new MerchantCategoryClassifier(repository);

        assertThat(classifier.classify("스타벅스 강남점")).isEqualTo(SpendingCategory.CAFE);
        assertThat(classifier.classify("알 수 없는 상점")).isEqualTo(SpendingCategory.ETC);
        assertThat(classifier.classify(null)).isEqualTo(SpendingCategory.ETC);
        assertThat(classifier.classify("  ")).isEqualTo(SpendingCategory.ETC);
    }

    @Test
    @DisplayName("잘못된 규칙(6개 업종이 아닌 값, 정규화하면 비는 문구)은 그 규칙만 건너뛰고 나머지는 쓴다")
    void skipsInvalidRules() {
        MerchantCategoryRule good = rule(1, "스타벅스", MatchType.CONTAINS, "CAFE");
        MerchantCategoryRule badCategory = rule(2, "잘못된업종", MatchType.CONTAINS, "NOT_A_CATEGORY");
        MerchantCategoryRule badPattern = rule(3, "-*-", MatchType.CONTAINS, "DINING");
        MerchantCategoryRuleRepository repository = mock(MerchantCategoryRuleRepository.class);
        when(repository.findByActiveTrue()).thenReturn(List.of(good, badCategory, badPattern));
        MerchantCategoryClassifier classifier = new MerchantCategoryClassifier(repository);

        assertThat(classifier.classify("스타벅스")).isEqualTo(SpendingCategory.CAFE);
        assertThat(classifier.classify("잘못된업종 상점")).isEqualTo(SpendingCategory.ETC);
        assertThat(classifier.classify("아무 상점")).isEqualTo(SpendingCategory.ETC);
    }

    @Test
    @DisplayName("규칙은 한 번만 읽고, refresh 뒤에는 다시 읽는다")
    void loadsOnceAndReloadsAfterRefresh() {
        MerchantCategoryRule oldRule = rule(1, "테스트상점", MatchType.CONTAINS, "CAFE");
        MerchantCategoryRule newRule = rule(1, "테스트상점", MatchType.CONTAINS, "DINING");
        MerchantCategoryRuleRepository repository = mock(MerchantCategoryRuleRepository.class);
        when(repository.findByActiveTrue()).thenReturn(List.of(oldRule)).thenReturn(List.of(newRule));
        MerchantCategoryClassifier classifier = new MerchantCategoryClassifier(repository);

        assertThat(classifier.classify("테스트상점")).isEqualTo(SpendingCategory.CAFE);
        assertThat(classifier.classify("테스트상점")).isEqualTo(SpendingCategory.CAFE);
        verify(repository, times(1)).findByActiveTrue();

        classifier.refresh();

        assertThat(classifier.classify("테스트상점")).isEqualTo(SpendingCategory.DINING);
        verify(repository, times(2)).findByActiveTrue();
    }

    @Test
    @DisplayName("이전 규칙을 읽는 도중에 refresh가 끝나도, 읽기가 끝난 뒤 낡은 규칙이 캐시에 남지 않는다")
    void refreshDuringLoadDoesNotLeaveStaleRules() throws Exception {
        MerchantCategoryRule oldRule = rule(1, "테스트상점", MatchType.CONTAINS, "CAFE");
        MerchantCategoryRule newRule = rule(1, "테스트상점", MatchType.CONTAINS, "DINING");
        MerchantCategoryRuleRepository repository = mock(MerchantCategoryRuleRepository.class);
        CountDownLatch loadStarted = new CountDownLatch(1);
        CountDownLatch letLoadFinish = new CountDownLatch(1);
        when(repository.findByActiveTrue()).thenAnswer(invocation -> {
            loadStarted.countDown();
            letLoadFinish.await(5, TimeUnit.SECONDS);
            return List.of(oldRule);
        }).thenReturn(List.of(newRule));
        MerchantCategoryClassifier classifier = new MerchantCategoryClassifier(repository);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> reader = pool.submit(() -> classifier.classify("테스트상점"));
            assertThat(loadStarted.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> refresher = pool.submit(classifier::refresh);
            Thread.sleep(300);
            letLoadFinish.countDown();
            reader.get(5, TimeUnit.SECONDS);
            refresher.get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(classifier.classify("테스트상점")).isEqualTo(SpendingCategory.DINING);
    }

}
