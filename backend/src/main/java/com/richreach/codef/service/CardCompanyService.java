package com.richreach.codef.service;

import com.richreach.codef.dto.CardCompanyResponse;
import com.richreach.codef.entity.CardCompany;
import java.util.Arrays;
import java.util.List;
import org.springframework.stereotype.Service;

/** 연결할 카드사 목록. 지금은 고정된 7개를 화면 순서대로 내려준다. */
@Service
public class CardCompanyService {

    /**
     * 기존 연결 여부는 카드사 연결을 만드는 API(POST /api/connections)가 생기면 그 결과로 채운다.
     * 그 전에는 연결이 있을 수 없으므로 모두 false다.
     */
    public List<CardCompanyResponse> list() {
        return Arrays.stream(CardCompany.values())
            .map(company -> CardCompanyResponse.of(company, false))
            .toList();
    }

}
