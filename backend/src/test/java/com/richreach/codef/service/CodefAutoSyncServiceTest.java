package com.richreach.codef.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.richreach.codef.config.CodefAutoSyncProperties;
import com.richreach.codef.config.CodefSyncProperties;
import com.richreach.codef.dto.AutoSyncCandidate;
import com.richreach.codef.dto.ConnectionSyncRequest;
import com.richreach.codef.repository.CodefOrgAccountRepository;
import com.richreach.codef.repository.CodefSyncJobRepository;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** 자동 동기화가 누구를 어떤 순서로 부르고, 실패나 진행 중인 동기화를 어떻게 다루는지 확인한다. */
class CodefAutoSyncServiceTest {

    private static final Instant START = Instant.parse("2026-11-24T06:00:00Z");
    private static final Duration MIN_INTERVAL = Duration.ofHours(20);
    private static final Duration GAP = Duration.ofSeconds(5);
    private static final Duration SYNC_TIMEOUT = Duration.ofMinutes(5);

    /** 쉰 만큼 시간이 흐르는 시계. 대기 시간 계산을 실제로 기다리지 않고 확인한다. */
    private static final class AdvancingClock extends Clock {

        private Instant now = START;

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

    }

    private CodefOrgAccountRepository accounts;
    private CodefSyncJobRepository jobs;
    private ConnectionSyncService syncService;
    private AdvancingClock clock;
    private List<Duration> sleeps;

    @BeforeEach
    void setUp() {
        accounts = mock(CodefOrgAccountRepository.class);
        jobs = mock(CodefSyncJobRepository.class);
        syncService = mock(ConnectionSyncService.class);
        clock = new AdvancingClock();
        sleeps = new ArrayList<>();
    }

    private CodefAutoSyncService service(int maxUsers) {
        CodefAutoSyncService service = new CodefAutoSyncService(accounts, jobs, syncService,
            new CodefAutoSyncProperties(true, "0 0 6 * * *", MIN_INTERVAL, GAP, maxUsers),
            new CodefSyncProperties(SYNC_TIMEOUT, Duration.ofHours(1)), clock);
        service.useSleeper(duration -> {
            sleeps.add(duration);
            clock.advance(duration);
        });
        return service;
    }

    private void candidates(AutoSyncCandidate... candidates) {
        when(accounts.findAutoSyncCandidates(any())).thenReturn(List.of(candidates));
    }

    @Test
    @DisplayName("조회 기준 시각은 지금에서 최소 간격을 뺀 값이다")
    void usesTheMinimumIntervalAsTheCutoff() {
        candidates();

        service(10).runOnce();

        LocalDateTime now = LocalDateTime.ofInstant(START, ZoneOffset.UTC);
        verify(accounts).findAutoSyncCandidates(now.minus(MIN_INTERVAL));
    }

    @Test
    @DisplayName("사용자별로 계정을 묶어 한 번씩 시작하고, 사용자 사이에만 쉰다")
    void groupsAccountsPerUserAndPausesBetweenUsers() {
        candidates(new AutoSyncCandidate(1L, 10L), new AutoSyncCandidate(2L, 10L),
            new AutoSyncCandidate(3L, 20L));

        CodefAutoSyncService.Result result = service(10).runOnce();

        ArgumentCaptor<ConnectionSyncRequest> request = ArgumentCaptor.forClass(ConnectionSyncRequest.class);
        verify(syncService).start(eq(10L), request.capture());
        assertThat(request.getValue().connectionIds()).containsExactly(1L, 2L);
        verify(syncService).start(eq(20L), any());
        assertThat(sleeps).containsExactly(GAP);
        assertThat(result).isEqualTo(new CodefAutoSyncService.Result(3, 2, 2, 0, 0, 0));
    }

    @Test
    @DisplayName("거절당한 사용자는 건너뛰고 다음 사용자를 계속 처리한다")
    void skipsABusinessRejectionAndContinues() {
        candidates(new AutoSyncCandidate(1L, 10L), new AutoSyncCandidate(2L, 20L));
        when(syncService.start(eq(10L), any())).thenThrow(new BusinessException(ErrorCode.SYNC_ALREADY_RUNNING));

        CodefAutoSyncService.Result result = service(10).runOnce();

        verify(syncService).start(eq(20L), any());
        assertThat(result).isEqualTo(new CodefAutoSyncService.Result(2, 2, 1, 1, 0, 0));
    }

