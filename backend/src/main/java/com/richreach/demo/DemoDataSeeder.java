package com.richreach.demo;

import com.richreach.card.dto.CollectedCard;
import com.richreach.card.entity.CardProduct;
import com.richreach.card.entity.OwnedCard;
import com.richreach.card.repository.BenefitRuleRepository;
import com.richreach.card.repository.CardProductRepository;
import com.richreach.card.repository.OwnedCardRepository;
import com.richreach.card.service.OwnedCardService;
import com.richreach.codef.entity.CardCompany;
import com.richreach.codef.entity.CodefConnection;
import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.repository.CodefConnectionRepository;
import com.richreach.codef.repository.CodefOrgAccountRepository;
import com.richreach.global.util.Times;
import com.richreach.transaction.dto.CollectedTransaction;
import com.richreach.transaction.service.TransactionStoreService;
import com.richreach.transaction.service.TransactionStoreService.SaveResult;
import com.richreach.user.entity.User;
import com.richreach.user.repository.UserRepository;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 카드사 연동 없이 시연용 보유카드와 거래를 만든다. 카드사 연동은 빌린 데모 키와 호출 한도에 묶여 있어서, 이게 있으면
 * 소비 요약, 혜택 계산, 플랜 생성을 연결 상태와 상관없이 개발하고 시연할 수 있다.
 *
 * <p>안전을 위해 다음을 지킨다.
 * <ul>
 *   <li>로컬 DB가 아니면 아무것도 만들지 않고 중단한다. ({@link DemoSeedGuard})</li>
 *   <li>이미 가입한 계정에만 만든다. 계정을 새로 만들지 않아 비밀번호를 코드에 두지 않는다.</li>
 *   <li>실제 카드사 연결이 있는 계정은 거부한다. 시연 데이터는 가짜 연결({@value #CONNECTED_ID_PREFIX} 접두어)로만 만들고,
 *       실제 연결의 데이터와 섞지 않는다. 가짜 연결로는 카드사 동기화를 할 수 없다.</li>
 * </ul>
 *
 * <p>여러 번 실행해도 같은 데이터가 나온다. 카드와 거래는 기존 저장 서비스를 그대로 써서, 같은 카드와 같은 거래는 새로
 * 만들지 않고 갱신한다. {@code reset}이면 이 계정의 시연 데이터를 지우고 다시 만든다. 카드와 거래의 업종은 지정하지 않고
 * 가맹점 분류 규칙이 정하게 한다. 가맹점명과 금액은 로그에 남기지 않고 건수만 남긴다.
 */
@Slf4j
@Service
public class DemoDataSeeder {

    /** 시연용 가짜 카드사 연결을 알아보는 접두어. 실제 CODEF가 발급하는 값은 이렇게 시작하지 않는다. */
    static final String CONNECTED_ID_PREFIX = "demo-connected-";

    /** 시연 카드를 고를 때 보는 카드사 순서(카드 상품의 issuer_code). 혜택이 있는 카드를 먼저 고른다. */
    private static final List<String> ISSUER_PRIORITY = List.of("woori", "kb", "bc", "shinhan", "nh", "hyundai", "citi");
    private static final int MAX_CARDS = 3;
    private static final String CARD_TYPE = "본인 로컬";
    private static final String PAYMENT_CARD = "PAYMENT_CARD";

    private final UserRepository users;
    private final CodefConnectionRepository connections;
    private final CodefOrgAccountRepository orgAccounts;
    private final CardProductRepository cardProducts;
    private final BenefitRuleRepository benefitRules;
    private final OwnedCardService ownedCardService;
    private final OwnedCardRepository ownedCards;
    private final TransactionStoreService transactionStore;
    private final TransactionTemplate transaction;
    private final DataSource dataSource;
    private final Clock clock;

    public DemoDataSeeder(UserRepository users, CodefConnectionRepository connections,
                          CodefOrgAccountRepository orgAccounts, CardProductRepository cardProducts,
                          BenefitRuleRepository benefitRules, OwnedCardService ownedCardService,
                          OwnedCardRepository ownedCards, TransactionStoreService transactionStore,
                          TransactionTemplate transaction, DataSource dataSource, Clock clock) {
        this.users = users;
        this.connections = connections;
        this.orgAccounts = orgAccounts;
        this.cardProducts = cardProducts;
        this.benefitRules = benefitRules;
        this.ownedCardService = ownedCardService;
        this.ownedCards = ownedCards;
        this.transactionStore = transactionStore;
        this.transaction = transaction;
        this.dataSource = dataSource;
        this.clock = clock;
    }

    /**
     * 만든 결과.
     *
     * @param cards             시연 카드 수
     * @param analyzableCards   카드 상품에 매칭되어 분석할 수 있는 카드 수
     * @param createdTransactions 새로 만든 거래 수
     * @param updatedTransactions 이미 있어서 바뀐 거래 수
     * @param unchangedTransactions 이미 있어서 그대로인 거래 수
     */
    public record DemoSeedResult(int cards, int analyzableCards, int createdTransactions, int updatedTransactions,
                                 int unchangedTransactions) {

    }

    private record Pick(CardCompany company, String cardName) {

    }

    /**
     * 계정에 시연용 보유카드와 거래를 만든다.
     *
     * @param userEmail 시연 데이터를 만들 계정의 이메일. 이미 가입한 계정이어야 한다.
     * @param reset     true이면 이 계정의 시연 데이터를 지우고 다시 만든다.
     * @throws IllegalStateException 로컬 DB가 아니거나, 계정이 없거나, 실제 카드사 연결이 있는 계정일 때
     */
    public DemoSeedResult seed(String userEmail, boolean reset) {
        DemoSeedGuard.requireLocalDatabase(currentJdbcUrl());
        if (userEmail == null || userEmail.isBlank()) {
            throw new IllegalStateException("시연 데이터를 만들 계정의 이메일(richreach.demo-seed.user-email)을 지정하세요.");
        }
        User user = users.findByEmail(userEmail.strip()).orElseThrow(() ->
            new IllegalStateException("지정한 이메일의 계정이 없습니다. 먼저 회원가입을 한 뒤 다시 실행하세요."));

        CodefConnection existing = connections.findByUserId(user.getId()).orElse(null);
        if (existing != null && !existing.getConnectedId().startsWith(CONNECTED_ID_PREFIX)) {
            throw new IllegalStateException("이미 실제 카드사 연결이 있는 계정이라 시연 데이터를 만들지 않습니다. 다른 계정을 쓰세요.");
        }
        if (reset && existing != null) {
            deleteDemoAccounts(existing);
        }

        LocalDateTime now = Times.nowSeconds(clock);
        CodefConnection connection = existing != null ? existing : connections.save(new CodefConnection(user,
            CONNECTED_ID_PREFIX + user.getId(), "DEMO", now, now, now, now));

        List<Pick> picks = chooseCards();
        List<Long> cardIds = new ArrayList<>();
        int analyzable = 0;
        for (int index = 0; index < picks.size(); index++) {
            Pick pick = picks.get(index);
            CodefOrgAccount account = orgAccounts.findByConnectionId(connection.getId()).stream()
                .filter(candidate -> candidate.getOrganization().equals(pick.company().organizationCode()))
                .findFirst()
                .orElseGet(() -> orgAccounts.save(
                    CodefOrgAccount.connected(connection, pick.company().organizationCode(), now)));

            var saved = ownedCardService.saveSynced(user.getId(), account.getId(), pick.company(), List.of(
                new CollectedCard(pick.cardName(), "9999-9999-9999-%04d".formatted(index + 1), CARD_TYPE)));
            analyzable += saved.matched();
            String storedName = OwnedCard.fit(pick.cardName().strip(), OwnedCard.CARD_NAME_MAX_LENGTH);
            cardIds.add(ownedCards.findByOrgAccountId(account.getId()).stream()
                .filter(OwnedCard::isActive).filter(card -> card.getCardName().equals(storedName))
                .findFirst().orElseThrow().getId());
        }

        List<List<CollectedTransaction>> scenario = DemoScenario.generate(LocalDate.now(clock), picks.size());
        int created = 0;
        int updated = 0;
        int unchanged = 0;
        for (int index = 0; index < cardIds.size(); index++) {
            SaveResult result = transactionStore.saveCollected(user.getId(), cardIds.get(index), scenario.get(index));
            created += result.created();
            updated += result.updated();
            unchanged += result.unchanged();
        }

        DemoSeedResult result = new DemoSeedResult(picks.size(), analyzable, created, updated, unchanged);
        log.info("시연 데이터를 만들었습니다: 카드={}장(분석 가능 {}장), 거래 신규={}건, 변경={}건, 그대로={}건", result.cards(),
            result.analyzableCards(), created, updated, unchanged);
        return result;
    }

    /** 시연 카드를 고른다. 카드 상품이 있으면 그 이름으로 만들어 분석할 수 있는 카드로 보이게 하고, 없으면 샘플 이름을 쓴다. */
    private List<Pick> chooseCards() {
        List<CardProduct> products = cardProducts.findAll();
        List<Pick> picks = new ArrayList<>();
        for (String issuerCode : ISSUER_PRIORITY) {
            if (picks.size() >= MAX_CARDS) {
                break;
            }
            List<CardProduct> candidates = products.stream()
                .filter(product -> issuerCode.equalsIgnoreCase(product.getIssuerCode()))
                .filter(product -> PAYMENT_CARD.equals(product.getProductClass()))
                .sorted(Comparator.comparing(CardProduct::getId)).toList();
            if (candidates.isEmpty()) {
                continue;
            }
            // 혜택 규칙이 있는 카드를 먼저 고른다. 혜택 계산 시연에 쓸 수 있어서다.
            CardProduct chosen = candidates.stream()
                .filter(product -> !benefitRules.findByCardProductId(product.getId()).isEmpty())
                .findFirst().orElse(candidates.get(0));
            picks.add(new Pick(CardCompany.valueOf(issuerCode.toUpperCase()), chosen.getCardName()));
        }
        if (picks.isEmpty()) {
            log.warn("카드 상품이 없어 분석할 수 없는 샘플 카드로 만듭니다. MVP 카드를 먼저 승격하세요. (STAGING_LOAD_ON_STARTUP)");
            picks.add(new Pick(CardCompany.KB, "KB국민 샘플 체크카드"));
            picks.add(new Pick(CardCompany.SHINHAN, "신한 샘플 신용카드"));
        }
        return picks;
    }

    /** 시연 카드사 계정을 지운다. 그 계정의 보유카드와 거래는 DB의 연쇄 삭제로 함께 지워진다. */
    private void deleteDemoAccounts(CodefConnection connection) {
        transaction.executeWithoutResult(status ->
            orgAccounts.deleteAll(orgAccounts.findByConnectionId(connection.getId())));
    }

    /** 지금 연결된 DB의 접속 주소. 설정 방식(속성, 컨테이너 연결 등)과 상관없이 실제 연결에서 읽는다. */
    private String currentJdbcUrl() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getURL();
        } catch (SQLException e) {
            // 접속 대상을 알 수 없으면 만들지 않는다.
            throw new IllegalStateException("DB 접속 대상을 확인할 수 없어 시연 데이터를 만들지 않습니다.");
        }
    }

}
