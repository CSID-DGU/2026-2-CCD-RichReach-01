package com.richreach.card.service;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 스테이징 테이블(stg_*)에서 "MVP로 선택된 카드만" 최종 테이블로 옮긴다.
 * (card_products -> benefit_rules -> performance_tiers / performance_exclusions)
 *
 * <ul>
 *   <li>같은 스테이징 행(source_*_id)이 이미 있으면 갱신하므로 여러 번 실행해도 중복되지 않는다.</li>
 *   <li>{@code benefit_rules.rule_engine_ready}는 사람이 원문과 대조해 확인한 표시라서 갱신하지 않는다.</li>
 *   <li>발급 중단(issuance_suspended) 카드도 옮기되 {@code recommendable = false}로 표시한다. 삭제하지 않는다.</li>
 *   <li>전체가 한 트랜잭션이라 중간에 실패하면 아무것도 반영되지 않는다.</li>
 * </ul>
 */
@Slf4j
@Service
public class CardPromotionService {

    /** 최종 테이블의 현재 행 수. 승격 후 확인용이다. */
    public record Result(long mvpSelected, long cardProducts, long benefitRules, long performanceTiers,
                         long performanceExclusions, long rawCatalogLinked) {
    }

    /** @param updatable 이미 있는 행을 승격할 때 새 값으로 바꿀지 */
    private record Mapping(String column, String expr, boolean updatable) {
    }

    private static final String MVP_CATALOG_JOIN =
        "JOIN stg_card_catalog c ON c.issuer_code = s.issuer_code AND c.card_id = s.card_id AND c.mvp_selected = 'SELECTED' "
            + "JOIN card_products p ON p.issuer_code = s.issuer_code AND p.card_id = s.card_id ";

    private static final String CARDS_SQL = upsertSql("card_products",
        List.of(
            key("issuer_code", "s.issuer_code"), key("card_id", "s.card_id"),
            same("issuer"), same("card_name"), same("card_type"), same("card_status"), same("product_class"),
            // 발급 중단이거나 결제용 카드가 아니면(기프트/바우처 등) 신규 추천 후보에서 뺀다.
            // card_status가 비어 있으면(미확정) 비교 결과가 NULL이 되므로 COALESCE로 FALSE 처리한다.
            of("recommendable", "COALESCE(s.card_status = 'listed' AND s.product_class = 'PAYMENT_CARD', FALSE)"),
            same("source_url"), same("source_snapshot_date"), same("source_text_sha256"), same("review_status")),
        "FROM stg_card_catalog s WHERE s.mvp_selected = 'SELECTED'");

    // rule_engine_ready는 일부러 넣지 않는다. 새 행은 DB 기본값(FALSE)이고, 기존 행의 사람 확인 표시는 유지된다.
    private static final String BENEFITS_SQL = upsertSql("benefit_rules",
        withSame(
            List.of(key("card_product_id", "p.id"), key("source_benefit_id", "s.benefit_id")),
            "benefit_type", "category", "category_name", "merchants", "target_raw", "targets_normalized",
            "payment_methods", "rate_percent", "rate_min_percent", "rate_max_percent", "amount", "reward_unit",
            "amount_basis", "monthly_limit", "monthly_limit_unit", "monthly_limit_status", "daily_limit",
            "shared_monthly_limit", "cap_group_id", "eligible_spend_monthly_limit_krw", "daily_count_limit",
            "monthly_count_limit", "annual_count_limit", "rule_mode", "conditions", "review_notes",
            "review_status", "source_scope_id", "extraction_method"),
        "FROM stg_card_benefit s " + MVP_CATALOG_JOIN);

    // 실적 조건이 하나라도 있는 혜택만 행을 만든다. 빈칸은 "조건 없음"이 아니라 미확정이다.
    private static final String TIERS_SQL = upsertSql("performance_tiers",
        withSame(
            List.of(key("benefit_rule_id", "r.id")),
            "previous_month_min_krw", "previous_month_max_exclusive_krw",
            "previous_month_requirement_status", "annual_spend_min_krw"),
        "FROM stg_card_benefit s JOIN benefit_rules r ON r.source_benefit_id = s.benefit_id "
            + "WHERE s.previous_month_min_krw IS NOT NULL OR s.previous_month_max_exclusive_krw IS NOT NULL "
            + "OR s.previous_month_requirement_status IS NOT NULL OR s.annual_spend_min_krw IS NOT NULL");

