package com.richreach.transaction;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.richreach.TestcontainersConfiguration;
import com.richreach.auth.repository.RefreshTokenRepository;
import com.richreach.auth.service.AuthTokenService;
import com.richreach.card.dto.CollectedCard;
import com.richreach.card.repository.OwnedCardRepository;
import com.richreach.card.service.OwnedCardService;
import com.richreach.codef.entity.CardCompany;
import com.richreach.codef.entity.CodefConnection;
import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.repository.CodefConnectionRepository;
import com.richreach.codef.repository.CodefOrgAccountRepository;
import com.richreach.transaction.dto.CollectedTransaction;
import com.richreach.transaction.entity.CardTransaction;
import com.richreach.transaction.repository.CardTransactionRepository;
import com.richreach.transaction.service.TransactionStoreService;
import com.richreach.user.entity.User;
import com.richreach.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 거래 목록·상세·카테고리 수정 API를 보안 필터와 실제 MySQL로 확인한다. 로그인, 정렬과 페이지, 조회 조건(기간, 카드, 업종,
 * 취소 거래), 사용자 격리, 상세, 업종 수정과 되돌리기, 재동기화에서 수정값 유지를 본다.
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class TransactionApiTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    /** 테스트와 서비스가 같은 시각을 보도록 고정한 시계. 자정을 넘겨도 기간 경계가 어긋나지 않는다. */
    private static final Clock CLOCK = Clock.fixed(Instant.now(), ZoneId.systemDefault());

    @TestConfiguration
    static class FixedClockConfig {

        @Bean
        @Primary
        Clock fixedClock() {
            return CLOCK;
        }

    }

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TransactionStoreService store;
    @Autowired
    private CardTransactionRepository transactions;
    @Autowired
    private OwnedCardService ownedCardService;
    @Autowired
    private OwnedCardRepository ownedCards;
    @Autowired
    private UserRepository users;
    @Autowired
    private CodefConnectionRepository connections;
    @Autowired
    private CodefOrgAccountRepository orgAccounts;
    @Autowired
    private RefreshTokenRepository refreshTokens;
    @Autowired
    private AuthTokenService authTokenService;
    @Autowired
    private JdbcTemplate jdbc;

    private final LocalDate today = LocalDate.now(CLOCK);
    private final AtomicInteger approvals = new AtomicInteger();
    private User user;
    private String accessToken;
    private long card;

    @BeforeEach
    void setUp() {
        user = newUser();
        accessToken = authTokenService.issue(user.getId()).accessToken();
        card = newCard(user, "테스트 카드A");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from card_transactions");
        jdbc.update("delete from user_cards");
        SelectedCards.cleanUp(jdbc);
        orgAccounts.deleteAll();
        connections.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    // ---- 준비 ----

    private User newUser() {
        int n = SEQUENCE.incrementAndGet();
        return users.save(new User("txapi" + n + "@example.com", "hash", "김리치", "010%08d".formatted(2000000 + n),
            "REAL", LocalDateTime.now()));
    }

    private long newCard(User owner, String cardName) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        CodefConnection connection = connections.save(
            new CodefConnection(owner, "test-connected-id-" + owner.getId(), "DEMO", now, now, now, now));
        CodefOrgAccount account = orgAccounts.save(CodefOrgAccount.connected(connection, "0301", now));
        ownedCardService.saveSynced(owner.getId(), account.getId(), CardCompany.KB,
            List.of(new CollectedCard(cardName, "1111", "check")));
        long cardId = ownedCards.findByOrgAccountId(account.getId()).get(0).getId();
        SelectedCards.select(jdbc, cardId);
        return cardId;
    }

    /** 같은 사용자의 두 번째 보유카드. 연결은 사용자당 하나라서 같은 연결의 다른 카드사 계정을 쓴다. */
    private long newCardForSameUser(String cardName) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        CodefConnection connection = connections.findByUserId(user.getId()).orElseThrow();
        CodefOrgAccount account = orgAccounts.save(CodefOrgAccount.connected(connection, "0306", now));
        ownedCardService.saveSynced(user.getId(), account.getId(), CardCompany.SHINHAN,
            List.of(new CollectedCard(cardName, "2222", "check")));
        long cardId = ownedCards.findByOrgAccountId(account.getId()).get(0).getId();
        SelectedCards.select(jdbc, cardId);
        return cardId;
    }

    private void save(User owner, long ownerCard, LocalDateTime at, String merchant, long amount, long canceled,
                      String category) {
        store.saveCollected(owner.getId(), ownerCard, List.of(new CollectedTransaction(
            "AP-" + approvals.incrementAndGet(), at, merchant, amount, canceled, 0, false, category)));
    }

    private void save(LocalDateTime at, String merchant, long amount, String category) {
        save(user, card, at, merchant, amount, 0, category);
    }

    private long idOf(String merchant) {
        return transactions.findByUserId(user.getId()).stream()
            .filter(t -> t.getMerchantName().equals(merchant)).findFirst().orElseThrow().getId();
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String token) throws Exception {
        return mockMvc.perform(token == null ? request : request.header("Authorization", "Bearer " + token));
    }

    private ResultActions list(String query) throws Exception {
        return call(get("/api/transactions" + query), accessToken);
    }

    private ResultActions patchCategory(String token, Object transactionId, String body) throws Exception {
        return call(patch("/api/transactions/" + transactionId + "/category")
            .contentType(MediaType.APPLICATION_JSON).content(body), token);
    }

    // ---- 로그인 ----

    @Test
    @DisplayName("모든 거래 API는 로그인이 필요하다 (토큰이 없으면 401 AUTH-001)")
    void requiresLogin() throws Exception {
        call(get("/api/transactions"), null).andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("AUTH-001"));
        call(get("/api/transactions/1"), null).andExpect(status().isUnauthorized());
        patchCategory(null, 1, "{\"category\":\"CAFE\"}").andExpect(status().isUnauthorized());
        call(delete("/api/transactions/1/category"), null).andExpect(status().isUnauthorized());
    }

    // ---- 목록: 정렬, 페이지 ----

    @Test
    @DisplayName("목록은 승인일시 최신순이고, 같은 시각이면 나중에 저장된 것이 먼저다")
    void newestFirst() throws Exception {
        LocalDateTime at = today.minusDays(3).atTime(10, 0);
        save(at.minusDays(1), "가장 오래됨", 1_000, "CAFE");
        save(at, "같은 시각 먼저 저장", 2_000, "CAFE");
        save(at, "같은 시각 나중 저장", 3_000, "CAFE");
        save(at.plusDays(1), "가장 최근", 4_000, "CAFE");

        list("").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].merchantName").value("가장 최근"))
            .andExpect(jsonPath("$.data.content[1].merchantName").value("같은 시각 나중 저장"))
            .andExpect(jsonPath("$.data.content[2].merchantName").value("같은 시각 먼저 저장"))
            .andExpect(jsonPath("$.data.content[3].merchantName").value("가장 오래됨"));
    }

    @Test
    @DisplayName("페이지 번호는 0부터이고, 전체 개수와 다음 페이지 여부를 알려준다")
    void pagination() throws Exception {
        for (int i = 0; i < 5; i++) {
            save(today.minusDays(i + 1).atTime(10, 0), "상점" + i, 1_000, "CAFE");
        }

        list("?size=2&page=0").andExpect(jsonPath("$.data.content", hasSize(2)))
            .andExpect(jsonPath("$.data.page").value(0)).andExpect(jsonPath("$.data.size").value(2))
            .andExpect(jsonPath("$.data.totalElements").value(5)).andExpect(jsonPath("$.data.totalPages").value(3))
            .andExpect(jsonPath("$.data.hasNext").value(true));
        list("?size=2&page=2").andExpect(jsonPath("$.data.content", hasSize(1)))
            .andExpect(jsonPath("$.data.hasNext").value(false));
        list("?size=2&page=9").andExpect(jsonPath("$.data.content", hasSize(0)));
    }

    @Test
    @DisplayName("페이지 크기의 기본은 20이고, 페이지 값이 범위를 벗어나면 400 COMMON-400이다")
    void invalidPageParameters() throws Exception {
        list("").andExpect(jsonPath("$.data.size").value(20));
        for (String query : new String[] {"?size=0", "?size=101", "?page=-1", "?size=abc"}) {
            list(query).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON-400"));
        }
    }

    // ---- 목록: 조건 ----

    @Test
    @DisplayName("기간은 요약과 같은 기준(오늘을 끝으로 N개월, 승인일)이고, 없으면 전체이며, 잘못된 값은 TRANS-002다")
    void periodFilter() throws Exception {
        save(today.minusDays(5).atTime(12, 0), "1M 안", 1_000, "CAFE");
        save(today.minusDays(40).atTime(12, 0), "3M 안", 1_000, "CAFE");
        save(today.minusDays(200).atTime(12, 0), "6M 밖", 1_000, "CAFE");

        list("?period=1M").andExpect(jsonPath("$.data.totalElements").value(1));
        list("?period=3M").andExpect(jsonPath("$.data.totalElements").value(2));
        list("?period=6m").andExpect(jsonPath("$.data.totalElements").value(2));
        list("").andExpect(jsonPath("$.data.totalElements").value(3));
        for (String query : new String[] {"?period=2M", "?period=", "?period=abc"}) {
            list(query).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("TRANS-002"));
        }
    }

    @Test
    @DisplayName("보유카드로 거른다. 내 카드가 아닌 번호는 결과가 비어 있고 오류가 아니다")
    void cardFilter() throws Exception {
        long second = newCardForSameUser("테스트 카드B");
        save(user, card, today.minusDays(2).atTime(9, 0), "카드A 거래", 1_000, 0, "CAFE");
        save(user, second, today.minusDays(3).atTime(9, 0), "카드B 거래", 2_000, 0, "CAFE");
        User other = newUser();
        long otherCard = newCard(other, "남의 카드");
        save(other, otherCard, today.minusDays(1).atTime(9, 0), "남의 거래", 9_000, 0, "CAFE");

        list("?cardId=" + card).andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.content[0].merchantName").value("카드A 거래"))
            .andExpect(jsonPath("$.data.content[0].cardId").value(card));
        list("?cardId=" + second).andExpect(jsonPath("$.data.content[0].merchantName").value("카드B 거래"));
        list("?cardId=" + otherCard).andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        list("?cardId=abc").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("기본은 분석 대상 카드의 거래만, cardScope=ALL이면 모두, cardId를 지정하면 그 카드가 우선이다")
    void cardScopeFilter() throws Exception {
        long unselected = newCardForSameUser("선택 안 한 카드");
        jdbc.update("update user_cards set selected = false where id = ?", unselected);
        save(user, card, today.minusDays(2).atTime(9, 0), "선택한 카드 거래", 1_000, 0, "CAFE");
        save(user, unselected, today.minusDays(3).atTime(9, 0), "선택 안 한 카드 거래", 2_000, 0, "CAFE");

        list("").andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.content[0].merchantName").value("선택한 카드 거래"));
        list("?cardScope=selected").andExpect(jsonPath("$.data.totalElements").value(1));
        list("?cardScope=ALL").andExpect(jsonPath("$.data.totalElements").value(2));
        list("?cardId=" + unselected).andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.content[0].merchantName").value("선택 안 한 카드 거래"));
        list("?cardScope=MINE").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    @Test
    @DisplayName("분석 대상 카드가 없으면 기본 범위의 목록은 비어 있고 오류가 아니다")
    void noSelectedCardsGivesEmptyList() throws Exception {
        jdbc.update("update user_cards set selected = false where id = ?", card);
        save(today.minusDays(2).atTime(9, 0), "거래", 1_000, "CAFE");

        list("").andExpect(status().isOk()).andExpect(jsonPath("$.data.totalElements").value(0));
        list("?cardScope=ALL").andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    @DisplayName("업종으로 거른다 (대소문자 무시). 사용자가 고친 값 기준이다")
    void categoryFilter() throws Exception {
        LocalDateTime at = today.minusDays(2).atTime(9, 0);
        save(at, "카페", 1_000, "CAFE");
        save(at, "음식점", 2_000, "DINING");
        save(at, "고친 거래", 3_000, "CAFE");
        patchCategory(accessToken, idOf("고친 거래"), "{\"category\":\"DINING\"}").andExpect(status().isOk());

        list("?category=CAFE").andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.content[0].merchantName").value("카페"));
        list("?category=dining").andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    @DisplayName("ETC는 분류 전(NULL)이거나 6개 업종이 아닌 값인 거래도 포함한다")
    void etcFilterIncludesUnclassified() throws Exception {
        LocalDateTime at = today.minusDays(2).atTime(9, 0);
        save(at, "ETC 명시", 1_000, "ETC");
        save(at, "알 수 없는 값", 2_000, "TEST_UNKNOWN");
        save(at, "분류 전", 3_000, "CAFE");
        jdbc.update("update card_transactions set category = null where merchant_name = '분류 전'");
        save(at, "카페", 4_000, "CAFE");

        list("?category=ETC").andExpect(jsonPath("$.data.totalElements").value(3))
            .andExpect(jsonPath("$.data.content[*].category").value(org.hamcrest.Matchers.everyItem(
                org.hamcrest.Matchers.is("ETC"))));
    }

    @Test
    @DisplayName("6개 업종이 아닌 값으로 거르면 400 COMMON-400이다")
    void invalidCategoryFilter() throws Exception {
        list("?category=FOOD").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("COMMON-400"));
        list("?category=").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("취소된 거래는 기본으로 보이고 상태로 구분하며, includeCanceled=false이면 전액 취소만 뺀다")
    void canceledTransactionVisibility() throws Exception {
        LocalDateTime at = today.minusDays(2).atTime(9, 0);
        save(user, card, at, "정상", 10_000, 0, "CAFE");
        save(user, card, at.minusMinutes(1), "일부 취소", 8_000, 3_000, "CAFE");
        save(user, card, at.minusMinutes(2), "전액 취소", 5_000, 5_000, "CAFE");

        list("").andExpect(jsonPath("$.data.totalElements").value(3))
            .andExpect(jsonPath("$.data.content[0].status").value("APPROVED"))
            .andExpect(jsonPath("$.data.content[1].status").value("PARTIALLY_CANCELED"))
            .andExpect(jsonPath("$.data.content[2].status").value("CANCELED"))
            .andExpect(jsonPath("$.data.content[2].netAmount").value(0));
        list("?includeCanceled=false").andExpect(jsonPath("$.data.totalElements").value(2))
            .andExpect(jsonPath("$.data.content[1].merchantName").value("일부 취소"));
    }

    @Test
    @DisplayName("조건을 함께 쓰면 모두 만족하는 거래만 나온다")
    void filtersCombine() throws Exception {
        long second = newCardForSameUser("테스트 카드B");
        save(user, card, today.minusDays(2).atTime(9, 0), "조건에 맞음", 1_000, 0, "CAFE");
        save(user, card, today.minusDays(2).atTime(9, 1), "업종이 다름", 1_000, 0, "DINING");
        save(user, second, today.minusDays(2).atTime(9, 2), "카드가 다름", 1_000, 0, "CAFE");
        save(user, card, today.minusDays(200).atTime(9, 3), "기간이 다름", 1_000, 0, "CAFE");

        list("?period=1M&cardId=" + card + "&category=CAFE").andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.content[0].merchantName").value("조건에 맞음"));
    }

    // ---- 격리, 상세 ----

    @Test
    @DisplayName("다른 사용자의 거래는 목록에 나오지 않고, 상세·수정·되돌리기는 없는 거래와 같은 404 TRANS-001이다")
    void usersAreIsolated() throws Exception {
        User other = newUser();
        long otherCard = newCard(other, "남의 카드");
        save(other, otherCard, today.minusDays(1).atTime(9, 0), "남의 거래", 9_000, 0, "CAFE");
        save(today.minusDays(2).atTime(9, 0), "내 거래", 1_000, "CAFE");
        long othersId = transactions.findByUserId(other.getId()).get(0).getId();

        list("").andExpect(jsonPath("$.data.totalElements").value(1))
            .andExpect(jsonPath("$.data.content[0].merchantName").value("내 거래"));
        call(get("/api/transactions/" + othersId), accessToken).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("TRANS-001"));
        patchCategory(accessToken, othersId, "{\"category\":\"DINING\"}").andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("TRANS-001"));
        call(delete("/api/transactions/" + othersId + "/category"), accessToken).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("TRANS-001"));

        CardTransaction untouched = transactions.findById(othersId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(untouched.getCategoryOverride()).isNull();
    }

    @Test
    @DisplayName("상세는 거래의 값과 업종을 내려주고, 카드 번호와 승인번호는 내려주지 않는다")
    void detail() throws Exception {
        save(user, card, today.minusDays(2).atTime(9, 0), "상세 거래", 12_000, 2_000, "CAFE");
        long id = idOf("상세 거래");

        call(get("/api/transactions/" + id), accessToken).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.transactionId").value(id))
            .andExpect(jsonPath("$.data.cardId").value(card))
            .andExpect(jsonPath("$.data.merchantName").value("상세 거래"))
            .andExpect(jsonPath("$.data.amount").value(12_000))
            .andExpect(jsonPath("$.data.canceledAmount").value(2_000))
            .andExpect(jsonPath("$.data.netAmount").value(10_000))
            .andExpect(jsonPath("$.data.status").value("PARTIALLY_CANCELED"))
            .andExpect(jsonPath("$.data.category").value("CAFE"))
            .andExpect(jsonPath("$.data.autoCategory").value("CAFE"))
            .andExpect(jsonPath("$.data.categoryEdited").value(false))
            .andExpect(jsonPath("$.data.approvalNo").doesNotExist())
            .andExpect(jsonPath("$.data.maskedNumber").doesNotExist());
    }

    @Test
    @DisplayName("없는 거래는 404 TRANS-001이고, 숫자가 아닌 ID는 400이다")
    void detailNotFound() throws Exception {
        call(get("/api/transactions/999999999"), accessToken).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("TRANS-001"));
        call(get("/api/transactions/abc"), accessToken).andExpect(status().isBadRequest());
    }

    // ---- 카테고리 수정 ----

    @Test
    @DisplayName("업종을 6개 중 하나로 고치면 그 값이 업종으로 보이고, 자동 분류 값은 그대로 남는다 (대소문자 무시)")
    void changeCategory() throws Exception {
        save(today.minusDays(2).atTime(9, 0), "고칠 거래", 5_000, "CAFE");
        long id = idOf("고칠 거래");

        patchCategory(accessToken, id, "{\"category\":\"dining\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.category").value("DINING"))
            .andExpect(jsonPath("$.data.autoCategory").value("CAFE"))
            .andExpect(jsonPath("$.data.categoryEdited").value(true));

        CardTransaction saved = transactions.findById(id).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(saved.getCategoryOverride()).isEqualTo("DINING");
        org.assertj.core.api.Assertions.assertThat(saved.getCategory()).isEqualTo("CAFE");
        call(get("/api/transactions/" + id), accessToken).andExpect(jsonPath("$.data.category").value("DINING"));
    }

    @Test
    @DisplayName("ETC로도 고칠 수 있고, 취소된 거래도 고칠 수 있다")
    void changeToEtcAndCanceledTransaction() throws Exception {
        save(user, card, today.minusDays(2).atTime(9, 0), "취소 거래", 5_000, 5_000, "CAFE");
        long id = idOf("취소 거래");

        patchCategory(accessToken, id, "{\"category\":\"ETC\"}").andExpect(status().isOk())
            .andExpect(jsonPath("$.data.category").value("ETC"))
            .andExpect(jsonPath("$.data.status").value("CANCELED"));
    }

    @Test
    @DisplayName("6개 업종이 아니거나 비어 있으면 400 COMMON-400이고 아무것도 바뀌지 않는다")
    void invalidCategoryIsRejected() throws Exception {
        save(today.minusDays(2).atTime(9, 0), "그대로", 5_000, "CAFE");
        long id = idOf("그대로");

        for (String body : new String[] {"{\"category\":\"FOOD\"}", "{\"category\":\"\"}", "{\"category\":\"  \"}",
            "{}", "{\"category\":null}"}) {
            patchCategory(accessToken, id, body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON-400"));
        }
        org.assertj.core.api.Assertions.assertThat(transactions.findById(id).orElseThrow().getCategoryOverride()).isNull();
    }

    @Test
    @DisplayName("수정한 업종을 지우면 자동 분류로 돌아가고, 고친 적이 없어도 오류 없이 현재 상태를 준다")
    void resetCategory() throws Exception {
        save(today.minusDays(2).atTime(9, 0), "되돌릴 거래", 5_000, "CAFE");
        long id = idOf("되돌릴 거래");
        patchCategory(accessToken, id, "{\"category\":\"DINING\"}").andExpect(status().isOk());

        call(delete("/api/transactions/" + id + "/category"), accessToken).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.category").value("CAFE"))
            .andExpect(jsonPath("$.data.categoryEdited").value(false));
        call(delete("/api/transactions/" + id + "/category"), accessToken).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.category").value("CAFE"));
        org.assertj.core.api.Assertions.assertThat(transactions.findById(id).orElseThrow().getCategoryOverride()).isNull();
    }

    @Test
    @DisplayName("수정한 업종은 이후 동기화(같은 거래를 다시 저장)에서 유지되고, 자동 분류 값만 갱신된다")
    void editedCategorySurvivesResync() throws Exception {
        LocalDateTime at = today.minusDays(2).atTime(9, 0);
        store.saveCollected(user.getId(), card, List.of(new CollectedTransaction("AP-FIXED", at, "유지 거래", 5_000, 0, 0,
            false, "CAFE")));
        long id = idOf("유지 거래");
        patchCategory(accessToken, id, "{\"category\":\"DINING\"}").andExpect(status().isOk());

        store.saveCollected(user.getId(), card, List.of(new CollectedTransaction("AP-FIXED", at, "유지 거래", 5_000, 0, 0,
            false, "SHOPPING")));

        call(get("/api/transactions/" + id), accessToken).andExpect(jsonPath("$.data.category").value("DINING"))
            .andExpect(jsonPath("$.data.autoCategory").value("SHOPPING"))
            .andExpect(jsonPath("$.data.categoryEdited").value(true));
    }

    @Test
    @DisplayName("고친 업종은 소비 요약의 업종별 합계에도 반영된다")
    void editedCategoryIsReflectedInSummary() throws Exception {
        save(today.minusDays(2).atTime(9, 0), "요약 반영", 4_000, "CAFE");
        patchCategory(accessToken, idOf("요약 반영"), "{\"category\":\"DINING\"}").andExpect(status().isOk());

        call(get("/api/transactions/summary?period=1M"), accessToken)
            .andExpect(jsonPath("$.data.categories[0].category").value("DINING"))
            .andExpect(jsonPath("$.data.categories[0].amount").value(4_000))
            .andExpect(jsonPath("$.data.categories[1].amount").value(0));
    }

}
