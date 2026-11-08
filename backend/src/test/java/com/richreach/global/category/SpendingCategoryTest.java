package com.richreach.global.category;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpendingCategoryTest {

    @Test
    @DisplayName("화면용 업종은 6개다")
    void hasSixCategories() {
        assertThat(SpendingCategory.values()).extracting(Enum::name)
            .containsExactly("DINING", "CAFE", "SHOPPING", "TRANSPORT", "CONVENIENCE_STORE", "ETC");
    }

    @Test
    @DisplayName("코드 이름으로 찾는다 (대소문자와 앞뒤 공백 무시)")
    void findsByCode() {
        assertThat(SpendingCategory.fromCode("CAFE")).contains(SpendingCategory.CAFE);
        assertThat(SpendingCategory.fromCode(" convenience_store ")).contains(SpendingCategory.CONVENIENCE_STORE);
    }

    @Test
    @DisplayName("없거나 모르는 코드는 비어 있다")
    void unknownCodeIsEmpty() {
        assertThat(SpendingCategory.fromCode(null)).isEmpty();
        assertThat(SpendingCategory.fromCode("  ")).isEmpty();
        assertThat(SpendingCategory.fromCode("CONVENIENCE")).isEmpty();
        assertThat(SpendingCategory.fromCode("MOVIE")).isEmpty();
    }

}
