package com.richreach.plan.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.richreach.TestcontainersConfiguration;
import com.richreach.card.dto.CollectedCard;
import com.richreach.card.repository.OwnedCardRepository;
import com.richreach.card.service.OwnedCardService;
import com.richreach.codef.entity.CardCompany;
import com.richreach.codef.entity.CodefConnection;
import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.repository.CodefConnectionRepository;
import com.richreach.codef.repository.CodefOrgAccountRepository;
import com.richreach.global.category.SpendingCategory;
import com.richreach.plan.entity.MonthlyPlan;
import com.richreach.plan.entity.PlanCardAllocation;
import com.richreach.plan.entity.PlanStrategy;
import com.richreach.plan.entity.SolverStatus;
import com.richreach.plan.entity.SpendingProfile;
import com.richreach.plan.entity.SpendingProfileItem;
import com.richreach.user.entity.User;
import com.richreach.user.repository.UserRepository;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** V21 월간 플랜 테이블과 엔티티 매핑을 실제 MySQL로 확인한다. (스키마 검증, 저장·조회, 유일 제약, 삭제 연쇄) */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class MonthlyPlanRepositoryTest {

    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    private static final YearMonth MONTH = YearMonth.of(2026, 12);

    @Autowired
    private SpendingProfileRepository profiles;
    @Autowired
    private SpendingProfileItemRepository items;
    @Autowired
    private MonthlyPlanRepository plans;
    @Autowired
    private PlanCardAllocationRepository allocations;
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
    private CodefOrgAccount account;
    private long cardId;
    private SpendingProfile profile;

    @BeforeEach
    void setUp() {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        user = users.save(new User("plan-test@example.com", "hash", "김리치", "01070000001", "REAL", now));
        CodefConnection connection = connections.save(
            new CodefConnection(user, "test-connected-id-" + user.getId(), "DEMO", now, now, now, now));
        account = orgAccounts.save(CodefOrgAccount.connected(connection, "0301", now));
        ownedCardService.saveSynced(user.getId(), account.getId(), CardCompany.KB,
            List.of(new CollectedCard("플랜 테스트 카드", "1111", "credit")));
        cardId = ownedCards.findByOrgAccountId(account.getId()).get(0).getId();
        profile = profiles.save(SpendingProfile.create(user.getId(), MONTH, LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 11, 30)));
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from plan_card_allocations");
        jdbc.update("delete from monthly_plans");
        jdbc.update("delete from spending_profile_items");
        jdbc.update("delete from spending_profiles");
        jdbc.update("delete from user_cards");
        orgAccounts.deleteAll();
        connections.deleteAll();
        users.deleteAll();
    }

    private MonthlyPlan newPlan(PlanStrategy strategy, int version) {
        return plans.save(MonthlyPlan.create(user.getId(), profile.getId(), MONTH, strategy, version, 1_000_000, 30_000));
    }

    @Test
    @DisplayName("소비 프로필과 카테고리별 항목을 저장하고 다시 읽는다")
    void savesProfileWithItems() {
        items.save(SpendingProfileItem.create(profile.getId(), SpendingCategory.DINING, 300_000, 12,
            new BigDecimal("0.9000"), new BigDecimal("0.8000")));
        items.save(SpendingProfileItem.create(profile.getId(), SpendingCategory.CAFE, 80_000, 15, BigDecimal.ONE,
            BigDecimal.ONE));

        SpendingProfile found = profiles.findFirstByUserIdAndTargetMonthOrderByIdDesc(user.getId(), "2026-12")
            .orElseThrow();
        List<SpendingProfileItem> found2 = items.findByProfileId(found.getId());

        assertThat(found.getSourceStart()).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(found2).hasSize(2);
        assertThat(found2).filteredOn(i -> i.getCategory() == SpendingCategory.DINING).singleElement()
            .satisfies(i -> {
                assertThat(i.getExpectedAmount()).isEqualTo(300_000);
                assertThat(i.getOccurrenceProb()).isEqualByComparingTo("0.9");
            });
    }

    @Test
    @DisplayName("같은 프로필에서 같은 카테고리 항목은 하나만 둘 수 있다")
    void profileItemCategoryIsUnique() {
        items.saveAndFlush(SpendingProfileItem.create(profile.getId(), SpendingCategory.DINING, 1, 1, BigDecimal.ONE,
            BigDecimal.ONE));

        assertThatThrownBy(() -> items.saveAndFlush(SpendingProfileItem.create(profile.getId(), SpendingCategory.DINING,
            2, 1, BigDecimal.ONE, BigDecimal.ONE))).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("플랜과 카드별 배분을 저장하고 계산 결과를 기록한다")
    void savesPlanWithAllocations() {
        MonthlyPlan plan = newPlan(PlanStrategy.CP_SAT, 1);
        plan.recordSolve(SolverStatus.OPTIMAL, 120);
        plan.explain("외식은 A카드가 유리합니다.");
        plans.saveAndFlush(plan);
        allocations.save(PlanCardAllocation.create(plan.getId(), cardId, SpendingCategory.DINING, 300_000, 15_000));

        MonthlyPlan found = plans.findById(plan.getId()).orElseThrow();

        assertThat(found.getPlanMonth()).isEqualTo("2026-12");
        assertThat(found.getSolverStatus()).isEqualTo(SolverStatus.OPTIMAL);
        assertThat(found.getSolveTimeMs()).isEqualTo(120);
        assertThat(found.getLlmExplanation()).contains("A카드");
        assertThat(allocations.findByPlanId(plan.getId())).singleElement().satisfies(a -> {
            assertThat(a.getUserCardId()).isEqualTo(cardId);
            assertThat(a.getTargetAmount()).isEqualTo(300_000);
        });
    }

    @Test
    @DisplayName("같은 월·전략은 version이 다르면 쌓이고, 같으면 거부하며, 최신 version을 찾는다")
    void versionsStackAndAreUnique() {
        newPlan(PlanStrategy.GREEDY, 1);
        MonthlyPlan v2 = newPlan(PlanStrategy.GREEDY, 2);
        newPlan(PlanStrategy.HABIT, 1);

        assertThat(plans.findFirstByUserIdAndPlanMonthAndStrategyOrderByVersionDesc(user.getId(), "2026-12",
            PlanStrategy.GREEDY).orElseThrow().getId()).isEqualTo(v2.getId());
        assertThat(plans.findByUserIdAndPlanMonth(user.getId(), "2026-12")).hasSize(3);
        assertThatThrownBy(() -> newPlan(PlanStrategy.GREEDY, 2)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("길이를 넘는 실패 원인은 잘라서 저장한다")
    void truncatesLongFailureReason() {
        MonthlyPlan plan = newPlan(PlanStrategy.CP_SAT, 1);
        plan.recordSolve(SolverStatus.INFEASIBLE, 5);
        plan.recordFailure("x".repeat(MonthlyPlan.FAILURE_REASON_MAX_LENGTH + 100));
        plans.saveAndFlush(plan);

        assertThat(plans.findById(plan.getId()).orElseThrow().getFailureReason())
            .hasSize(MonthlyPlan.FAILURE_REASON_MAX_LENGTH);
    }

    @Test
    @DisplayName("잘못된 값으로는 만들 수 없다")
    void rejectsInvalidValues() {
        assertThatThrownBy(() -> MonthlyPlan.create(1L, 1L, MONTH, PlanStrategy.HABIT, 0, 0, 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PlanCardAllocation.create(1L, 1L, SpendingCategory.ETC, -1, 0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SpendingProfileItem.create(1L, SpendingCategory.ETC, 1, 1, new BigDecimal("1.5"),
            BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> SpendingProfile.create(1L, MONTH, LocalDate.of(2026, 11, 2), LocalDate.of(2026, 11, 1)))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("보유카드를 지우면 그 카드의 배분이, 사용자를 지우면 프로필과 플랜이 함께 지워진다")
    void deletesCascade() {
        MonthlyPlan plan = newPlan(PlanStrategy.HABIT, 1);
        allocations.save(PlanCardAllocation.create(plan.getId(), cardId, SpendingCategory.CAFE, 1, 0));

        jdbc.update("delete from user_cards where id = ?", cardId);
        assertThat(allocations.findByPlanId(plan.getId())).isEmpty();

        jdbc.update("delete from codef_org_accounts where id = ?", account.getId());
        jdbc.update("delete from codef_connections where user_id = ?", user.getId());
        jdbc.update("delete from users where id = ?", user.getId());
        assertThat(plans.count()).isZero();
        assertThat(profiles.count()).isZero();
    }
}
