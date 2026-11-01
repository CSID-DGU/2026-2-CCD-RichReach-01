package com.richreach.codef.controller;

import com.richreach.codef.dto.CardCompanyResponse;
import com.richreach.codef.service.CardCompanyService;
import com.richreach.global.response.ApiResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 카드 연동 화면(ONB-02A)에서 쓰는 카드사 목록. 로그인이 필요하다. */
@RestController
@RequestMapping("/api/card-companies")
public class CardCompanyController {

    private final CardCompanyService cardCompanyService;

    public CardCompanyController(CardCompanyService cardCompanyService) {
        this.cardCompanyService = cardCompanyService;
    }

    /** 연결할 카드사 목록. 지원하지 않는 카드사는 {@code supported=false}로 내려간다. */
    @GetMapping
    public ApiResponse<List<CardCompanyResponse>> list() {
        return ApiResponse.ok(cardCompanyService.list());
    }

}
