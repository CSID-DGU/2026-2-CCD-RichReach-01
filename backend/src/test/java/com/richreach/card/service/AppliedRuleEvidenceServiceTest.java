package com.richreach.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.richreach.card.dto.AppliedRuleEvidence;
import java.sql.ResultSet;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class AppliedRuleEvidenceServiceTest {

    @Test
    void includesRulesReducedBySharedLimitsButNotUnchangedRules() {
        assertThat(AppliedRuleEvidenceService.changedRules(
            Map.of("10", 100L, "20", 50L, "30", 1L),
            Map.of("10", 120L, "20", 40L, "30", 1L)))
            .containsExactly(Map.entry(10L, AppliedRuleEvidence.Impact.INCREASED),
                Map.entry(20L, AppliedRuleEvidence.Impact.DECREASED));
    }

    @Test
    @SuppressWarnings("unchecked")
    void retrievesOnlyMatchingCardAndReturnsAnUnverifiedSnapshot() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.query(anyString(), any(RowMapper.class), any(Object[].class))).thenAnswer(call -> {
            String sql = call.getArgument(0);
            Object[] args = call.getArguments();
            assertThat(sql).contains("b.card_product_id = ?", "s.issuer_code = c.issuer_code",
                "s.card_id = c.card_id", "sb.source_scope_id = b.source_scope_id");
            assertThat(args).containsExactly(sql, call.getArgument(1), 7L, 42L);
            ResultSet rs = mock(ResultSet.class);
            when(rs.getLong("id")).thenReturn(42L);
            when(rs.getString("source_scope_id")).thenReturn("scope-42");
            when(rs.getString("source_url")).thenReturn("https://example.com/card");
            when(rs.getString("scope_text")).thenReturn("카페 5% 할인");
            when(rs.getString("source_quote")).thenReturn("카페 5% 할인");
            when(rs.getDate("source_snapshot_date")).thenReturn(Date.valueOf("2026-11-20"));
            return List.of(((RowMapper<AppliedRuleEvidence>) call.getArgument(1)).mapRow(rs, 0));
        });

        var found = new AppliedRuleEvidenceService(jdbc).find(7L, Map.of("42", 0L),
            Map.of("42", 500L));

        assertThat(found).hasSize(1);
        assertThat(found.get(0).status()).isEqualTo(AppliedRuleEvidence.Status.SOURCE_FOUND);
        assertThat(found.get(0).sourceScopeId()).isEqualTo("scope-42");
        assertThat(found.get(0).excerpt()).isEqualTo("카페 5% 할인");
        assertThat(found.get(0).sourceSnapshotDate()).isEqualTo(LocalDate.of(2026, 11, 20));
        assertThat(found.get(0).documentVersion()).isNull();
    }
}
