package com.richreach.card;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.richreach.TestcontainersConfiguration;
import com.richreach.auth.repository.RefreshTokenRepository;
import com.richreach.auth.service.AuthTokenService;
import com.richreach.card.dto.CollectedCard;
import com.richreach.card.entity.OwnedCard;
import com.richreach.card.repository.OwnedCardRepository;
import com.richreach.card.service.OwnedCardService;
import com.richreach.codef.entity.CardCompany;
import com.richreach.codef.entity.CodefConnection;
import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.repository.CodefConnectionRepository;
import com.richreach.codef.repository.CodefOrgAccountRepository;
import com.richreach.user.entity.User;
import com.richreach.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 보유카드 목록 조회와 분석 대상 선택 API를 보안 필터와 실제 MySQL로 확인한다.
 * 카드는 동기화 저장 서비스로 만들어서, 실제 동기화가 만드는 상태와 같은 데이터로 확인한다.
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class OwnedCardApiTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    private static final String PRODUCT_PREFIX = "test-cardapi-";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private MockMvc mockMvc;
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

    private User user;
    private String accessToken;
    private CodefOrgAccount kbAccount;

    @BeforeEach
    void setUp() {
        user = newUser();
        accessToken = authTokenService.issue(user.getId()).accessToken();
        kbAccount = newAccount(user);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from user_cards");
        orgAccounts.deleteAll();
        connections.deleteAll();
        refreshTokens.deleteAll();
        users.deleteAll();
        jdbc.update("delete from card_products where card_id like ?", PRODUCT_PREFIX + "%");
    }

    private User newUser() {
        int n = SEQUENCE.incrementAndGet();
        return users.save(new User("cards" + n + "@example.com", "hash", "김리치", "010%08d".formatted(5000000 + n),
            "REAL", LocalDateTime.now()));
    }

    private CodefOrgAccount newAccount(User owner) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        CodefConnection connection = connections.findByUserId(owner.getId()).orElseGet(() -> connections.save(
            new CodefConnection(owner, "test-connected-id-" + owner.getId(), "DEMO", now, now, now, now)));
        return orgAccounts.save(CodefOrgAccount.connected(connection, "0301", now));
    }

    private long insertProduct(String cardName, String cardType) {
        String cardId = PRODUCT_PREFIX + SEQUENCE.incrementAndGet();
        jdbc.update("""
            insert into card_products (issuer_code, issuer, card_id, card_name, card_type, card_status, product_class,
                recommendable, source_snapshot_date, source_text_sha256, review_status)
            values ('kb', '테스트카드사', ?, ?, ?, 'listed', 'PAYMENT_CARD', true, '2026-10-01', ?,
                'AUTO_EXTRACTED_NEEDS_REVIEW')
            """, cardId, cardName, cardType, "0".repeat(64));
        return jdbc.queryForObject("select id from card_products where card_id = ?", Long.class, cardId);
    }

    /** 동기화가 카드를 저장한 것과 같은 방식으로 보유카드를 만든다. */
    private void sync(User owner, CodefOrgAccount account, CollectedCard... cards) {
        ownedCardService.saveSynced(owner.getId(), account.getId(), CardCompany.KB, List.of(cards));
    }

    private OwnedCard cardNamed(String name) {
        return ownedCards.findByUserId(user.getId()).stream()
            .filter(card -> card.getCardName().equals(name)).findFirst().orElseThrow();
    }

    private ResultActions list(String token) throws Exception {
        var request = get("/api/cards");
        return mockMvc.perform(token == null ? request : request.header("Authorization", "Bearer " + token));
    }

    private ResultActions select(String token, String json) throws Exception {
        var request = put("/api/cards/selection").contentType(MediaType.APPLICATION_JSON).content(json);
        return mockMvc.perform(token == null ? request : request.header("Authorization", "Bearer " + token));
    }

    private static String body(Long... ids) {
        return "{\"cardIds\":" + java.util.Arrays.toString(ids) + "}";
    }

    private boolean selectedInDb(String name) {
        return cardNamed(name).isSelected();
    }

    // ---- 로그인 ----

    @Test
    @DisplayName("목록과 선택 모두 로그인이 필요하다 (토큰이 없으면 401 AUTH-001)")
    void requiresLogin() throws Exception {
        list(null).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH-001"));
        select(null, body(1L)).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTH-001"));
    }

    // ---- 목록 ----

    @Test
    @DisplayName("보유카드가 없으면 빈 배열이다")
    void emptyList() throws Exception {
        list(accessToken).andExpect(status().isOk()).andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("목록은 카드 정보, 끝 4자리 번호, 분석 상태, 기본 선택을 내려준다 (매칭된 카드는 선택됨, 아닌 카드는 분석 불가)")
    void listShape() throws Exception {
        long productId = insertProduct("노리 체크카드", "check");
        sync(user, kbAccount,
            new CollectedCard("KB국민카드 노리 체크카드", "1234-5678-9012-3456", "체크"),
            new CollectedCard("전혀 다른 카드", "2222333344445555", "신용"));

        list(accessToken).andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data", hasSize(2)))
            .andExpect(jsonPath("$.data[0].cardId").value(cardNamed("KB국민카드 노리 체크카드").getId()))
            .andExpect(jsonPath("$.data[0].cardProductId").value(productId))
            .andExpect(jsonPath("$.data[0].cardCompany").value("KB"))
            .andExpect(jsonPath("$.data[0].cardName").value("KB국민카드 노리 체크카드"))
            .andExpect(jsonPath("$.data[0].maskedNumber").value("3456"))
            .andExpect(jsonPath("$.data[0].cardType").value("CHECK"))
            .andExpect(jsonPath("$.data[0].analysisStatus").value("AVAILABLE"))
            .andExpect(jsonPath("$.data[0].selected").value(true))
            .andExpect(jsonPath("$.data[0].imageUrl").doesNotExist())
            .andExpect(jsonPath("$.data[1].cardProductId").doesNotExist())
            .andExpect(jsonPath("$.data[1].maskedNumber").value("5555"))
            .andExpect(jsonPath("$.data[1].cardType").value("CREDIT"))
            .andExpect(jsonPath("$.data[1].analysisStatus").value("UNAVAILABLE"))
            .andExpect(jsonPath("$.data[1].selected").value(false));
    }

    @Test
    @DisplayName("응답에는 전체 카드 번호가 나오지 않는다 (끝 4자리만)")
    void fullNumberIsNeverExposed() throws Exception {
        sync(user, kbAccount, new CollectedCard("테스트 카드A", "4111111111111234", "신용"));

        String body = list(accessToken).andReturn().getResponse().getContentAsString();

        assertThat(body).contains("1234").doesNotContain("4111").doesNotContain("****");
    }

    @Test
    @DisplayName("카드 상품이 있으면 상품의 종류로, 없으면 카드사가 준 종류로 신용·체크를 정하고 모르면 UNKNOWN이다")
    void cardTypeMapping() throws Exception {
        insertProduct("종류 신용카드", "credit_or_special");
        sync(user, kbAccount,
            new CollectedCard("종류 신용카드", "1111", "체크"),
            new CollectedCard("카드 하나", "2222", "CHECK"),
            new CollectedCard("카드 둘", "3333", ""),
            new CollectedCard("카드 셋", "4444", "기타"));

        list(accessToken)
            .andExpect(jsonPath("$.data[0].cardType").value("CREDIT"))
            .andExpect(jsonPath("$.data[1].cardType").value("CHECK"))
            .andExpect(jsonPath("$.data[2].cardType").value("UNKNOWN"))
            .andExpect(jsonPath("$.data[3].cardType").value("UNKNOWN"));
    }

    @Test
    @DisplayName("동기화에서 사라진(비활성) 카드는 목록에 나오지 않는다")
    void inactiveCardsAreHidden() throws Exception {
        sync(user, kbAccount, new CollectedCard("테스트 카드A", "1111", "신용"), new CollectedCard("테스트 카드B", "2222", "신용"));
        sync(user, kbAccount, new CollectedCard("테스트 카드A", "1111", "신용"));

        list(accessToken).andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].cardName").value("테스트 카드A"));
    }

    @Test
    @DisplayName("다른 사용자의 카드는 내 목록에 나오지 않는다")
    void otherUsersCardsAreNotListed() throws Exception {
        User other = newUser();
        sync(other, newAccount(other), new CollectedCard("남의 카드", "9999", "신용"));
        sync(user, kbAccount, new CollectedCard("내 카드", "1111", "신용"));

        list(accessToken).andExpect(jsonPath("$.data", hasSize(1)))
            .andExpect(jsonPath("$.data[0].cardName").value("내 카드"));
    }

    // ---- 선택 ----

    @Test
    @DisplayName("선택은 보낸 카드 전체로 교체되고, 갱신된 목록을 돌려준다")
    void selectionReplacesAndReturnsList() throws Exception {
        insertProduct("에이 카드", "credit");
        insertProduct("비이 카드", "credit");
        insertProduct("씨이 카드", "credit");
        sync(user, kbAccount, new CollectedCard("에이 카드", "1111", "신용"),
            new CollectedCard("비이 카드", "2222", "신용"), new CollectedCard("씨이 카드", "3333", "신용"));
        assertThat(ownedCards.findByUserId(user.getId())).allMatch(OwnedCard::isSelected);

        Long b = cardNamed("비이 카드").getId();
        select(accessToken, body(b)).andExpect(status().isOk())
            .andExpect(jsonPath("$.data", hasSize(3)))
            .andExpect(jsonPath("$.data[0].selected").value(false))
            .andExpect(jsonPath("$.data[1].selected").value(true))
            .andExpect(jsonPath("$.data[2].selected").value(false));

        assertThat(selectedInDb("에이 카드")).isFalse();
        assertThat(selectedInDb("비이 카드")).isTrue();
        assertThat(selectedInDb("씨이 카드")).isFalse();

        select(accessToken, body(cardNamed("에이 카드").getId(), cardNamed("씨이 카드").getId()))
            .andExpect(status().isOk());

        assertThat(selectedInDb("에이 카드")).isTrue();
        assertThat(selectedInDb("비이 카드")).isFalse();
        assertThat(selectedInDb("씨이 카드")).isTrue();
    }

    @Test
    @DisplayName("같은 카드 ID를 여러 번 보내도 한 번으로 본다")
    void duplicateIdsAreCollapsed() throws Exception {
        insertProduct("에이 카드", "credit");
        sync(user, kbAccount, new CollectedCard("에이 카드", "1111", "신용"));
        Long a = cardNamed("에이 카드").getId();

        select(accessToken, body(a, a, a)).andExpect(status().isOk());

        assertThat(selectedInDb("에이 카드")).isTrue();
    }

    @Test
    @DisplayName("분석할 수 없는(상품에 매칭되지 않은) 카드를 선택하면 400 CARD-002이고 아무것도 바뀌지 않는다")
    void cannotSelectUnanalyzableCard() throws Exception {
        insertProduct("에이 카드", "credit");
        sync(user, kbAccount, new CollectedCard("에이 카드", "1111", "신용"), new CollectedCard("매칭 안 됨", "2222", "신용"));

        select(accessToken, body(cardNamed("에이 카드").getId(), cardNamed("매칭 안 됨").getId()))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CARD-002"));

        assertThat(selectedInDb("에이 카드")).isTrue();
        assertThat(selectedInDb("매칭 안 됨")).isFalse();
    }

    @Test
    @DisplayName("없는 카드는 404 CARD-001이고 아무것도 바뀌지 않는다")
    void unknownCardIsNotFound() throws Exception {
        insertProduct("에이 카드", "credit");
        sync(user, kbAccount, new CollectedCard("에이 카드", "1111", "신용"));

        select(accessToken, body(cardNamed("에이 카드").getId(), 999999999L))
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("CARD-001"));

        assertThat(selectedInDb("에이 카드")).isTrue();
    }

    @Test
    @DisplayName("다른 사용자의 카드는 없는 카드와 같은 404 CARD-001이고, 그 카드의 선택은 바뀌지 않는다")
    void otherUsersCardCannotBeSelected() throws Exception {
        User other = newUser();
        insertProduct("남의 카드", "credit");
        sync(other, newAccount(other), new CollectedCard("남의 카드", "9999", "신용"));
        Long theirs = ownedCards.findByUserId(other.getId()).get(0).getId();
        jdbc.update("update user_cards set selected = false where id = ?", theirs);

        select(accessToken, body(theirs)).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CARD-001"));

        assertThat(ownedCards.findById(theirs).orElseThrow().isSelected()).isFalse();
    }

    @Test
    @DisplayName("사라진(비활성) 카드를 선택하면 404 CARD-001이다")
    void inactiveCardCannotBeSelected() throws Exception {
        insertProduct("에이 카드", "credit");
        insertProduct("비이 카드", "credit");
        sync(user, kbAccount, new CollectedCard("에이 카드", "1111", "신용"), new CollectedCard("비이 카드", "2222", "신용"));
        Long gone = cardNamed("비이 카드").getId();
        sync(user, kbAccount, new CollectedCard("에이 카드", "1111", "신용"));

        select(accessToken, body(gone)).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("CARD-001"));
    }

    @Test
    @DisplayName("카드를 하나도 보내지 않거나 빈 값이 있으면 400 COMMON-400이다")
    void validatesRequest() throws Exception {
        select(accessToken, "{\"cardIds\":[]}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-400"));
        select(accessToken, "{}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-400"));
        select(accessToken, "{\"cardIds\":[null]}").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-400"));
        select(accessToken, "{\"cardIds\":[\"abc\"]}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("다시 동기화해도 사용자가 고른 선택은 유지되고, 목록에도 그대로 보인다")
    void selectionSurvivesResync() throws Exception {
        insertProduct("에이 카드", "credit");
        insertProduct("비이 카드", "credit");
        sync(user, kbAccount, new CollectedCard("에이 카드", "1111", "신용"), new CollectedCard("비이 카드", "2222", "신용"));
        select(accessToken, body(cardNamed("에이 카드").getId())).andExpect(status().isOk());

        sync(user, kbAccount, new CollectedCard("에이 카드", "1111", "신용"), new CollectedCard("비이 카드", "2222", "신용"));

        list(accessToken).andExpect(jsonPath("$.data[0].selected").value(true))
            .andExpect(jsonPath("$.data[1].selected").value(false));
    }

    @Test
    @DisplayName("응답에서 읽은 카드 ID로 혜택 상세 조회의 ID(cardProductId)와 구분된다")
    void cardIdIsNotTheCardProductId() throws Exception {
        long productId = insertProduct("에이 카드", "credit");
        sync(user, kbAccount, new CollectedCard("에이 카드", "1111", "신용"));

        String body = list(accessToken).andReturn().getResponse().getContentAsString();

        long cardId = ((Number) JsonPath.read(body, "$.data[0].cardId")).longValue();
        long cardProductId = ((Number) JsonPath.read(body, "$.data[0].cardProductId")).longValue();
        assertThat(cardProductId).isEqualTo(productId);
        assertThat(cardId).isEqualTo(cardNamed("에이 카드").getId());
    }

}
