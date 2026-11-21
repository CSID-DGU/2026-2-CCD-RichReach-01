package com.richreach.card.controller;

import com.richreach.card.dto.OwnedCardResponse;
import com.richreach.card.dto.SelectCardsRequest;
import com.richreach.card.service.OwnedCardQueryService;
import com.richreach.card.service.OwnedCardSelectionService;
import com.richreach.global.response.ApiResponse;
import com.richreach.global.security.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 보유카드 API. 로그인이 필요하다. */
@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
public class OwnedCardController {

    private final OwnedCardQueryService queryService;
    private final OwnedCardSelectionService selectionService;

    /**
     * 내 보유카드 목록. 카드 상품에 매칭된 카드는 {@code cardProductId}로 혜택을 조회할 수 있고({@code analysisStatus=AVAILABLE}),
     * 매칭되지 않은 카드는 {@code cardProductId}가 {@code null}이며 분석할 수 없다.
     */
    @GetMapping
    public ApiResponse<List<OwnedCardResponse>> list(@AuthenticationPrincipal AuthenticatedUser user) {
        return ApiResponse.ok(queryService.list(user.userId()));
    }

    /**
     * 분석 대상 카드를 선택한다. 보낸 카드 전체로 선택을 교체하고, 바뀐 뒤의 목록을 돌려준다.
     * 내 카드가 아니거나 없는 카드는 404({@code CARD-001}), 분석할 수 없는 카드는 400({@code CARD-002}),
     * 카드를 하나도 보내지 않으면 400({@code COMMON-400})이다.
     */
    @PutMapping("/selection")
    public ApiResponse<List<OwnedCardResponse>> select(@AuthenticationPrincipal AuthenticatedUser user,
                                                       @Valid @RequestBody SelectCardsRequest request) {
        return ApiResponse.ok(selectionService.select(user.userId(), request.cardIds()));
    }

}
