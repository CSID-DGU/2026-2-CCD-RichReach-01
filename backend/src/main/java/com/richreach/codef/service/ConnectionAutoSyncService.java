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
 * 카드사별 거래내역 자동 동기화 설정을 바꾼다. <b>설정 값만 저장한다.</b> 이 설정이 켜진 계정을 서버가 주기적으로
 * 동기화하는 일은 {@link CodefAutoSyncScheduler}가 맡고, 서버에서 {@code codef.auto-sync.enabled=true}일 때만 돈다. 없거나 해제했거나 다른 사용자의 계정은 {@code CONN-001}이다. 같은 값으로 다시 바꿔도 오류가 아니다.
 *
 * <p>다른 카드 연동 요청(재연결, 연결 해제, 동기화)과 같은 사용자 잠금을 먼저 잡는다. 재연결은 CODEF 인증을 기다리는 동안
 * 읽어 둔 계정을 마지막에 통째로 저장하므로, 잠금 없이 설정을 바꾸면 사용자가 끈 설정이 재연결 결과로 되돌아갈 수 있다.
 * 잠금을 잡으면 설정 변경은 진행 중인 요청이 끝난 뒤 최신 값을 읽고 저장한다.
 */
@Service
@RequiredArgsConstructor
public class ConnectionAutoSyncService {

    private final ConnectionUserLock userLock;
    private final CodefOrgAccountRepository orgAccounts;

    @Transactional
    public AutoSyncResponse change(long userId, long accountId, boolean enabled) {
        userLock.lock(userId);
        CodefOrgAccount account = orgAccounts.findActiveByUserIdAndIdIn(userId, List.of(accountId)).stream()
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.CONNECTION_NOT_FOUND));
        account.changeAutoSync(enabled);
        return new AutoSyncResponse(account.getId(), account.isAutoSyncEnabled());
    }

}
