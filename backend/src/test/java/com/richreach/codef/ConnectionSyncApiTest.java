package com.richreach.codef;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.richreach.TestcontainersConfiguration;
import com.richreach.auth.repository.RefreshTokenRepository;
import com.richreach.auth.service.AuthTokenService;
import com.richreach.card.entity.OwnedCard;
import com.richreach.card.repository.OwnedCardRepository;
import com.richreach.codef.client.CodefCardClient;
import com.richreach.codef.client.CodefCardClient.CardListResult;
import com.richreach.codef.client.CodefCardClient.CodefCard;
import com.richreach.codef.entity.CodefConnection;
import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.entity.CodefSyncJob;
import com.richreach.codef.repository.CodefConnectionRepository;
import com.richreach.codef.repository.CodefOrgAccountRepository;
import com.richreach.codef.repository.CodefSyncJobRepository;
import com.richreach.codef.service.CardSyncWorker;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.user.entity.User;
import com.richreach.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 카드 데이터 동기화 시작·상태 조회·연결 목록 API를 보안 필터와 실제 MySQL로 확인한다.
 * CODEF 카드 조회는 모킹하고, 별도 스레드에서 실행되는 수집이 끝날 때까지 상태를 조회하며 기다린다.
 */
@ExtendWith(OutputCaptureExtension.class)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ConnectionSyncApiTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    private static final String KB = "0301";
    private static final String SHINHAN = "0306";
    private static final String CONNECTED_ID = "test-connected-id-sync";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @MockitoBean
    private CodefCardClient cards;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository users;
    @Autowired
    private CodefConnectionRepository connections;
    @Autowired
    private CodefOrgAccountRepository orgAccounts;
    @Autowired
    private CodefSyncJobRepository syncJobs;
    @Autowired
    private RefreshTokenRepository refreshTokens;
    @Autowired
    private AuthTokenService authTokenService;
    @Autowired
    private CardSyncWorker worker;
    @Autowired
    private OwnedCardRepository ownedCards;

    private User user;
    private String accessToken;
    private CountDownLatch release;

    @BeforeEach
    void createUserAndLogin() {
        user = newUser();
        accessToken = authTokenService.issue(user.getId()).accessToken();
        release = new CountDownLatch(1);
    }

    @AfterEach
    void cleanUp() throws Exception {
        release.countDown();
        awaitNoRunningJobs();
        syncJobs.deleteAll();
        orgAccounts.deleteAll();
        connections.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    private User newUser() {
        int n = SEQUENCE.incrementAndGet();
        return users.save(new User("sync" + n + "@example.com", "hash", "김리치", "010%08d".formatted(8000000 + n),
            "REAL", LocalDateTime.now()));
    }

    private CodefOrgAccount connect(User owner, String organization, String status) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        CodefConnection connection = connections.findByUserId(owner.getId()).orElseGet(() -> connections.save(
            new CodefConnection(owner, CONNECTED_ID + owner.getId(), "DEMO", now, now, now, now)));
        return orgAccounts.save(new CodefOrgAccount(connection, null, organization, "CD", "P", "1", status, now));
    }

    private CodefOrgAccount connect(String organization) {
        return connect(user, organization, "ACTIVE");
    }

    private ResultActions startSync(String token, String json) throws Exception {
        var request = post("/api/connections/sync");
        if (json != null) {
            request = request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(token == null ? request : request.header("Authorization", "Bearer " + token));
    }

    private ResultActions syncStatus(String token, String jobId) throws Exception {
        return mockMvc.perform(get("/api/connections/sync/" + jobId).header("Authorization", "Bearer " + token));
    }

    /** 로그인한 사용자로 연결 목록을 조회한다. */
    private ResultActions listConnections() throws Exception {
        return mockMvc.perform(get("/api/connections").header("Authorization", "Bearer " + accessToken));
    }

    private String startAndGetJobId() throws Exception {
        String body = startSync(accessToken, null).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data.jobId");
    }

    /** 상태가 expected가 될 때까지 최대 10초 기다린다. */
    private void awaitStatus(String jobId, String expected) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        String last = null;
        while (System.currentTimeMillis() < deadline) {
            String body = syncStatus(accessToken, jobId).andReturn().getResponse().getContentAsString();
            last = JsonPath.read(body, "$.data.status");
            if (expected.equals(last)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("상태가 " + expected + "가 되지 않았습니다. 마지막 상태: " + last);
    }

    private void awaitNoRunningJobs() throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            if (syncJobs.findAll().stream().noneMatch(CodefSyncJob::isInProgress)) {
                return;
            }
            Thread.sleep(50);
        }
    }

    /** release가 열릴 때까지 기다렸다가 count장을 가져온 것으로 응답한다. */
    private void stubBlocking(String organization, int count) {
        when(cards.fetchCardList(anyString(), eq(organization))).thenAnswer(invocation -> {
            release.await(10, TimeUnit.SECONDS);
            return cardsOf(count);
        });
    }

    /** count장을 정상으로 읽어낸 성공 응답. 카드 번호는 전체 번호 모양이라, 저장할 때 가려지는지도 함께 확인할 수 있다. */
    private static CardListResult cardsOf(int count) {
        List<CodefCard> cards = IntStream.rangeClosed(1, count)
            .mapToObj(i -> new CodefCard("테스트 동기화 카드" + i, "1111222233334%03d".formatted(i), "check"))
            .toList();
        return new CardListResult(true, count, "CF-00000", cards);
    }

    private void stubResult(String organization, CardListResult result) {
        when(cards.fetchCardList(anyString(), eq(organization))).thenReturn(result);
    }

    // ---- 로그인·입력 ----

    @Test
    @DisplayName("세 API 모두 로그인이 필요하다 (토큰이 없으면 401 AUTH-001)")
    void requiresLogin() throws Exception {
        startSync(null, null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH-001"));
        mockMvc.perform(get("/api/connections/sync/any")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/connections")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("연결한 카드사가 없으면 404 CONN-001이고 CODEF를 부르지 않는다")
    void noConnectionToSync() throws Exception {
        startSync(accessToken, null).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CONN-001"));

        verify(cards, never()).fetchCardList(any(), any());
    }

    @Test
    @DisplayName("연결에 실패한 상태(ERROR)의 카드사만 있으면 동기화할 수 없다 (404 CONN-001)")
    void failedAccountIsNotSynced() throws Exception {
        connect(user, KB, "ERROR");

        startSync(accessToken, null).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CONN-001"));
    }

    // ---- 시작과 진행 ----

    @Test
    @DisplayName("시작하면 jobId를 바로 돌려주고, 진행 중에는 IN_PROGRESS, 끝나면 SUCCESS와 카드 수를 보여준다")
    void startAndPollUntilSuccess() throws Exception {
        CodefOrgAccount kb = connect(KB);
        stubBlocking(KB, 2);

        String jobId = startAndGetJobId();

        syncStatus(accessToken, jobId).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.jobId").value(jobId))
            .andExpect(jsonPath("$.data.status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.data.totalCount").value(1))
            .andExpect(jsonPath("$.data.completedCount").value(0))
            .andExpect(jsonPath("$.data.results[0].status").value("IN_PROGRESS"))
            .andExpect(jsonPath("$.data.results[0].cardCompany").value("KB"))
            .andExpect(jsonPath("$.data.results[0].cardCount").value(0))
            .andExpect(jsonPath("$.data.results[0].failureCode").doesNotExist());

        release.countDown();
        awaitStatus(jobId, "SUCCESS");

        syncStatus(accessToken, jobId)
            .andExpect(jsonPath("$.data.completedCount").value(1))
            .andExpect(jsonPath("$.data.results[0].connectionId").value(kb.getId()))
            .andExpect(jsonPath("$.data.results[0].status").value("SUCCESS"))
            .andExpect(jsonPath("$.data.results[0].cardCount").value(2));
        assertThat(orgAccounts.findById(kb.getId()).orElseThrow().getLastSyncedAt()).isNotNull();
    }

    @Test
    @DisplayName("진행 중에 다시 시작하면 409 CONN-007이고, 끝난 뒤에는 다시 시작할 수 있다")
    void cannotStartWhileRunning() throws Exception {
        connect(KB);
        stubBlocking(KB, 1);
        String jobId = startAndGetJobId();

        startSync(accessToken, null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("CONN-007"));

        release.countDown();
        awaitStatus(jobId, "SUCCESS");
        startSync(accessToken, null).andExpect(status().isOk());
    }

    @Test
    @DisplayName("카드사 하나가 성공하고 하나가 실패하면 PARTIAL_SUCCESS다")
    void partialSuccess() throws Exception {
        connect(KB);
        CodefOrgAccount shinhan = connect(SHINHAN);
        stubResult(KB, cardsOf(3));
        stubResult(SHINHAN, CardListResult.failure("CF-12345"));

        String jobId = startAndGetJobId();
        awaitStatus(jobId, "PARTIAL_SUCCESS");

        syncStatus(accessToken, jobId)
            .andExpect(jsonPath("$.data.totalCount").value(2))
            .andExpect(jsonPath("$.data.completedCount").value(2))
            .andExpect(jsonPath("$.data.results[0].status").value("SUCCESS"))
            .andExpect(jsonPath("$.data.results[0].cardCount").value(3))
            .andExpect(jsonPath("$.data.results[1].cardCompany").value("SHINHAN"))
            .andExpect(jsonPath("$.data.results[1].status").value("FAILED"));
        assertThat(orgAccounts.findById(shinhan.getId()).orElseThrow().getLastErrorCode()).isEqualTo("CF-12345");
    }

    @Test
    @DisplayName("실패하면 FAILED와 화면용 코드·문구를 주고, CODEF 응답 코드는 내보내지 않는다")
    void failureHidesCodefCode() throws Exception {
        connect(KB);
        stubResult(KB, CardListResult.failure("CF-12345"));

        String jobId = startAndGetJobId();
        awaitStatus(jobId, "FAILED");

        String body = syncStatus(accessToken, jobId)
            .andExpect(jsonPath("$.data.results[0].failureCode").value("SYNC_FAILED"))
            .andExpect(jsonPath("$.data.results[0].failureReason").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("CF-12345");
    }

    @Test
    @DisplayName("계정이 일시 제한되면(CF-12207) FAILED이고 failureCode는 ACCOUNT_RESTRICTED이며, 다시 시도하면 안 되는 실패로 남는다")
    void restrictedAccountIsNotRetryable() throws Exception {
        connect(KB);
        stubResult(KB, CardListResult.failure("CF-12207"));

        String jobId = startAndGetJobId();
        awaitStatus(jobId, "FAILED");

        String body = syncStatus(accessToken, jobId)
            .andExpect(jsonPath("$.data.results[0].failureCode").value("ACCOUNT_RESTRICTED"))
            .andExpect(jsonPath("$.data.results[0].failureReason").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("CF-12207");
        assertThat(syncJobs.findAll()).allMatch(job -> !job.isRetryable());
    }

    @Test
    @DisplayName("CODEF에 연결하지 못하면 FAILED이고 failureCode는 CODEF_UNAVAILABLE이다")
    void codefUnavailable() throws Exception {
        connect(KB);
        when(cards.fetchCardList(anyString(), eq(KB))).thenThrow(new BusinessException(ErrorCode.EXTERNAL_API_ERROR));

        String jobId = startAndGetJobId();
        awaitStatus(jobId, "FAILED");

        syncStatus(accessToken, jobId).andExpect(jsonPath("$.data.results[0].failureCode").value("CODEF_UNAVAILABLE"));
    }

    @Test
    @DisplayName("예상하지 못한 오류가 나도 진행 중으로 남지 않고 FAILED가 된다")
    void unexpectedErrorEndsAsFailed() throws Exception {
        connect(KB);
        when(cards.fetchCardList(anyString(), eq(KB))).thenThrow(new IllegalStateException("boom"));

        String jobId = startAndGetJobId();
        awaitStatus(jobId, "FAILED");
    }

    @Test
    @DisplayName("가져온 카드가 없으면 성공이고 카드 수는 0이다")
    void noCardsIsSuccess() throws Exception {
        CodefOrgAccount kb = connect(KB);
        stubResult(KB, cardsOf(0));

        String jobId = startAndGetJobId();
        awaitStatus(jobId, "SUCCESS");

        syncStatus(accessToken, jobId).andExpect(jsonPath("$.data.results[0].cardCount").value(0));
        assertThat(syncJobs.findByOrgAccountIdOrderByStartedAtDesc(kb.getId()).get(0).getStatus())
            .isEqualTo(CodefSyncJob.STATUS_NO_DATA);
    }

    // ---- 카드사 지정 ----

    @Test
    @DisplayName("connectionIds를 보내면 그 카드사만 동기화한다")
    void syncsOnlySelectedAccounts() throws Exception {
        CodefOrgAccount kb = connect(KB);
        connect(SHINHAN);
        stubResult(KB, cardsOf(1));

        String body = startSync(accessToken, "{\"connectionIds\":[" + kb.getId() + "]}")
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String jobId = JsonPath.read(body, "$.data.jobId");
        awaitStatus(jobId, "SUCCESS");

        syncStatus(accessToken, jobId).andExpect(jsonPath("$.data.totalCount").value(1))
            .andExpect(jsonPath("$.data.results[0].cardCompany").value("KB"));
        verify(cards, never()).fetchCardList(anyString(), eq(SHINHAN));
    }

    @Test
    @DisplayName("다른 사용자의 연결이나 없는 연결을 지정하면 404 CONN-001이고 아무것도 시작하지 않는다")
    void rejectsForeignOrUnknownConnection() throws Exception {
        CodefOrgAccount mine = connect(KB);
        User other = newUser();
        CodefOrgAccount theirs = connect(other, SHINHAN, "ACTIVE");

        startSync(accessToken, "{\"connectionIds\":[" + mine.getId() + "," + theirs.getId() + "]}")
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CONN-001"));
        startSync(accessToken, "{\"connectionIds\":[999999999]}")
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CONN-001"));

        assertThat(syncJobs.count()).isZero();
        verify(cards, never()).fetchCardList(any(), any());
    }

    // ---- 조회 권한 ----

    @Test
    @DisplayName("없는 jobId나 다른 사용자의 jobId는 404 CONN-006이다")
    void unknownOrForeignJobIsNotFound() throws Exception {
        connect(KB);
        stubResult(KB, cardsOf(1));
        String jobId = startAndGetJobId();
        awaitStatus(jobId, "SUCCESS");
        User other = newUser();
        String otherToken = authTokenService.issue(other.getId()).accessToken();

        syncStatus(accessToken, "no-such-job").andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CONN-006"));
        syncStatus(otherToken, jobId).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CONN-006"));
    }

    // ---- 멈춘 작업 ----

    @Test
    @DisplayName("오래전에 시작해 멈춘 작업은 SYNC_TIMEOUT 실패로 보이고, 새 동기화를 막지 않는다")
    void staleJobIsReportedAsTimedOutAndDoesNotBlock() throws Exception {
        CodefOrgAccount kb = connect(KB);
        syncJobs.save(CodefSyncJob.startCardList(kb, "stale-batch", LocalDateTime.now().minusMinutes(30)));
        stubResult(KB, cardsOf(1));

        syncStatus(accessToken, "stale-batch")
            .andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.results[0].failureCode").value("SYNC_TIMEOUT"));

        startSync(accessToken, null).andExpect(status().isOk());
    }

    @Test
    @DisplayName("제한 시간이 지난 뒤 끝난 결과는 반영하지 않는다 (작업과 계정 모두 그대로)")
    void lateResultAfterTimeoutIsNotApplied() throws Exception {
        CodefOrgAccount kb = connect(KB);
        CodefSyncJob stale = syncJobs.save(
            CodefSyncJob.startCardList(kb, "late-batch", LocalDateTime.now().minusMinutes(30)));
        stubResult(KB, cardsOf(7));

        worker.run(stale.getId());

        CodefSyncJob reloaded = syncJobs.findById(stale.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(CodefSyncJob.STATUS_IN_PROGRESS);
        assertThat(reloaded.getFetchedCount()).isNull();
        assertThat(orgAccounts.findById(kb.getId()).orElseThrow().getLastSyncedAt()).isNull();
        syncStatus(accessToken, "late-batch")
            .andExpect(jsonPath("$.data.status").value("FAILED"))
            .andExpect(jsonPath("$.data.results[0].failureCode").value("SYNC_TIMEOUT"));
    }

    // ---- 보유카드 저장 ----

    @Test
    @DisplayName("동기화에 성공하면 가져온 카드를 보유카드로 저장하고, 카드 번호는 마지막 4자리만 남긴다")
    void savesFetchedCardsAsOwnedCards() throws Exception {
        CodefOrgAccount kb = connect(KB);
        stubResult(KB, cardsOf(2));

        awaitStatus(startAndGetJobId(), "SUCCESS");

        List<OwnedCard> saved = ownedCards.findByOrgAccountId(kb.getId());
        assertThat(saved).extracting(OwnedCard::getCardName)
            .containsExactlyInAnyOrder("테스트 동기화 카드1", "테스트 동기화 카드2");
        assertThat(saved).extracting(OwnedCard::getMaskedNumber)
            .containsExactlyInAnyOrder("************4001", "************4002");
        assertThat(saved).allSatisfy(card -> {
            assertThat(card.getUserId()).isEqualTo(user.getId());
            assertThat(card.getOrganization()).isEqualTo(KB);
            assertThat(card.isActive()).isTrue();
            assertThat(card.isSelected()).isFalse();
        });
    }

    @Test
    @DisplayName("다시 동기화할 때 보이지 않는 카드는 지우지 않고 비활성으로 표시한다")
    void resyncDeactivatesMissingCards() throws Exception {
        CodefOrgAccount kb = connect(KB);
        stubResult(KB, cardsOf(2));
        awaitStatus(startAndGetJobId(), "SUCCESS");

        stubResult(KB, cardsOf(1));
        awaitStatus(startAndGetJobId(), "SUCCESS");

        List<OwnedCard> saved = ownedCards.findByOrgAccountId(kb.getId());
        assertThat(saved).hasSize(2);
        assertThat(saved.stream().filter(OwnedCard::isActive)).extracting(OwnedCard::getCardName)
            .containsExactly("테스트 동기화 카드1");
    }

    @Test
    @DisplayName("응답의 카드 수와 읽어낸 카드 수가 다르면 저장하지 않고 동기화를 실패로 기록하며 기존 카드는 그대로다")
    void formatMismatchFailsWithoutTouchingCards() throws Exception {
        CodefOrgAccount kb = connect(KB);
        stubResult(KB, cardsOf(2));
        awaitStatus(startAndGetJobId(), "SUCCESS");

        // 응답에는 3장이 있다고 나오는데 읽어낸 카드는 1장뿐이다. (카드 필드 이름이 예상과 다른 경우)
        stubResult(KB, new CardListResult(true, 3, "CF-00000",
            List.of(new CodefCard("테스트 동기화 카드1", "1111222233334001", "check"))));
        String jobId = startAndGetJobId();
        awaitStatus(jobId, "FAILED");

        syncStatus(accessToken, jobId).andExpect(jsonPath("$.data.results[0].failureCode").value("SYNC_FAILED"));
        assertThat(ownedCards.findByOrgAccountId(kb.getId())).hasSize(2).allMatch(OwnedCard::isActive);
    }

    @Test
    @DisplayName("카드가 0장으로 오면 기존 보유카드를 건드리지 않는다 (정말 0장인지 형식이 어긋난 것인지 구분할 수 없어서)")
    void zeroCardsKeepsExistingCards() throws Exception {
        CodefOrgAccount kb = connect(KB);
        stubResult(KB, cardsOf(2));
        awaitStatus(startAndGetJobId(), "SUCCESS");

        stubResult(KB, cardsOf(0));
        awaitStatus(startAndGetJobId(), "SUCCESS");

        assertThat(ownedCards.findByOrgAccountId(kb.getId())).hasSize(2).allMatch(OwnedCard::isActive);
    }

    @Test
    @DisplayName("동기화가 실패하면 보유카드를 건드리지 않는다")
    void failedSyncKeepsExistingCards() throws Exception {
        CodefOrgAccount kb = connect(KB);
        stubResult(KB, cardsOf(2));
        awaitStatus(startAndGetJobId(), "SUCCESS");

        stubResult(KB, CardListResult.failure("CF-12345"));
        awaitStatus(startAndGetJobId(), "FAILED");

        assertThat(ownedCards.findByOrgAccountId(kb.getId())).hasSize(2).allMatch(OwnedCard::isActive);
    }

    @Test
    @DisplayName("카드 목록은 로그에 남지 않는다 (카드명과 번호)")
    void cardContentIsNotLogged(CapturedOutput output) throws Exception {
        connect(KB);
        stubResult(KB, cardsOf(2));

        awaitStatus(startAndGetJobId(), "SUCCESS");

        assertThat(output.getAll()).doesNotContain("테스트 동기화 카드").doesNotContain("1111222233334001");
    }

    // ---- 연결 목록 ----

    @Test
    @DisplayName("연결한 카드사가 없으면 목록은 빈 배열이다")
    void emptyConnectionList() throws Exception {
        listConnections()
            .andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("목록은 카드사별 상태를 보여주고, 동기화 전이면 NOT_SYNCED, 끝나면 SUCCESS와 카드 수다")
    void connectionListShowsStatus() throws Exception {
        CodefOrgAccount kb = connect(KB);
        connect(SHINHAN);
        stubResult(KB, cardsOf(4));
        stubResult(SHINHAN, CardListResult.failure("CF-09999"));

        listConnections()
            .andExpect(jsonPath("$.data", hasSize(2)))
            .andExpect(jsonPath("$.data[0].status").value("NOT_SYNCED"))
            .andExpect(jsonPath("$.data[0].lastSyncedAt").doesNotExist());

        String jobId = startAndGetJobId();
        awaitStatus(jobId, "PARTIAL_SUCCESS");

        listConnections()
            .andExpect(jsonPath("$.data[0].connectionId").value(kb.getId()))
            .andExpect(jsonPath("$.data[0].cardCompany").value("KB"))
            .andExpect(jsonPath("$.data[0].status").value("SUCCESS"))
            .andExpect(jsonPath("$.data[0].cardCount").value(4))
            .andExpect(jsonPath("$.data[0].lastSyncedAt").isNotEmpty())
            .andExpect(jsonPath("$.data[1].cardCompany").value("SHINHAN"))
            .andExpect(jsonPath("$.data[1].status").value("FAILED"))
            .andExpect(jsonPath("$.data[1].failureCode").value("CODEF_UNAVAILABLE"));
    }

    @Test
    @DisplayName("목록에는 해제한 카드사와 다른 사용자의 카드사가 나오지 않는다")
    void connectionListExcludesDeletedAndOthers() throws Exception {
        connect(KB);
        connect(user, SHINHAN, "DELETED");
        User other = newUser();
        connect(other, "0302", "ACTIVE");

        listConnections()
            .andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].cardCompany").value("KB"));
    }

    // ---- 보안 ----

    @Test
    @DisplayName("connectedId는 응답과 로그에 나오지 않는다")
    void connectedIdIsNeverExposed(CapturedOutput output) throws Exception {
        connect(KB);
        stubResult(KB, CardListResult.failure("CF-12345"));

        String jobId = startAndGetJobId();
        awaitStatus(jobId, "FAILED");
        String status = syncStatus(accessToken, jobId).andReturn().getResponse().getContentAsString();
        String list = listConnections()
            .andReturn().getResponse().getContentAsString();

        assertThat(status).doesNotContain(CONNECTED_ID);
        assertThat(list).doesNotContain(CONNECTED_ID);
        assertThat(output.getAll()).doesNotContain(CONNECTED_ID);
    }

}
