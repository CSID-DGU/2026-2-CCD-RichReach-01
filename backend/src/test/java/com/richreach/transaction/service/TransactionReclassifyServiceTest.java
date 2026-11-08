package com.richreach.transaction.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.richreach.TestcontainersConfiguration;
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
import com.richreach.user.entity.User;
import com.richreach.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
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
 * 분류 규칙이 바뀐 뒤 기존 거래를 다시 분류하는 일을 실제 MySQL로 확인한다. 바뀐 거래만 바뀌는지, 사용자가 고친 카테고리와
 * 수집 시각은 그대로인지, 여러 묶음에 걸쳐도 모두 처리하는지를 본다.
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class TransactionReclassifyServiceTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 5, 12, 30, 0);

    @Autowired
    private TransactionStoreService store;
    @Autowired
    private TransactionReclassifyService reclassifier;
    @Autowired
    private MerchantCategoryClassifier classifier;
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
    private JdbcTemplate jdbc;

    private User user;
    private long card;

    @BeforeEach
    void setUp() {
        int n = SEQUENCE.incrementAndGet();
        user = users.save(new User("reclass" + n + "@example.com", "hash", "김리치", "010%08d".formatted(4000000 + n),
            "REAL", LocalDateTime.now()));
        LocalDateTime now = LocalDateTime.now().withNano(0);
        CodefConnection connection = connections.save(
            new CodefConnection(user, "test-connected-id-" + user.getId(), "DEMO", now, now, now, now));
        CodefOrgAccount account = orgAccounts.save(CodefOrgAccount.connected(connection, "0301", now));
        ownedCardService.saveSynced(user.getId(), account.getId(), CardCompany.KB,
            List.of(new CollectedCard("테스트 카드A", "1111", "check")));
        card = ownedCards.findByOrgAccountId(account.getId()).get(0).getId();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from card_transactions");
        jdbc.update("delete from user_cards");
        orgAccounts.deleteAll();
        connections.deleteAll();
        users.deleteAll();
        jdbc.update("update merchant_category_rules set ui_category = 'CAFE' where pattern = '스타벅스'");
        classifier.refresh();
    }

    private static CollectedTransaction tx(String approvalNo, LocalDateTime at, String merchant) {
        return new CollectedTransaction(approvalNo, at, merchant, 1_000, 0, 0, false, null);
    }

    private CardTransaction byApproval(String approvalNo) {
        return transactions.findByUserCardId(card).stream()
            .filter(t -> t.getApprovalNo().equals(approvalNo)).findFirst().orElseThrow();
    }

    private void changeStarbucksRuleTo(String category) {
        jdbc.update("update merchant_category_rules set ui_category = ? where pattern = '스타벅스'", category);
    }

    @Test
    @DisplayName("규칙이 바뀌면 영향을 받는 거래만 새 분류로 바뀌고, 바뀐 건수를 돌려준다")
    void reclassifiesOnlyAffectedTransactions() {
        store.saveCollected(user.getId(), card, List.of(
            tx("AP-1", AT, "스타벅스 강남점"), tx("AP-2", AT.plusMinutes(1), "GS25 역삼점"),
            tx("AP-3", AT.plusMinutes(2), "알 수 없는 상점")));
        assertThat(byApproval("AP-1").getCategory()).isEqualTo("CAFE");

        changeStarbucksRuleTo("DINING");
        int changed = reclassifier.reclassifyAll();

        assertThat(changed).isEqualTo(1);
        assertThat(byApproval("AP-1").getCategory()).isEqualTo("DINING");
        assertThat(byApproval("AP-2").getCategory()).isEqualTo("CONVENIENCE_STORE");
        assertThat(byApproval("AP-3").getCategory()).isEqualTo("ETC");
    }

    @Test
    @DisplayName("다시 실행해도 바뀌는 것이 없으면 0건이다")
    void secondRunChangesNothing() {
        store.saveCollected(user.getId(), card, List.of(tx("AP-1", AT, "스타벅스 강남점")));
        changeStarbucksRuleTo("DINING");
        reclassifier.reclassifyAll();

        assertThat(reclassifier.reclassifyAll()).isZero();
    }

    @Test
    @DisplayName("사용자가 고친 카테고리와 수집 시각은 재분류가 건드리지 않는다")
    void overrideAndCollectedTimeAreKept() {
        store.saveCollected(user.getId(), card, List.of(tx("AP-1", AT, "스타벅스 강남점")));
        CardTransaction saved = byApproval("AP-1");
        saved.overrideCategory("SHOPPING");
        transactions.save(saved);
        LocalDateTime collectedAt = byApproval("AP-1").getCollectedAt();

        changeStarbucksRuleTo("DINING");
        reclassifier.reclassifyAll();

        CardTransaction after = byApproval("AP-1");
        assertThat(after.getCategory()).isEqualTo("DINING");
        assertThat(after.getCategoryOverride()).isEqualTo("SHOPPING");
        assertThat(after.effectiveCategory()).isEqualTo("SHOPPING");
        assertThat(after.getCollectedAt()).isEqualTo(collectedAt);
    }

    @Test
    @DisplayName("거래가 한 묶음(500건)보다 많아도 모두 다시 분류한다")
    void processesMoreThanOneBatch() {
        List<CollectedTransaction> items = IntStream.range(0, 1_100)
            .mapToObj(i -> tx("AP-" + i, AT.plusMinutes(i), "스타벅스 " + i + "호점")).toList();
        store.saveCollected(user.getId(), card, items);

        changeStarbucksRuleTo("DINING");
        int changed = reclassifier.reclassifyAll();

        assertThat(changed).isEqualTo(1_100);
        assertThat(jdbc.queryForObject(
            "select count(*) from card_transactions where category = 'DINING'", Integer.class)).isEqualTo(1_100);
    }

    @Test
    @DisplayName("거래가 없으면 0건이다")
    void noTransactions() {
        assertThat(reclassifier.reclassifyAll()).isZero();
    }

}
