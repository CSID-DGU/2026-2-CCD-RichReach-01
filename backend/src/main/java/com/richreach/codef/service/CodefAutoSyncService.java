package com.richreach.codef.service;

import com.richreach.codef.config.CodefAutoSyncProperties;
import com.richreach.codef.config.CodefSyncProperties;
import com.richreach.codef.dto.AutoSyncCandidate;
import com.richreach.codef.dto.ConnectionSyncRequest;
import com.richreach.codef.repository.CodefOrgAccountRepository;
import com.richreach.codef.repository.CodefSyncJobRepository;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.util.Times;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 사용자가 켜 둔 카드사 연결을 서버가 대신 동기화한다. 누구를 부를지만 정하고, 실제 동기화는 사용자가 직접 누를 때와 같은
 * {@link ConnectionSyncService#start}를 쓴다. 그래서 사용자별 잠금, 진행 중인 동기화 확인, 계정 일시 제한(재시도 차단) 기간
 * 건너뛰기, 수집한 거래의 플랜 재계산 연결이 모두 그대로 적용된다.
 *
 * <ul>
 *   <li><b>대상</b>: 연결이 정상(ACTIVE)이고 자동 동기화가 켜져 있으며, 최근 {@code min-interval} 안에 수집을 시작한 적이 없는
 *       카드사 계정. (성공과 실패를 가리지 않는다. 사용자가 방금 직접 동기화했거나 오늘 이미 실패한 계정은 부르지 않는다)</li>
 *   <li><b>순서</b>: 사용자 한 명씩 차례로 한다. 한 사용자의 동기화가 끝날 때까지 기다린 뒤 {@code gap-between-users}만큼 쉬고
 *       다음 사용자를 시작한다. 카드사와 CODEF 호출이 몰리지 않게 하려는 것이다.</li>
 *   <li><b>건너뛰기</b>: 진행 중인 동기화가 있거나, 계정이 일시 제한 중이거나, 연결 상태가 그 사이에 바뀐 사용자는 이번에는
 *       건너뛴다. 한 사용자의 실패가 다른 사용자에게 영향을 주지 않는다.</li>
 *   <li><b>상한</b>: 한 번에 {@code max-users-per-run}명까지만 처리한다. 나머지는 다음 실행에서 처리한다.</li>
 * </ul>
 * 서버가 한 대라는 전제라 여러 서버가 동시에 실행하는 경우는 막지 않는다. (같은 사용자의 동시 동기화는 사용자 잠금이 막는다)
 * 로그에는 사용자 수와 결과 건수, 거절 코드만 남기고 사용자 식별 정보, 카드번호, 거래 내용, connectedId는 남기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodefAutoSyncService {

    /** 사용자 한 명의 동기화가 끝났는지 확인하는 간격. */
    static final Duration POLL_INTERVAL = Duration.ofSeconds(2);

    /** 대기 시간을 쉬는 방법. 테스트에서는 실제로 쉬지 않게 바꾼다. */
    @FunctionalInterface
    interface Sleeper {

        void sleep(Duration duration) throws InterruptedException;

    }

    /**
     * 한 번 실행한 결과.
     *
     * @param candidates 대상이 된 카드사 계정 수
     * @param users      처리를 시작한 사용자 수
     * @param started    동기화를 시작한 사용자 수
     * @param skipped    거절당해 건너뛴 사용자 수(진행 중, 일시 제한 등)
     * @param failed     예상하지 못한 오류가 난 사용자 수
     * @param deferred   상한 때문에 다음 실행으로 넘긴 사용자 수
     */
    public record Result(int candidates, int users, int started, int skipped, int failed, int deferred) {

    }

    private final CodefOrgAccountRepository accounts;
    private final CodefSyncJobRepository jobs;
    private final ConnectionSyncService syncService;
    private final CodefAutoSyncProperties properties;
    private final CodefSyncProperties syncProperties;
    private final Clock clock;

    private Sleeper sleeper = duration -> Thread.sleep(duration.toMillis());

    /** 테스트에서 실제로 쉬지 않도록 대기 방법을 바꾼다. */
    void useSleeper(Sleeper sleeper) {
        this.sleeper = sleeper;
    }

    /** 대상 사용자를 한 번 순서대로 동기화한다. 예상하지 못한 오류가 나도 다음 사용자를 계속 처리한다. */
    public Result runOnce() {
        LocalDateTime now = Times.nowSeconds(clock);
        List<AutoSyncCandidate> candidates = accounts.findAutoSyncCandidates(now.minus(properties.minInterval()));
        Map<Long, List<Long>> byUser = groupByUser(candidates);

        int users = 0;
        int started = 0;
        int skipped = 0;
        int failed = 0;
        int deferred = 0;
        for (Map.Entry<Long, List<Long>> entry : byUser.entrySet()) {
            if (users >= properties.maxUsersPerRun()) {
                deferred++;
                continue;
            }
            if (users > 0 && !pause(properties.gapBetweenUsers())) {
                break;
            }
            users++;
            try {
                syncService.start(entry.getKey(), new ConnectionSyncRequest(entry.getValue()));
                started++;
                if (!waitUntilFinished(entry.getKey())) {
                    break;
                }
            } catch (BusinessException e) {
                // 진행 중인 동기화, 일시 제한, 연결 상태 변경 등. 이번에는 건너뛰고 다음에 다시 시도한다.
                skipped++;
                log.info("자동 동기화를 건너뜁니다: code={}", e.getErrorCode().getCode());
            } catch (RuntimeException e) {
                failed++;
                log.error("자동 동기화 시작 실패: type={}", e.getClass().getSimpleName());
            }
        }
        Result result = new Result(candidates.size(), users, started, skipped, failed, deferred);
        log.info("자동 동기화 실행: accounts={}, users={}, started={}, skipped={}, failed={}, deferred={}",
            result.candidates(), result.users(), result.started(), result.skipped(), result.failed(),
            result.deferred());
        return result;
    }

    /** 사용자별로 계정 ID를 묶는다. 조회 결과가 사용자 순서이므로 그 순서를 지킨다. */
    private static Map<Long, List<Long>> groupByUser(List<AutoSyncCandidate> candidates) {
        Map<Long, List<Long>> byUser = new LinkedHashMap<>();
        for (AutoSyncCandidate candidate : candidates) {
            byUser.computeIfAbsent(candidate.userId(), id -> new ArrayList<>()).add(candidate.accountId());
        }
        return byUser;
    }

    /**
     * 사용자의 동기화가 끝날 때까지 기다린다. 멈춘 작업을 영원히 기다리지 않도록 {@code codef.sync.timeout}이 지나면 끝난
     * 것으로 본다. (그 시간이 지난 작업은 어차피 진행 중으로 보지 않는다) 기다리다 중단되면 false다.
     */
    private boolean waitUntilFinished(long userId) {
        LocalDateTime deadline = LocalDateTime.now(clock).plus(syncProperties.timeout());
        while (jobs.existsRunningSince(userId, LocalDateTime.now(clock).minus(syncProperties.timeout()))
            && LocalDateTime.now(clock).isBefore(deadline)) {
            if (!pause(POLL_INTERVAL)) {
                return false;
            }
        }
        return true;
    }

    /** 쉰다. 서버를 끄는 중이라 중단되면 중단 표시를 되돌려 놓고 false를 돌려준다. */
    private boolean pause(Duration duration) {
        if (duration.isZero()) {
            return true;
        }
        try {
            sleeper.sleep(duration);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

}
