package com.richreach.card.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.richreach.card.entity.BenefitCategoryMapping;
import com.richreach.card.repository.BenefitCategoryMappingRepository;
import com.richreach.global.category.SpendingCategory;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BenefitCategoryMapperSafetyTest {

    @Test
    void unknownPartOfCompositeCategoryDoesNotCreateAnEtcBenefit() {
        BenefitCategoryMappingRepository repository = mock(BenefitCategoryMappingRepository.class);
        BenefitCategoryMapping cafe = mock(BenefitCategoryMapping.class);
        when(cafe.getCategoryCode()).thenReturn("CAFE");
        when(cafe.spendingCategory()).thenReturn(Optional.of(SpendingCategory.CAFE));
        when(repository.findAll()).thenReturn(List.of(cafe));

        var mapped = new BenefitCategoryMapper(repository).map("CAFE|UNRECOGNIZED_CONDITION");

        assertThat(mapped).isEqualTo(BenefitCategoryMapper.MappedCategories.NONE);
    }
}
