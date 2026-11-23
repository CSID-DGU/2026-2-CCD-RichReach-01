package com.richreach.user.service;

import com.richreach.codef.service.ConnectionWithdrawalService;
import com.richreach.codef.service.ConnectionWithdrawalService.RemoteAccount;
import com.richreach.user.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원 탈퇴. 사용자와 그 데이터를 <b>즉시 완전히 삭제</b>한다. (소프트 삭제와 복구 기간은 두지 않는다) 사용자 행을 지우면 토큰, 약관
 * 동의, 보유카드, 거래, 월간 플랜, 예정지출, 작업 기록은 DB의 {@code ON DELETE CASCADE}로 같이 지워진다. 이메일과 휴대폰이 사용자
 * 행의 유니크 키라서, 행이 사라지면 같은 이메일로 다시 가입할 수 있다.
 *
 * <p>한 트랜잭션 안에서 이 순서로 한다. 되돌릴 수 없는 CODEF 호출은 마지막이라, 그 앞의 단계가 실패하면 CODEF를 건드리지 않고, CODEF가
 * 실패하면 이쪽 삭제가 모두 되돌려져 계정이 그대로 남는다. 사용자는 다시 시도하면 된다.
 * <ol>
 *   <li>사용자 행을 잠근다. 같은 사용자의 카드 연동·플랜 요청과 겹치지 않는다. 이미 없는 사용자면 {@code AUTH-005}다.</li>
 *   <li>카드 연동 데이터를 지운다. 동기화가 진행 중이면 {@code CONN-007}로 막는다.</li>
 *   <li>사용자 행을 지우고 DB에 반영한다(flush). DB 쪽 오류는 CODEF 호출 전에 드러난다.</li>
 *   <li>CODEF에서 카드사 계정을 지운다.</li>
 * </ol>
 * 이미 발급된 access 토큰(최대 {@code jwt.access-token-ttl})은 서버가 요청마다 사용자를 조회하지 않아서 만료 전까지 인증을
 * 통과한다. 탈퇴한 사용자의 요청은 빈 결과나 오류로 끝난다. refresh 토큰은 같이 지워져 더는 갱신되지 않고, 앱은 탈퇴에 성공하면
 * 토큰을 바로 지운다. 이메일, 이름, 카드 정보는 로그에 남기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserWithdrawalService {

    private final UserLockService userLock;
    private final UserRepository users;
    private final ConnectionWithdrawalService connections;

    @Transactional
    public void withdraw(long userId) {
        userLock.lock(userId);
        List<RemoteAccount> remote = connections.removeLocalData(userId);
        users.deleteById(userId);
        users.flush();
        connections.deleteAtCodef(remote);
        log.info("회원 탈퇴 완료");
    }

}
