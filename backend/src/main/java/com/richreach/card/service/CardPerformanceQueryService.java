package com.richreach.card.service;

import com.richreach.card.entity.PerformanceExclusion;
import com.richreach.card.repository.PerformanceExclusionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카드 상품의 전월실적 산정에서 제외되는 조건을 읽는다. 조건은 원문 그대로의 문장이라 금액이나 업종으로 계산할 수 없고, 화면에
 * 안내 문구로만 쓴다. 다른 도메인이 카드의 엔티티와 리포지토리를 직접 읽지 않도록 문장만 내려준다.
 */
@Service
@RequiredArgsConstructor
public class CardPerformanceQueryService {

    /** 안내 문구로 쓰기에 너무 긴 원문은 이 길이에서 자른다. */
    static final int MAX_NOTE_LENGTH = 200;
    /** 한 카드에 담는 안내 문구의 최대 개수. */
    static final int MAX_NOTES = 5;

    private static final String APPLIES_TO_PERFORMANCE = "PERFORMANCE";

    private final PerformanceExclusionRepository exclusions;

    /**
     * 전월실적 산정에서 제외되는 조건(원문). 조건이 없거나 카드 상품이 없으면 빈 목록이다. 같은 문장은 한 번만, 최대
     * {@value #MAX_NOTES}개를 돌려주고 긴 문장은 {@value #MAX_NOTE_LENGTH}자에서 줄인다.
     */
    @Transactional(readOnly = true)
    public List<String> performanceExclusionNotes(long cardProductId) {
        return exclusions.findByCardProductId(cardProductId).stream()
            .filter(exclusion -> APPLIES_TO_PERFORMANCE.equals(exclusion.getAppliesTo()))
            .map(PerformanceExclusion::getConditionText)
            .map(text -> text == null ? "" : text.strip())
            .filter(text -> !text.isEmpty())
            .map(text -> text.length() <= MAX_NOTE_LENGTH ? text : text.substring(0, MAX_NOTE_LENGTH) + "…")
            .distinct()
            .limit(MAX_NOTES)
            .toList();
    }

}