    // 조건은 혜택이 아니라 원문 블록에 붙어 있고 혜택과의 연결이 불완전해서, 카드 단위로만 연결한다.
    private static final String EXCLUSIONS_SQL = upsertSql("performance_exclusions",
        withSame(
            List.of(key("card_product_id", "p.id"), key("source_condition_id", "s.condition_id"),
                of("applies_to",
                    "CASE WHEN s.condition_type LIKE '%PERFORMANCE%' THEN 'PERFORMANCE' ELSE 'BENEFIT' END")),
            "condition_type", "condition_text", "source_scope_id"),
        "FROM stg_card_condition s " + MVP_CATALOG_JOIN + "WHERE s.condition_type LIKE '%EXCLUSION%'");

    private static final String LINK_RAW_SQL =
        "UPDATE card_catalog_raw r JOIN card_products p ON p.issuer = r.issuer AND p.card_id = r.card_id "
            + "SET r.structured_product_id = p.id, r.review_status = 'structured'";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public CardPromotionService(JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** MVP로 선택된 카드를 최종 테이블로 옮긴다. MVP 카드가 없으면 아무것도 옮기지 않는다. */
    public Result promote() {
        Result result = tx.execute(status -> {
            jdbc.update(CARDS_SQL);
            jdbc.update(BENEFITS_SQL);
            jdbc.update(TIERS_SQL);
            jdbc.update(EXCLUSIONS_SQL);
            jdbc.update(LINK_RAW_SQL);
            return new Result(
                count("SELECT COUNT(*) FROM stg_card_catalog WHERE mvp_selected = 'SELECTED'"),
                count("SELECT COUNT(*) FROM card_products"),
                count("SELECT COUNT(*) FROM benefit_rules"),
                count("SELECT COUNT(*) FROM performance_tiers"),
                count("SELECT COUNT(*) FROM performance_exclusions"),
                count("SELECT COUNT(*) FROM card_catalog_raw WHERE structured_product_id IS NOT NULL"));
        });
        if (result.mvpSelected() == 0) {
            log.warn("[promotion] MVP로 선택된 카드가 없어 옮긴 것이 없습니다. (mvp_cards.csv 확인)");
        }
        log.info("[promotion] MVP 카드 {}개 -> card_products {}, benefit_rules {}, performance_tiers {}, "
                + "performance_exclusions {}, 원문 연결 {}",
            result.mvpSelected(), result.cardProducts(), result.benefitRules(), result.performanceTiers(),
            result.performanceExclusions(), result.rawCatalogLinked());
        return result;
    }

    private long count(String sql) {
        Long value = jdbc.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    /**
     * {@code INSERT ... SELECT ... ON DUPLICATE KEY UPDATE}를 만든다.
     * 키(updatable=false)가 같은 행이 있으면 updatable 컬럼만 새 값으로 바꾼다.
     */
    private static String upsertSql(String table, List<Mapping> mappings, String fromAndWhere) {
        String columns = join(mappings, Mapping::column);
        String selected = mappings.stream().map(m -> m.expr() + " AS " + m.column()).collect(Collectors.joining(", "));
        String outer = join(mappings, m -> "src." + m.column());
        String updates = mappings.stream().filter(Mapping::updatable)
            .map(m -> m.column() + " = src." + m.column()).collect(Collectors.joining(", "));
        return "INSERT INTO %s (%s) SELECT %s FROM (SELECT %s %s) AS src ON DUPLICATE KEY UPDATE %s"
            .formatted(table, columns, outer, selected, fromAndWhere, updates);
    }

    private static String join(List<Mapping> mappings, Function<Mapping, String> part) {
        return mappings.stream().map(part).collect(Collectors.joining(", "));
    }

    private static List<Mapping> withSame(List<Mapping> first, String... sameColumns) {
        return Stream.concat(first.stream(), Arrays.stream(sameColumns).map(CardPromotionService::same)).toList();
    }

    private static Mapping key(String column, String expr) {
        return new Mapping(column, expr, false);
    }

    private static Mapping of(String column, String expr) {
        return new Mapping(column, expr, true);
    }

    private static Mapping same(String column) {
        return new Mapping(column, "s." + column, true);
    }
}
