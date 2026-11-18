package com.richreach.codef.service;

import com.richreach.codef.dto.AutoSyncResponse;
import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.repository.CodefOrgAccountRepository;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 카드사별 거래내역 자동 동기화 설정을 바꾼다. <b>설정 값만 저장한다.</b> 이 설정에 따라 서버가 주기적으로 동기화하는
 * 기능은 별도로 제공한다. 없거나 해제했거나 다른 사용자의 계정은 {@code CONN-001}이다. 같은 값으로 다시 바꿔도 오류가 아니다.
 */
@Service
@RequiredArgsConstructor
public class ConnectionAutoSyncService {

    private final CodefOrgAccountRepository orgAccounts;

    @Transactional
    public AutoSyncResponse change(long userId, long accountId, boolean enabled) {
        CodefOrgAccount account = orgAccounts.findActiveByUserIdAndIdIn(userId, List.of(accountId)).stream()
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.CONNECTION_NOT_FOUND));
        account.changeAutoSync(enabled);
        return new AutoSyncResponse(account.getId(), account.isAutoSyncEnabled());
    }

}
