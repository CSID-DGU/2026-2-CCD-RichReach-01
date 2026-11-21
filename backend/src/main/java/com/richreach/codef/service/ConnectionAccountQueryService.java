package com.richreach.codef.service;

import com.richreach.codef.dto.LinkedAccountResponse;
import com.richreach.codef.entity.CardCompany;
import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.repository.CodefOrgAccountRepository;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카드가 연결된 카드사 계정의 화면용 정보를 읽는다. 카드 도메인이 카드사 연동의 Entity와 Repository를 직접 알지 않고 이
 * 서비스로 읽는다. 다른 사용자의 계정이나 해제한 계정은 나오지 않는다.
 */
@Service
@RequiredArgsConstructor
public class ConnectionAccountQueryService {

    private final CodefOrgAccountRepository orgAccounts;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Optional<LinkedAccountResponse> find(long userId, long accountId) {
        return orgAccounts.findActiveByUserIdAndIdIn(userId, List.of(accountId)).stream().findFirst()
            .map(this::toResponse);
    }

    private LinkedAccountResponse toResponse(CodefOrgAccount account) {
        return new LinkedAccountResponse(account.getId(), CardCompany.fromOrganizationCode(account.getOrganization()),
            account.getLoginIdMasked(), account.getStatus(), account.isAutoSyncEnabled(),
            account.getLastSyncedAt() == null ? null
                : account.getLastSyncedAt().atZone(clock.getZone()).toOffsetDateTime());
    }

}
