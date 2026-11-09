package com.richreach.global.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class PageResponseTest {

    @Test
    @DisplayName("Page를 항목과 페이지 정보로 옮긴다 (페이지 번호는 0부터)")
    void fromPage() {
        PageImpl<String> page = new PageImpl<>(List.of("a", "b"), PageRequest.of(1, 2), 5);

        PageResponse<String> response = PageResponse.from(page);

        assertThat(response.content()).containsExactly("a", "b");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(2);
        assertThat(response.totalElements()).isEqualTo(5);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.hasNext()).isTrue();
    }

    @Test
    @DisplayName("마지막 페이지는 hasNext가 false다")
    void lastPage() {
        PageImpl<String> page = new PageImpl<>(List.of("e"), PageRequest.of(2, 2), 5);

        assertThat(PageResponse.from(page).hasNext()).isFalse();
    }

    @Test
    @DisplayName("매퍼로 항목을 응답 DTO로 바꿔서 담는다")
    void fromPageWithMapper() {
        PageImpl<Integer> page = new PageImpl<>(List.of(1, 2), PageRequest.of(0, 10), 2);

        PageResponse<String> response = PageResponse.from(page, n -> "n" + n);

        assertThat(response.content()).containsExactly("n1", "n2");
        assertThat(response.totalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("결과가 없어도 빈 목록과 0 페이지 정보를 내려준다")
    void emptyPage() {
        PageResponse<String> response = PageResponse.from(new PageImpl<>(List.of(), PageRequest.of(0, 10), 0));

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.hasNext()).isFalse();
    }

}
