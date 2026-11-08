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
import com.richreach.transaction.entity.TransactionStatus;
import com.richreach.transaction.repository.CardTransactionRepository;
import com.richreach.transaction.service.TransactionStoreService.SaveResult;
import com.richreach.user.entity.User;
import com.richreach.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
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
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 거래 저장을 실제 MySQL로 확인한다. 저장, 중복 없는 갱신, 취소·환불, 사용자가 고친 카테고리 보존, 사용자·카드별 격리,
 * 입력 다듬기, 삭제 연쇄, 로그에 소비 정보가 남지 않는지를 본다.
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class CardTransactionServiceTest {

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
    private TransactionStoreService service;
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
    private long cardA;
    private long cardB;

    @BeforeEach
    void setUp() {
        user = newUser();
        CodefOrgAccount account = newAccount(user);
        ownedCardService.saveSynced(user.getId(), account.getId(), CardCompany.KB,
            List.of(new CollectedCard("테스트 카드A", "1111", "check"), new CollectedCard("테스트 카드B", "2222", "check")));
        cardA = cardIdOf(account, "테스트 카드A");
        cardB = cardIdOf(account, "테스트 카드B");
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from card_transactions");
        jdbc.update("delete from user_cards");
        orgAccounts.deleteAll();
        connections.deleteAll();
        users.deleteAll();
    }

    private User newUser() {
        int n = SEQUENCE.incrementAndGet();
        return users.save(new User("txn" + n + "@example.com", "hash", "김리치", "010%08d".formatted(7000000 + n),
            "REAL", LocalDateTime.now()));
    }

    private CodefOrgAccount newAccount(User owner) {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        CodefConnection connection = connections.findByUserId(owner.getId()).orElseGet(() -> connections.save(
            new CodefConnection(owner, "test-connected-id-" + owner.getId(), "DEMO", now, now, now, now)));
        return orgAccounts.save(CodefOrgAccount.connected(connection, "0301", now));
    }

    private long cardIdOf(CodefOrgAccount account, String cardName) {
        return ownedCards.findByOrgAccountId(account.getId()).stream()
            .filter(card -> card.getCardName().equals(cardName)).findFirst().orElseThrow().getId();
    }

    private static CollectedTransaction tx(String approvalNo, LocalDateTime at, String merchant, long amount) {
        return new CollectedTransaction(approvalNo, at, merchant, amount, 0, 0, false, null);
    }

    private SaveResult save(long card, CollectedTransaction... items) {
        return service.saveCollected(user.getId(), card, List.of(items));
    }

    private CardTransaction only(long card) {
        List<CardTransaction> found = transactions.findByUserCardId(card);
        assertThat(found).hasSize(1);
        return found.get(0);
    }

    // ---- 저장 ----

    @Test
    @DisplayName("처음 보는 거래는 사용자와 보유카드에 묶어서 저장하고 실제 금액을 계산한다")
    void savesNewTransactions() {
        SaveResult result = save(cardA, new CollectedTransaction("AP-1", AT, "테스트 카페", 4500, 0, 3, true, "TEST_CAFE"));

        assertThat(result).isEqualTo(new SaveResult(1, 0, 0, 0));
        CardTransaction saved = only(cardA);
        assertThat(saved.getUserId()).isEqualTo(user.getId());
        assertThat(saved.getUserCardId()).isEqualTo(cardA);
        assertThat(saved.getApprovalNo()).isEqualTo("AP-1");
        assertThat(saved.getApprovedAt()).isEqualTo(AT);
        assertThat(saved.getMerchantName()).isEqualTo("테스트 카페");
        assertThat(saved.getAmount()).isEqualTo(4500);
        assertThat(saved.getCanceledAmount()).isZero();
        assertThat(saved.getStatus()).isEqualTo(TransactionStatus.APPROVED);
        assertThat(saved.getInstallmentMonths()).isEqualTo(3);
        assertThat(saved.isOverseas()).isTrue();
        assertThat(saved.getCategory()).isEqualTo("TEST_CAFE");
        assertThat(saved.getCategoryOverride()).isNull();
        assertThat(saved.effectiveCategory()).isEqualTo("TEST_CAFE");
        assertThat(saved.netAmount()).isEqualTo(4500);
        assertThat(saved.getCollectedAt()).isNotNull();
    }

    @Test
    @DisplayName("같은 거래를 다시 저장하면 새로 만들지 않고 바뀐 것이 없다고 센다")
    void resyncCreatesNoDuplicates() {
        save(cardA, tx("AP-1", AT, "테스트 카페", 4500));

        SaveResult again = save(cardA, tx("AP-1", AT, "테스트 카페", 4500));

        assertThat(again).isEqualTo(new SaveResult(0, 0, 1, 0));
        assertThat(transactions.findByUserCardId(cardA)).hasSize(1);
    }

    @Test
    @DisplayName("승인번호가 같으면 가맹점명이나 금액 표기가 바뀌어도 같은 거래로 보고 갱신한다")
    void sameApprovalNumberUpdatesInPlace() {
        save(cardA, tx("AP-1", AT, "테스트 카페", 4500));
        Long id = only(cardA).getId();

        SaveResult again = save(cardA, tx("AP-1", AT, "테스트 카페 본점", 5000));

        assertThat(again).isEqualTo(new SaveResult(0, 1, 0, 0));
        CardTransaction updated = only(cardA);
        assertThat(updated.getId()).isEqualTo(id);
        assertThat(updated.getMerchantName()).isEqualTo("테스트 카페 본점");
        assertThat(updated.getAmount()).isEqualTo(5000);
    }

    @Test
    @DisplayName("승인번호가 없으면 일시·금액·가맹점이 같은 거래를 같은 거래로 보고(대소문자·공백 차이 무시) 표기만 최신으로 갱신하며, 금액이 다르면 다른 거래다")
    void withoutApprovalNumberUsesTimeAmountAndMerchant() {
        save(cardA, tx("", AT, "Test  Cafe", 4500));

        SaveResult sameWithOtherSpelling = save(cardA, tx(null, AT, "test cafe", 4500));
        SaveResult otherAmount = save(cardA, tx("", AT, "test cafe", 4600));

        assertThat(sameWithOtherSpelling).isEqualTo(new SaveResult(0, 1, 0, 0));
        assertThat(otherAmount).isEqualTo(new SaveResult(1, 0, 0, 0));
        List<CardTransaction> saved = transactions.findByUserCardId(cardA);
        assertThat(saved).hasSize(2);
        assertThat(saved).extracting(CardTransaction::getMerchantName).containsOnly("test cafe");
    }

    @Test
    @DisplayName("같은 시각에 같은 금액·가맹점을 두 번 결제해도 서로 다른 거래로 저장하고, 재동기화에서도 두 건을 유지한다")
    void identicalTransactionsKeepBothRows() {
        CollectedTransaction coffee = tx("", AT, "테스트 카페", 4500);

        SaveResult first = save(cardA, coffee, coffee);
        SaveResult second = save(cardA, coffee, coffee);
        SaveResult third = save(cardA, coffee, coffee, coffee);

        assertThat(first).isEqualTo(new SaveResult(2, 0, 0, 0));
        assertThat(second).isEqualTo(new SaveResult(0, 0, 2, 0));
        assertThat(third).isEqualTo(new SaveResult(1, 0, 2, 0));
        assertThat(transactions.findByUserCardId(cardA)).hasSize(3);
    }

    // ---- 취소·환불 ----

    @Test
    @DisplayName("전액 취소되면 새 거래를 만들지 않고 원거래의 상태와 취소 금액을 갱신한다")
    void fullCancellationUpdatesTheOriginal() {
        save(cardA, tx("AP-1", AT, "테스트 상점", 10_000));

        SaveResult result = save(cardA, new CollectedTransaction("AP-1", AT, "테스트 상점", 10_000, 10_000, 0, false, null));

        assertThat(result).isEqualTo(new SaveResult(0, 1, 0, 0));
        CardTransaction canceled = only(cardA);
        assertThat(canceled.getStatus()).isEqualTo(TransactionStatus.CANCELED);
        assertThat(canceled.getCanceledAmount()).isEqualTo(10_000);
        assertThat(canceled.netAmount()).isZero();
    }

    @Test
    @DisplayName("일부만 취소되면 부분 취소 상태이고 남은 금액을 계산한다")
    void partialCancellation() {
        save(cardA, tx("AP-1", AT, "테스트 상점", 10_000));

        save(cardA, new CollectedTransaction("AP-1", AT, "테스트 상점", 10_000, 3_000, 0, false, null));

        CardTransaction partial = only(cardA);
        assertThat(partial.getStatus()).isEqualTo(TransactionStatus.PARTIALLY_CANCELED);
        assertThat(partial.netAmount()).isEqualTo(7_000);
    }

    @Test
    @DisplayName("취소 금액이 승인 금액보다 크면 승인 금액으로, 음수면 0으로 본다")
    void cancelAmountIsClamped() {
        save(cardA, new CollectedTransaction("AP-1", AT, "테스트 상점", 10_000, 99_999, 0, false, null));
        save(cardA, new CollectedTransaction("AP-2", AT.plusHours(1), "테스트 상점", 10_000, -5, 0, false, null));

        List<CardTransaction> saved = transactions.findByUserCardId(cardA);
        assertThat(saved).filteredOn(t -> t.getApprovalNo().equals("AP-1"))
            .singleElement().satisfies(t -> {
                assertThat(t.getCanceledAmount()).isEqualTo(10_000);
                assertThat(t.getStatus()).isEqualTo(TransactionStatus.CANCELED);
            });
        assertThat(saved).filteredOn(t -> t.getApprovalNo().equals("AP-2"))
            .singleElement().satisfies(t -> {
                assertThat(t.getCanceledAmount()).isZero();
                assertThat(t.getStatus()).isEqualTo(TransactionStatus.APPROVED);
            });
    }

    // ---- 업종 카테고리 ----

    @Test
    @DisplayName("재동기화는 자동 분류를 갱신하지만 사용자가 고친 카테고리는 건드리지 않는다 (입력에 업종이 없으면 가맹점명으로 다시 분류)")
    void userCategoryOverrideSurvivesResync() {
        save(cardA, new CollectedTransaction("AP-1", AT, "테스트 상점", 10_000, 0, 0, false, "TEST_FOOD"));
        CardTransaction saved = only(cardA);
        saved.overrideCategory("TEST_TRAVEL");
        transactions.save(saved);

        SaveResult explicit = save(cardA,
            new CollectedTransaction("AP-1", AT, "테스트 상점", 10_000, 0, 0, false, "TEST_SHOPPING"));
        SaveResult withoutCategory = save(cardA, tx("AP-1", AT, "테스트 상점", 10_000));

        assertThat(explicit).isEqualTo(new SaveResult(0, 1, 0, 0));
        assertThat(withoutCategory).isEqualTo(new SaveResult(0, 1, 0, 0)); // 가맹점명으로 분류하면 ETC
        CardTransaction after = only(cardA);
        assertThat(after.getCategory()).isEqualTo("ETC");
        assertThat(after.getCategoryOverride()).isEqualTo("TEST_TRAVEL");
        assertThat(after.effectiveCategory()).isEqualTo("TEST_TRAVEL");
    }

    @Test
    @DisplayName("입력에 업종이 없으면 가맹점명으로 자동 분류하고, 어디에도 안 맞으면 NULL이 아니라 ETC로 저장한다")
    void classifiesByMerchantNameWhenCategoryIsMissing() {
        save(cardA,
            tx("AP-1", AT, "스타벅스 강남점", 4_500),
            tx("AP-2", AT.plusMinutes(1), "GS25 역삼점", 2_000),
            tx("AP-3", AT.plusMinutes(2), "알 수 없는 상점", 9_000),
            tx("AP-4", AT.plusMinutes(3), null, 1_000));

        assertThat(transactions.findByUserCardId(cardA)).extracting(CardTransaction::getCategory)
            .containsExactlyInAnyOrder("CAFE", "CONVENIENCE_STORE", "ETC", "ETC");
    }

    @Test
    @DisplayName("입력에 업종이 있으면 가맹점명으로 분류하지 않고 그 값을 쓴다")
    void explicitCategoryWinsOverClassification() {
        save(cardA, new CollectedTransaction("AP-1", AT, "스타벅스 강남점", 4_500, 0, 0, false, "TEST_EXPLICIT"));

        assertThat(only(cardA).getCategory()).isEqualTo("TEST_EXPLICIT");
    }

    @Test
    @DisplayName("재동기화에서 같은 거래를 다시 분류해도 바뀐 것이 없으면 갱신하지 않고, 사용자가 고친 카테고리는 그대로다")
    void resyncKeepsClassificationStableAndOverride() {
        save(cardA, tx("AP-1", AT, "스타벅스 강남점", 4_500));
        CardTransaction saved = only(cardA);
        saved.overrideCategory("DINING");
        transactions.save(saved);

        SaveResult again = save(cardA, tx("AP-1", AT, "스타벅스 강남점", 4_500));

        assertThat(again).isEqualTo(new SaveResult(0, 0, 1, 0));
        CardTransaction after = only(cardA);
        assertThat(after.getCategory()).isEqualTo("CAFE");
        assertThat(after.effectiveCategory()).isEqualTo("DINING");
    }

    // ---- 격리 ----

    @Test
    @DisplayName("같은 거래라도 보유카드와 사용자가 다르면 따로 저장하고 서로 섞이지 않는다")
    void cardsAndUsersAreIsolated() {
        User other = newUser();
        CodefOrgAccount otherAccount = newAccount(other);
        ownedCardService.saveSynced(other.getId(), otherAccount.getId(), CardCompany.KB,
            List.of(new CollectedCard("남의 카드", "9999", "check")));
        long otherCard = cardIdOf(otherAccount, "남의 카드");
        CollectedTransaction same = tx("AP-1", AT, "테스트 카페", 4500);

        save(cardA, same);
        save(cardB, same);
        service.saveCollected(other.getId(), otherCard, List.of(same));

        assertThat(transactions.findByUserCardId(cardA)).hasSize(1);
        assertThat(transactions.findByUserCardId(cardB)).hasSize(1);
        assertThat(transactions.findByUserCardId(otherCard)).hasSize(1);
        assertThat(transactions.findByUserId(user.getId())).hasSize(2);
        assertThat(transactions.findByUserId(other.getId())).hasSize(1);
    }

    // ---- 입력 다듬기 ----

    @Test
    @DisplayName("승인일시가 없거나 금액이 음수이거나 비어 있는 항목은 저장하지 않고 건수만 센다")
    void invalidItemsAreSkipped() {
        List<CollectedTransaction> items = new ArrayList<>();
        items.add(tx("AP-1", null, "테스트 상점", 1_000));
        items.add(tx("AP-2", AT, "테스트 상점", -1));
        items.add(null);
        items.add(tx("AP-3", AT, "테스트 상점", 0));

        SaveResult result = service.saveCollected(user.getId(), cardA, items);

        assertThat(result).isEqualTo(new SaveResult(1, 0, 0, 3));
        assertThat(only(cardA).getApprovalNo()).isEqualTo("AP-3");
    }

    @Test
    @DisplayName("컬럼 길이를 넘는 값은 잘라서 저장하고, 다시 저장해도 같은 거래로 본다")
    void overlongValuesAreTruncated() {
        CollectedTransaction longValues = new CollectedTransaction("N".repeat(60), AT, "가".repeat(300), 1_000, 0, 0,
            false, "C".repeat(50));

        save(cardA, longValues);
        SaveResult again = save(cardA, longValues);

        CardTransaction saved = only(cardA);
        assertThat(saved.getApprovalNo()).hasSize(40);
        assertThat(saved.getMerchantName()).hasSize(200);
        assertThat(saved.getCategory()).hasSize(30);
        assertThat(again).isEqualTo(new SaveResult(0, 0, 1, 0));
    }

    @Test
    @DisplayName("가맹점명이 없어도 저장하고, 앞뒤 공백은 없앤다")
    void missingMerchantIsStoredAsEmpty() {
        save(cardA, tx("  AP-1  ", AT, null, 1_000));

        CardTransaction saved = only(cardA);
        assertThat(saved.getMerchantName()).isEmpty();
        assertThat(saved.getApprovalNo()).isEqualTo("AP-1");
    }

    @Test
    @DisplayName("빈 목록은 아무것도 저장하지 않는다")
    void emptyInputIsNoOp() {
        assertThat(service.saveCollected(user.getId(), cardA, List.of())).isEqualTo(new SaveResult(0, 0, 0, 0));
    }

    @Test
    @DisplayName("거래가 많아도 한 번에 저장하고, 재동기화에서는 모두 바뀐 것이 없다")
    void manyTransactions() {
        List<CollectedTransaction> items = IntStream.range(0, 300)
            .mapToObj(i -> tx("AP-" + i, AT.plusMinutes(i), "테스트 상점" + i, 1_000 + i)).toList();

        SaveResult first = service.saveCollected(user.getId(), cardA, items);
        SaveResult second = service.saveCollected(user.getId(), cardA, items);

        assertThat(first).isEqualTo(new SaveResult(300, 0, 0, 0));
        assertThat(second).isEqualTo(new SaveResult(0, 0, 300, 0));
        assertThat(transactions.findByUserCardId(cardA)).hasSize(300);
    }

    // ---- 삭제 연쇄와 보안 ----

    @Test
    @DisplayName("보유카드를 지우면 그 카드의 거래도 함께 지워지고, 다른 카드의 거래는 남는다")
    void deletingCardRemovesItsTransactions() {
        save(cardA, tx("AP-1", AT, "테스트 상점", 1_000));
        save(cardB, tx("AP-2", AT, "테스트 상점", 2_000));

        jdbc.update("delete from user_cards where id = ?", cardA);

        assertThat(transactions.findByUserCardId(cardA)).isEmpty();
        assertThat(transactions.findByUserCardId(cardB)).hasSize(1);
    }

    @Test
    @DisplayName("카드사 연결을 해제하면(카드사 계정을 지우면) 그 계정의 보유카드와 거래도 함께 지워진다")
    void disconnectingCardCompanyRemovesTransactions() {
        save(cardA, tx("AP-1", AT, "테스트 상점", 1_000));
        save(cardB, tx("AP-2", AT, "테스트 상점", 2_000));

        orgAccounts.deleteAll();

        assertThat(jdbc.queryForObject("select count(*) from user_cards", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from card_transactions", Integer.class)).isZero();
    }

    @Test
    @DisplayName("가맹점명과 금액은 로그에 남기지 않는다")
    void spendingContentIsNotLogged(CapturedOutput output) {
        CollectedTransaction secret = new CollectedTransaction("SECRET-APPROVAL", AT, "비밀가맹점XYZ", 987_654, 0, 0,
            false, null);

        save(cardA, secret);
        save(cardA, secret);

        assertThat(output.getAll()).doesNotContain("비밀가맹점XYZ").doesNotContain("987654")
            .doesNotContain("SECRET-APPROVAL");
        assertThat(secret.toString()).doesNotContain("비밀가맹점XYZ").doesNotContain("987654");
    }

}
