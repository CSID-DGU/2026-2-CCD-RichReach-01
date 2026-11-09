package com.richreach.codef.service;

import com.richreach.codef.client.CodefApprovalClient;
import com.richreach.codef.client.CodefApprovalClient.ApprovalListResult;
import com.richreach.codef.dto.ApprovalProbeResponse;
import com.richreach.codef.entity.CodefOrgAccount;
import com.richreach.codef.repository.CodefOrgAccountRepository;
import com.richreach.global.exception.BusinessException;
import com.richreach.global.exception.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * [임시] 승인내역 응답 구조를 확인하기 위한 개발용 호출. 확인이 끝나면 삭제한다.
 *
 * <p>내 카드사 연결로 승인내역을 한 번 조회하고 <b>건수와 결과 코드만</b> 돌려준다. 가맹점명, 금액 같은 값은 응답과 로그
 * 어디에도 남기지 않는다. 응답의 구조(필드 이름과 형식)는 {@code CODEF_DEBUG_LOGSHAPE=true}일 때 값 없이 서버 로그로 확인한다.
 *
 * <p>호출 횟수 제한과 카드사의 접속 차단 위험이 있어서 조회 기간은 {@value #MAX_DAYS}일 이하, 오늘까지로 제한한다.
 * 카드 비밀번호는 보내지 않는다. (카드사가 카드번호나 비밀번호를 요구하면 입력 오류 코드만 돌아오고, 비밀번호를 틀려서 계정이
 * 잠기는 일은 없다.) 계정 제한 중인 계정은 건너뛴다.
 */
@Slf4j
@Profile("local")
@Service
@RequiredArgsConstructor
public class CodefApprovalProbeService {

    static final int MAX_DAYS = 31;
    static final int DEFAULT_DAYS = 7;

    private final CodefOrgAccountRepository orgAccounts;
    private final CodefApprovalClient approvals;
    private final TransactionTemplate transaction;
    private final Clock clock;

    /**
     * @param accountId 조회할 카드사 계정 ID. 비우면 제한 중이 아닌 첫 계정
     * @param from      조회 시작일. 비우면 {@code to} 기준 {@value #DEFAULT_DAYS}일 전
     * @param to        조회 종료일. 비우면 오늘
     */
    public ApprovalProbeResponse probe(long userId, Long accountId, LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        LocalDate end = to == null ? today : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_DAYS - 1L) : from;
        if (start.isAfter(end) || end.isAfter(today) || ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE,
                "기간은 오늘까지, 최대 " + MAX_DAYS + "일이어야 합니다.");
        }

        // DB에서 읽는 일은 짧은 트랜잭션으로 끝내고, 카드사 호출은 트랜잭션 밖에서 한다.
        Target target = transaction.execute(status -> findTarget(userId, accountId));
        ApprovalListResult result = approvals.fetchApprovals(target.connectedId(), target.organization(), start, end);

        // 값은 남기지 않고 건수와 결과 코드만 남긴다.
        log.info("승인내역 확인 호출: accountId={}, codefCode={}, 응답={}건, 읽은 거래={}건", target.accountId(),
            result.code(), result.approvalCount(), result.approvals().size());
        return new ApprovalProbeResponse(result.success(), result.code(), start, end, result.approvalCount(),
            result.approvals().size());
    }

    private Target findTarget(long userId, Long accountId) {
        List<CodefOrgAccount> candidates = orgAccounts.findActiveByUserId(userId).stream()
            .filter(account -> !account.isLocked())
            .filter(account -> accountId == null || account.getId().equals(accountId)).toList();
        if (candidates.isEmpty()) {
            throw new BusinessException(ErrorCode.CONNECTION_NOT_FOUND);
        }
        CodefOrgAccount account = candidates.get(0);
        return new Target(account.getId(), account.getConnection().getConnectedId(), account.getOrganization());
    }

    /** connectedId는 로그에 남기지 않는다. */
    private record Target(Long accountId, String connectedId, String organization) {

        @Override
        public String toString() {
            return "Target[accountId=" + accountId + "]";
        }

    }

}
