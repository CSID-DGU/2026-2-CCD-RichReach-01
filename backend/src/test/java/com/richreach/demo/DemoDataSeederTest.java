package com.richreach.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.richreach.TestcontainersConfiguration;
import com.richreach.card.entity.OwnedCard;
import com.richreach.card.repository.OwnedCardRepository;
import com.richreach.codef.entity.CodefConnection;
import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.repository.CodefConnectionRepository;
import com.richreach.codef.repository.CodefOrgAccountRepository;
import com.richreach.demo.DemoDataSeeder.DemoSeedResult;
import com.richreach.global.category.SpendingCategory;
import com.richreach.transaction.dto.CardScope;
import com.richreach.transaction.dto.CollectedTransaction;
import com.richreach.transaction.dto.SummaryPeriod;
import com.richreach.transaction.dto.TransactionSummaryResponse;
import com.richreach.transaction.entity.CardTransaction;
import com.richreach.transaction.repository.CardTransactionRepository;
import com.richreach.transaction.service.TransactionStoreService;
import com.richreach.transaction.service.TransactionSummaryService;
import com.richreach.user.entity.User;
import com.richreach.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
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
 * 시연 데이터 만들기를 실제 MySQL로 확인한다. 보유카드와 거래가 만들어지는지, 가맹점 분류와 소비 요약까지 이어지는지,
 * 다시 실행해도 중복이 없는지, 지우고 다시 만들기, 그리고 실제 연결이 있는 계정이나 없는 계정을 거부하는지를 본다.
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class DemoDataSeederTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    private static final String PRODUCT_PREFIX = "test-demoseed-";
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    @Autowired
    private DemoDataSeeder seeder;
    @Autowired
    private TransactionStoreService store;
    @Autowired
    private TransactionSummaryService summaryService;
    @Autowired
    private CardTransactionRepository transactions;
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

    @BeforeEach
    void setUp() {
        user = newUser();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from card_transactions");
        jdbc.update("delete from user_cards");
        orgAccounts.deleteAll();
        connections.deleteAll();
        users.deleteAll();
        jdbc.update("delete from card_products where card_id like ?", PRODUCT_PREFIX + "%");
    }

    private User newUser() {
        int n = SEQUENCE.incrementAndGet();
        return users.save(new User("demoseed" + n + "@example.com", "hash", "김리치", "010%08d".formatted(1000000 + n),
            "REAL", LocalDateTime.now()));
    }

    private void insertProduct(String issuerCode, String cardName) {
        jdbc.update("""
            insert into card_products (issuer_code, issuer, card_id, card_name, card_type, card_status, product_class,
                recommendable, source_snapshot_date, source_text_sha256, review_status)
            values (?, '테스트카드사', ?, ?, 'credit', 'listed', 'PAYMENT_CARD', true, '2026-10-01', ?,
                'AUTO_EXTRACTED_NEEDS_REVIEW')
            """, issuerCode, PRODUCT_PREFIX + SEQUENCE.incrementAndGet(), cardName, "0".repeat(64));
    }

    private void insertProducts() {
        insertProduct("woori", "우리 데모테스트카드");
        insertProduct("kb", "KB국민 데모테스트카드");
    }

    private List<CardTransaction> transactionsOf(User owner) {
        return transactions.findByUserId(owner.getId());
    }

    // ---- 카드 ----

    @Test
    @DisplayName("카드 상품이 있으면 그 이름으로 보유카드를 만들어, 분석할 수 있고 처음부터 선택된 카드로 보인다")
    void createsAnalyzableCardsFromProducts() {
        insertProducts();

        DemoSeedResult result = seeder.seed(user.getEmail(), false);

        assertThat(result.cards()).isEqualTo(2);
        assertThat(result.analyzableCards()).isEqualTo(2);
        List<OwnedCard> cards = ownedCards.findByUserId(user.getId());
        assertThat(cards).hasSize(2).allSatisfy(card -> {
            assertThat(card.isAnalyzable()).isTrue();
            assertThat(card.isSelected()).isTrue();
            assertThat(card.getMaskedNumber()).endsWith("000" + (cards.indexOf(card) + 1));
        });
        assertThat(cards).extracting(OwnedCard::getCardName)
            .containsExactlyInAnyOrder("우리 데모테스트카드", "KB국민 데모테스트카드");
    }

    @Test
    @DisplayName("카드 상품이 없으면 분석할 수 없는 샘플 카드 2장으로 만든다")
    void fallsBackToSampleCardsWithoutProducts() {
        DemoSeedResult result = seeder.seed(user.getEmail(), false);

        assertThat(result.cards()).isEqualTo(2);
        assertThat(result.analyzableCards()).isZero();
        assertThat(ownedCards.findByUserId(user.getId())).hasSize(2)
            .allSatisfy(card -> assertThat(card.isAnalyzable()).isFalse());
        assertThat(result.createdTransactions()).isPositive();
    }

    @Test
    @DisplayName("가짜 카드사 연결(demo- 접두어)을 만들고, 그 연결로는 카드사 동기화를 할 수 없다")
    void createsFakeConnection() {
        insertProducts();

        seeder.seed(user.getEmail(), false);

        CodefConnection connection = connections.findByUserId(user.getId()).orElseThrow();
        assertThat(connection.getConnectedId()).startsWith("demo-connected-");
        assertThat(orgAccounts.findByConnectionId(connection.getId())).hasSize(2)
            .allSatisfy(account -> assertThat(account.getStatus()).isEqualTo(CodefOrgAccount.STATUS_ACTIVE));
    }

    // ---- 거래, 분류, 요약 ----

    @Test
    @DisplayName("거래를 만들고, 가맹점 분류 규칙이 6개 업종을 모두 채운다")
    void createsClassifiedTransactions() {
        insertProducts();

        DemoSeedResult result = seeder.seed(user.getEmail(), false);

        List<CardTransaction> created = transactionsOf(user);
        assertThat(created).hasSize(result.createdTransactions()).hasSizeGreaterThan(300);
        assertThat(created).allSatisfy(tx -> assertThat(tx.getApprovalNo()).startsWith("DEMO-"));
        Set<SpendingCategory> categories = created.stream()
            .map(tx -> SpendingCategory.fromCode(tx.effectiveCategory()).orElse(SpendingCategory.ETC))
            .collect(Collectors.toSet());
        assertThat(categories).containsExactlyInAnyOrder(SpendingCategory.values());
    }

    @Test
    @DisplayName("만든 데이터가 소비 요약까지 이어지고, 1·3·6개월 조회 모두 부분 데이터로 표시되지 않는다")
    void feedsTheSummary() {
        insertProducts();
        seeder.seed(user.getEmail(), false);

        for (SummaryPeriod period : SummaryPeriod.values()) {
            TransactionSummaryResponse summary = summaryService.summarize(user.getId(), period, CardScope.SELECTED);

            assertThat(summary.totalAmount()).as(period.code()).isPositive();
            assertThat(summary.partial()).as(period.code()).isFalse();
        }
        TransactionSummaryResponse sixMonths = summaryService.summarize(user.getId(), SummaryPeriod.SIX_MONTHS, CardScope.SELECTED);
        assertThat(sixMonths.categories()).allSatisfy(item -> assertThat(item.amount()).isPositive());
        assertThat(sixMonths.canceledAmount()).isPositive();
    }

    // ---- 다시 실행, 지우고 다시 만들기 ----

    @Test
    @DisplayName("다시 실행해도 새로 만드는 거래가 없고 개수도 그대로다")
    void rerunCreatesNoDuplicates() {
        insertProducts();
        DemoSeedResult first = seeder.seed(user.getEmail(), false);
        int cardCount = ownedCards.findByUserId(user.getId()).size();

        DemoSeedResult second = seeder.seed(user.getEmail(), false);

        assertThat(second.createdTransactions()).isZero();
        assertThat(second.unchangedTransactions()).isEqualTo(first.createdTransactions());
        assertThat(transactionsOf(user)).hasSize(first.createdTransactions());
        assertThat(ownedCards.findByUserId(user.getId())).hasSize(cardCount);
    }

    @Test
    @DisplayName("reset이면 이 계정의 시연 데이터를 지우고 같은 데이터로 다시 만든다")
    void resetRebuildsTheSameData() {
        insertProducts();
        DemoSeedResult first = seeder.seed(user.getEmail(), false);
        long cardId = ownedCards.findByUserId(user.getId()).get(0).getId();
        store.saveCollected(user.getId(), cardId, List.of(new CollectedTransaction("EXTRA-1",
            LocalDate.now().minusDays(3).atTime(9, 0), "직접 추가한 거래", 1_000, 0, 0, false, null)));
        assertThat(transactionsOf(user)).hasSize(first.createdTransactions() + 1);

        DemoSeedResult again = seeder.seed(user.getEmail(), true);

        assertThat(again.createdTransactions()).isEqualTo(first.createdTransactions());
        assertThat(transactionsOf(user)).hasSize(first.createdTransactions())
            .noneMatch(tx -> tx.getApprovalNo().equals("EXTRA-1"));
    }

    @Test
    @DisplayName("다른 사용자의 데이터는 건드리지 않는다")
    void otherUsersAreUntouched() {
        User other = newUser();
        seeder.seed(other.getEmail(), false);
        int othersTransactions = transactionsOf(other).size();
        int othersCards = ownedCards.findByUserId(other.getId()).size();

        insertProducts();
        seeder.seed(user.getEmail(), true);

        assertThat(transactionsOf(other)).hasSize(othersTransactions);
        assertThat(ownedCards.findByUserId(other.getId())).hasSize(othersCards);
    }

    // ---- 거부 ----

    @Test
    @DisplayName("이메일을 지정하지 않으면 중단한다")
    void blankEmailIsRejected() {
        assertThatThrownBy(() -> seeder.seed("  ", false)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> seeder.seed(null, false)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("가입하지 않은 이메일이면 계정을 새로 만들지 않고 중단한다")
    void unknownUserIsRejected() {
        long usersBefore = users.count();

        assertThatThrownBy(() -> seeder.seed("nobody-" + SEQUENCE.incrementAndGet() + "@example.com", false))
            .isInstanceOf(IllegalStateException.class);

        assertThat(users.count()).isEqualTo(usersBefore);
    }

    @Test
    @DisplayName("실제 카드사 연결이 있는 계정에는 만들지 않고, reset으로도 지우지 않는다")
    void accountWithRealConnectionIsRejected() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        CodefConnection real = connections.save(new CodefConnection(user, "real-connected-id-" + user.getId(), "DEMO",
            now, now, now, now));
        orgAccounts.save(CodefOrgAccount.connected(real, "0301", now));

        assertThatThrownBy(() -> seeder.seed(user.getEmail(), false)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> seeder.seed(user.getEmail(), true)).isInstanceOf(IllegalStateException.class);

        assertThat(orgAccounts.findByConnectionId(real.getId())).hasSize(1);
        assertThat(ownedCards.findByUserId(user.getId())).isEmpty();
        assertThat(transactionsOf(user)).isEmpty();
    }

}
