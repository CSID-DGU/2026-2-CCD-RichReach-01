package com.richreach.codef.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CodefAutoSyncSchedulerTest {

    @Test
    @DisplayName("스케줄이 돌면 자동 동기화를 한 번 실행한다")
    void runsTheAutoSync() {
        CodefAutoSyncService autoSync = mock(CodefAutoSyncService.class);

        new CodefAutoSyncScheduler(autoSync).run();

        verify(autoSync).runOnce();
    }

    @Test
    @DisplayName("실행 중 오류가 나도 예외를 밖으로 내보내지 않는다")
    void swallowsFailures() {
        CodefAutoSyncService autoSync = mock(CodefAutoSyncService.class);
        doThrow(new IllegalStateException("boom")).when(autoSync).runOnce();

        assertThatCode(() -> new CodefAutoSyncScheduler(autoSync).run()).doesNotThrowAnyException();
    }

}
