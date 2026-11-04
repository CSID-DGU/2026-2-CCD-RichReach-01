package com.richreach.codef.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 동기화 시작 요청. 본문을 보내지 않거나 {@code connectionIds}가 비어 있으면 연결된 카드사 전체를 동기화한다.
 *
 * @param connectionIds 동기화할 카드사 연결 ID({@code POST /api/connections}가 돌려준 {@code connectionId}). 일부만 다시
 *                      불러올 때 쓴다.
 */
public record ConnectionSyncRequest(
    @Size(max = 20, message = "한 번에 동기화할 카드사가 너무 많습니다.")
    List<@NotNull(message = "카드사 연결 ID에 빈 값이 있습니다.") Long> connectionIds) {

}
