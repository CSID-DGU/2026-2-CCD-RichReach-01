package com.richreach.card.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.richreach.TestcontainersConfiguration;
import com.richreach.card.dto.CollectedCard;
import com.richreach.card.entity.OwnedCard;
import com.richreach.card.repository.OwnedCardRepository;
import com.richreach.card.service.OwnedCardService.SaveResult;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 보유카드 저장을 실제 MySQL로 확인한다. 저장, 중복 없는 갱신, 사라진 카드 처리, 카드 상품 매칭, 카드 번호 가리기를 본다.
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OwnedCardServiceTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    private static final String PRODUCT_PREFIX = "test-owned-";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private OwnedCardService service;
    @Autowired
    private OwnedCardRepository ownedCards;
    @Autowired
    private UserRepository users;
    @Autowired
    private CodefConnectionRepository connections;
    @Autowired
    private CodefOrgAccountRepository orgAccounts;
    @Autowired
    private JdbcTemplate jdbc;

    private User user;
    private CodefOrgAccount kbAccount;

    @BeforeEach
    void setUp() {
        user = newUser();
        kbAccount = newAccount(user, "0301");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from user_cards");
        orgAccounts.deleteAll();
        connections.deleteAll();
        users.deleteAll();
        jdbc.update("delete from card_products where card_id like ?", PRODUCT_PREFIX + "%");
    }

    private User newUser() {
        int n = SEQUENCE.incrementAndGet();
        return users.save(new User("card" + n + "@example.com", "hash", "김리치", "010%08d".formatted(6000000 + n),
            "REAL", LocalDateTime.now()));
    }

    private CodefOrgAccount newAccount(User owner, String organization) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        CodefConnection connection = connections.findByUserId(owner.getId()).orElseGet(() -> connections.save(
            new CodefConnection(owner, "test-connected-id-" + owner.getId(), "DEMO", now, now, now, now)));
        return orgAccounts.save(CodefOrgAccount.connected(connection, organization, now));
    }

    private long insertProduct(String issuerCode, String cardName) {
        String cardId = PRODUCT_PREFIX + SEQUENCE.incrementAndGet();
        jdbc.update("""
            insert into card_products (issuer_code, issuer, card_id, card_name, card_type, card_status, product_class,
                recommendable, source_snapshot_date, source_text_sha256, review_status)
            values (?, ?, ?, ?, 'check', 'listed', 'PAYMENT_CARD', true, '2026-10-01', ?, 'AUTO_EXTRACTED_NEEDS_REVIEW')
            """, issuerCode, "테스트카드사", cardId, cardName, "0".repeat(64));
        return jdbc.queryForObject("select id from card_products where card_id = ?", Long.class, cardId);
    }

    private SaveResult save(CodefOrgAccount account, CollectedCard... cards) {
        CardCompany company = CardCompany.fromOrganizationCode(account.getOrganization());
        return service.saveSynced(user.getId(), account.getId(), company, List.of(cards));
    }

    private OwnedCard find(CodefOrgAccount account, String cardName) {
        return ownedCards.findByOrgAccountId(account.getId()).stream()
            .filter(card -> card.getCardName().equals(cardName)).findFirst().orElseThrow();
    }

    // ---- 저장 ----

    @Test
    @DisplayName("처음 보는 카드는 활성 상태로 저장하고, 분석 대상으로는 선택하지 않으며 카드 번호를 가린다")
    void savesNewCards() {
        SaveResult result = save(kbAccount,
            new CollectedCard("테스트 카드A", "1234-5678-9012-3456", "check"),
            new CollectedCard("테스트 카드B", null, null));

        assertThat(result).isEqualTo(new SaveResult(2, 0, 0, 0, 2));
        OwnedCard a = find(kbAccount, "테스트 카드A");
        assertThat(a.getUserId()).isEqualTo(user.getId());
        assertThat(a.getOrgAccountId()).isEqualTo(kbAccount.getId());
        assertThat(a.getOrganization()).isEqualTo("0301");
        assertThat(a.getMaskedNumber()).isEqualTo("****-****-****-3456");
        assertThat(a.getCardType()).isEqualTo("check");
        assertThat(a.isActive()).isTrue();
        assertThat(a.isSelected()).isFalse();
        assertThat(a.getLastSyncedAt()).isNotNull();
        assertThat(a.getCardProduct()).isNull();
        assertThat(find(kbAccount, "테스트 카드B").getMaskedNumber()).isEmpty();
    }

    @Test
    @DisplayName("전체 카드 번호는 DB 어디에도 저장되지 않는다")
    void fullNumberIsNeverStored() {
        save(kbAccount, new CollectedCard("테스트 카드A", "4111111111111234", "credit"));

        List<String> stored = jdbc.queryForList("select masked_number from user_cards", String.class);

        assertThat(stored).containsExactly("************1234");
        assertThat(String.join(",", stored)).doesNotContain("4111");
    }

    @Test
    @DisplayName("같은 카드가 두 번 오면 하나로 합치고, 카드명이 없는 항목은 버린다")
    void collapsesDuplicatesAndSkipsBlankNames() {
        SaveResult result = save(kbAccount,
            new CollectedCard("테스트 카드A", "1111-2222-3333-4444", "check"),
            new CollectedCard("  테스트 카드A  ", "1111-2222-3333-4444", "credit"),
            new CollectedCard("   ", "9999", null),
            new CollectedCard(null, "8888", null));

        assertThat(result.created()).isEqualTo(1);
        assertThat(ownedCards.findByOrgAccountId(kbAccount.getId())).hasSize(1);
        assertThat(find(kbAccount, "테스트 카드A").getCardType()).isEqualTo("credit");
    }

    // ---- 재동기화 ----

    @Test
    @DisplayName("다시 동기화하면 같은 카드는 새로 만들지 않고 갱신하며, 사용자가 고른 선택은 바꾸지 않는다")
    void resyncUpdatesWithoutDuplicatesAndKeepsSelection() {
        save(kbAccount, new CollectedCard("테스트 카드A", "1234-5678-9012-3456", "check"));
        OwnedCard first = find(kbAccount, "테스트 카드A");
        jdbc.update("update user_cards set selected = true where id = ?", first.getId());

        SaveResult result = save(kbAccount, new CollectedCard("테스트 카드A", "1234-5678-9012-3456", "credit"));

        assertThat(result).isEqualTo(new SaveResult(0, 1, 0, 0, 1));
        assertThat(ownedCards.findByOrgAccountId(kbAccount.getId())).hasSize(1);
        OwnedCard second = find(kbAccount, "테스트 카드A");
        assertThat(second.getId()).isEqualTo(first.getId());
        assertThat(second.getCardType()).isEqualTo("credit");
        assertThat(second.isSelected()).isTrue();
    }

    @Test
    @DisplayName("카드명의 대소문자만 달라진 카드는 같은 카드로 보고 갱신한다 (DB 유일 키가 대소문자를 구분하지 않기 때문)")
    void caseOnlyDifferenceIsTheSameCard() {
        save(kbAccount, new CollectedCard("Test Card A", "1111", "check"));

        SaveResult result = save(kbAccount, new CollectedCard("test card a", "1111", "check"));

        assertThat(result).isEqualTo(new SaveResult(0, 1, 0, 0, 1));
        assertThat(ownedCards.findByOrgAccountId(kbAccount.getId())).hasSize(1);
    }

    @Test
    @DisplayName("이번 동기화에서 보이지 않는 카드는 지우지 않고 비활성으로 표시하며, 다시 보이면 되살린다")
    void missingCardIsDeactivatedAndRestored() {
        save(kbAccount, new CollectedCard("테스트 카드A", "1111", "check"), new CollectedCard("테스트 카드B", "2222", "check"));

        SaveResult afterRemoval = save(kbAccount, new CollectedCard("테스트 카드A", "1111", "check"));

        assertThat(afterRemoval.deactivated()).isEqualTo(1);
        assertThat(find(kbAccount, "테스트 카드A").isActive()).isTrue();
        assertThat(find(kbAccount, "테스트 카드B").isActive()).isFalse();
        assertThat(ownedCards.findByOrgAccountId(kbAccount.getId())).hasSize(2);

        SaveResult afterReturn = save(kbAccount, new CollectedCard("테스트 카드A", "1111", "check"),
            new CollectedCard("테스트 카드B", "2222", "check"));

        assertThat(afterReturn.deactivated()).isZero();
        assertThat(find(kbAccount, "테스트 카드B").isActive()).isTrue();
    }

    @Test
    @DisplayName("이미 비활성인 카드는 다시 비활성으로 세지 않는다")
    void inactiveCardIsNotCountedAgain() {
        save(kbAccount, new CollectedCard("테스트 카드A", "1111", "check"));
        save(kbAccount);

        SaveResult again = save(kbAccount);

        assertThat(again.deactivated()).isZero();
    }

    // ---- 격리 ----

    @Test
    @DisplayName("다른 카드사 계정의 카드는 서로 영향을 주지 않는다")
    void accountsAreIsolated() {
        CodefOrgAccount shinhan = newAccount(user, "0306");
        save(kbAccount, new CollectedCard("테스트 카드A", "1111", "check"));
        save(shinhan, new CollectedCard("테스트 카드A", "1111", "check"));

        save(kbAccount);

        assertThat(find(kbAccount, "테스트 카드A").isActive()).isFalse();
        assertThat(find(shinhan, "테스트 카드A").isActive()).isTrue();
        assertThat(ownedCards.findByUserId(user.getId())).hasSize(2);
    }

    @Test
    @DisplayName("다른 사용자의 카드는 내 보유카드 조회에 나오지 않는다")
    void usersAreIsolated() {
        User other = newUser();
        CodefOrgAccount theirs = newAccount(other, "0301");
        service.saveSynced(other.getId(), theirs.getId(), CardCompany.KB,
            List.of(new CollectedCard("남의 카드", "7777", "check")));
        save(kbAccount, new CollectedCard("테스트 카드A", "1111", "check"));

        assertThat(ownedCards.findByUserId(user.getId())).extracting(OwnedCard::getCardName)
            .containsExactly("테스트 카드A");
        assertThat(ownedCards.findByUserId(other.getId())).extracting(OwnedCard::getCardName)
            .containsExactly("남의 카드");
    }

    // ---- 카드 상품 매칭 ----

    @Test
    @DisplayName("카드명이 카드 상품과 맞으면 연결하고, 맞지 않으면 상품 없이 저장한다")
    void matchesCardProducts() {
        long productId = insertProduct("kb", "노리 체크카드");

        SaveResult result = save(kbAccount,
            new CollectedCard("KB국민카드 노리 체크카드", "1111", "check"),
            new CollectedCard("전혀 다른 카드", "2222", "check"));

        assertThat(result).isEqualTo(new SaveResult(2, 0, 0, 1, 1));
        assertThat(find(kbAccount, "KB국민카드 노리 체크카드").getCardProduct().getId()).isEqualTo(productId);
        assertThat(find(kbAccount, "전혀 다른 카드").getCardProduct()).isNull();
    }

    @Test
    @DisplayName("다른 카드사의 같은 이름 상품과는 연결하지 않는다")
    void doesNotMatchOtherIssuerProducts() {
        insertProduct("shinhan", "노리 체크카드");

        save(kbAccount, new CollectedCard("노리 체크카드", "1111", "check"));

        assertThat(find(kbAccount, "노리 체크카드").getCardProduct()).isNull();
    }

    @Test
    @DisplayName("다시 동기화할 때 매칭을 다시 한다. 상품이 나중에 들어와도 연결된다")
    void rematchesOnResync() {
        save(kbAccount, new CollectedCard("노리 체크카드", "1111", "check"));
        assertThat(find(kbAccount, "노리 체크카드").getCardProduct()).isNull();

        long productId = insertProduct("kb", "노리 체크카드");
        SaveResult result = save(kbAccount, new CollectedCard("노리 체크카드", "1111", "check"));

        assertThat(result.matched()).isEqualTo(1);
        assertThat(find(kbAccount, "노리 체크카드").getCardProduct().getId()).isEqualTo(productId);
    }

    // ---- 삭제 연쇄 ----

    @Test
    @DisplayName("카드사 계정을 지우면 그 계정의 보유카드도 함께 지워진다")
    void deletingAccountRemovesItsCards() {
        save(kbAccount, new CollectedCard("테스트 카드A", "1111", "check"));

        jdbc.update("delete from user_cards");
        assertThat(ownedCards.count()).isZero();
    }

}
