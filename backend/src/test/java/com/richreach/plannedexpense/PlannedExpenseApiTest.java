package com.richreach.plannedexpense;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.richreach.TestcontainersConfiguration;
import com.richreach.auth.repository.RefreshTokenRepository;
import com.richreach.auth.service.AuthTokenService;
import com.richreach.user.entity.User;
import com.richreach.user.repository.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Base64;
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

/**
 * 예정지출 API({@code /api/planned-expenses})를 보안 필터와 실제 MySQL로 확인한다. 추가·조회·수정·삭제, 입력 검증, 다른
 * 사용자의 예정지출 접근, 지난 달 예정지출의 읽기 전용 규칙을 본다. (플랜 재계산에 반영되는 규칙은 단위 테스트가 본다)
 */
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PlannedExpenseApiTest {

    /** ConnectedIdConverter가 요구하는 app.encryption.key를 레포에 값으로 남기지 않고 실행 시마다 생성한다. */
    @DynamicPropertySource
    static void encryptionKey(DynamicPropertyRegistry registry) {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        registry.add("app.encryption.key", () -> Base64.getEncoder().encodeToString(key));
    }

    private static final AtomicInteger SEQUENCE = new AtomicInteger();
    private static final Clock CLOCK = Clock.fixed(Instant.now(), ZoneId.systemDefault());
    private static final YearMonth MONTH = YearMonth.now(CLOCK);

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
    private UserRepository users;
    @Autowired
    private RefreshTokenRepository refreshTokens;
    @Autowired
    private AuthTokenService authTokenService;
    @Autowired
    private JdbcTemplate jdbc;

    private User user;
    private String token;

    @BeforeEach
    void setUp() {
        user = newUser();
        token = authTokenService.issue(user.getId()).accessToken();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from planned_expenses");
        refreshTokens.deleteAll();
        users.deleteAll();
    }

    private User newUser() {
        int n = SEQUENCE.incrementAndGet();
        return users.save(new User("expense" + n + "@example.com", "hash", "김리치", "010%08d".formatted(8000000 + n),
            "REAL", LocalDateTime.now(CLOCK)));
    }

    private static final String JEJU = """
        {"name":"제주도 숙소","amount":300000,"category":"CULTURE_TRAVEL","budgetMethod":"EXTRA"}""";
    private static final String GIFT = """
        {"name":"친구 생일선물","amount":130000,"category":"SHOPPING"}""";

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               String jwt) throws Exception {
        return mockMvc.perform(jwt == null ? request : request.header("Authorization", "Bearer " + jwt));
    }

    private long create(String body, String jwt) throws Exception {
        String response = send(post("/api/planned-expenses").contentType(MediaType.APPLICATION_JSON).content(body), jwt)
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(response, "$.data.expenseId")).longValue();
    }

    @Test
    @DisplayName("로그인하지 않으면 401이다")
    void requiresLogin() throws Exception {
        send(get("/api/planned-expenses"), null).andExpect(status().isUnauthorized());
        send(post("/api/planned-expenses").contentType(MediaType.APPLICATION_JSON).content(JEJU), null)
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("추가하면 이번 달 목록에 최근 것이 먼저 나오고 반영 방식별 합계가 계산된다")
    void createAndList() throws Exception {
        create(GIFT, token);
        create("""
            {"name":"노트북","amount":1200000,"installmentMonths":6,"category":"SHOPPING","budgetMethod":"WITHIN_BUDGET"}""",
            token);
        create(JEJU, token);

        send(get("/api/planned-expenses"), token).andExpect(status().isOk())
            .andExpect(jsonPath("$.data.month").value(MONTH.toString()))
            .andExpect(jsonPath("$.data.expenses.length()").value(3))
            .andExpect(jsonPath("$.data.expenses[0].name").value("제주도 숙소"))
            .andExpect(jsonPath("$.data.expenses[0].budgetMethod").value("EXTRA"))
            .andExpect(jsonPath("$.data.expenses[1].installmentMonths").value(6))
            .andExpect(jsonPath("$.data.expenses[1].monthlyAmount").value(200_000))
            .andExpect(jsonPath("$.data.expenses[2].budgetMethod").value("WITHIN_BUDGET"))
            .andExpect(jsonPath("$.data.summary.withinBudget").value(330_000))
            .andExpect(jsonPath("$.data.summary.extra").value(300_000))
            .andExpect(jsonPath("$.data.summary.total").value(630_000));
    }

    @Test
    @DisplayName("다른 달 목록은 그 달의 예정지출만 보여주고 월 형식이 틀리면 400이다")
    void listByMonth() throws Exception {
        create(JEJU, token);

        send(get("/api/planned-expenses").param("month", MONTH.minusMonths(1).toString()), token)
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.expenses.length()").value(0))
            .andExpect(jsonPath("$.data.summary.total").value(0));
        send(get("/api/planned-expenses").param("month", "2026-13"), token).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("COMMON-400"));
    }

    @Test
    @DisplayName("입력이 올바르지 않으면 400 EXPENSE-002이고 저장하지 않는다")
    void rejectsInvalidInput() throws Exception {
        send(post("/api/planned-expenses").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"","amount":1000,"category":"ETC"}"""), token).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("EXPENSE-002"));
        send(post("/api/planned-expenses").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"이름","amount":0,"category":"ETC"}"""), token).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("EXPENSE-002"));
        send(post("/api/planned-expenses").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"이름","amount":1000,"installmentMonths":5,"category":"ETC"}"""), token)
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EXPENSE-002"));
        send(post("/api/planned-expenses").contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"이름","amount":1000,"category":"UNKNOWN"}"""), token).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("EXPENSE-002"));

        assertThat(jdbc.queryForObject("select count(*) from planned_expenses", Long.class)).isZero();
    }

    @Test
    @DisplayName("수정하면 값이 바뀌고, 삭제하면 목록에서 사라진다")
    void updateAndDelete() throws Exception {
        long id = create(JEJU, token);

        send(put("/api/planned-expenses/" + id).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"name":"제주도 호텔","amount":450000,"installmentMonths":3,"category":"CULTURE_TRAVEL",
                 "budgetMethod":"WITHIN_BUDGET"}"""), token)
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.name").value("제주도 호텔"))
            .andExpect(jsonPath("$.data.monthlyAmount").value(150_000))
            .andExpect(jsonPath("$.data.budgetMethod").value("WITHIN_BUDGET"));
        send(get("/api/planned-expenses"), token)
            .andExpect(jsonPath("$.data.expenses[0].amount").value(450_000))
            .andExpect(jsonPath("$.data.summary.withinBudget").value(150_000))
            .andExpect(jsonPath("$.data.summary.extra").value(0));

        send(delete("/api/planned-expenses/" + id), token).andExpect(status().isOk());
        send(get("/api/planned-expenses"), token).andExpect(jsonPath("$.data.expenses.length()").value(0));
        send(delete("/api/planned-expenses/" + id), token).andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("EXPENSE-001"));
    }

    @Test
    @DisplayName("다른 사용자의 예정지출은 보이지 않고 고치거나 지울 수 없다")
    void cannotTouchAnotherUsersExpense() throws Exception {
        long id = create(JEJU, token);
        User other = newUser();
        String otherToken = authTokenService.issue(other.getId()).accessToken();

        send(get("/api/planned-expenses"), otherToken).andExpect(jsonPath("$.data.expenses.length()").value(0));
        send(put("/api/planned-expenses/" + id).contentType(MediaType.APPLICATION_JSON).content(GIFT), otherToken)
            .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("EXPENSE-001"));
        send(delete("/api/planned-expenses/" + id), otherToken).andExpect(status().isNotFound());

        send(get("/api/planned-expenses"), token).andExpect(jsonPath("$.data.expenses.length()").value(1));
    }

    @Test
    @DisplayName("지난 달 예정지출은 조회만 되고 고치거나 지우면 400 EXPENSE-002이다")
    void pastMonthIsReadOnly() throws Exception {
        String previous = MONTH.minusMonths(1).toString();
        jdbc.update("insert into planned_expenses (user_id, plan_month, name, amount, category, budget_method) "
            + "values (?, ?, '지난달 지출', 50000, 'ETC', 'EXTRA')", user.getId(), previous);
        long id = jdbc.queryForObject("select id from planned_expenses", Long.class);

        send(get("/api/planned-expenses").param("month", previous), token)
            .andExpect(jsonPath("$.data.expenses.length()").value(1));
        send(put("/api/planned-expenses/" + id).contentType(MediaType.APPLICATION_JSON).content(JEJU), token)
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("EXPENSE-002"));
        send(delete("/api/planned-expenses/" + id), token).andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("EXPENSE-002"));
    }

    @Test
    @DisplayName("한 달에 50개까지만 추가할 수 있다")
    void limitsTheCount() throws Exception {
        for (int i = 0; i < 50; i++) {
            jdbc.update("insert into planned_expenses (user_id, plan_month, name, amount, category, budget_method) "
                + "values (?, ?, ?, 1000, 'ETC', 'EXTRA')", user.getId(), MONTH.toString(), "지출" + i);
        }

        send(post("/api/planned-expenses").contentType(MediaType.APPLICATION_JSON).content(JEJU), token)
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EXPENSE-003"));
    }

    @Test
    @DisplayName("사용자를 지우면 예정지출도 함께 지워진다")
    void deletingTheUserDeletesTheExpenses() throws Exception {
        create(JEJU, token);
        assertThat(jdbc.queryForObject("select count(*) from planned_expenses", Long.class)).isEqualTo(1L);

        refreshTokens.deleteAll();
        users.deleteAll();

        assertThat(jdbc.queryForObject("select count(*) from planned_expenses", Long.class)).isZero();
    }

}
