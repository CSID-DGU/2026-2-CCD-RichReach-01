package com.richreach.card.service;

import com.richreach.card.entity.BenefitCategoryMapping;
import com.richreach.card.repository.BenefitCategoryMappingRepository;
import com.richreach.global.category.SpendingCategory;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 혜택의 업종 코드를 화면용 6개 업종으로 바꾼다. 혜택 계산이 "이 혜택은 어느 업종 거래에 적용되는가"를 정할 때 쓴다.
 *
 * <p>대응표는 DB({@code benefit_category_mappings})에 있고, 작아서 처음 쓸 때 한 번 읽어 둔다. 표를 바꾼 뒤에는
 * {@link #refresh()}로 다시 읽는다.
 * <ul>
 *   <li>혜택 하나에 업종이 여러 개면 {@code CAFE|BAKERY}처럼 '|'로 이어서 오며, 코드마다 바꿔서 모은다.
 *       같은 업종으로 모이면 하나로 본다. (예: {@code CAFE|BAKERY}는 카페 하나)</li>
 *   <li>업종이 아닌 코드(결제 수단, 해외 조건)는 업종을 만들지 않고, 전 가맹점 코드({@code ALL_MERCHANTS}, {@code ALL})는
 *       {@code allMerchants}만 켠다.</li>
 *   <li>대응표에 없는 코드는 {@code ETC}로 본다. 코드표에 새 업종이 생겨도 혜택이 사라지지 않게 하려는 것이고, 서버 로그에
 *       코드를 남겨서 대응표에 추가하도록 알린다.</li>
 *   <li>{@code OTHER}는 {@code ETC}로 바꾼다. 다만 가맹점이 지정된 혜택(예: 특정 브랜드)은 업종만으로 거래와 이을 수 없어서
 *       혜택 계산이 가맹점 정보를 따로 봐야 한다.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BenefitCategoryMapper {

    private final BenefitCategoryMappingRepository mappings;

    private volatile Map<String, BenefitCategoryMapping> table;

    /**
     * 혜택 업종 코드를 바꾼 결과.
     *
     * @param categories   혜택이 적용되는 화면용 업종. 업종이 아닌 코드만 있으면 비어 있다.
     * @param allMerchants 업종과 상관없이 모든 가맹점에 적용되는지
     */
    public record MappedCategories(Set<SpendingCategory> categories, boolean allMerchants) {

        public static final MappedCategories NONE = new MappedCategories(Set.of(), false);

    }

    /** 혜택 업종 코드(단일 코드 또는 '|'로 이어진 여러 코드)를 화면용 업종으로 바꾼다. 없거나 비어 있으면 {@link MappedCategories#NONE}. */
    public MappedCategories map(String benefitCategory) {
        if (benefitCategory == null || benefitCategory.isBlank()) {
            return MappedCategories.NONE;
        }
        Map<String, BenefitCategoryMapping> current = table();
        Set<SpendingCategory> categories = EnumSet.noneOf(SpendingCategory.class);
        boolean allMerchants = false;
        for (String raw : benefitCategory.split("\\|")) {
            String code = raw.strip().toUpperCase(Locale.ROOT);
            if (code.isEmpty()) {
                continue;
            }
            BenefitCategoryMapping mapping = current.get(code);
            if (mapping == null) {
                log.warn("대응표에 없는 혜택 업종 코드입니다. ETC로 봅니다: code={}", code);
                categories.add(SpendingCategory.ETC);
                continue;
            }
            mapping.spendingCategory().ifPresent(categories::add);
            allMerchants |= mapping.isAllMerchants();
        }
        return new MappedCategories(Collections.unmodifiableSet(categories), allMerchants);
    }

    /** 대응표를 다시 읽는다. 마이그레이션으로 표를 바꾼 뒤나 테스트에서 쓴다. */
    public void refresh() {
        table = null;
    }

    private Map<String, BenefitCategoryMapping> table() {
        Map<String, BenefitCategoryMapping> loaded = table;
        if (loaded == null) {
            loaded = mappings.findAll().stream()
                .collect(Collectors.toUnmodifiableMap(BenefitCategoryMapping::getCategoryCode, Function.identity()));
            table = loaded;
        }
        return loaded;
    }

}
