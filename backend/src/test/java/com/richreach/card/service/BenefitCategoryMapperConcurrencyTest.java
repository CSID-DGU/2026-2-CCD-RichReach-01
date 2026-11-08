package com.richreach.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.richreach.card.entity.BenefitCategoryMapping;
import com.richreach.card.repository.BenefitCategoryMappingRepository;
import com.richreach.global.category.SpendingCategory;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 대응표를 읽는 도중에 다시 읽기(refresh)를 요청해도 낡은 표가 캐시에 남지 않는지 본다. DB 없이 가짜 리포지토리로 확인한다. */
class BenefitCategoryMapperConcurrencyTest {

    private static BenefitCategoryMapping row(String code, SpendingCategory category) {
        BenefitCategoryMapping mapping = mock(BenefitCategoryMapping.class);
        when(mapping.getCategoryCode()).thenReturn(code);
        when(mapping.spendingCategory()).thenReturn(Optional.of(category));
        return mapping;
    }

    @Test
    @DisplayName("이전 데이터를 읽는 도중에 refresh가 끝나도, 읽기가 끝난 뒤 낡은 표가 캐시에 남지 않고 다음 변환은 새 표를 읽는다")
    void refreshDuringLoadDoesNotLeaveStaleTable() throws Exception {
        BenefitCategoryMappingRepository repository = mock(BenefitCategoryMappingRepository.class);
        CountDownLatch loadStarted = new CountDownLatch(1);
        CountDownLatch letLoadFinish = new CountDownLatch(1);
        when(repository.findAll()).thenAnswer(invocation -> {
            loadStarted.countDown();
            letLoadFinish.await(5, TimeUnit.SECONDS);
            return List.of(row("TEST_CODE", SpendingCategory.CAFE)); // 이전 데이터
        }).thenReturn(List.of(row("TEST_CODE", SpendingCategory.DINING))); // 갱신된 데이터
        BenefitCategoryMapper mapper = new BenefitCategoryMapper(repository);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<?> reader = pool.submit(() -> mapper.map("TEST_CODE"));
            assertThat(loadStarted.await(5, TimeUnit.SECONDS)).isTrue();

            Future<?> refresher = pool.submit(mapper::refresh); // 읽는 도중에 refresh를 요청한다
            Thread.sleep(300); // refresh가 먼저 실행을 시도할 시간을 준다
            letLoadFinish.countDown();
            reader.get(5, TimeUnit.SECONDS);
            refresher.get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        assertThat(mapper.map("TEST_CODE").categories()).containsExactly(SpendingCategory.DINING);
    }

}
