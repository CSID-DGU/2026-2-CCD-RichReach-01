package com.richreach.codef.service;

import com.richreach.codef.dto.CardCompanyResponse;
import com.richreach.codef.entity.CardCompany;
import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.repository.CodefConnectionRepository;
import com.richreach.codef.repository.CodefOrgAccountRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 연결할 카드사 목록. 고정된 7개를 화면 순서대로 내려주고, 사용자가 이미 연결한 카드사를 표시한다. */
@Service
public class CardCompanyService {

    private final CodefConnectionRepository connections;
    private final CodefOrgAccountRepository orgAccounts;

    public CardCompanyService(CodefConnectionRepository connections, CodefOrgAccountRepository orgAccounts) {
        this.connections = connections;
        this.orgAccounts = orgAccounts;
    }

    @Transactional(readOnly = true)
    public List<CardCompanyResponse> list(long userId) {
        Set<String> connected = connections.findByUserId(userId)
            .map(connection -> orgAccounts.findByConnectionId(connection.getId()).stream()
                .filter(CodefOrgAccount::isConnected)
                .map(CodefOrgAccount::getOrganization)
                .collect(Collectors.toSet()))
            .orElse(Set.of());
        return Arrays.stream(CardCompany.values())
            .map(company -> CardCompanyResponse.of(company, connected.contains(company.organizationCode())))
            .toList();
    }

}
