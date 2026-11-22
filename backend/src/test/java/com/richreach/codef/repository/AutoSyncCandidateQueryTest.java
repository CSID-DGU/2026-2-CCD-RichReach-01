package com.richreach.codef.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.richreach.TestcontainersConfiguration;
import com.richreach.codef.dto.AutoSyncCandidate;
import com.richreach.codef.entity.CodefConnection;
import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.entity.CodefSyncJob;
import com.richreach.user.entity.User;
import com.richreach.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** 자동 동기화 대상 조회({@code findAutoSyncCandidates})를 실제 MySQL로 확인한다. */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AutoSyncCandidateQueryTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    /** 시계와 무관하게 값이 고정되도록 모든 시각을 이 기준에서 만든다. */
    private static final LocalDateTime SINCE = LocalDateTime.of(2026, 11, 23, 6, 0, 0);

    @Autowired
    private UserRepository users;
    @Autowired
    private CodefConnectionRepository connections;
    @Autowired
    private CodefOrgAccountRepository orgAccounts;
    @Autowired
    private CodefSyncJobRepository syncJobs;
    @Autowired
    private JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        syncJobs.deleteAll();
        orgAccounts.deleteAll();
        connections.deleteAll();
        users.deleteAll();
    }

    private User newUser() {
        int n = SEQUENCE.incrementAndGet();
        return users.save(new User("autosync" + n + "@example.com", "hash", "김리치",
            "010%08d".formatted(7000000 + n), "REAL", LocalDateTime.now()));
    }

    private CodefOrgAccount account(User owner, String organization) {
        CodefConnection connection = connections.findByUserId(owner.getId()).orElseGet(() -> connections.save(
            new CodefConnection(owner, "test-connected-id-" + owner.getId(), "DEMO", SINCE, SINCE, SINCE, SINCE)));
        return orgAccounts.save(CodefOrgAccount.connected(connection, organization, SINCE));
    }

    private void job(CodefOrgAccount account, LocalDateTime startedAt, boolean failed) {
        CodefSyncJob job = syncJobs.save(CodefSyncJob.startCardList(account, "batch", startedAt));
        if (failed) {
            job.markFailed("CF-00000", true, startedAt.plusMinutes(1));
            syncJobs.save(job);
        }
    }

    private void setStatus(CodefOrgAccount account, String status) {
        jdbc.update("update codef_org_accounts set status = ? where id = ?", status, account.getId());
    }

    private List<Long> candidateIds() {
        return orgAccounts.findAutoSyncCandidates(SINCE).stream().map(AutoSyncCandidate::accountId).toList();
    }

    @Test
    @DisplayName("한 번도 수집한 적 없는 정상 계정은 대상이다")
    void neverSyncedIsACandidate() {
        User owner = newUser();
        CodefOrgAccount account = account(owner, "0301");

        assertThat(orgAccounts.findAutoSyncCandidates(SINCE))
            .containsExactly(new AutoSyncCandidate(account.getId(), owner.getId()));
    }

    @Test
    @DisplayName("정상(ACTIVE)이 아닌 계정과 자동 동기화를 끈 계정은 대상이 아니다")
    void onlyActiveAccountsWithAutoSyncOn() {
        User owner = newUser();
        CodefOrgAccount active = account(owner, "0301");
        CodefOrgAccount off = account(owner, "0302");
        off.changeAutoSync(false);
        orgAccounts.save(off);
        for (String organization : List.of("0303", "0304", "0305", "0306")) {
            // 연결 상태가 정상이 아닌 계정
            CodefOrgAccount other = account(owner, organization);
            setStatus(other, switch (organization) {
                case "0303" -> "NEEDS_CARD_AUTH";
                case "0304" -> "LOCKED";
                case "0305" -> "ERROR";
                default -> "DELETED";
            });
        }

        assertThat(candidateIds()).containsExactly(active.getId());
    }

    @Test
    @DisplayName("기준 시각 이후에 수집을 시작한 계정은 성공과 실패를 가리지 않고 뺀다")
    void recentAttemptsAreExcluded() {
        User owner = newUser();
        CodefOrgAccount succeeded = account(owner, "0301");
        CodefOrgAccount failed = account(owner, "0302");
        CodefOrgAccount stale = account(owner, "0303");
        job(succeeded, SINCE.plusHours(1), false);
        job(failed, SINCE.plusSeconds(1), true);
        job(stale, SINCE.minusSeconds(1), false);

        assertThat(candidateIds()).containsExactly(stale.getId());
    }

    @Test
    @DisplayName("기준 시각과 같은 시각에 시작한 수집은 최근으로 보지 않아 대상이다")
    void jobStartedExactlyAtTheCutoffIsStillACandidate() {
        User owner = newUser();
        CodefOrgAccount account = account(owner, "0301");
        job(account, SINCE, false);

        assertThat(candidateIds()).containsExactly(account.getId());
    }

    @Test
    @DisplayName("오래된 수집이 있어도 최근 수집이 하나라도 있으면 뺀다")
    void aRecentJobWinsOverOldOnes() {
        User owner = newUser();
        CodefOrgAccount account = account(owner, "0301");
        job(account, SINCE.minusDays(3), false);
        job(account, SINCE.plusMinutes(5), true);

        assertThat(candidateIds()).isEmpty();
    }

    @Test
    @DisplayName("사용자 순서, 계정 순서로 돌려준다")
    void ordersByUserThenAccount() {
        User first = newUser();
        User second = newUser();
        CodefOrgAccount secondA = account(second, "0301");
        CodefOrgAccount firstA = account(first, "0301");
        CodefOrgAccount firstB = account(first, "0302");
        CodefOrgAccount secondB = account(second, "0302");

        assertThat(candidateIds()).containsExactly(firstA.getId(), firstB.getId(), secondA.getId(),
            secondB.getId());
    }

}
