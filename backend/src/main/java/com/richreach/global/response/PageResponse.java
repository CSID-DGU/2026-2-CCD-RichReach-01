package com.richreach.global.response;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * 목록 API의 공통 페이지네이션 응답. {@link ApiResponse}의 data에 담아 내려준다.
 * 페이지 번호는 Spring Data와 같이 0부터 시작한다.
 *
 * @param content       현재 페이지의 항목
 * @param page          현재 페이지 번호 (0부터)
 * @param size          요청한 페이지 크기
 * @param totalElements 전체 항목 수
 * @param totalPages    전체 페이지 수
 * @param hasNext       다음 페이지 존재 여부
 */
public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages,
                              boolean hasNext) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
            page.getTotalPages(), page.hasNext());
    }

    /** 엔티티 페이지를 응답 DTO로 바꿔서 내려줄 때 쓴다. */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return from(page.map(mapper));
    }

}