    @Test
    @DisplayName("예상하지 못한 오류가 나도 다음 사용자를 계속 처리한다")
    void continuesAfterAnUnexpectedError() {
        candidates(new AutoSyncCandidate(1L, 10L), new AutoSyncCandidate(2L, 20L));
        when(syncService.start(eq(10L), any())).thenThrow(new IllegalStateException("boom"));

        CodefAutoSyncService.Result result = service(10).runOnce();

        verify(syncService).start(eq(20L), any());
        assertThat(result).isEqualTo(new CodefAutoSyncService.Result(2, 2, 1, 0, 1, 0));
    }

    @Test
    @DisplayName("한 번에 처리하는 사용자 수에는 상한이 있고, 넘긴 사용자 수를 알린다")
    void capsTheNumberOfUsersPerRun() {
        candidates(new AutoSyncCandidate(1L, 10L), new AutoSyncCandidate(2L, 20L),
            new AutoSyncCandidate(3L, 30L));

        CodefAutoSyncService.Result result = service(1).runOnce();

        verify(syncService).start(eq(10L), any());
        verify(syncService, never()).start(eq(20L), any());
        verify(syncService, never()).start(eq(30L), any());
        assertThat(result).isEqualTo(new CodefAutoSyncService.Result(3, 1, 1, 0, 0, 2));
    }

    @Test
    @DisplayName("사용자의 동기화가 끝날 때까지 기다린 뒤 다음 사용자를 시작한다")
    void waitsWhileTheUsersSyncIsRunning() {
        candidates(new AutoSyncCandidate(1L, 10L), new AutoSyncCandidate(2L, 20L));
        when(jobs.existsRunningSince(eq(10L), any())).thenReturn(true, true, false);

        service(10).runOnce();

        assertThat(sleeps).containsExactly(CodefAutoSyncService.POLL_INTERVAL, CodefAutoSyncService.POLL_INTERVAL,
            GAP);
    }

    @Test
    @DisplayName("멈춘 동기화는 타임아웃이 지나면 기다리지 않고 넘어간다")
    void stopsWaitingAfterTheSyncTimeout() {
        candidates(new AutoSyncCandidate(1L, 10L), new AutoSyncCandidate(2L, 20L));
        when(jobs.existsRunningSince(eq(10L), any())).thenReturn(true);

        CodefAutoSyncService.Result result = service(10).runOnce();

        verify(syncService).start(eq(20L), any());
        assertThat(result.started()).isEqualTo(2);
        long polls = sleeps.stream().filter(CodefAutoSyncService.POLL_INTERVAL::equals).count();
        assertThat(polls).isEqualTo(SYNC_TIMEOUT.dividedBy(CodefAutoSyncService.POLL_INTERVAL));
    }

    @Test
    @DisplayName("쉬는 중 중단되면 남은 사용자를 시작하지 않고 중단 표시를 남긴다")
    void stopsWhenInterrupted() {
        candidates(new AutoSyncCandidate(1L, 10L), new AutoSyncCandidate(2L, 20L));
        when(jobs.existsRunningSince(anyLong(), any())).thenReturn(true);
        CodefAutoSyncService service = service(10);
        service.useSleeper(duration -> {
            throw new InterruptedException();
        });

        try {
            service.runOnce();

            verify(syncService, times(1)).start(anyLong(), any());
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    @DisplayName("대상이 없으면 아무것도 시작하지 않는다")
    void doesNothingWithoutCandidates() {
        candidates();

        CodefAutoSyncService.Result result = service(10).runOnce();

        verify(syncService, never()).start(anyLong(), any());
        assertThat(result).isEqualTo(new CodefAutoSyncService.Result(0, 0, 0, 0, 0, 0));
        assertThat(sleeps).isEmpty();
    }

}
