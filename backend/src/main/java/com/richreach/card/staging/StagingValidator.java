package com.richreach.card.staging;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * 적재 후 테이블 사이의 연결 상태를 센다. FK를 걸지 않았으므로 연결이 끊긴 행은 여기서만 드러난다.
 *
 * <p>건수가 0이 아니어도 적재를 실패시키지 않는다. 원본 자료에 알려진 누락이 있어서
 * (예: 조건의 source_scope_id 중 혜택에서 찾아지지 않는 것) 사람이 건수를 보고 판단한다.
 */
@Slf4j
@Component
class StagingValidator {

    private record Check(String name, String description, String sql) {
    }

    /** @param count 문제가 되는 행(또는 값) 수. 0이면 이상 없음 */
    record CheckResult(String name, String description, long count) {
    }

    private static final List<Check> CHECKS = List.of(
        new Check("benefit_without_catalog", "카탈로그에 없는 카드의 혜택 행",
            """
            SELECT COUNT(*) FROM stg_card_benefit b
            LEFT JOIN stg_card_catalog c ON c.issuer_code = b.issuer_code AND c.card_id = b.card_id
            WHERE c.id IS NULL"""),
        new Check("condition_without_catalog", "카탈로그에 없는 카드의 조건 행",
            """
            SELECT COUNT(*) FROM stg_card_condition d
            LEFT JOIN stg_card_catalog c ON c.issuer_code = d.issuer_code AND c.card_id = d.card_id
            WHERE c.id IS NULL"""),
        new Check("source_scope_without_catalog", "카탈로그에 없는 카드의 원문 블록",
            """
            SELECT COUNT(*) FROM stg_source_scope s
            LEFT JOIN stg_card_catalog c ON c.issuer_code = s.issuer_code AND c.card_id = s.card_id
            WHERE c.id IS NULL"""),
        new Check("review_queue_without_catalog", "카탈로그에 없는 카드의 검토 항목",
            """
            SELECT COUNT(*) FROM stg_review_queue r
            LEFT JOIN stg_card_catalog c ON c.issuer_code = r.issuer_code AND c.card_id = r.card_id
            WHERE c.id IS NULL"""),
        new Check("special_case_without_catalog", "카탈로그에 없는 카드의 특수 항목",
            """
            SELECT COUNT(*) FROM stg_special_case x
            LEFT JOIN stg_card_catalog c ON c.issuer_code = x.issuer_code AND c.card_id = x.card_id
            WHERE c.id IS NULL"""),
        new Check("benefit_without_source_scope", "원문 블록을 찾을 수 없는 혜택 행",
            """
            SELECT COUNT(*) FROM stg_card_benefit b
            LEFT JOIN stg_source_scope s ON s.scope_id = b.source_scope_id
            WHERE s.id IS NULL"""),
        new Check("condition_without_source_scope", "원문 블록을 찾을 수 없는 조건 행",
            """
            SELECT COUNT(*) FROM stg_card_condition d
            LEFT JOIN stg_source_scope s ON s.scope_id = d.source_scope_id
            WHERE s.id IS NULL"""),
        new Check("special_case_without_source_scope", "원문 블록을 찾을 수 없는 특수 항목(scope가 있는 행만)",
            """
            SELECT COUNT(*) FROM stg_special_case x
            LEFT JOIN stg_source_scope s ON s.scope_id = x.source_scope_id
            WHERE x.source_scope_id IS NOT NULL AND s.id IS NULL"""),
        new Check("condition_scope_without_benefit", "혜택 행이 하나도 없는 조건의 source_scope_id 개수 (알려진 누락)",
            """
            SELECT COUNT(DISTINCT d.source_scope_id) FROM stg_card_condition d
            WHERE NOT EXISTS (SELECT 1 FROM stg_card_benefit b WHERE b.source_scope_id = d.source_scope_id)"""),
        new Check("target_name_without_benefit", "혜택 행에 없는 benefit_id를 가리키는 가맹점 연결",
            """
            SELECT COUNT(*) FROM stg_target_name t
            LEFT JOIN stg_card_benefit b ON b.benefit_id = t.benefit_id
            WHERE b.id IS NULL"""),
        // target_code가 빈 문자열이면 아직 코드가 정해지지 않은 연결이라 "없는 코드"로 세지 않는다.
        new Check("target_name_without_standard", "공통 코드표에 없는 target_code (코드가 비어 있는 연결은 제외)",
            """
            SELECT COUNT(*) FROM stg_target_name t
            LEFT JOIN ref_common_standard r ON r.code = t.target_code
            WHERE t.target_code <> '' AND r.id IS NULL"""),
        new Check("target_name_without_code", "코드가 아직 정해지지 않은 대상 명칭 연결 (참고용)",
            """
            SELECT COUNT(*) FROM stg_target_name WHERE target_code = ''"""),
        new Check("catalog_without_raw", "원문(card_catalog_raw)이 없는 카탈로그 카드",
            """
            SELECT COUNT(*) FROM stg_card_catalog c
            LEFT JOIN card_catalog_raw r ON r.issuer = c.issuer AND r.card_id = c.card_id
            WHERE r.id IS NULL"""),
        new Check("catalog_hash_mismatch", "원문 해시가 card_catalog_raw.content_hash와 다른 카드",
            """
            SELECT COUNT(*) FROM stg_card_catalog c
            JOIN card_catalog_raw r ON r.issuer = c.issuer AND r.card_id = c.card_id
            WHERE r.content_hash <> c.source_text_sha256""")
    );

    private final JdbcTemplate jdbc;

    StagingValidator(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    List<CheckResult> validate() {
        return CHECKS.stream().map(this::run).toList();
    }

    private CheckResult run(Check check) {
        Long count = jdbc.queryForObject(check.sql(), Long.class);
        long value = count == null ? 0 : count;
        if (value == 0) {
            log.info("[staging-check] OK   {}", check.name());
        } else {
            log.warn("[staging-check] {}건 {} - {}", value, check.name(), check.description());
        }
        return new CheckResult(check.name(), check.description(), value);
    }
}
