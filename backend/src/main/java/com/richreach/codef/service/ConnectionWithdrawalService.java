package com.richreach.codef.service;

import com.richreach.codef.config.CodefSyncProperties;
import com.richreach.codef.entity.CardCompany;
import com.richreach.codef.entity.CodefConnection;
import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.repository.CodefConnectionRepository;
import com.richreach.codef.repository.CodefOrgAccountRepository;
import com.richreach.codef.repository.CodefSyncJobRepository;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import com.richreach.global.util.Times;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴 때 사용자의 카드 연동 데이터를 정리한다. 두 단계로 나뉘고, 둘 다 탈퇴 서비스의 트랜잭션 안에서 불러야 한다.
 *
 * <ol>
 *   <li>{@link #removeLocalData}: 동기화 작업 기록, 카드사 계정, 연동을 지운다. 이 테이블들은 사용자 삭제에 따라 같이 지워지지
 *       않아서({@code ON DELETE CASCADE}가 없다) 직접 지워야 사용자 행을 지울 수 있다. 이 계정에서 수집한 보유카드와 거래는 DB의
 *       CASCADE로 같이 지워진다. 그리고 CODEF에서 지워야 할 계정 목록을 돌려준다.</li>
 *   <li>{@link #deleteAtCodef}: CODEF에서 그 카드사 계정을 지운다. <b>되돌릴 수 없는 호출이라 마지막 단계</b>이고, 하나라도 실패하면
 *       예외가 트랜잭션을 되돌려서 이쪽 데이터가 모두 그대로 남는다. (카드사 연결 해제 {@link ConnectionDisconnectService}와 같은
 *       순서다)</li>
 * </ol>
 * <b>남은 위험</b>: 카드사 계정이 여러 개일 때 중간에 실패하면, 앞에서 지운 계정은 CODEF에서 이미 삭제됐는데 이쪽은 되돌아간다.
 * 다시 탈퇴하면 이미 삭제된 계정에 CODEF가 어떤 응답을 주는지에 따라 실패할 수 있다. 그 응답은 아직 확인하지 못했다. 마찬가지로
 * CODEF 삭제에 성공한 직후 커밋이 실패해도 불일치가 생긴다.
 * 이미 해제한 계정({@code DELETED})은 CODEF에서 지운 것이므로 부르지 않는다. 지원하지 않는 카드사의 계정은 부를 수 없어서 건너뛴다.
 * 카드사 아이디와 비밀번호는 다루지 않고, 로그에는 건수만 남긴다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConnectionWithdrawalService {

    /** CODEF에서 지워야 하는 카드사 계정 하나. connectedId는 비밀이라 {@code toString}에 담지 않는다. */
    public record RemoteAccount(String connectedId, CardCompany company) {

        @Override
        public String toString() {
            return "RemoteAccount[company=" + company + "]";
        }

    }

    private final CodefConnectionRepository connections;
    private final CodefOrgAccountRepository orgAccounts;
    private final CodefSyncJobRepository jobs;
    private final CardCompanyAuthenticator authenticator;
    private final CodefSyncProperties syncProperties;
    private final Clock clock;

    /**
     * 사용자의 카드 연동 데이터를 지우고, CODEF에서 지워야 할 계정 목록을 돌려준다. 동기화가 진행 중이면
     * {@code CONN-007}이다. (같은 connectedId를 쓰는 작업이 있는 채로 지울 수 없다) 연동이 없으면 빈 목록이다.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public List<RemoteAccount> removeLocalData(long userId) {
        LocalDateTime now = Times.nowSeconds(clock);
        if (jobs.existsRunningSince(userId, now.minus(syncProperties.timeout()))) {
            throw new BusinessException(ErrorCode.SYNC_ALREADY_RUNNING);
        }
        Optional<CodefConnection> connection = connections.findByUserId(userId);
        if (connection.isEmpty()) {
            return List.of();
        }

        String connectedId = connection.get().getConnectedId();
        List<RemoteAccount> remote = new ArrayList<>();
        int skipped = 0;
        for (CodefOrgAccount account : orgAccounts.findByConnectionId(connection.get().getId())) {
            if (CodefOrgAccount.STATUS_DELETED.equals(account.getStatus())) {
                continue;
            }
            CardCompany company = CardCompany.fromOrganizationCode(account.getOrganization());
            if (company == null || !company.supported()) {
                skipped++;
                continue;
            }
            remote.add(new RemoteAccount(connectedId, company));
        }

        int deletedJobs = jobs.deleteAllByUserId(userId);
        int deletedAccounts = orgAccounts.deleteAllByUserId(userId);
        int deletedConnections = connections.deleteByUserId(userId);
        log.info("탈퇴: 카드 연동 정리 jobs={}, accounts={}, connections={}, codefTargets={}, skipped={}", deletedJobs,
            deletedAccounts, deletedConnections, remote.size(), skipped);
        return remote;
    }

    /** CODEF에서 카드사 계정을 지운다. 하나라도 실패하면 예외로 끝나고, 호출한 쪽의 트랜잭션이 모두 되돌린다. */
    public void deleteAtCodef(List<RemoteAccount> accounts) {
        for (RemoteAccount account : accounts) {
            authenticator.disconnect(account.connectedId(), account.company());
        }
        log.info("탈퇴: CODEF 카드사 계정 삭제 {}개", accounts.size());
    }

}
