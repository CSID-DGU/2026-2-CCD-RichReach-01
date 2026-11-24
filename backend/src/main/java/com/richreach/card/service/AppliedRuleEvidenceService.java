package com.richreach.card.service;

import com.richreach.card.dto.AppliedRuleEvidence;
import com.richreach.card.dto.AppliedRuleEvidence.Impact;
import com.richreach.card.dto.AppliedRuleEvidence.Status;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** 계산기에서 실제 값이 달라진 규칙 ID를 같은 카드의 원문 블록에만 연결한다. */
@Service
public class AppliedRuleEvidenceService {
    private static final int EXCERPT_LIMIT = 1_000;
    private final JdbcTemplate jdbc;

    public AppliedRuleEvidenceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AppliedRuleEvidence> find(long cardProductId, Map<String, Long> before,
                                          Map<String, Long> after) {
        Map<Long, Impact> impacts = changedRules(before, after);
        if (impacts.isEmpty()) return List.of();
        List<Object> params = new ArrayList<>();
        params.add(cardProductId);
        params.addAll(impacts.keySet());
        String placeholders = String.join(",", java.util.Collections.nCopies(impacts.size(), "?"));
        String sql = """
            select b.id, b.source_scope_id, b.target_raw, sb.source_quote,
                   s.source_url, s.scope_text, c.source_snapshot_date
            from benefit_rules b
            join card_products c on c.id = b.card_product_id
            left join stg_card_benefit sb on sb.benefit_id = b.source_benefit_id
                and sb.issuer_code = c.issuer_code and sb.card_id = c.card_id
                and sb.source_scope_id = b.source_scope_id
            left join stg_source_scope s on s.scope_id = b.source_scope_id
                and s.issuer_code = c.issuer_code and s.card_id = c.card_id
            where b.card_product_id = ? and b.id in (%s)
            order by b.id
            """.formatted(placeholders);
        List<AppliedRuleEvidence> found = jdbc.query(sql, (rs, rowNum) -> {
            long ruleId = rs.getLong("id");
            String text = rs.getString("scope_text");
            String quote = rs.getString("source_quote");
            LocalDate date = rs.getDate("source_snapshot_date") == null ? null
                : rs.getDate("source_snapshot_date").toLocalDate();
            boolean available = text != null && !text.isBlank() && rs.getString("source_url") != null;
            String excerpt = available ? excerpt(rs.getString("source_scope_id"),
                rs.getString("source_url"), text, quote, rs.getString("target_raw")) : null;
            return new AppliedRuleEvidence(ruleId, impacts.get(ruleId),
                available ? Status.SOURCE_FOUND : Status.SOURCE_NOT_FOUND,
                rs.getString("source_scope_id"), rs.getString("source_url"),
                excerpt, available ? "PRODUCT_PAGE_EXCERPT" : null, date, null);
        }, params.toArray());
        Map<Long, AppliedRuleEvidence> byId = new HashMap<>();
        found.forEach(item -> byId.put(item.ruleId(), item));
        return impacts.entrySet().stream().map(entry -> byId.getOrDefault(entry.getKey(),
            new AppliedRuleEvidence(entry.getKey(), entry.getValue(), Status.SOURCE_NOT_FOUND,
                null, null, null, null, null, null))).toList();
    }

    static Map<Long, Impact> changedRules(Map<String, Long> before, Map<String, Long> after) {
        Map<Long, Impact> result = new LinkedHashMap<>();
        Map<String, Long> all = new HashMap<>(before);
        after.forEach((id, value) -> all.putIfAbsent(id, value));
        all.keySet().stream().sorted(Comparator.comparingLong(AppliedRuleEvidenceService::parseId))
            .forEach(id -> {
                long delta = Math.subtractExact(after.getOrDefault(id, 0L), before.getOrDefault(id, 0L));
                if (delta != 0) {
                    result.put(parseId(id), delta > 0 ? Impact.INCREASED : Impact.DECREASED);
                }
            });
        return result;
    }

    private static long parseId(String id) {
        return Long.parseLong(id);
    }

    private static String excerpt(String scopeId, String url, String text, String quote, String target) {
        if (quote != null && !quote.isBlank()) {
            return quote.substring(0, Math.min(EXCERPT_LIMIT, quote.length()));
        }
        if (target != null && !target.isBlank()) {
            List<CardEvidenceService.Ranked> ranked = CardEvidenceService.rank(
                List.of(new CardEvidenceService.Source(scopeId, url, text)), target);
            if (!ranked.isEmpty()) return ranked.get(0).citation().excerpt();
        }
        return text.substring(0, Math.min(EXCERPT_LIMIT, text.length()));
    }
}
