package com.richreach.card.staging;

import static com.richreach.card.staging.StagingTable.ColumnType.BOOL;
import static com.richreach.card.staging.StagingTable.ColumnType.DATE;
import static com.richreach.card.staging.StagingTable.ColumnType.DECIMAL;
import static com.richreach.card.staging.StagingTable.ColumnType.INT;
import static com.richreach.card.staging.StagingTable.ColumnType.LONG;

import com.richreach.card.staging.StagingTable.ColumnType;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 적재할 CSV 8종. 테이블 정의는 db/migration의 V6~V9와 맞춰야 한다.
 *
 * <ul>
 *   <li>stg_card_catalog의 mvp_selected, sample_owner는 일부러 뺐다. 이 값은 CSV를 다시 적재해도
 *       유지돼야 해서 {@link StagingService#selectMvpCards}가 따로 관리한다.</li>
 *   <li>빈칸은 전부 NULL로 넣는다. 0이나 '무제한'으로 바꾸면 안 된다.</li>
 * </ul>
 */
final class StagingTables {

    static final List<StagingTable> ALL = List.of(
        table("card_catalog_319.csv", "stg_card_catalog",
            "issuer_code card_id",
            """
            issuer_code issuer card_id card_name card_type card_status source_summary source_url
            source_snapshot_date source_text_sha256 source_html_count source_scope_count
            source_table_count benefit_row_count categories category_names benefit_types
            review_status product_class
            """,
            Map.of("source_snapshot_date", DATE, "source_html_count", INT, "source_scope_count", INT,
                "source_table_count", INT, "benefit_row_count", INT)),

        table("source_scopes.csv", "stg_source_scope",
            "scope_id",
            "scope_id issuer_code card_id card_name source_url source_html html_line scope_text",
            Map.of("html_line", INT)),

        table("card_benefits_all.csv", "stg_card_benefit",
            "benefit_id",
            """
            benefit_id issuer_code card_id card_name issuer card_status source_url source_file
            source_snapshot_date benefit_type category category_name merchants target_raw
            targets_normalized payment_methods rate_percent rate_min_percent rate_max_percent amount
            reward_unit amount_basis per_transaction_min_krw previous_month_min_krw
            previous_month_max_exclusive_krw previous_month_requirement_status annual_spend_min_krw
            monthly_limit monthly_limit_unit monthly_limit_status daily_limit shared_monthly_limit
            cap_group_id eligible_spend_monthly_limit_krw daily_count_limit monthly_count_limit
            annual_count_limit rule_mode conditions review_notes review_status rule_engine_ready
            source_scope_id source_lines source_quote extraction_method
            """,
            Map.ofEntries(
                Map.entry("source_snapshot_date", DATE),
                Map.entry("rate_percent", DECIMAL), Map.entry("rate_min_percent", DECIMAL),
                Map.entry("rate_max_percent", DECIMAL),
                Map.entry("amount", LONG), Map.entry("per_transaction_min_krw", LONG),
                Map.entry("previous_month_min_krw", LONG),
                Map.entry("previous_month_max_exclusive_krw", LONG),
                Map.entry("annual_spend_min_krw", LONG), Map.entry("monthly_limit", LONG),
                Map.entry("daily_limit", LONG), Map.entry("shared_monthly_limit", LONG),
                Map.entry("eligible_spend_monthly_limit_krw", LONG),
                Map.entry("daily_count_limit", INT), Map.entry("monthly_count_limit", INT),
                Map.entry("annual_count_limit", INT),
                Map.entry("rule_engine_ready", BOOL))),

        table("card_conditions_all.csv", "stg_card_condition",
            "condition_id",
            "condition_id issuer_code card_id card_name condition_type condition_text source_scope_id scope_status",
            Map.of()),

        table("common_standards.csv", "ref_common_standard",
            "standard_kind code",
            "standard_kind code name source_alias_pattern definition",
            Map.of()),

        table("target_names.csv", "stg_target_name",
            "benefit_id target_code target_name",
            "benefit_id issuer_code card_id target_code target_name target_kind matching_status",
            Map.of()),

        table("review_queue.csv", "stg_review_queue",
            "issuer_code card_id issue_type detail",
            "issuer_code card_id card_name issue_type detail status",
            Map.of()),

        table("special_cases.csv", "stg_special_case",
            "issuer_code card_id exception_type description",
            "issuer_code card_id card_name exception_type description source_scope_id source_quote",
            Map.of())
    );

    private StagingTables() {
    }

    private static StagingTable table(String csvFile, String table, String keys, String columns,
                                      Map<String, ColumnType> types) {
        return new StagingTable(csvFile, table, split(keys), split(columns), types);
    }

    private static List<String> split(String names) {
        return Arrays.asList(names.trim().split("\\s+"));
    }
}
